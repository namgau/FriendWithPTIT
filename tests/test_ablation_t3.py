import pandas as pd
import pytest

from src.m3_health.features_t3 import T3_COLUMNS
from src.m3_health.run_ablation_a import ALL, add_t3, sets_for


def test_ablation_is_progressive_and_baselines_stay_unchanged():
    base = pd.DataFrame(columns=ALL)
    original = sets_for(base)
    with_t3 = sets_for(pd.DataFrame(columns=ALL + T3_COLUMNS))
    assert with_t3["T1+T2 (đủ)"] == original["T1+T2 (đủ)"]
    assert with_t3["T1+T2+T3"] == ALL + T3_COLUMNS
    with_t4 = sets_for(pd.DataFrame(columns=ALL + T3_COLUMNS + ["t4_example"]))
    assert with_t4["T1+T2+T3+T4"] == ALL + T3_COLUMNS + ["t4_example"]
    with pytest.raises(ValueError, match="incomplete"):
        sets_for(pd.DataFrame(columns=ALL + T3_COLUMNS[:1]))


def test_add_t3_aligns_samples_and_lightgbm_accepts_missing(tmp_path, monkeypatch):
    from lightgbm import LGBMClassifier
    import src.m3_health.run_ablation_a as ablation

    folder = tmp_path / "E1"
    folder.mkdir()
    pd.DataFrame({"conversation_id": ["a", "a", "b", "b"], "turn_idx": [0, 1, 0, 1],
                  "sender_id": ["x", "y", "x", "y"], "text": ["I tea", "you tea", "hello", "world"]
                  }).to_parquet(folder / "messages.parquet", index=False)
    monkeypatch.setattr(ablation, "PROC", tmp_path)
    lab = pd.DataFrame({"conversation_id": ["b", "a", "a", "b"], "turn_idx": [1, 1, 0, 0]}, index=[4, 2, 9, 7])
    base = pd.DataFrame(0., index=lab.index, columns=ALL)
    result = add_t3("E1", lab, base)
    assert result.index.tolist() == [4, 2, 9, 7]
    assert result.loc[2, "t3_lsm"] == pytest.approx(1)
    assert pd.isna(result.loc[4, "t3_lsm"])
    model = LGBMClassifier(n_estimators=2, min_child_samples=1, verbosity=-1)
    model.fit(result[sets_for(result)["T1+T2+T3"]], [0, 1, 0, 1])
    assert model.predict_proba(result[sets_for(result)["T1+T2+T3"]]).shape == (4, 2)
