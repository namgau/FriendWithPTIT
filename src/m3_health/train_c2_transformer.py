"""C2 Training, Platt Calibration & Cross-Dataset Evaluation Pipeline.

Phụ trách C2:
1. Huấn luyện mô hình Transformer cho phân loại độc hại tiếng Việt trên H1 (ViHSD):
   - Hỗ trợ mô hình Transformer (PhoBERT `vinai/phobert-base-v2` / sentence-transformers / embedding classifier fallback nếu CPU).
   - Tích hợp GroupKFold K=5 (dùng `src.eval.splits.group_kfold` và `held_out_group_split` 80/20 train/cal).
   - Hiệu chỉnh Platt scaling qua `src.eval.calibration.fit_calibrator`.
   - Quét ngưỡng tau_B với FPR <= 5% qua `src.eval.threshold.threshold_by_fpr`.
   - Đánh giá Cross-dataset zero-shot trên H2 train (8,001 bình luận - Thí nghiệm X12).
   - Lưu artifact hoàn chỉnh vào `models/m3/c2/` gồm `config.json`, `metadata.json`, `calibrator.pkl`.
   - Ghi kết quả vào `experiments/ledger.csv` (owner=C, module=m3_headB).
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import logging
from pathlib import Path
import pickle
from typing import Any, Sequence

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    brier_score_loss,
    f1_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from src.eval.calibration import ece, fit_calibrator
from src.eval.report import append_ledger, data_hash, git_commit, mean_sd
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.c2_toxicity_data import (
    ToxicityDataset,
    WeightedBCEWithLogitsLoss,
    load_h1_train_data,
    load_h2_train_data,
)
from src.m3_health.embedding_cache import has_cache, load_cache
from src.m3_health.labels import PROC, ROOT, load_config

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 1. Platt Calibrator (Picklable & Callable)
# ---------------------------------------------------------------------------

class PlattCalibrator:
    """Hiệu chỉnh xác suất bằng Platt scaling (sigmoid mapping trên logit thô).

    Lớp này picklable (khắc phục giới hạn lambda của fit_calibrator) và có thể gọi
    trực tiếp như một hàm Callable: p_calibrated = calibrator(scores_raw).
    """

    def __init__(self, lr: LogisticRegression | None = None) -> None:
        self.lr = lr

    def _logit(self, p: Sequence[float] | np.ndarray) -> np.ndarray:
        p_arr = np.clip(np.asarray(p, dtype=float), 1e-6, 1.0 - 1e-6)
        return np.log(p_arr / (1.0 - p_arr))

    def fit(
        self,
        scores: Sequence[float] | np.ndarray,
        y: Sequence[int | float] | np.ndarray,
    ) -> PlattCalibrator:
        y_arr = np.asarray(y, dtype=int)
        if len(np.unique(y_arr)) < 2:
            self.lr = None
            return self
        logit_features = self._logit(scores).reshape(-1, 1)
        self.lr = LogisticRegression(C=1e6).fit(logit_features, y_arr)
        return self

    def predict_proba(self, scores: Sequence[float] | np.ndarray) -> np.ndarray:
        if self.lr is None:
            return np.clip(np.asarray(scores, dtype=float), 0.0, 1.0)
        logit_features = self._logit(scores).reshape(-1, 1)
        return self.lr.predict_proba(logit_features)[:, 1]

    def __call__(self, scores: Sequence[float] | np.ndarray) -> np.ndarray:
        return self.predict_proba(scores)


# ---------------------------------------------------------------------------
# 2. Classifier Implementations (PhoBERT, Sentence-Transformers, Fallback)
# ---------------------------------------------------------------------------

class EmbeddingClassifierFallback:
    """Bộ phân loại fallback dựa trên vector embedding (BGE-M3 / TF-IDF) + LogisticRegression.

    Phù hợp chạy trên CPU với tốc độ tính bằng mili-giây, hỗ trợ cả vector nén sẵn lẫn
    chuỗi ký tự văn bản mới khi suy luận phục vụ serving.
    """

    def __init__(
        self,
        seed: int = 42,
        class_weight: str | None = "balanced",
        max_iter: int = 2000,
    ) -> None:
        self.seed = seed
        self.class_weight = class_weight
        self.max_iter = max_iter
        self.scaler = StandardScaler(with_mean=False)
        self.clf = LogisticRegression(
            C=1.0,
            class_weight=self.class_weight,
            max_iter=self.max_iter,
            solver="liblinear",
            random_state=self.seed,
        )
        self.vectorizer: TfidfVectorizer | None = None
        self.fitted_on_dense: bool = False

    def fit(
        self,
        X: np.ndarray | Sequence[str] | pd.Series,
        y: Sequence[int] | np.ndarray,
    ) -> EmbeddingClassifierFallback:
        y_arr = np.asarray(y, dtype=int)
        if isinstance(X, np.ndarray) and X.ndim == 2:
            self.fitted_on_dense = True
            X_scaled = self.scaler.fit_transform(X)
            self.clf.fit(X_scaled, y_arr)
        else:
            self.fitted_on_dense = False
            texts = ["" if pd.isna(t) else str(t) for t in X]
            self.vectorizer = TfidfVectorizer(
                ngram_range=(1, 2),
                max_features=10000,
                sublinear_tf=True,
                min_df=2,
            )
            X_feats = self.vectorizer.fit_transform(texts)
            self.clf.fit(X_feats, y_arr)
        return self

    def predict_proba(
        self,
        X: np.ndarray | Sequence[str] | pd.Series,
    ) -> np.ndarray:
        if isinstance(X, np.ndarray) and X.ndim == 2:
            X_scaled = self.scaler.transform(X)
            return self.clf.predict_proba(X_scaled)[:, 1]
        elif self.fitted_on_dense and self.vectorizer is None:
            raise ValueError(
                "Mô hình được fit trên ma trận embedding; cần truyền ma trận embedding hoặc đã fit vectorizer."
            )
        else:
            texts = ["" if pd.isna(t) else str(t) for t in X]
            if self.vectorizer is None:
                raise ValueError("Chưa fit vectorizer cho mô hình văn bản.")
            X_feats = self.vectorizer.transform(texts)
            return self.clf.predict_proba(X_feats)[:, 1]


class SentenceTransformerClassifier:
    """Phân loại độc hại qua SentenceTransformer (MiniLM/BGE-M3) + LogisticRegression."""

    def __init__(
        self,
        model_name: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2",
        seed: int = 42,
        device: str = "cpu",
    ) -> None:
        self.model_name = model_name
        self.seed = seed
        self.device = device
        self._encoder = None
        self.clf = LogisticRegression(
            C=1.0,
            class_weight="balanced",
            max_iter=2000,
            solver="liblinear",
            random_state=self.seed,
        )

    def _get_encoder(self):
        if self._encoder is None:
            from sentence_transformers import SentenceTransformer
            self._encoder = SentenceTransformer(self.model_name, device=self.device)
        return self._encoder

    def fit(
        self,
        texts: Sequence[str] | pd.Series,
        y: Sequence[int] | np.ndarray,
        batch_size: int = 32,
    ) -> SentenceTransformerClassifier:
        encoder = self._get_encoder()
        raw_texts = ["" if pd.isna(t) else str(t) for t in texts]
        embeddings = encoder.encode(
            raw_texts,
            batch_size=batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
            device=self.device,
        )
        self.clf.fit(embeddings, np.asarray(y, dtype=int))
        return self

    def predict_proba(
        self,
        texts: Sequence[str] | pd.Series,
        batch_size: int = 32,
    ) -> np.ndarray:
        encoder = self._get_encoder()
        raw_texts = ["" if pd.isna(t) else str(t) for t in texts]
        embeddings = encoder.encode(
            raw_texts,
            batch_size=batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
            device=self.device,
        )
        return self.clf.predict_proba(embeddings)[:, 1]


class PhoBERTClassifier:
    """Mô hình phân loại độc hại bằng PhoBERT (vinai/phobert-base-v2)."""

    def __init__(
        self,
        model_name: str = "vinai/phobert-base-v2",
        max_length: int = 128,
        lr: float = 2e-5,
        batch_size: int = 16,
        epochs: int = 2,
        seed: int = 42,
        device: str | None = None,
    ) -> None:
        import torch

        self.model_name = model_name
        self.max_length = max_length
        self.lr = lr
        self.batch_size = batch_size
        self.epochs = epochs
        self.seed = seed
        if device is None:
            self.device = "cuda" if torch.cuda.is_available() else "cpu"
        else:
            self.device = device
        self.tokenizer = None
        self.model = None

    def _init_components(self) -> None:
        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer

        if self.tokenizer is None:
            self.tokenizer = AutoTokenizer.from_pretrained(self.model_name, use_fast=False)
        if self.model is None:
            torch.manual_seed(self.seed)
            self.model = AutoModelForSequenceClassification.from_pretrained(
                self.model_name,
                num_labels=1,
            )
            self.model.to(self.device)

    def fit(
        self,
        texts: Sequence[str] | pd.Series,
        y: Sequence[int | float] | np.ndarray,
    ) -> PhoBERTClassifier:
        import torch
        from torch.utils.data import DataLoader

        self._init_components()
        df = pd.DataFrame({"text": list(texts), "label": list(y)})
        dataset = ToxicityDataset(
            df,
            tokenizer=self.tokenizer,
            max_length=self.max_length,
            text_col="text",
            label_col="label",
        )
        loader = DataLoader(dataset, batch_size=self.batch_size, shuffle=True)
        loss_fn = WeightedBCEWithLogitsLoss.from_labels(y)
        optimizer = torch.optim.AdamW(self.model.parameters(), lr=self.lr, weight_decay=0.01)

        self.model.train()
        for epoch in range(self.epochs):
            for batch in loader:
                optimizer.zero_grad()
                input_ids = batch["input_ids"].to(self.device)
                attention_mask = batch["attention_mask"].to(self.device)
                labels = batch["label"].to(self.device)

                outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
                logits = outputs.logits.squeeze(-1)
                loss = loss_fn(logits, labels)
                loss.backward()
                optimizer.step()
        return self

    def predict_proba(
        self,
        texts: Sequence[str] | pd.Series,
        batch_size: int = 32,
    ) -> np.ndarray:
        import torch
        from torch.utils.data import DataLoader

        self._init_components()
        df = pd.DataFrame({"text": list(texts), "label": [0] * len(texts)})
        dataset = ToxicityDataset(
            df,
            tokenizer=self.tokenizer,
            max_length=self.max_length,
            text_col="text",
            label_col="label",
        )
        loader = DataLoader(dataset, batch_size=batch_size, shuffle=False)

        self.model.eval()
        probs: list[float] = []
        with torch.no_grad():
            for batch in loader:
                input_ids = batch["input_ids"].to(self.device)
                attention_mask = batch["attention_mask"].to(self.device)
                outputs = self.model(input_ids=input_ids, attention_mask=attention_mask)
                logits = outputs.logits.squeeze(-1)
                p = torch.sigmoid(logits).detach().cpu().numpy().tolist()
                if isinstance(p, float):
                    probs.append(p)
                else:
                    probs.extend(p)
        return np.asarray(probs, dtype=np.float32)


def build_classifier(
    model_type: str = "auto",
    seed: int = 42,
    epochs: int = 1,
) -> tuple[Any, str]:
    """Khởi tạo mô hình dựa trên cấu hình yêu cầu và phần cứng hiện hữu."""
    try:
        import torch
        has_cuda = torch.cuda.is_available()
    except ImportError:
        torch = None
        has_cuda = False

    if model_type == "phobert":
        if torch is None:
            raise ImportError("PyTorch is required for PhoBERTClassifier. Please install torch.")
        return PhoBERTClassifier(seed=seed, epochs=epochs), "phobert"
    elif model_type == "sentence-transformer":
        device = "cuda" if has_cuda else "cpu"
        return SentenceTransformerClassifier(seed=seed, device=device), "sentence-transformer"
    elif model_type == "embedding_fallback":
        return EmbeddingClassifierFallback(seed=seed), "embedding_fallback"
    else:  # auto
        if has_cuda:
            return PhoBERTClassifier(seed=seed, epochs=epochs), "phobert"
        else:
            # Fallback trên CPU đảm bảo chạy nhanh theo đúng tài nguyên được giao
            return EmbeddingClassifierFallback(seed=seed), "embedding_fallback"


# ---------------------------------------------------------------------------
# 3. Embedding Matrix Alignment Helper
# ---------------------------------------------------------------------------

def align_cached_embeddings(
    dataset: str,
    df: pd.DataFrame,
) -> np.ndarray | None:
    """Trích xuất ma trận embedding từ cache B8 theo thứ tự dòng của df."""
    if not has_cache(dataset):
        return None
    try:
        emb, idx = load_cache(dataset)
        row_map = pd.Series(np.arange(len(idx)), index=idx["conversation_id"].to_numpy())
        pos = row_map.reindex(df["conversation_id"].to_numpy())
        if pos.isna().any():
            return None
        return np.asarray(emb[pos.to_numpy(np.int64)], dtype=np.float32)
    except Exception as exc:
        logger.warning(f"Không thể đọc embedding cache cho {dataset}: {exc}")
        return None


# ---------------------------------------------------------------------------
# 4. GroupKFold K=5 CV Pipeline (Train/Val/Cal, Leak-Free, Platt Calibration)
# ---------------------------------------------------------------------------

def run_group_cv(
    df: pd.DataFrame,
    k_folds: int = 5,
    seed: int = 42,
    cal_fraction: float = 0.2,
    threshold_fpr: float = 0.05,
    model_type: str = "auto",
    X_emb: np.ndarray | None = None,
) -> tuple[pd.DataFrame, dict]:
    """GroupKFold K=5 với inner held-out 80/20 train/cal và Platt calibration.

    Giao thức chuẩn:
    - 5 folds outer: chia theo group_id, phân tầng theo nhãn y.
    - Outer train tiếp tục tách 80% fit, 20% cal theo group_id (không rò rỉ sang validation/test).
    - Calibrator fit trên tập cal; tau_B quét trên tập cal với FPR <= threshold_fpr.
    - Đo PR-AUC, ROC-AUC, Brier, ECE và Confusion matrix trên tập validation độc lập.
    """
    groups = df["group_id"].to_numpy()
    y = df["label"].to_numpy(int)
    texts = df["text"].tolist()

    fold_records: list[dict] = []

    for fold, (tr, va) in enumerate(group_kfold(groups, k=k_folds, seed=seed, y=y)):
        fit_idx, cal_idx = held_out_group_split(
            groups[tr], test_fraction=cal_fraction, seed=seed + fold, y=y[tr]
        )
        fit, cal = tr[fit_idx], tr[cal_idx]

        # Kiểm tra chống rò rỉ group tuyệt đối
        assert_no_group_leak(groups, fit, cal)
        assert_no_group_leak(groups, fit, va)
        assert_no_group_leak(groups, cal, va)

        # Khởi tạo mô hình cho fold
        model, resolved_tag = build_classifier(model_type=model_type, seed=seed + fold)

        # Huấn luyện mô hình trên fit fold
        if isinstance(model, EmbeddingClassifierFallback) and X_emb is not None:
            model.fit(X_emb[fit], y[fit])
            scores_cal = model.predict_proba(X_emb[cal])
            scores_va = model.predict_proba(X_emb[va])
        else:
            fit_texts = [texts[i] for i in fit]
            cal_texts = [texts[i] for i in cal]
            va_texts = [texts[i] for i in va]
            model.fit(fit_texts, y[fit])
            scores_cal = model.predict_proba(cal_texts)
            scores_va = model.predict_proba(va_texts)

        # Hiệu chỉnh xác suất bằng Platt scaling trên tập cal
        calibrator = PlattCalibrator().fit(scores_cal, y[cal])
        p_cal = calibrator(scores_cal)
        p_va = calibrator(scores_va)

        # Quét ngưỡng tau_B trên tập cal với FPR <= 5%
        tau_info = threshold_by_fpr(y[cal], p_cal, max_fpr=threshold_fpr)
        tau_B = float(tau_info["threshold"])
        if np.isinf(tau_B) or tau_B > 1.0:
            tau_B = 1.0

        # Đánh giá trên tập validation ngoài
        at = confusion_at(y[va], p_va, tau_B)
        pr_auc_val = float(average_precision_score(y[va], p_va))
        roc_auc_val = float(roc_auc_score(y[va], p_va))
        brier_val = float(brier_score_loss(y[va], p_va))
        ece_cal_val = float(ece(y[va], p_va))
        ece_uncal_val = float(ece(y[va], scores_va))

        fold_records.append({
            "fold": fold,
            "n_fit": len(fit),
            "n_cal": len(cal),
            "n_val": len(va),
            "prev_val": float(y[va].mean()),
            "tau_b": tau_B,
            "pr_auc": pr_auc_val,
            "roc_auc": roc_auc_val,
            "brier": brier_val,
            "ece": ece_cal_val,
            "ece_uncalibrated": ece_uncal_val,
            "recall_at_tau": float(at["recall"]),
            "fpr_at_tau": float(at["fpr"]),
            "precision_at_tau": float(at["precision"]),
            "f1_at_tau": float(at["f1"]),
        })

    cv_df = pd.DataFrame(fold_records)

    summary = {
        "model_type": resolved_tag,
        "k_folds": k_folds,
        "pr_auc_mean": float(cv_df["pr_auc"].mean()),
        "pr_auc_sd": float(cv_df["pr_auc"].std(ddof=1)),
        "roc_auc_mean": float(cv_df["roc_auc"].mean()),
        "roc_auc_sd": float(cv_df["roc_auc"].std(ddof=1)),
        "brier_mean": float(cv_df["brier"].mean()),
        "brier_sd": float(cv_df["brier"].std(ddof=1)),
        "ece_mean": float(cv_df["ece"].mean()),
        "ece_sd": float(cv_df["ece"].std(ddof=1)),
        "ece_uncalibrated_mean": float(cv_df["ece_uncalibrated"].mean()),
        "ece_uncalibrated_sd": float(cv_df["ece_uncalibrated"].std(ddof=1)),
        "recall_at_tau_mean": float(cv_df["recall_at_tau"].mean()),
        "recall_at_tau_sd": float(cv_df["recall_at_tau"].std(ddof=1)),
        "fpr_at_tau_mean": float(cv_df["fpr_at_tau"].mean()),
        "fpr_at_tau_sd": float(cv_df["fpr_at_tau"].std(ddof=1)),
        "precision_at_tau_mean": float(cv_df["precision_at_tau"].mean()),
        "precision_at_tau_sd": float(cv_df["precision_at_tau"].std(ddof=1)),
        "f1_at_tau_mean": float(cv_df["f1_at_tau"].mean()),
        "f1_at_tau_sd": float(cv_df["f1_at_tau"].std(ddof=1)),
        "tau_b_mean": float(cv_df["tau_b"].mean()),
    }
    return cv_df, summary


# ---------------------------------------------------------------------------
# 5. Final Model Training & Calibration on H1 Train
# ---------------------------------------------------------------------------

def train_final_h1_model(
    df_h1: pd.DataFrame,
    seed: int = 42,
    cal_fraction: float = 0.2,
    threshold_fpr: float = 0.05,
    model_type: str = "auto",
    X_emb: np.ndarray | None = None,
) -> tuple[Any, PlattCalibrator, float, dict]:
    """Huấn luyện mô hình chốt trên H1 train với 80/20 train/cal split theo group."""
    groups = df_h1["group_id"].to_numpy()
    y = df_h1["label"].to_numpy(int)
    texts = df_h1["text"].tolist()

    fit_idx, cal_idx = held_out_group_split(
        groups, test_fraction=cal_fraction, seed=seed, y=y
    )
    assert_no_group_leak(groups, fit_idx, cal_idx)

    model, resolved_tag = build_classifier(model_type=model_type, seed=seed)

    if isinstance(model, EmbeddingClassifierFallback) and X_emb is not None:
        model.fit(X_emb[fit_idx], y[fit_idx])
        scores_cal = model.predict_proba(X_emb[cal_idx])
    else:
        fit_texts = [texts[i] for i in fit_idx]
        cal_texts = [texts[i] for i in cal_idx]
        model.fit(fit_texts, y[fit_idx])
        scores_cal = model.predict_proba(cal_texts)

    calibrator = PlattCalibrator().fit(scores_cal, y[cal_idx])
    p_cal = calibrator(scores_cal)

    tau_info = threshold_by_fpr(y[cal_idx], p_cal, max_fpr=threshold_fpr)
    tau_B = float(tau_info["threshold"])
    if np.isinf(tau_B) or tau_B > 1.0:
        tau_B = 1.0

    cal_eval = confusion_at(y[cal_idx], p_cal, tau_B)
    cal_meta = {
        "model_type": resolved_tag,
        "n_fit": len(fit_idx),
        "n_cal": len(cal_idx),
        "tau_b": tau_B,
        "cal_prevalence": float(y[cal_idx].mean()),
        "cal_pr_auc": float(average_precision_score(y[cal_idx], p_cal)),
        "cal_roc_auc": float(roc_auc_score(y[cal_idx], p_cal)),
        "cal_brier": float(brier_score_loss(y[cal_idx], p_cal)),
        "cal_ece": float(ece(y[cal_idx], p_cal)),
        "cal_ece_uncalibrated": float(ece(y[cal_idx], scores_cal)),
        "cal_recall_at_tau": float(cal_eval["recall"]),
        "cal_fpr_at_tau": float(cal_eval["fpr"]),
        "cal_precision_at_tau": float(cal_eval["precision"]),
        "cal_f1_at_tau": float(cal_eval["f1"]),
    }
    return model, calibrator, tau_B, cal_meta


# ---------------------------------------------------------------------------
# 6. Cross-Dataset Zero-Shot on H2 Train (8,001 comments - X12)
# ---------------------------------------------------------------------------

def evaluate_cross_dataset_x12(
    model: Any,
    calibrator: PlattCalibrator,
    tau_B: float,
    df_h2: pd.DataFrame,
    X_emb_h2: np.ndarray | None = None,
) -> dict:
    """Đánh giá zero-shot cross-dataset từ H1 sang H2 (8,001 bình luận - Thí nghiệm X12)."""
    y_h2 = df_h2["label"].to_numpy(int)
    texts_h2 = df_h2["text"].tolist()

    if isinstance(model, EmbeddingClassifierFallback) and X_emb_h2 is not None:
        scores_h2 = model.predict_proba(X_emb_h2)
    else:
        scores_h2 = model.predict_proba(texts_h2)

    p_h2 = calibrator(scores_h2)
    at_h2 = confusion_at(y_h2, p_h2, tau_B)

    pr_auc_h2 = float(average_precision_score(y_h2, p_h2))
    roc_auc_h2 = float(roc_auc_score(y_h2, p_h2))
    brier_h2 = float(brier_score_loss(y_h2, p_h2))
    ece_h2 = float(ece(y_h2, p_h2))
    ece_uncal_h2 = float(ece(y_h2, scores_h2))

    return {
        "dataset": "H2 (UIT-ViCTSD)",
        "n_comments": len(df_h2),
        "prevalence": float(y_h2.mean()),
        "pr_auc": pr_auc_h2,
        "roc_auc": roc_auc_h2,
        "brier": brier_h2,
        "ece": ece_h2,
        "ece_uncalibrated": ece_uncal_h2,
        "tau_b_used": float(tau_B),
        "recall_at_tau": float(at_h2["recall"]),
        "fpr_at_tau": float(at_h2["fpr"]),
        "precision_at_tau": float(at_h2["precision"]),
        "f1_at_tau": float(at_h2["f1"]),
        "tp": int(at_h2["tp"]),
        "fp": int(at_h2["fp"]),
        "fn": int(at_h2["fn"]),
        "tn": int(at_h2["tn"]),
    }


# ---------------------------------------------------------------------------
# 7. Artifact Export (`models/m3/c2/`)
# ---------------------------------------------------------------------------

def export_c2_artifacts(
    output_dir: Path | str,
    config_dict: dict,
    metadata_dict: dict,
    calibrator: PlattCalibrator,
    model: Any | None = None,
) -> dict[str, Path]:
    """Lưu artifact hoàn chỉnh vào models/m3/c2/ gồm config.json, metadata.json, calibrator.pkl, model.pkl."""
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)

    config_path = out / "config.json"
    with open(config_path, "w", encoding="utf-8") as f:
        json.dump(config_dict, f, indent=2, ensure_ascii=False)

    metadata_path = out / "metadata.json"
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata_dict, f, indent=2, ensure_ascii=False)

    calibrator_path = out / "calibrator.pkl"
    with open(calibrator_path, "wb") as f:
        pickle.dump(calibrator, f)

    paths = {
        "config": config_path,
        "metadata": metadata_path,
        "calibrator": calibrator_path,
    }

    if model is not None:
        model_path = out / "model.pkl"
        with open(model_path, "wb") as f:
            pickle.dump(model, f)
        paths["model"] = model_path

    return paths


# ---------------------------------------------------------------------------
# 8. Record Results in Ledger
# ---------------------------------------------------------------------------

def record_c2_to_ledger(
    cv_summary: dict,
    x12_results: dict,
    model_tag: str,
    seed: int,
    data_files: list[Path] | None = None,
) -> list[dict]:
    """Ghi nhận kết quả thí nghiệm C2 vào experiments/ledger.csv."""
    cfg = load_config()
    hb = cfg.get("head_b", {})
    files = data_files or [
        PROC / "H1" / "labels_headB_train.parquet",
        PROC / "H2" / "labels_headB_train.parquet",
    ]
    hash_str = data_hash(*[p for p in files if p.exists()])

    rows = []
    # 1. Kết quả H1 in-domain 5-fold CV
    row_cv = append_ledger(
        module="m3_headB",
        owner="C",
        cfg=hb,
        data_files=hash_str,
        seeds=[seed],
        model=f"H1:{model_tag}",
        metric_name="pr_auc",
        values=[cv_summary["pr_auc_mean"]],
        metric_sd=cv_summary["pr_auc_sd"],
        notes=f"C2 transformer pipeline, group K-fold K=5, Platt calib, FPR<=5% (tau_B={cv_summary['tau_b_mean']:.4f})",
    )
    rows.append(row_cv)

    # 2. Kết quả H2 cross-dataset zero-shot (X12)
    row_x12 = append_ledger(
        module="m3_headB",
        owner="C",
        cfg=hb,
        data_files=hash_str,
        seeds=[seed],
        model=f"H2:x12_zero_shot:{model_tag}",
        metric_name="pr_auc",
        values=[x12_results["pr_auc"]],
        metric_sd=0.0,
        notes=f"X12 zero-shot transfer from H1 to H2 (8,001 comments), evaluated at tau_B={x12_results['tau_b_used']:.4f}",
    )
    rows.append(row_x12)

    return rows


# ---------------------------------------------------------------------------
# 9. Main Pipeline Driver
# ---------------------------------------------------------------------------

def run_pipeline(
    model_type: str = "auto",
    seed: int = 42,
    k_folds: int = 5,
    cal_fraction: float = 0.2,
    threshold_fpr: float = 0.05,
    output_dir: Path | str = ROOT / "models" / "m3" / "c2",
    smoke_test: bool = False,
    smoke_samples: int = 500,
    write_ledger: bool = True,
) -> dict:
    """Toàn bộ quy trình C2 từ H1 CV, Platt calibration, H2 zero-shot tới xuất artifact."""
    print("=" * 70)
    print("Bắt đầu C2 Training, Calibration & Cross-Dataset Evaluation Pipeline")
    print("=" * 70)

    # 1. Đọc dữ liệu H1 và H2 train
    df_h1 = load_h1_train_data()
    df_h2 = load_h2_train_data()

    if smoke_test:
        print(f"[Smoke Test] Cắt ngắn dữ liệu thử nghiệm: {smoke_samples} mẫu.")
        df_h1 = df_h1.sample(n=min(smoke_samples, len(df_h1)), random_state=seed).reset_index(drop=True)
        df_h2 = df_h2.sample(n=min(smoke_samples // 2, len(df_h2)), random_state=seed).reset_index(drop=True)

    print(f"H1 Train: {len(df_h1):,} dòng (prevalence {df_h1['label'].mean():.4f}, {df_h1['group_id'].nunique():,} groups)")
    print(f"H2 Train: {len(df_h2):,} dòng (prevalence {df_h2['label'].mean():.4f}, {df_h2['group_id'].nunique():,} groups)")

    # 2. Nạp precomputed embeddings nếu có
    X_emb_h1 = align_cached_embeddings("H1", df_h1) if not smoke_test else None
    X_emb_h2 = align_cached_embeddings("H2", df_h2) if not smoke_test else None
    if X_emb_h1 is not None and X_emb_h2 is not None:
        print(f"Embedding cache B8 sẵn sàng: H1={X_emb_h1.shape}, H2={X_emb_h2.shape}")

    # 3. Chạy GroupKFold K=5 trên H1
    print(f"\n--- BƯỚC 1: GroupKFold K={k_folds} trên H1 (ViHSD) ---")
    cv_df, cv_summary = run_group_cv(
        df_h1,
        k_folds=k_folds,
        seed=seed,
        cal_fraction=cal_fraction,
        threshold_fpr=threshold_fpr,
        model_type=model_type,
        X_emb=X_emb_h1,
    )
    print(f"Kết quả CV (mean ± SD):")
    print(f"  PR-AUC:           {cv_summary['pr_auc_mean']:.4f} ± {cv_summary['pr_auc_sd']:.4f}")
    print(f"  ROC-AUC:          {cv_summary['roc_auc_mean']:.4f} ± {cv_summary['roc_auc_sd']:.4f}")
    print(f"  Brier:            {cv_summary['brier_mean']:.4f} ± {cv_summary['brier_sd']:.4f}")
    print(f"  ECE chưa chỉnh:   {cv_summary['ece_uncalibrated_mean']:.4f} ± {cv_summary['ece_uncalibrated_sd']:.4f}")
    print(f"  ECE sau chỉnh:    {cv_summary['ece_mean']:.4f} ± {cv_summary['ece_sd']:.4f}")
    print(f"  Recall @ tau_B:   {cv_summary['recall_at_tau_mean']:.4f} ± {cv_summary['recall_at_tau_sd']:.4f}")
    print(f"  FPR @ tau_B:      {cv_summary['fpr_at_tau_mean']:.4f} ± {cv_summary['fpr_at_tau_sd']:.4f}")
    print(f"  tau_B trung bình: {cv_summary['tau_b_mean']:.4f}")

    # 4. Huấn luyện mô hình chốt và fit Calibrator trên H1
    print(f"\n--- BƯỚAL 2: Huấn luyện mô hình chốt & Platt Calibrator trên H1 ---")
    final_model, final_calibrator, tau_B, cal_meta = train_final_h1_model(
        df_h1,
        seed=seed,
        cal_fraction=cal_fraction,
        threshold_fpr=threshold_fpr,
        model_type=model_type,
        X_emb=X_emb_h1,
    )
    print(f"Mô hình chốt: {cal_meta['model_type']}")
    print(f"Tập fit: {cal_meta['n_fit']} mẫu, Tập cal: {cal_meta['n_cal']} mẫu")
    print(f"Ngưỡng triển khai tau_B (FPR <= {threshold_fpr:.0%}): {tau_B:.4f}")

    # 5. Đánh giá Zero-shot Cross-Dataset trên H2 train (8,001 bình luận - X12)
    print(f"\n--- BƯỚC 3: Cross-Dataset Zero-Shot trên H2 Train (X12) ---")
    x12_results = evaluate_cross_dataset_x12(
        final_model,
        final_calibrator,
        tau_B,
        df_h2,
        X_emb_h2=X_emb_h2,
    )
    print(f"Kết quả H2 (8,001 bình luận - Thí nghiệm X12):")
    print(f"  PR-AUC:         {x12_results['pr_auc']:.4f}")
    print(f"  ROC-AUC:        {x12_results['roc_auc']:.4f}")
    print(f"  Brier:          {x12_results['brier']:.4f}")
    print(f"  ECE chưa chỉnh: {x12_results['ece_uncalibrated']:.4f}")
    print(f"  ECE sau chỉnh:  {x12_results['ece']:.4f}")
    print(f"  Recall @ tau_B: {x12_results['recall_at_tau']:.4f}")
    print(f"  FPR @ tau_B:    {x12_results['fpr_at_tau']:.4f}")
    print(f"  Precision:      {x12_results['precision_at_tau']:.4f}")
    print(f"  Confusion:      TP={x12_results['tp']}, FP={x12_results['fp']}, FN={x12_results['fn']}, TN={x12_results['tn']}")

    # 6. Chuẩn bị metadata và config
    config_dict = {
        "module": "m3",
        "version": "c2",
        "model_name": "c2_toxicity_transformer",
        "model_type": cal_meta["model_type"],
        "seed": seed,
        "cv_folds": k_folds,
        "calibration_fraction": cal_fraction,
        "calibration_method": "platt",
        "threshold_fpr": threshold_fpr,
        "tau_b": tau_B,
        "training_dataset": "H1 (ViHSD)",
        "evaluation_dataset": "H2 (UIT-ViCTSD)",
    }

    metadata_dict = {
        "schema_version": 1,
        "module": "m3",
        "version": "c2",
        "model_name": "c2_toxicity_transformer",
        "model_type": cal_meta["model_type"],
        "date": dt.date.today().isoformat(),
        "git_commit": git_commit(),
        "seed": seed,
        "tau_b": tau_B,
        "h1_train_summary": {
            "n_rows": len(df_h1),
            "prevalence": float(df_h1["label"].mean()),
            "n_groups": int(df_h1["group_id"].nunique()),
        },
        "h1_cv_summary": cv_summary,
        "calibration_metadata": cal_meta,
        "h2_x12_zero_shot": x12_results,
    }

    # 7. Xuất artifact vào models/m3/c2/
    print(f"\n--- BƯỚC 4: Xuất artifact vào {output_dir} ---")
    exported_paths = export_c2_artifacts(
        output_dir=output_dir,
        config_dict=config_dict,
        metadata_dict=metadata_dict,
        calibrator=final_calibrator,
        model=final_model,
    )
    for name, p in exported_paths.items():
        print(f"  + {name}: {p} ({p.stat().st_size:,} bytes)")

    # 8. Ghi vào ledger
    if write_ledger and not smoke_test:
        print(f"\n--- BƯỚC 5: Ghi nhận thí nghiệm vào experiments/ledger.csv ---")
        ledger_rows = record_c2_to_ledger(
            cv_summary=cv_summary,
            x12_results=x12_results,
            model_tag=cal_meta["model_type"],
            seed=seed,
        )
        for row in ledger_rows:
            print(f"  + Ledger: {row['model']} | {row['metric_name']}={row['metric_mean']} ± {row['metric_sd']}")

    print("=" * 70)
    print("HOÀN TẤT C2 PIPELINE THÀNH CÔNG!")
    print("=" * 70)

    return {
        "cv_summary": cv_summary,
        "x12_results": x12_results,
        "tau_b": tau_B,
        "exported_paths": exported_paths,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Huấn luyện và đánh giá C2 Toxicity Pipeline.")
    parser.add_argument("--model-type", choices=["auto", "phobert", "sentence-transformer", "embedding_fallback"], default="auto")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--cv-folds", type=int, default=5)
    parser.add_argument("--cal-fraction", type=float, default=0.2)
    parser.add_argument("--threshold-fpr", type=float, default=0.05)
    parser.add_argument("--output-dir", type=str, default=str(ROOT / "models" / "m3" / "c2"))
    parser.add_argument("--smoke-test", action="store_true", help="Chạy thử nghiệm nhanh trên tập nhỏ.")
    parser.add_argument("--smoke-samples", type=int, default=500)
    parser.add_argument("--no-ledger", action="store_true", help="Không ghi vào experiments/ledger.csv.")
    args = parser.parse_args()

    run_pipeline(
        model_type=args.model_type,
        seed=args.seed,
        k_folds=args.cv_folds,
        cal_fraction=args.cal_fraction,
        threshold_fpr=args.threshold_fpr,
        output_dir=Path(args.output_dir),
        smoke_test=args.smoke_test,
        smoke_samples=args.smoke_samples,
        write_ledger=not args.no_ledger,
    )


if __name__ == "__main__":
    main()
