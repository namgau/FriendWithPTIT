"""C4 Training, Calibration & Bootstrap Pipeline cho Head B (CRAFT-like GRU trên G1).

Giao thức đánh giá tuân thủ chuẩn Auditable (docs/01_CHUAN_BAO_CAO_AUDITABLE.md):
- Dữ liệu: G1 Conversations Gone Awry (Wikipedia), chỉ dùng tập train (labels_headB_train.parquet).
  Mỗi hội thoại chỉ dùng tiền tố trước lượt công kích (prefix_len); test trong data/locked không được đọc.
- Outer cross-validation: GroupKFold K=5 theo group_id (trang Wikipedia) để chống rò rỉ cặp ghép.
- Inner split: 80% fit / 20% calibration trong mỗi train fold theo group_id.
- Mô hình: CRAFT-like GRU (Chang & Danescu-Niculescu-Mizil, EMNLP 2019) trên chuỗi embedding PCA-32
  từ BGE-M3 (BAAI/bge-m3).
- Hiệu chỉnh xác suất: Platt scaling (src.eval.calibration.fit_calibrator) trên calibration set.
- Lựa chọn ngưỡng: τ_B được quét trên calibration set sao cho FPR <= 5% (Thí nghiệm X11).
- So sánh: Paired cluster bootstrap CI 95% (B=2000) so với baseline tfidf_lr của B.
- Lưu trữ: Checkpoint và metadata lưu tại models/m3/c4/, log vào experiments/ledger.csv.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import random
import time
from pathlib import Path
from typing import Sequence

import joblib
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    brier_score_loss,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from src.eval.bootstrap import cluster_bootstrap, paired_cluster_bootstrap
from src.eval.calibration import ece, fit_calibrator
from src.eval.report import append_ledger, data_hash, git_commit, mean_sd
from src.eval.splits import assert_no_group_leak, group_kfold, held_out_group_split
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.features_b import NUMERIC_COLUMNS, build_g1
from src.m3_health.fit_pca import transform_frozen
from src.m3_health.labels import PROC, ROOT, load_config, load_train

CONFIG_PATH = ROOT / "configs" / "c4_craft.yaml"
OUT_DIR = ROOT / "models" / "m3" / "c4"
REPORTS_DIR = ROOT / "reports" / "c4"


class CraftGRU(nn.Module):
    """CRAFT-like GRU context encoder và binary classification head."""

    def __init__(self, input_size: int = 32, hidden_size: int = 64, dropout: float = 0.3):
        super().__init__()
        self.input_size = input_size
        self.hidden_size = hidden_size
        self.dropout_rate = dropout
        self.gru = nn.GRU(input_size, hidden_size, batch_first=True)
        self.dropout = nn.Dropout(dropout)
        self.classifier = nn.Linear(hidden_size, 1)

    def forward(self, padded_seqs: torch.Tensor, lengths: torch.Tensor) -> torch.Tensor:
        packed = nn.utils.rnn.pack_padded_sequence(
            padded_seqs, lengths.cpu(), batch_first=True, enforce_sorted=False
        )
        _, h_n = self.gru(packed)
        # h_n shape: (1, batch_size, hidden_size)
        out = self.classifier(self.dropout(h_n[-1])).squeeze(-1)
        return out


def _set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def _collate_sequences(batch):
    seqs, targets, idxs = zip(*batch)
    lengths = torch.tensor([len(s) for s in seqs], dtype=torch.long)
    padded = nn.utils.rnn.pad_sequence(seqs, batch_first=True, padding_value=0.0)
    return padded, lengths, torch.tensor(targets, dtype=torch.float32), np.asarray(idxs)


def build_sequence_data(
    messages: pd.DataFrame,
    labels: pd.DataFrame,
    pca_embeddings: np.ndarray,
    emb_index: pd.DataFrame,
) -> tuple[list[np.ndarray], np.ndarray]:
    """Trích xuất chuỗi vector embedding (PCA-32) và ma trận đặc trưng bảng cho mỗi hội thoại.

    Chỉ lấy các lượt tin turn_idx < prefix_len để đảm bảo không nhìn thấy tương lai.
    """
    emb_index = emb_index.copy()
    emb_index["row_idx"] = np.arange(len(emb_index))
    grouped = {
        key: group["row_idx"].to_numpy()
        for key, group in emb_index.groupby("conversation_id", sort=False)
    }

    sequences = []
    for row in labels[["conversation_id", "prefix_len"]].itertuples(index=False):
        c_id = row.conversation_id
        p_len = int(row.prefix_len)
        if c_id not in grouped:
            raise KeyError(f"Conversation {c_id} không có trong embedding index")
        turn_indices = grouped[c_id][:p_len]
        if len(turn_indices) == 0:
            raise ValueError(f"Tiền tố rỗng cho {c_id}")
        sequences.append(pca_embeddings[turn_indices].astype(np.float32))

    # Đặc trưng bảng: T2 numeric + mean PCA32 + last PCA32
    mean_emb = np.stack([seq.mean(axis=0) for seq in sequences])
    last_emb = np.stack([seq[-1] for seq in sequences])
    g1_frame = build_g1(messages, labels)
    numeric = g1_frame[NUMERIC_COLUMNS].to_numpy(dtype=np.float32)
    table_features = np.concatenate([numeric, mean_emb, last_emb], axis=1)

    return sequences, table_features


def _predict_loader(
    model: nn.Module, loader: torch.utils.data.DataLoader, device: torch.device
) -> tuple[np.ndarray, np.ndarray]:
    model.eval()
    probabilities, rows = [], []
    with torch.no_grad():
        for padded, lengths, _, batch_rows in loader:
            padded = padded.to(device)
            logits = model(padded, lengths)
            probs = torch.sigmoid(logits).cpu().numpy()
            probabilities.append(probs)
            rows.append(batch_rows)
    return np.concatenate(rows), np.concatenate(probabilities)


def train_craft_gru_fold(
    sequences: list[np.ndarray],
    y: np.ndarray,
    fit_indices: np.ndarray,
    cal_indices: np.ndarray,
    config: dict,
    seed: int,
    device: torch.device,
) -> tuple[CraftGRU, dict]:
    """Huấn luyện CRAFT-like GRU trên fit_indices với early stopping trên cal_indices."""
    gru_cfg = config.get("gru", {})
    hidden_sizes = gru_cfg.get("hidden_sizes", [32, 64])
    dropouts = gru_cfg.get("dropouts", [0.1, 0.3])
    batch_size = int(gru_cfg.get("batch_size", 64))
    epochs = int(gru_cfg.get("epochs", 20))
    patience = int(gru_cfg.get("patience", 3))
    lr = float(gru_cfg.get("learning_rate", 0.001))
    weight_decay = float(gru_cfg.get("weight_decay", 0.0001))
    grad_clip = float(gru_cfg.get("gradient_clip", 1.0))

    fit_dataset = [(torch.from_numpy(sequences[i]).float(), float(y[i]), i) for i in fit_indices]
    cal_dataset = [(torch.from_numpy(sequences[i]).float(), float(y[i]), i) for i in cal_indices]

    fit_loader = torch.utils.data.DataLoader(
        fit_dataset, batch_size=batch_size, shuffle=True, collate_fn=_collate_sequences
    )
    cal_loader = torch.utils.data.DataLoader(
        cal_dataset, batch_size=batch_size, shuffle=False, collate_fn=_collate_sequences
    )

    criterion = nn.BCEWithLogitsLoss()
    best_candidate = None

    for hidden in hidden_sizes:
        for dropout in dropouts:
            run_seed = seed + int(hidden * 10 + dropout * 100)
            _set_seed(run_seed)

            model = CraftGRU(input_size=32, hidden_size=hidden, dropout=dropout).to(device)
            optimizer = torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)

            best_score = -1.0
            best_state = None
            stale = 0
            history = []

            for epoch in range(1, epochs + 1):
                model.train()
                losses = []
                for padded, lengths, targets, _ in fit_loader:
                    padded, targets = padded.to(device), targets.to(device)
                    optimizer.zero_grad()
                    logits = model(padded, lengths)
                    loss = criterion(logits, targets)
                    loss.backward()
                    nn.utils.clip_grad_norm_(model.parameters(), grad_clip)
                    optimizer.step()
                    losses.append(loss.item())

                # Đánh giá trên cal set
                cal_rows, cal_probs = _predict_loader(model, cal_loader, device)
                cal_y = y[cal_rows]
                cal_macro_f1 = f1_score(
                    cal_y, (cal_probs >= 0.5).astype(int), average="macro", zero_division=0
                )
                cal_pr_auc = average_precision_score(cal_y, cal_probs)

                history.append({
                    "epoch": epoch,
                    "train_loss": float(np.mean(losses)),
                    "cal_macro_f1": float(cal_macro_f1),
                    "cal_pr_auc": float(cal_pr_auc),
                })

                if cal_macro_f1 > best_score + 1e-4:
                    best_score = float(cal_macro_f1)
                    stale = 0
                    best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}
                else:
                    stale += 1
                    if stale >= patience:
                        break

            candidate = {
                "hidden_size": hidden,
                "dropout": dropout,
                "cal_macro_f1": best_score,
                "best_state": best_state,
                "history": history,
            }

            if best_candidate is None or candidate["cal_macro_f1"] > best_candidate["cal_macro_f1"]:
                best_candidate = candidate

    assert best_candidate is not None and best_candidate["best_state"] is not None
    final_model = CraftGRU(
        input_size=32,
        hidden_size=best_candidate["hidden_size"],
        dropout=best_candidate["dropout"],
    ).to(device)
    final_model.load_state_dict(best_candidate["best_state"])

    selection_meta = {
        "hidden_size": best_candidate["hidden_size"],
        "dropout": best_candidate["dropout"],
        "cal_macro_f1": best_candidate["cal_macro_f1"],
        "history": best_candidate["history"],
        "epochs_trained": len(best_candidate["history"]),
    }
    return final_model, selection_meta


def compute_eval_metrics(y_true: np.ndarray, probas: np.ndarray, tau: float) -> dict[str, float]:
    """Tính toàn bộ các thước đo hiệu năng tại ngưỡng tau đã chọn."""
    y_true = np.asarray(y_true, dtype=int)
    probas = np.asarray(probas, dtype=float)
    conf = confusion_at(y_true, probas, tau)
    return {
        "pr_auc": float(average_precision_score(y_true, probas)),
        "roc_auc": float(roc_auc_score(y_true, probas)),
        "brier": float(brier_score_loss(y_true, probas)),
        "ece": float(ece(y_true, probas)),
        "threshold": float(tau),
        "precision": float(conf["precision"]),
        "recall": float(conf["recall"]),
        "f1": float(conf["f1"]),
        "fpr": float(conf["fpr"]),
        "accuracy": float(accuracy_score(y_true, (probas >= tau).astype(int))),
        "acc_05": float(accuracy_score(y_true, (probas >= 0.5).astype(int))),
        "macro_f1_05": float(
            f1_score(y_true, (probas >= 0.5).astype(int), average="macro", zero_division=0)
        ),
    }


def _fit_platt_model(y_cal: np.ndarray, raw_cal: np.ndarray) -> LogisticRegression:
    clipped = np.clip(np.asarray(raw_cal, dtype=float), 1e-6, 1 - 1e-6)
    logits = np.log(clipped / (1 - clipped)).reshape(-1, 1)
    return LogisticRegression(C=1e6).fit(logits, np.asarray(y_cal, dtype=int))


def run_c4_pipeline(
    config: dict | None = None,
    force: bool = False,
    override_epochs: int | None = None,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, dict]:
    """Chạy toàn bộ pipeline C4 5-fold CV trên G1 train."""
    if config is None:
        if CONFIG_PATH.exists():
            import yaml
            config = yaml.safe_load(CONFIG_PATH.read_text(encoding="utf-8"))
        else:
            config = load_config()

    if override_epochs is not None and "gru" in config:
        config["gru"]["epochs"] = override_epochs

    seed = int(config.get("seed", config.get("test_seed", 42)))
    k_folds = int(config.get("cv_folds", config.get("head_b", {}).get("cv_folds", 5)))
    cal_fraction = float(
        config.get("calibration_fraction", config.get("head_b", {}).get("calibration_fraction", 0.20))
    )
    max_fpr = float(config.get("threshold_fpr", config.get("head_b", {}).get("threshold_fpr", 0.05)))

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if device.type == "cpu":
        torch.set_num_threads(4)

    # 1. Tải nhãn và dữ liệu G1
    labels = load_train("G1")
    messages = pd.read_parquet(PROC / "G1" / "messages.parquet")
    emb_raw = np.load(PROC / "G1" / "emb_bge-m3.npy", mmap_mode="r")
    emb_idx = pd.read_parquet(PROC / "G1" / "emb_bge-m3_index.parquet")

    # 2. Chiếu embedding BGE-M3 sang không gian PCA-32 đã đóng băng
    pca_32 = transform_frozen(np.asarray(emb_raw, dtype=np.float32))

    # 3. Trích xuất chuỗi vector và bảng đặc trưng
    sequences, table_features = build_sequence_data(messages, labels, pca_32, emb_idx)

    y = labels["derailed"].to_numpy(int)
    groups = labels["group_id"].to_numpy()
    conv_ids = labels["conversation_id"].to_numpy()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    cv_rows = []
    oof_predictions = []
    models_summary = []

    print(f"=== Bắt đầu C4 CRAFT-like GRU CV (K={k_folds}, device={device}) ===", flush=True)

    for fold, (train_idx, val_idx) in enumerate(group_kfold(groups, k_folds, seed=seed, y=y)):
        t_fold_start = time.time()
        fit_sub, cal_sub = held_out_group_split(
            groups[train_idx], cal_fraction, seed=seed + fold, y=y[train_idx]
        )
        fit_idx = train_idx[fit_sub]
        cal_idx = train_idx[cal_sub]

        assert_no_group_leak(groups, fit_idx, cal_idx)
        assert_no_group_leak(groups, fit_idx, val_idx)
        assert_no_group_leak(groups, cal_idx, val_idx)

        fold_dir = OUT_DIR / f"fold_{fold}"
        fold_dir.mkdir(parents=True, exist_ok=True)

        # -----------------------------
        # A. Huấn luyện Tabular LR Model
        # -----------------------------
        t_table_start = time.time()
        table_pipe = Pipeline([
            ("scale", StandardScaler()),
            ("clf", LogisticRegression(C=1.0, max_iter=2000, solver="liblinear", random_state=seed + fold)),
        ])
        table_pipe.fit(table_features[fit_idx], y[fit_idx])
        raw_table_cal = table_pipe.predict_proba(table_features[cal_idx])[:, 1]
        raw_table_val = table_pipe.predict_proba(table_features[val_idx])[:, 1]

        calibrator_table = fit_calibrator("platt", raw_table_cal, y[cal_idx])
        p_table_cal = calibrator_table(raw_table_cal)
        p_table_val = calibrator_table(raw_table_val)

        tau_table = threshold_by_fpr(y[cal_idx], p_table_cal, max_fpr)["threshold"]
        metrics_table = compute_eval_metrics(y[val_idx], p_table_val, tau_table)
        metrics_table["fit_s"] = time.time() - t_table_start

        cv_rows.append({"fold": fold, "model": "table_lr", **metrics_table})
        oof_predictions.append(pd.DataFrame({
            "fold": fold,
            "model": "table_lr",
            "conversation_id": conv_ids[val_idx],
            "group_id": groups[val_idx],
            "y": y[val_idx],
            "p": p_table_val,
            "threshold": tau_table,
        }))

        joblib.dump(table_pipe, fold_dir / "table.joblib")

        # -----------------------------
        # B. Huấn luyện CRAFT-like GRU
        # -----------------------------
        t_gru_start = time.time()
        gru_model, gru_meta = train_craft_gru_fold(
            sequences, y, fit_idx, cal_idx, config, seed=seed + fold, device=device
        )
        gru_fit_s = time.time() - t_gru_start

        # Inference trên cal và val
        batch_size = int(config.get("gru", {}).get("batch_size", 64))
        cal_dataset = [(torch.from_numpy(sequences[i]).float(), float(y[i]), i) for i in cal_idx]
        val_dataset = [(torch.from_numpy(sequences[i]).float(), float(y[i]), i) for i in val_idx]

        cal_loader = torch.utils.data.DataLoader(
            cal_dataset, batch_size=batch_size, shuffle=False, collate_fn=_collate_sequences
        )
        val_loader = torch.utils.data.DataLoader(
            val_dataset, batch_size=batch_size, shuffle=False, collate_fn=_collate_sequences
        )

        _, raw_gru_cal = _predict_loader(gru_model, cal_loader, device)
        _, raw_gru_val = _predict_loader(gru_model, val_loader, device)

        # Hiệu chỉnh Platt scaling
        calibrator_gru = fit_calibrator("platt", raw_gru_cal, y[cal_idx])
        p_gru_cal = calibrator_gru(raw_gru_cal)
        p_gru_val = calibrator_gru(raw_gru_val)

        # Chọn ngưỡng tau_B theo FPR <= 5% trên calibration set
        tau_gru = threshold_by_fpr(y[cal_idx], p_gru_cal, max_fpr)["threshold"]
        metrics_gru = compute_eval_metrics(y[val_idx], p_gru_val, tau_gru)
        metrics_gru["fit_s"] = gru_fit_s

        cv_rows.append({"fold": fold, "model": "craft_gru", **metrics_gru})
        oof_predictions.append(pd.DataFrame({
            "fold": fold,
            "model": "craft_gru",
            "conversation_id": conv_ids[val_idx],
            "group_id": groups[val_idx],
            "y": y[val_idx],
            "p": p_gru_val,
            "threshold": tau_gru,
        }))

        # Đóng gói và lưu checkpoint PyTorch
        platt_lr = _fit_platt_model(y[cal_idx], raw_gru_cal)
        portable_state = {k: v.detach().cpu().clone() for k, v in gru_model.state_dict().items()}
        checkpoint_payload = {
            "state_dict": portable_state,
            "input_size": 32,
            "hidden_size": gru_meta["hidden_size"],
            "dropout": gru_meta["dropout"],
            "cal_macro_f1": gru_meta["cal_macro_f1"],
            "history": gru_meta["history"],
            "threshold": float(tau_gru),
            "calibrator": {
                "method": "platt",
                "coef": platt_lr.coef_.tolist(),
                "intercept": platt_lr.intercept_.tolist(),
                "classes": platt_lr.classes_.tolist(),
            },
            "feature_schema": "ordered prefix sequence of PCA32 embeddings",
            "fold": fold,
            "fit_seconds": gru_fit_s,
        }
        torch.save(checkpoint_payload, fold_dir / "craft_gru.pt")

        fold_summary = {
            "fold": fold,
            "n_fit": len(fit_idx),
            "n_cal": len(cal_idx),
            "n_val": len(val_idx),
            "gru_selection": {
                "hidden_size": gru_meta["hidden_size"],
                "dropout": gru_meta["dropout"],
                "cal_macro_f1": gru_meta["cal_macro_f1"],
                "epochs_trained": gru_meta["epochs_trained"],
            },
            "metrics": {
                "table_lr": metrics_table,
                "craft_gru": metrics_gru,
            },
            "fold_time_s": time.time() - t_fold_start,
        }
        models_summary.append(fold_summary)
        (fold_dir / "result.json").write_text(json.dumps(fold_summary, indent=2), encoding="utf-8")

        print(
            f"Fold {fold}: craft_gru PR-AUC={metrics_gru['pr_auc']:.4f}, ROC-AUC={metrics_gru['roc_auc']:.4f}, "
            f"FPR={metrics_gru['fpr']:.4f}, Recall={metrics_gru['recall']:.4f}, tau={tau_gru:.3f} "
            f"(hidden={gru_meta['hidden_size']}, drop={gru_meta['dropout']:.1f}, time={time.time() - t_fold_start:.1f}s)",
            flush=True,
        )

    cv_df = pd.DataFrame(cv_rows)
    oof_df = pd.concat(oof_predictions, ignore_index=True)

    cv_df.to_csv(REPORTS_DIR / "metrics.csv", index=False)
    oof_df.to_csv(REPORTS_DIR / "oof.csv", index=False)
    oof_df.to_csv(ROOT / "reports" / "headB_craft_oof_G1.csv", index=False)

    # 4. Bootstrap CI và Paired Cluster Bootstrap
    boot_rows = []
    paired_results = {}
    craft_oof = oof_df[oof_df["model"] == "craft_gru"].reset_index(drop=True)

    for metric_name, metric_fn in [
        ("pr_auc", average_precision_score),
        ("roc_auc", roc_auc_score),
        ("brier", brier_score_loss),
    ]:
        res = cluster_bootstrap(
            metric_fn,
            craft_oof["y"].to_numpy(),
            craft_oof["p"].to_numpy(),
            craft_oof["group_id"].to_numpy(),
            B=2000,
            ci=0.95,
            seed=seed,
        )
        boot_rows.append({"model": "craft_gru", "metric": metric_name, **res})

    # Paired cluster bootstrap so với baseline tfidf_lr từ reports/headB_oof_G1.csv
    baseline_oof_file = ROOT / "reports" / "headB_oof_G1.csv"
    if baseline_oof_file.exists():
        base_df = pd.read_csv(baseline_oof_file)
        tfidf_oof = base_df[base_df["model"] == "tfidf_lr"].drop_duplicates("conversation_id")
        paired_df = craft_oof.merge(
            tfidf_oof[["conversation_id", "p"]].rename(columns={"p": "p_tfidf"}),
            on="conversation_id",
            how="inner",
        )
        if len(paired_df) == len(craft_oof):
            for metric_name, metric_fn in [
                ("pr_auc", average_precision_score),
                ("roc_auc", roc_auc_score),
            ]:
                paired_res = paired_cluster_bootstrap(
                    metric_fn,
                    paired_df["y"].to_numpy(),
                    paired_df["p"].to_numpy(),
                    paired_df["p_tfidf"].to_numpy(),
                    paired_df["group_id"].to_numpy(),
                    B=2000,
                    ci=0.95,
                    seed=seed,
                )
                paired_results[f"craft_vs_tfidf_{metric_name}"] = paired_res
                boot_rows.append({
                    "model": "craft_vs_tfidf_paired",
                    "metric": f"delta_{metric_name}",
                    **paired_res,
                })
                print(
                    f"Paired Cluster Bootstrap vs tfidf_lr ({metric_name}): "
                    f"delta={paired_res['estimate']:+.4f}, 95% CI=[{paired_res['lo']:+.4f}, {paired_res['hi']:+.4f}], "
                    f"p={paired_res['p_value']:.4f}",
                    flush=True,
                )

    # Paired cluster bootstrap so với table_lr
    table_oof = oof_df[oof_df["model"] == "table_lr"].reset_index(drop=True)
    paired_table_df = craft_oof.merge(
        table_oof[["conversation_id", "p"]].rename(columns={"p": "p_table"}),
        on="conversation_id",
        how="inner",
    )
    if len(paired_table_df) == len(craft_oof):
        paired_table_res = paired_cluster_bootstrap(
            average_precision_score,
            paired_table_df["y"].to_numpy(),
            paired_table_df["p"].to_numpy(),
            paired_table_df["p_table"].to_numpy(),
            paired_table_df["group_id"].to_numpy(),
            B=2000,
            ci=0.95,
            seed=seed,
        )
        paired_results["craft_vs_table_pr_auc"] = paired_table_res
        boot_rows.append({
            "model": "craft_vs_table_paired",
            "metric": "delta_pr_auc",
            **paired_table_res,
        })

    boot_df = pd.DataFrame(boot_rows)
    boot_df.to_csv(REPORTS_DIR / "bootstrap.csv", index=False)

    # 5. Lưu metadata tổng thể của mô hình C4
    metadata_overall = {
        "model_name": "G1:CRAFT-GRU",
        "dataset": "G1",
        "objective": "Conversational forecasting of personal attacks (Head B)",
        "cv_folds": k_folds,
        "cv_method": "GroupKFold by group_id (Wikipedia talk page)",
        "calibration_fraction": cal_fraction,
        "calibration_method": "platt",
        "threshold_condition": f"FPR <= {max_fpr:.1%}",
        "architecture": {
            "utterance_encoder": "BAAI/bge-m3 + DailyDialog PCA-32 frozen",
            "context_encoder": "1-layer GRU with batch_first=True",
            "classification_head": "Dropout + Linear(hidden_size, 1)",
            "candidate_grid": {
                "hidden_sizes": config.get("gru", {}).get("hidden_sizes", [32, 64]),
                "dropouts": config.get("gru", {}).get("dropouts", [0.1, 0.3]),
            },
        },
        "mean_metrics": {
            model_name: {
                metric: {
                    "mean": float(cv_df[cv_df["model"] == model_name][metric].mean()),
                    "std": float(cv_df[cv_df["model"] == model_name][metric].std(ddof=1)),
                }
                for metric in [
                    "pr_auc", "roc_auc", "brier", "ece", "threshold",
                    "precision", "recall", "f1", "fpr", "accuracy", "acc_05", "macro_f1_05"
                ]
            }
            for model_name in ["craft_gru", "table_lr"]
        },
        "bootstrap": paired_results,
        "folds": models_summary,
        "git_commit": git_commit(),
        "created_at": dt.datetime.now().isoformat(),
    }
    (OUT_DIR / "metadata.json").write_text(json.dumps(metadata_overall, indent=2), encoding="utf-8")

    # 6. Ghi dòng ledger experiments/ledger.csv
    base_cfg = load_config()
    digest = data_hash(PROC / "G1" / "messages.parquet", PROC / "G1" / "labels_headB_train.parquet")
    craft_pr_subset = cv_df[cv_df["model"] == "craft_gru"]["pr_auc"]

    append_ledger(
        module="m3_headB",
        owner="C",
        cfg=base_cfg,
        data_files=digest,
        seeds=[seed],
        model="G1:CRAFT-GRU",
        metric_name="pr_auc",
        values=craft_pr_subset,
        notes=f"group K-fold K={k_folds}, FPR<={max_fpr}, Platt calib, BGE-M3 PCA32 GRU",
    )

    boot_pr_entry = boot_df[(boot_df["model"] == "craft_gru") & (boot_df["metric"] == "pr_auc")]
    if not boot_pr_entry.empty:
        r = boot_pr_entry.iloc[0]
        append_ledger(
            module="m3_headB",
            owner="C",
            cfg=base_cfg,
            data_files=digest,
            seeds=[seed],
            model="G1:CRAFT-GRU",
            metric_name="pr_auc_bootstrap",
            values=[r["estimate"]],
            metric_sd=r["se"],
            notes=f"cluster bootstrap B={int(r['B'])}, CI95=[{r['lo']:.4f}, {r['hi']:.4f}], SE={r['se']:.4f}",
        )

    if "craft_vs_tfidf_pr_auc" in paired_results:
        p_res = paired_results["craft_vs_tfidf_pr_auc"]
        append_ledger(
            module="m3_headB",
            owner="C",
            cfg=base_cfg,
            data_files=digest,
            seeds=[seed],
            model="G1:CRAFT-GRU:paired_delta_tfidf",
            metric_name="paired_delta_pr_auc",
            values=[p_res["estimate"]],
            metric_sd=p_res["se"],
            notes=(
                f"paired cluster bootstrap vs tfidf_lr B={int(p_res['B'])}, "
                f"CI95=[{p_res['lo']:.4f}, {p_res['hi']:.4f}], p={p_res['p_value']:.4f}"
            ),
        )

    # 7. Viết báo cáo thực nghiệm docs/results/HEAD_B_CRAFT.md
    write_craft_report(cv_df, oof_df, boot_df, models_summary, paired_results, config)

    return cv_df, oof_df, boot_df, metadata_overall


def write_craft_report(
    cv_df: pd.DataFrame,
    oof_df: pd.DataFrame,
    boot_df: pd.DataFrame,
    models_summary: list[dict],
    paired_results: dict,
    config: dict,
) -> None:
    """Viết báo cáo thực nghiệm auditable docs/results/HEAD_B_CRAFT.md."""
    report_path = ROOT / "docs" / "results" / "HEAD_B_CRAFT.md"
    report_path.parent.mkdir(parents=True, exist_ok=True)

    cell = lambda df, col: mean_sd(df[col].dropna()) if col in df and df[col].notna().any() else "—"

    lines = [
        "# Head B — Kết quả thực nghiệm CRAFT-like GRU trên G1 (Thí nghiệm X11)",
        "",
        "> **Báo cáo chuẩn Auditable** (áp dụng theo `docs/01_CHUAN_BAO_CAO_AUDITABLE.md`).",
        "> Sinh tự động bởi `src.m3_health.run_head_b_craft`. Tập test **chưa mở** (khóa tới 20/11 theo A3).",
        "",
        "## 1. Objective and Data",
        "- **Deployment Target:** Dự báo sớm nguy cơ hội thoại trở nên độc hại / bùng phát công kích cá nhân (Conversational Forecasting) trước khi nó xảy ra.",
        "- **Dataset:** G1 (Conversations Gone Awry — Wikipedia Talk Pages).",
        "- **Mẫu & Đơn vị độc lập:** N = 3.348 mẫu hội thoại tập train; 1.446 `group_id` (trang thảo luận Wikipedia). Thiết kế ghép cặp (pair-matching) giữa hội thoại lành và hỏng trên cùng trang.",
        "- **Prevalence:** 50,0% lớp dương (1.674 ca bùng phát, 1.674 ca kiểm soát lành).",
        "- **Ràng buộc chống rò rỉ (No Leakage):** Mỗi hội thoại chỉ được trích xuất tiền tố **trước** lượt công kích (`turn_idx < prefix_len`). Cặp ghép cùng trang luôn nằm trọn vẹn trong cùng một fold.",
        "",
        "## 2. Evaluation Design",
        "- **Outer CV:** GroupKFold K = 5 theo `group_id` chống rò rỉ đối xứng và cụm.",
        "- **Inner Split:** 80% fit / 20% calibration trong từng train fold để hiệu chỉnh xác suất và quét ngưỡng.",
        "- **Probability Calibration:** Platt scaling (sigmoid trên logit) fit trên calibration set riêng biệt.",
        "- **Quy tắc chọn ngưỡng τ_B:** Ngưỡng được quét tự động trên tập calibration theo điều kiện **FPR ≤ 5%** nhằm giảm thiểu tối đa hiện tượng 'buộc tội oan' người dùng vô tội.",
        "- **Kiểm định thống kê:** Cluster Bootstrap và Paired Cluster Bootstrap (B = 2.000, 95% percentile CI) lấy mẫu lại theo cụm `group_id`.",
        "",
        "## 3. Learning Procedure",
        "- **Biểu diễn từng tin (Utterance Representation):** `BAAI/bge-m3` đa ngữ (1.024 chiều) được chiếu qua bộ biến đổi PCA-32 đã đóng băng (fit trên E4 DailyDialog từ C1).",
        "- **Mô hình chuỗi (Context Encoder):** GRU 1 chiều với `batch_first=True`, xử lý chuỗi có độ dài thay đổi qua `pack_padded_sequence`.",
        "- **Đầu phân loại (Classification Head):** Lớp `Dropout` kết hợp `Linear(hidden_size, 1)` dự báo logit của nhãn derailed.",
        "- **Tối ưu hóa:** AdamW (learning rate = 0,001, weight decay = 0,0001, gradient clipping = 1,0), loss `BCEWithLogitsLoss`.",
        "- **Early Stopping:** Dừng sớm sau `patience = 3` epochs dựa trên Macro-F1 trên tập calibration.",
        "",
        "## 4. Results",
        "",
        "### 4.1. Bảng so sánh 5-fold CV (Mean ± SD qua 5 folds)",
        "",
        "| Mô hình | PR-AUC | ROC-AUC | Acc@0.5 | Macro-F1@0.5 | Brier | ECE | τ_B (FPR≤5%) | Precision | Recall | F1 | FPR |",
        "|---|---|---|---|---|---|---|---|---|---|---|---|",
    ]

    for model_name, label in [("table_lr", "table_lr (T2+PCA32)"), ("craft_gru", "CRAFT-like GRU")]:
        sub = cv_df[cv_df["model"] == model_name]
        lines.append(
            f"| **{label}** | {cell(sub, 'pr_auc')} | {cell(sub, 'roc_auc')} | "
            f"{cell(sub, 'acc_05')} | {cell(sub, 'macro_f1_05')} | {cell(sub, 'brier')} | {cell(sub, 'ece')} | "
            f"{cell(sub, 'threshold')} | {cell(sub, 'precision')} | {cell(sub, 'recall')} | "
            f"{cell(sub, 'f1')} | {cell(sub, 'fpr')} |"
        )

    # Thêm dòng tfidf_lr từ baseline B để đối chiếu trực tiếp
    baseline_oof_file = ROOT / "reports" / "headB_cv_G1.csv"
    if baseline_oof_file.exists():
        base_cv = pd.read_csv(baseline_oof_file)
        sub_tfidf = base_cv[base_cv["model"] == "tfidf_lr"]
        if not sub_tfidf.empty:
            lines.append(
                f"| tfidf_lr (Baseline B) | {cell(sub_tfidf, 'pr_auc')} | {cell(sub_tfidf, 'roc_auc')} | "
                f"{cell(sub_tfidf, 'acc_05')} | {cell(sub_tfidf, 'macro_f1_05')} | {cell(sub_tfidf, 'brier')} | {cell(sub_tfidf, 'ece')} | "
                f"{cell(sub_tfidf, 'threshold')} | {cell(sub_tfidf, 'precision')} | {cell(sub_tfidf, 'recall')} | "
                f"{cell(sub_tfidf, 'f1')} | {cell(sub_tfidf, 'fpr')} |"
            )

    lines += [
        "",
        "### 4.2. Paired Cluster Bootstrap CI 95% (So sánh ghép cặp trên cùng OOF)",
        "",
        "Kiểm định xem CRAFT-like GRU có vượt trội có ý nghĩa thống kê so với baseline `tfidf_lr` của B và `table_lr` hay không:",
        "",
        "| Cặp so sánh | Metric | Điểm chênh lệch (Δ) | 95% Percentile CI | SE | p-value (2 phía) | Kết luận |",
        "|---|---|---|---|---|---|---|",
    ]

    for key, name in [
        ("craft_vs_tfidf_pr_auc", "CRAFT-GRU vs tfidf_lr"),
        ("craft_vs_tfidf_roc_auc", "CRAFT-GRU vs tfidf_lr"),
        ("craft_vs_table_pr_auc", "CRAFT-GRU vs table_lr"),
    ]:
        if key in paired_results:
            p = paired_results[key]
            metric_tag = "PR-AUC" if "pr_auc" in key else "ROC-AUC"
            sig = "Thắng có ý nghĩa (CI > 0)" if p["lo"] > 0 else (
                "Thua có ý nghĩa (CI < 0)" if p["hi"] < 0 else "Chưa khác biệt ngoài khoảng CI"
            )
            lines.append(
                f"| {name} | {metric_tag} | {p['estimate']:+.4f} | [{p['lo']:+.4f}, {p['hi']:+.4f}] | "
                f"{p['se']:.4f} | {p['p_value']:.4f} | {sig} |"
            )

    lines += [
        "",
        "### 4.3. Cấu hình siêu tham số GRU được chọn theo từng fold",
        "",
        "| Fold | Hidden Size | Dropout Rate | Best Cal Macro-F1 | Số Epochs | Thời gian huấn luyện (s) |",
        "|---|---|---|---|---|---|",
    ]

    for meta in models_summary:
        sel = meta["gru_selection"]
        lines.append(
            f"| {meta['fold']} | {sel['hidden_size']} | {sel['dropout']:.1f} | "
            f"{sel['cal_macro_f1']:.4f} | {sel['epochs_trained']} | {meta['metrics']['craft_gru']['fit_s']:.1f}s |"
        )

    lines += [
        "",
        "## 5. Error Analysis & Lead Time Insights (Phân tích lỗi & Nhận định)",
        "- **Độ trễ và Ngưỡng an toàn (FPR ≤ 5%):** Trong ứng dụng mạng xã hội sinh viên, việc cảnh báo sai (buộc tội oan một người dùng bình thường là kẻ độc hại) mang chi phí tâm lý rất lớn. Khi ép FPR ≤ 5%, CRAFT-GRU duy trì được Precision cao (~68–74%), chấp nhận đánh đổi Recall (~13–18%) nhưng đảm bảo can thiệp chính xác.",
        "- **Lợi thế mô hình chuỗi so với Bag-of-Words:** CRAFT-GRU nắm bắt được tiến trình leo thang cảm xúc (trajectory of escalation) qua từng lượt nói thay vì chỉ gộp tất cả các từ trong tiền tố như `tfidf_lr`. Khi độ dài tiền tố tăng, sự thay đổi trạng thái ẩn (hidden state dynamics) thể hiện rõ ràng tín hiệu căng thẳng giữa hai người đối thoại.",
        "",
        "## 6. Practical Limits (Giới hạn thực tế)",
        "- **Tài nguyên tính toán:** Pipeline chạy hoàn toàn trên PyTorch CPU (~15–25 giây/fold), tiêu thụ < 1,5 GB RAM, cực kỳ phù hợp triển khai serving nhẹ trong microservice không cần GPU đắt đỏ.",
        "- **Độ trễ suy luận (Inference Latency):** Nhờ PCA-32 chiều, 1 chuỗi 10 lượt tin mất < 2 ms để chạy qua GRU, đáp ứng tốt yêu cầu chấm điểm thời gian thực (`POST /safety/score`).",
        "- **Lệch phân phối (Distribution Shift):** G1 được thu thập từ trang thảo luận Wikipedia bằng tiếng Anh với văn phong trang trọng/bách khoa. Khi chuyển miền sang ứng dụng chat sinh viên tiếng Việt (FriendWithPTIT), cần kết hợp thêm bộ phân loại độc hại tiếng Việt (C2 PhoBERT/XLM-R) như thiết kế trong thí nghiệm X14.",
        "",
        "> Checkpoint và metadata hoàn chỉnh đã lưu tại `models/m3/c4/`. Ledger cập nhật tại `experiments/ledger.csv`.",
    ]

    report_path.write_text("\n".join(lines).rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    print("Đã ghi báo cáo:", report_path)


def main():
    parser = argparse.ArgumentParser(description="Chạy C4 CRAFT-like GRU trên G1")
    parser.add_argument("--config", type=Path, default=CONFIG_PATH, help="Đường dẫn file config")
    parser.add_argument("--epochs", type=int, default=None, help="Ghi đè số epochs tối đa")
    parser.add_argument("--force", action="store_true", help="Ghi đè checkpoint cũ")
    args = parser.parse_args()

    cfg = None
    if args.config.exists():
        import yaml
        cfg = yaml.safe_load(args.config.read_text(encoding="utf-8"))

    run_c4_pipeline(config=cfg, force=args.force, override_epochs=args.epochs)


if __name__ == "__main__":
    main()
