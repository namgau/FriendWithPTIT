"""Đưa mọi dataset về bảng tin nhắn chung (docs/06_MODULE_3_HEALTH_MONITOR.md §1.4).

Chạy:  python -m src.data.prepare E1 E2 G1 H1 H2

Đầu vào: data/raw/<ID>/ (tải theo docs/data_cards/DATA_CARD_<ID>.md).
Đầu ra:  data/processed/<ID>/messages.parquet       — bảng tin nhắn chung
         data/processed/<ID>/conversations.parquet  — siêu dữ liệu & nhãn gốc mức hội thoại
         data/processed/<ID>/stats.json             — số liệu cho Data Card (in từ code)
Không gán nhãn Head A/B ở đây: việc đó ở src/m3_health/labels.py (A2b, A2c).
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "data" / "raw"
OUT = ROOT / "data" / "processed"
COLUMNS = ["dataset", "conversation_id", "turn_idx", "sender_id", "timestamp_s", "text", "group_id"]


def hash_id(dataset: str, value) -> str:
    """Băm tên người dùng / id gốc, 12 ký tự hex (đủ ít va chạm cho ~1 triệu người)."""
    return hashlib.sha1(f"{dataset}:{value}".encode()).hexdigest()[:12]


def to_unix_s(ts: pd.Series) -> pd.Series:
    """Datetime có múi giờ → Unix giây (không phụ thuộc độ phân giải ns/ms của pandas)."""
    return (ts - pd.Timestamp(0, tz="UTC")).dt.total_seconds()


def _finish(df: pd.DataFrame, dataset: str) -> pd.DataFrame:
    df = df.sort_values(["conversation_id", "timestamp_s", "_order"], kind="stable")
    df["turn_idx"] = df.groupby("conversation_id").cumcount()
    df["dataset"] = dataset
    return df.reset_index(drop=True)


# ---------------------------------------------------------------- Head A

def load_e1() -> tuple[pd.DataFrame, pd.DataFrame]:
    """Ubuntu Dialogue Corpus (Kaggle rtatman/ubuntu-dialogue-corpus).

    dialogueText.csv chỉ có hội thoại ≤ 3 tin → cần thêm dialogueText_196.csv (hội thoại dài hơn).
    """
    base = RAW / "E1" / "Ubuntu-dialogue-corpus"
    raw = pd.concat([pd.read_csv(base / f, dtype=str) for f in ["dialogueText.csv", "dialogueText_196.csv"]
                     if (base / f).exists()], ignore_index=True)
    raw = raw.drop_duplicates(["folder", "dialogueID", "date", "from", "text"])
    raw["conversation_id"] = "E1_" + raw["folder"] + "_" + raw["dialogueID"].str.replace(".tsv", "", regex=False)
    raw["_order"] = np.arange(len(raw))
    # Timestamp gốc làm tròn tới phút; thứ tự trong file giữ thứ tự tin cùng phút.
    raw["timestamp_s"] = to_unix_s(pd.to_datetime(raw["date"], utc=True))
    n_senders = raw.groupby("conversation_id")["from"].nunique()
    keep = n_senders[n_senders == 2].index  # chỉ hội thoại đúng 2 người
    df = raw[raw["conversation_id"].isin(keep)].copy()
    df["sender_id"] = df["from"].map(lambda s: hash_id("E1", s))
    df["group_id"] = df["conversation_id"]
    df["text"] = df["text"].fillna("")
    conv = pd.DataFrame({"conversation_id": keep})
    conv.attrs["dropped_not_dyadic"] = int((n_senders != 2).sum())
    return _finish(df, "E1"), conv


def load_e2() -> tuple[pd.DataFrame, pd.DataFrame]:
    """Customer Support on Twitter (Kaggle thoughtvector/customer-support-on-twitter, twcs.csv)."""
    raw = pd.read_csv(RAW / "E2" / "twcs" / "twcs.csv",
                      dtype={"tweet_id": "int64", "author_id": str, "text": str,
                             "response_tweet_id": str, "in_response_to_tweet_id": "float64"})
    raw["inbound"] = raw["inbound"].astype(str).str.lower() == "true"
    raw["timestamp_s"] = to_unix_s(pd.to_datetime(raw["created_at"], format="%a %b %d %H:%M:%S %z %Y",
                                                  utc=True))
    # Nối chuỗi trả lời thành cây: đi ngược in_response_to tới gốc (pointer jumping).
    idx = pd.Series(np.arange(len(raw)), index=raw["tweet_id"].values)
    parent = raw["in_response_to_tweet_id"].map(idx).fillna(-1).astype("int64").to_numpy()
    parent = np.where(parent < 0, np.arange(len(raw)), parent)
    for _ in range(64):
        nxt = parent[parent]
        if np.array_equal(nxt, parent):
            break
        parent = nxt
    raw["conversation_id"] = "E2_" + raw["tweet_id"].to_numpy()[parent].astype(str)
    g = raw.groupby("conversation_id")
    inbound_authors = raw[raw["inbound"]].groupby("conversation_id")["author_id"].nunique()
    stats = pd.DataFrame({"n_authors": g["author_id"].nunique(), "n_msgs": g.size()})
    stats["n_inbound_authors"] = inbound_authors.reindex(stats.index, fill_value=0)
    # Giữ đúng 1 khách hàng + 1 tài khoản CSKH, ≥ 2 tin.
    ok = stats[(stats.n_authors == 2) & (stats.n_inbound_authors == 1) & (stats.n_msgs >= 2)].index
    df = raw[raw["conversation_id"].isin(ok)].copy()
    customer = df[df["inbound"]].groupby("conversation_id")["author_id"].first()
    company = df[~df["inbound"]].groupby("conversation_id")["author_id"].first()
    df["sender_id"] = df["author_id"].map(lambda s: hash_id("E2", s))
    df["group_id"] = df["conversation_id"].map(customer).map(lambda s: "cust_" + hash_id("E2", s))
    df["_order"] = df["tweet_id"]
    df["text"] = df["text"].fillna("")
    conv = pd.DataFrame({"conversation_id": ok})
    conv["company"] = conv["conversation_id"].map(company).values  # tài khoản công ty là công khai
    conv.attrs["dropped_not_dyadic"] = int(len(stats) - len(ok))
    return _finish(df, "E2"), conv


# ---------------------------------------------------------------- Head B

def load_g1() -> tuple[pd.DataFrame, pd.DataFrame]:
    """Conversations Gone Awry — Wikipedia (ConvoKit conversations-gone-awry-corpus)."""
    base = RAW / "G1" / "conversations-gone-awry-corpus"
    rows = []
    with open(base / "utterances.jsonl", encoding="utf-8") as f:
        for i, line in enumerate(f):
            u = json.loads(line)
            m = u.get("meta", {})
            if m.get("is_section_header"):
                continue  # tiêu đề mục, không phải lượt nói (giống CRAFT)
            rows.append((u["id"], u["conversation_id"], u["speaker"], u.get("timestamp"), u.get("text") or "",
                         bool(m.get("comment_has_personal_attack")), i))
    df = pd.DataFrame(rows, columns=["utterance_id", "conversation_id", "speaker", "timestamp_s", "text",
                                     "is_attack", "_order"])
    conv = pd.read_json(base / "conversations.json", orient="index")
    conv.index.name = "conversation_id"
    conv = conv.reset_index()[["conversation_id", "pair_id", "page_id", "conversation_has_personal_attack", "split"]]
    conv = conv.rename(columns={"conversation_has_personal_attack": "has_attack", "split": "orig_split"})
    # Cặp ghép cùng trang → cùng group, không bao giờ bị tách giữa train/test.
    df["group_id"] = df["conversation_id"].map(conv.set_index("conversation_id")["page_id"]).map(lambda p: f"page_{p}")
    df["sender_id"] = df["speaker"].map(lambda s: hash_id("G1", s))
    df["timestamp_s"] = df["timestamp_s"].astype(float)
    return _finish(df, "G1"), conv


def _single_messages(df: pd.DataFrame, dataset: str, text_col: str, group_of) -> pd.DataFrame:
    out = pd.DataFrame({"conversation_id": df["conversation_id"], "text": df[text_col].fillna("").astype(str)})
    out["sender_id"] = None
    out["timestamp_s"] = np.nan
    out["group_id"] = group_of
    out["_order"] = 0
    return _finish(out, dataset)


def load_h1() -> tuple[pd.DataFrame, pd.DataFrame]:
    """ViHSD (bản sao nguyên vẹn htdung167/ViHSD của uitnlp/vihsd). 0 CLEAN, 1 OFFENSIVE, 2 HATE."""
    parts = []
    for split, fname in [("train", "train.csv"), ("dev", "validation.csv"), ("test", "test.csv")]:
        p = pd.read_csv(RAW / "H1" / fname)
        p["orig_split"] = split
        p["conversation_id"] = [f"H1_{split}_{i}" for i in range(len(p))]
        parts.append(p)
    raw = pd.concat(parts, ignore_index=True)
    # Bình luận trùng nội dung → cùng group (không để bản trùng rơi vào cả train lẫn test).
    norm = raw["free_text"].fillna("").str.lower().str.split().str.join(" ")
    group = "txt_" + norm.map(lambda s: hashlib.sha1(s.encode()).hexdigest()[:12])
    conv = raw[["conversation_id", "orig_split"]].assign(label_3class=raw["label_id"].astype(int))
    conv["toxic"] = (conv["label_3class"] > 0).astype(int)
    return _single_messages(raw, "H1", "free_text", group.values), conv


def load_h2() -> tuple[pd.DataFrame, pd.DataFrame]:
    """UIT-ViCTSD (tarudesu/ViCTSD). Toxicity 0/1, Constructiveness 0/1, Title = bài báo gốc."""
    parts = []
    for split in ["train", "valid", "test"]:
        p = pd.read_csv(RAW / "H2" / f"ViCTSD_{split}.csv")
        p["orig_split"] = split
        p["conversation_id"] = [f"H2_{split}_{i}" for i in range(len(p))]
        parts.append(p)
    raw = pd.concat(parts, ignore_index=True)
    # Bình luận cùng một bài báo phụ thuộc nhau → group theo bài báo.
    group = "art_" + raw["Title"].fillna("").map(lambda s: hashlib.sha1(s.encode()).hexdigest()[:12])
    conv = raw[["conversation_id", "orig_split", "Topic"]].rename(columns={"Topic": "topic"})
    conv["toxic"] = raw["Toxicity"].astype(int)
    conv["constructive"] = raw["Constructiveness"].astype(int)
    return _single_messages(raw, "H2", "Comment", group.values), conv


LOADERS = {"E1": load_e1, "E2": load_e2, "G1": load_g1, "H1": load_h1, "H2": load_h2}


def describe(msgs: pd.DataFrame, conv: pd.DataFrame) -> dict:
    turns = msgs.groupby("conversation_id").size()
    s = {
        "n_messages": int(len(msgs)),
        "n_conversations": int(msgs["conversation_id"].nunique()),
        "n_groups": int(msgs["group_id"].nunique()),
        "n_senders": int(msgs["sender_id"].nunique()),
        "turns_per_conversation": {"median": float(turns.median()), "p90": float(turns.quantile(0.9)),
                                   "max": int(turns.max())},
        "conversations_with_6plus_turns": int((turns >= 6).sum()),
        "empty_text": int((msgs["text"].str.strip() == "").sum()),
    }
    ts = msgs["timestamp_s"].dropna()
    if len(ts):
        s["time_range_utc"] = [str(pd.to_datetime(ts.min(), unit="s")), str(pd.to_datetime(ts.max(), unit="s"))]
        gap = msgs.groupby("conversation_id")["timestamp_s"].diff().dropna()
        s["gap_between_messages_s"] = {"median": float(gap.median()), "p90": float(gap.quantile(0.9))}
    if "dropped_not_dyadic" in conv.attrs:
        s["dropped_not_dyadic"] = conv.attrs["dropped_not_dyadic"]
    for col in ["has_attack", "toxic", "label_3class", "constructive", "orig_split"]:
        if col in conv:
            s[f"dist_{col}"] = {str(k): int(v) for k, v in conv[col].value_counts().sort_index().items()}
    if "is_attack" in msgs:
        s["attack_utterances"] = int(msgs["is_attack"].sum())
    return s


def main(ids: list[str]) -> None:
    for d in ids:
        msgs, conv = LOADERS[d]()
        extra = [c for c in msgs.columns if c not in COLUMNS and not c.startswith("_")
                 and c in ("is_attack", "utterance_id")]
        msgs = msgs[COLUMNS + extra]
        out = OUT / d
        out.mkdir(parents=True, exist_ok=True)
        msgs.to_parquet(out / "messages.parquet", index=False)
        conv.to_parquet(out / "conversations.parquet", index=False)
        stats = describe(msgs, conv)
        (out / "stats.json").write_text(json.dumps(stats, indent=2, ensure_ascii=False), encoding="utf-8")
        print(d, json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1:] or list(LOADERS))
