"""CRAFT-like Model Architecture for Conversational Forecasting (Head B).

Paper reference:
    "Conversations Gone Awry: Detecting Early Signs of Conversational Failure"
    Chang & Danescu-Niculescu-Mizil, EMNLP 2019 (https://arxiv.org/abs/1909.01362).

Architecture:
    1. Utterance projection:
       Linear(input_dim, proj_dim) + LayerNorm(proj_dim) + Dropout(p)
       Default: Linear(1024, 256) -> LayerNorm(256) -> Dropout(0.2)
    2. Context GRU:
       nn.GRU(input_size=256, hidden_size=128, num_layers=1, batch_first=True, dropout=0.0)
    3. Pooling:
       - 'last': hidden state at the last valid turn (lengths - 1)
       - 'attention': additive (Bahdanau) attention over turns, masked by lengths / padding_mask
    4. Classifier head:
       Linear(128, 64) -> GELU -> Dropout(0.2) -> Linear(64, 1)
       Returns raw uncalibrated logits of shape (B, 1).
"""

from __future__ import annotations

from typing import Optional, Tuple, Union

try:
    import torch
    import torch.nn as nn
    from torch.nn.utils.rnn import pack_padded_sequence, pad_packed_sequence
    _TORCH_AVAILABLE = True
except ImportError:
    torch = None
    nn = None
    pack_padded_sequence = None
    pad_packed_sequence = None
    _TORCH_AVAILABLE = False

_ModuleBase = nn.Module if _TORCH_AVAILABLE else object


