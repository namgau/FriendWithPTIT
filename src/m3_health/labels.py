"""Nhãn Head A (A2b), Head B (A2c) và tập test khóa (A3) — docs/06_MODULE_3_HEALTH_MONITOR.md §1.2–1.4.

Chạy (sau src.data.prepare):  python -m src.m3_health.labels

Đầu ra:
  data/processed/<ID>/labels_head{A,B}_train.parquet   — mọi người dùng (CV, chọn mô hình)
  data/locked/<ID>/labels_head{A,B}_test.parquet       — CHỈ A mở, đúng 1 lần, 20/11
  configs/test_lock.json                               — commit: băm danh sách test, ai đổi split là lộ
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
import pandas as pd
import yaml

from src.eval.splits import assert_no_group_leak, assert_time_order, held_out_group_split, held_out_time_split

ROOT = Path(__file__).resolve().parents[2]
PROC = ROOT / "data" / "processed"
LOCKED = ROOT / "data" / "locked"
LOCK_FILE = ROOT / "configs" / "test_lock.json"


def load_config() -> dict:
    return yaml.safe_load((ROOT / "configs" / "default.yaml").read_text(encoding="utf-8"))


# ---------------------------------------------------------------- Head A

def head_a_samples(msgs: pd.DataFrame, delta_s: float, start_turn: int = 5, every: int = 2,
                   data_end_s: float | None = None) -> pd.DataFrame:
    """Mẫu (c, t) tại tin thứ start_turn+1, cách nhau ``every`` tin.

    Survival: T = thời gian tới tin kế tiếp ; event = 1 nếu có tin kế tiếp trong dữ liệu,
    nếu không thì T bị kiểm duyệt (censored) tại thời điểm cuối của dataset.
    Nhãn nhị phân: y = 1 ⇔ không có tin nào trong (t, t + Δ] ⇔ T > Δ ;
    không xác định (censored) khi event = 0 và T < Δ.
    """
    m = msgs[["conversation_id", "group_id", "turn_idx", "timestamp_s"]]
    data_end_s = m["timestamp_s"].max() if data_end_s is None else data_end_s
    g = m.groupby("conversation_id")["timestamp_s"]
    m = m.assign(next_s=g.shift(-1), conv_start_s=g.transform("min"))
    s = m[(m["turn_idx"] >= start_turn) & ((m["turn_idx"] - start_turn) % every == 0)].copy()
    s["event"] = s["next_s"].notna().astype(int)
    s["duration_s"] = np.where(s["event"] == 1, s["next_s"] - s["timestamp_s"], data_end_s - s["timestamp_s"])
    s = s.rename(columns={"timestamp_s": "t_s"}).drop(columns="next_s")
    s["censored"] = censored_at(s, delta_s)
    s["y"] = label_at(s, delta_s)
    return s.reset_index(drop=True)


def censored_at(s: pd.DataFrame, delta_s: float) -> pd.Series:
    return (s["event"] == 0) & (s["duration_s"] < delta_s)


def label_at(s: pd.DataFrame, delta_s: float) -> pd.Series:
    """Nhãn ở Δ bất kỳ từ (duration, event) — dùng cho thí nghiệm độ nhạy X5. NaN = censored."""
    return (s["duration_s"] > delta_s).astype(float).mask(censored_at(s, delta_s))


# ---------------------------------------------------------------- Head B

def head_b_g1(msgs: pd.DataFrame, conv: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
    """Mỗi hội thoại G1 một dòng; ``prefix_len`` = số lượt đầu được phép dùng làm đặc trưng.

    Hỏng: tiền tố dừng **trước** lượt công kích đầu tiên. Lành: bỏ lượt cuối (giống CRAFT,
    để độ dài tiền tố hai loại tương đương). Tiền tố rỗng → bỏ cả cặp ghép.
    """
    n_turns = msgs.groupby("conversation_id").size()
    first_attack = msgs[msgs["is_attack"]].groupby("conversation_id")["turn_idx"].min()
    c = conv.set_index("conversation_id")
    out = pd.DataFrame({"conversation_id": c.index, "pair_id": c["pair_id"].values,
                        "derailed": c["has_attack"].astype(int).values, "orig_split": c["orig_split"].values})
    out["group_id"] = out["conversation_id"].map(msgs.groupby("conversation_id")["group_id"].first())
    out["n_turns"] = out["conversation_id"].map(n_turns)
    out["attack_turn_idx"] = out["conversation_id"].map(first_attack).fillna(-1).astype(int)
    out["prefix_len"] = np.where(out["derailed"] == 1, out["attack_turn_idx"], out["n_turns"] - 1)
    info = {
        "derailed_without_attack_turn": int(((out.derailed == 1) & (out.attack_turn_idx < 0)).sum()),
        "calm_with_attack_turn": int(((out.derailed == 0) & (out.attack_turn_idx >= 0)).sum()),
    }
    bad = out["conversation_id"][(out["prefix_len"] < 1) | ((out.derailed == 1) & (out.attack_turn_idx < 0))]
    drop = set(bad) | set(out.loc[out["conversation_id"].isin(bad), "pair_id"])
    info["dropped_pairs_empty_prefix"] = len(drop) // 2
    out = out[~out["conversation_id"].isin(drop)].reset_index(drop=True)
    return out, info


def head_b_single(msgs: pd.DataFrame, conv: pd.DataFrame) -> pd.DataFrame:
    """H1/H2: mỗi bình luận một dòng, nhãn có sẵn."""
    keep = [c for c in ["toxic", "label_3class", "constructive", "orig_split"] if c in conv]
    return msgs[["conversation_id", "group_id"]].merge(conv[["conversation_id"] + keep], on="conversation_id")


# ---------------------------------------------------------------- tập test (A3)

def _split(df: pd.DataFrame, method: str, cfg: dict, y_col: str | None) -> tuple[np.ndarray, np.ndarray]:
    if method == "time":
        tr, te = held_out_time_split(df["group_id"], df["conv_start_s"], cfg["test_fraction"])
        assert_time_order(df["group_id"], df["conv_start_s"], tr, te)
    elif method == "official":
        te = np.flatnonzero(df["orig_split"].to_numpy() == "test")
        tr = np.flatnonzero(df["orig_split"].to_numpy() != "test")
    else:  # group, phân tầng theo nhãn
        tr, te = held_out_group_split(df["group_id"], cfg["test_fraction"], seed=cfg["test_seed"],
                                      y=None if y_col is None else df[y_col])
    assert_no_group_leak(df["group_id"], tr, te)
    return tr, te


def _describe(df: pd.DataFrame, y_col: str) -> dict:
    y = df[y_col].dropna()
    return {"n_rows": int(len(df)), "n_conversations": int(df["conversation_id"].nunique()),
            "n_groups": int(df["group_id"].nunique()), "n_positive": int(y.sum()),
            "prevalence": round(float(y.mean()), 4)}


def _sha(ids) -> str:
    return hashlib.sha256("\n".join(sorted(map(str, set(ids)))).encode()).hexdigest()


def build(datasets: list[str] | None = None) -> dict:
    cfg = load_config()
    ha, hb, methods = cfg["head_a"], cfg["head_b"], cfg["test_split"]
    lock = json.loads(LOCK_FILE.read_text(encoding="utf-8")) if LOCK_FILE.exists() else {}
    for d in datasets or list(methods):
        msgs = pd.read_parquet(PROC / d / "messages.parquet")
        conv = pd.read_parquet(PROC / d / "conversations.parquet")
        info: dict = {}
        if d in ("E1", "E2"):
            head, y_col = "headA", "y"
            df = head_a_samples(msgs, ha["delta_s"], ha["start_turn"], ha["every"])
            info["censored_at_delta"] = int(df["censored"].sum())
        elif d == "G1":
            head, y_col = "headB", "derailed"
            df, info = head_b_g1(msgs, conv)
        else:
            head, y_col = "headB", "toxic"
            df = head_b_single(msgs, conv)
            if d == "H1":
                info["duplicate_text_label_conflicts"] = int((df.groupby("group_id")["toxic"].nunique() > 1).sum())
        tr, te = _split(df, methods[d], cfg, y_col if methods[d] == "group" else None)
        train, test = df.iloc[tr], df.iloc[te]
        (PROC / d).mkdir(parents=True, exist_ok=True)
        (LOCKED / d).mkdir(parents=True, exist_ok=True)
        train.to_parquet(PROC / d / f"labels_{head}_train.parquet", index=False)
        test.to_parquet(LOCKED / d / f"labels_{head}_test.parquet", index=False)
        entry = {"head": head, "split_method": methods[d], "test_fraction": cfg["test_fraction"],
                 "test_seed": cfg["test_seed"], "label": y_col,
                 "test_group_ids_sha256": _sha(test["group_id"]),
                 "train": _describe(train, y_col), "test": _describe(test, y_col), **info}
        if head == "headA":
            entry["delta_s"] = ha["delta_s"]
        old = lock.get(d)
        if old and old["test_group_ids_sha256"] != entry["test_group_ids_sha256"]:
            raise RuntimeError(f"{d}: tập test KHÁC bản đã khóa — không ghi đè. Báo A.")
        lock[d] = entry
        print(d, json.dumps(entry, ensure_ascii=False))
    LOCK_FILE.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return lock


def load_train(dataset: str) -> pd.DataFrame:
    """Nhãn tập train (để CV). Không có hàm tương ứng cho test: test chỉ A mở, ngày 20/11."""
    f = next((PROC / dataset).glob("labels_head*_train.parquet"))
    return pd.read_parquet(f)


if __name__ == "__main__":
    import sys
    build(sys.argv[1:] or None)
