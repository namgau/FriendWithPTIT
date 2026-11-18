import zipfile

import pandas as pd
import pytest

from src.m3_health.evaluate_b6_dailydialog import evaluate, read_split


def make_zip(path, dialogs, acts):
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("validation/dialogues_validation.txt", dialogs)
        archive.writestr("validation/dialogues_act_validation.txt", acts)


def test_reads_alignment_and_evaluates_actual_heuristics(tmp_path):
    path = tmp_path / "validation.zip"
    make_zip(path, "Really ? __eou__ yes __eou__ Why __eou__ hi? __eou__\n", "2 1 2 1\n")
    result = evaluate(read_split(path, "validation"))
    assert result["dialogues"] == 1 and result["utterances"] == 4
    assert result["question"] == {"tp": 1, "fp": 1, "fn": 1, "tn": 1,
                                   "precision": .5, "recall": .5, "f1": .5}
    assert result["short"]["count"] == 4
    assert result["short"]["act_counts"] == {"inform": 2, "question": 2, "directive": 0, "commissive": 0}


@pytest.mark.parametrize("dialogs,acts", [
    ("hello __eou__ bye __eou__\n", "1\n"),
    ("hello __eou__\nbye __eou__\n", "1\n"),
    ("hello __eou__\n", "0\n"),
    ("hello __eou__ __eou__\n", "1 1\n"),
])
def test_rejects_invalid_data(tmp_path, dialogs, acts):
    path = tmp_path / "bad.zip"
    make_zip(path, dialogs, acts)
    with pytest.raises(ValueError):
        read_split(path, "validation")


def test_rejects_duplicate_turns():
    frame = pd.DataFrame({"conversation_id": [0, 0], "turn_idx": [0, 0], "act": [1, 1]})
    with pytest.raises(ValueError, match="duplicate"):
        evaluate(frame)