class BahdanauAttention(_ModuleBase):
    """Additive (Bahdanau) attention pooling over conversation turns.

    Computes turn importance scores:
        e_t = v^T tanh(W_a h_t + b_a)
    and aggregates turn hidden states via softmax-normalized weights:
        c = sum_{t=1}^T alpha_t h_t
    where alpha_t is strictly 0 for padded positions.

    Args:
        hidden_dim: Dimensionality of GRU hidden states. Default: 128.
        attn_dim: Dimensionality of attention projection. Default: 64.
    """

    def __init__(self, hidden_dim: int = 128, attn_dim: int = 64) -> None:
        super().__init__()
        self.hidden_dim = hidden_dim
        self.attn_dim = attn_dim
        self.proj = nn.Linear(hidden_dim, attn_dim)
        self.v = nn.Linear(attn_dim, 1, bias=False)

    def forward(
        self,
        h: torch.Tensor,
        mask: Optional[torch.Tensor] = None,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        """Aggregate turn hidden states using additive attention.

        Args:
            h: Hidden state tensor of shape `(B, T, hidden_dim)`.
            mask: Optional boolean tensor of shape `(B, T)` where `True` indicates
                padding positions (turns to ignore), following PyTorch's
                `key_padding_mask` convention.

        Returns:
            context: Aggregated conversation vector of shape `(B, hidden_dim)`.
            weights: Attention weights of shape `(B, T)`, summing to 1.0 over valid turns.
        """
        # (B, T, hidden_dim) -> (B, T, attn_dim) -> (B, T, 1) -> (B, T)
        scores = self.v(torch.tanh(self.proj(h))).squeeze(-1)

        if mask is not None:
            # Mask padding turns with large negative value before softmax
            scores = scores.masked_fill(mask, -1e9)

        weights = torch.softmax(scores, dim=-1)

        if mask is not None:
            # Explicitly zero out padding weights for strict padding invariance
            weights = weights.masked_fill(mask, 0.0)
            norm = weights.sum(dim=-1, keepdim=True).clamp(min=1e-9)
            weights = weights / norm

        # Context: (B, 1, T) @ (B, T, hidden_dim) -> (B, 1, hidden_dim) -> (B, hidden_dim)
        context = torch.bmm(weights.unsqueeze(1), h).squeeze(1)
        return context, weights


class CRAFTModel(_ModuleBase):
    """CRAFT-like hierarchical recurrent model for conversational forecasting.

    Processes a sequence of utterance embeddings through a linear projection layer,
    a unidirectional GRU context encoder, turn-level pooling (last or Bahdanau attention),
    and a multi-layer perceptron classification head.

    Args:
        input_dim: Dimension of input utterance embeddings (e.g., 1024 for BGE-M3).
        proj_dim: Dimension of projected utterance representation. Default: 256.
        hidden_dim: Dimension of GRU hidden state. Default: 128.
        num_layers: Number of GRU layers. Default: 1.
        pooling: Pooling strategy over turns, either 'last' or 'attention'. Default: 'last'.
        attn_dim: Dimension of Bahdanau attention projection if pooling='attention'. Default: 64.
        proj_dropout: Dropout probability in utterance projection. Default: 0.2.
        classifier_dropout: Dropout probability in classification head. Default: 0.2.
        classifier_hidden_dim: Hidden dimension of classification head MLP. Default: 64.
        num_classes: Output dimension. Default: 1 (raw binary logit).
    """

    def __init__(
        self,
        input_dim: int = 1024,
        proj_dim: int = 256,
        hidden_dim: int = 128,
        num_layers: int = 1,
        pooling: str = "last",
        attn_dim: int = 64,
        proj_dropout: float = 0.2,
        classifier_dropout: float = 0.2,
        classifier_hidden_dim: int = 64,
        num_classes: int = 1,
    ) -> None:
        super().__init__()
        if pooling not in ("last", "attention"):
            raise ValueError(
                f"Invalid pooling mode '{pooling}'. Must be either 'last' or 'attention'."
            )

        self.input_dim = input_dim
        self.proj_dim = proj_dim
        self.hidden_dim = hidden_dim
        self.num_layers = num_layers
        self.pooling = pooling
        self.attn_dim = attn_dim
        self.classifier_hidden_dim = classifier_hidden_dim
        self.num_classes = num_classes

        # 1. Utterance projection: Linear(1024, 256) + LayerNorm(256) + Dropout(0.2)
        self.projection = nn.Sequential(
            nn.Linear(input_dim, proj_dim),
            nn.LayerNorm(proj_dim),
            nn.Dropout(proj_dropout),
        )

        # 2. Context GRU: nn.GRU(256, hidden_dim=128, num_layers=1, batch_first=True, dropout=0.0)
        self.gru = nn.GRU(
            input_size=proj_dim,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            dropout=0.0,
            bidirectional=False,
        )

        # 3. Bahdanau additive attention layer (used when pooling='attention')
        self.attention = BahdanauAttention(hidden_dim=hidden_dim, attn_dim=attn_dim)

        # 4. Classifier head: Linear(hidden_dim, 64) -> GELU -> Dropout(0.2) -> Linear(64, 1)
        self.classifier = nn.Sequential(
            nn.Linear(hidden_dim, classifier_hidden_dim),
            nn.GELU(),
            nn.Dropout(classifier_dropout),
            nn.Linear(classifier_hidden_dim, num_classes),
        )

    def _resolve_masks(
        self,
        x: torch.Tensor,
        lengths: Optional[Union[torch.Tensor, list[int]]] = None,
        padding_mask: Optional[torch.Tensor] = None,
    ) -> Tuple[torch.Tensor, torch.Tensor]:
        """Resolve lengths and padding mask tensors consistently.

        Returns:
            lengths: LongTensor of shape `(B,)` with sequence lengths clamped to `[1, T]`.
            padding_mask: BoolTensor of shape `(B, T)` where `True` indicates padding turns.
        """
        B, T = x.shape[0], x.shape[1]

        # Convert lengths to tensor on correct device if provided
        if lengths is not None:
            if not isinstance(lengths, torch.Tensor):
                resolved_lengths = torch.tensor(lengths, dtype=torch.long, device=x.device)
            else:
                resolved_lengths = lengths.to(device=x.device, dtype=torch.long)
            resolved_lengths = resolved_lengths.clamp(min=1, max=T)
        else:
            resolved_lengths = None

        # Convert padding_mask to boolean tensor on correct device if provided
        if padding_mask is not None:
            if not isinstance(padding_mask, torch.Tensor):
                resolved_mask = torch.tensor(padding_mask, device=x.device)
            else:
                resolved_mask = padding_mask.to(device=x.device)
            if resolved_mask.dtype != torch.bool:
                # If passed integer/float mask (1 = valid turn, 0 = padding), convert to boolean pad mask
                resolved_mask = (resolved_mask == 0)
        else:
            resolved_mask = None

        # Determine lengths and mask if either is missing
        if resolved_lengths is None and resolved_mask is None:
            resolved_lengths = torch.full((B,), T, dtype=torch.long, device=x.device)
            resolved_mask = torch.zeros((B, T), dtype=torch.bool, device=x.device)
        elif resolved_lengths is None:
            # Derived from padding_mask: count non-padding turns
            resolved_lengths = (~resolved_mask).long().sum(dim=-1).clamp(min=1, max=T)
        elif resolved_mask is None:
            # Derived from lengths: positions >= length are padding
            resolved_mask = torch.arange(T, device=x.device).unsqueeze(0) >= resolved_lengths.unsqueeze(1)

        return resolved_lengths, resolved_mask

    def encode_context(
        self,
        x: torch.Tensor,
        lengths: Optional[Union[torch.Tensor, list[int]]] = None,
        padding_mask: Optional[torch.Tensor] = None,
    ) -> Tuple[torch.Tensor, torch.Tensor, Optional[torch.Tensor]]:
        """Encode turn sequence through projection and GRU, returning pooled representation.

        Args:
            x: Input tensor of shape `(B, T, input_dim)` or `(B, input_dim)`.
            lengths: Sequence lengths `(B,)`.
            padding_mask: Boolean mask `(B, T)` where `True` indicates padding.

        Returns:
            pooled: Conversation vector of shape `(B, hidden_dim)`.
            gru_out: Full GRU output tensor `(B, T, hidden_dim)`.
            attn_weights: Attention weights `(B, T)` if pooling='attention', else None.
        """
        if x.dim() == 2:
            x = x.unsqueeze(1)
        B, T, _ = x.shape

        lengths_tensor, pad_mask = self._resolve_masks(x, lengths, padding_mask)

        # 1. Project utterances
        x_proj = self.projection(x)

        # 2. Context GRU with pack_padded_sequence for efficiency and strict padding invariance
        if (lengths_tensor < T).any():
            packed = pack_padded_sequence(
                x_proj,
                lengths_tensor.cpu(),
                batch_first=True,
                enforce_sorted=False,
            )
            gru_out, _ = self.gru(packed)
            gru_out, _ = pad_packed_sequence(gru_out, batch_first=True, total_length=T)
        else:
            gru_out, _ = self.gru(x_proj)

        # 3. Pooling over turns
        attn_weights: Optional[torch.Tensor] = None
        if self.pooling == "last":
            batch_indices = torch.arange(B, device=x.device)
            last_indices = lengths_tensor - 1
            pooled = gru_out[batch_indices, last_indices]
        elif self.pooling == "attention":
            pooled, attn_weights = self.attention(gru_out, mask=pad_mask)
        else:
            raise ValueError(f"Unknown pooling mode: {self.pooling}")

        return pooled, gru_out, attn_weights

    def forward(
        self,
        x: torch.Tensor,
        lengths: Optional[Union[torch.Tensor, list[int]]] = None,
        padding_mask: Optional[torch.Tensor] = None,
        return_attention: bool = False,
        return_pooled: bool = False,
    ) -> Union[torch.Tensor, Tuple[torch.Tensor, ...]]:
        """Forward pass through CRAFT model.

        Args:
            x: Input utterance embeddings of shape `(B, T, input_dim)` or `(B, input_dim)`.
            lengths: Optional sequence lengths for each conversation in batch, shape `(B,)`.
            padding_mask: Optional boolean mask of shape `(B, T)` where `True` indicates
                padding turns (ignored in attention and pooling).
            return_attention: If True, also return attention weights `(B, T)` (or None for 'last').
            return_pooled: If True, also return conversation embedding vector `(B, hidden_dim)`.

        Returns:
            logits: Raw logit tensor of shape `(B, 1)`.
            (Optional extra returns if return_attention or return_pooled is requested).
        """
        pooled, _, attn_weights = self.encode_context(x, lengths=lengths, padding_mask=padding_mask)

        # 4. Classification head
        logits = self.classifier(pooled)

        if not return_attention and not return_pooled:
            return logits

        returns = [logits]
        if return_pooled:
            returns.append(pooled)
        if return_attention:
            returns.append(attn_weights)
        return tuple(returns)

    def extract_features(
        self,
        x: torch.Tensor,
        lengths: Optional[Union[torch.Tensor, list[int]]] = None,
        padding_mask: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        """Extract conversation-level pooled feature vectors of shape `(B, hidden_dim)`.

        Useful for multi-task sharing (Head A / Head B) and representation probing.
        """
        pooled, _, _ = self.encode_context(x, lengths=lengths, padding_mask=padding_mask)
        return pooled

    def predict_proba(
        self,
        x: torch.Tensor,
        lengths: Optional[Union[torch.Tensor, list[int]]] = None,
        padding_mask: Optional[torch.Tensor] = None,
    ) -> torch.Tensor:
        """Predict sigmoid probabilities of shape `(B, 1)` for conversational derailment."""
        logits = self.forward(x, lengths=lengths, padding_mask=padding_mask)
        if isinstance(logits, tuple):
            logits = logits[0]
        return torch.sigmoid(logits)
