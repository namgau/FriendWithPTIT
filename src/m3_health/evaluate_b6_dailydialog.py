"""Evaluate the existing T2 heuristics on an original DailyDialog split ZIP.

DailyDialog act 2 is question; its acts do not label short/dry replies.
No model is fitted and only aggregate statistics are written.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from itertools import zip_longest
from pathlib import Path

import pandas as pd

from src.m3_health.features import _message_table

ACTS = {1: "inform", 2: "question", 3: "directive", 4: "commissive"}


def read_split(path: Path, split: str) -> pd.DataFrame:
    """Read aligned dialogue and act lines; fail rather than discard mismatches."""
    with zipfile.ZipFile(path) as archive:
        texts = archive.read(f"{split}/dialogues_{split}.txt").decode("utf-8").splitlines()
        acts = archive.read(f"{split}/dialogues_act_{split}.txt").decode("utf-8").splitlines()
    rows = []
    for conversation, pair in enumerate(zip_longest(texts, acts)):
        text_line, act_line = pair
        if text_line is None or act_line is None:
            raise ValueError("dialogue and act files contain different numbers of lines")
        pieces = text_line.split("__eou__")
        if not pieces[-1].strip():
            pieces.pop()
        utterances = [piece.strip() for piece in pieces]
        labels = [int(label) for label in act_line.split()]
        if len(utterances) != len(labels) or not utterances or any(not u for u in utterances):
            raise ValueError(f"misaligned or empty utterances in dialogue {conversation}")
        if any(label not in ACTS for label in labels):
            raise ValueError(f"unknown dialog act in dialogue {conversation}")
        rows.extend({"conversation_id": conversation, "turn_idx": turn, "text": text,
                     "timestamp_s": turn, "sender_id": turn % 2, "act": label}
                    for turn, (text, label) in enumerate(zip(utterances, labels)))
    if not rows:
        raise ValueError("empty dataset")
    return pd.DataFrame(rows)


def evaluate(frame: pd.DataFrame) -> dict:
    """Reuse the production feature table, not a separate approximation."""
    frame = frame.sort_values(["conversation_id", "turn_idx"]).reset_index(drop=True)
    if frame.duplicated(["conversation_id", "turn_idx"]).any():
        raise ValueError("duplicate dialogue/turn")
    if not frame["act"].isin(ACTS).all():
        raise ValueError("unknown dialog act")
    features = _message_table(frame)
    truth = frame["act"].eq(2)
    predicted = features["is_question"].astype(bool)
    tp = int((truth & predicted).sum())
    fp = int((~truth & predicted).sum())
    fn = int((truth & ~predicted).sum())
    tn = int((~truth & ~predicted).sum())
    precision = tp / (tp + fp) if tp + fp else None
    recall = tp / (tp + fn) if tp + fn else None
    f1 = 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else None
    short = features["is_short"].astype(bool)
    return {"dialogues": int(frame["conversation_id"].nunique()), "utterances": len(frame),
            "question": {"tp": tp, "fp": fp, "fn": fn, "tn": tn,
                         "precision": precision, "recall": recall, "f1": f1},
            "short": {"count": int(short.sum()), "fraction": float(short.mean()),
                      "act_counts": {name: int((short & frame["act"].eq(act)).sum())
                                     for act, name in ACTS.items()}},
            "act_counts": {name: int(frame["act"].eq(act).sum()) for act, name in ACTS.items()}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="DailyDialog split ZIP")
    parser.add_argument("--split", choices=("train", "validation", "test"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = evaluate(read_split(args.input, args.split))
    result.update({"split": args.split, "input_sha256": hashlib.sha256(args.input.read_bytes()).hexdigest()})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as out:
        json.dump(result, out, indent=2, ensure_ascii=False)
        out.write("\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
