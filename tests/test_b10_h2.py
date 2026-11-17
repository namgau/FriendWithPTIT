import numpy as np
import pytest

from src.m3_health.b10_h2 import h2_splits, oversample_fit_indices
from src.eval.splits import group_kfold, held_out_group_split


def test_oversampling_is_balanced_deterministic_and_fit_only():
    y = np.array([0, 0, 0, 1, 1, 1, 1])
    fit = np.array([0, 1, 2, 3])
    selected = oversample_fit_indices(y, fit, seed=42)
    assert set(selected) == set(fit)
    assert np.bincount(y[selected]).tolist() == [3, 3]
    np.testing.assert_array_equal(selected, oversample_fit_indices(y, fit, seed=42))
    with pytest.raises(ValueError):
        oversample_fit_indices(y, [0, 1], seed=1)


def test_splits_match_a_and_no_group_leak():
    groups = np.repeat(np.arange(60), 4)
    y = np.tile([0, 1, 0, 1], 60)
    splits = list(h2_splits(y, groups))
    for fold, ((fit, cal, val), (tr, expected_val)) in enumerate(zip(splits, group_kfold(groups, 5, seed=42, y=y))):
        a, b = held_out_group_split(groups[tr], .2, seed=42 + fold, y=y[tr])
        np.testing.assert_array_equal(fit, tr[a])
        np.testing.assert_array_equal(cal, tr[b])
        np.testing.assert_array_equal(val, expected_val)
        assert set(groups[fit]).isdisjoint(groups[cal])
        assert set(groups[fit]).isdisjoint(groups[val])
        assert set(groups[cal]).isdisjoint(groups[val])


@pytest.mark.parametrize("strategy", ["none", "weighted", "oversampling"])
@pytest.mark.parametrize("word_segment", [False, True])
def test_phobert_imbalance_and_word_segmentation_work_together(monkeypatch, strategy, word_segment):
    torch = pytest.importorskip("torch")
    from types import SimpleNamespace
    from src.m3_health import c2_toxicity_data, train_c2_transformer

    seen = []
    original_dataset = train_c2_transformer.ToxicityDataset

    def capture_dataset(*args, **kwargs):
        dataset = original_dataset(*args, **kwargs)
        seen.append(dataset)
        return dataset

    monkeypatch.setattr(train_c2_transformer, "ToxicityDataset", capture_dataset)
    monkeypatch.setattr(c2_toxicity_data, "segment_text_underthesea", lambda text: "seg_" + text)

    class Tokenizer:
        def __call__(self, text, **kwargs):
            return {"input_ids": torch.tensor([[1, 2]]), "attention_mask": torch.tensor([[1, 1]])}

    class Model(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.weight = torch.nn.Parameter(torch.tensor(0.1))

        def forward(self, input_ids, attention_mask):
            return SimpleNamespace(logits=self.weight.expand(input_ids.shape[0], 1))

    model = train_c2_transformer.PhoBERTClassifier(
        device="cpu", epochs=1, batch_size=2, imbalance=strategy, word_segment=word_segment,
        revision="test-revision",
    )
    model.tokenizer, model.model = Tokenizer(), Model()
    model.fit(["a", "b", "c", "d"], [0, 0, 0, 1])
    assert np.bincount(seen[0].labels.astype(int)).tolist() == (
        [3, 3] if strategy == "oversampling" else [3, 1]
    )
    assert all(text.startswith("seg_") == word_segment for text in seen[0].texts)
    assert model.revision == "test-revision"
    assert len(model.predict_proba(["x", "y"])) == 2
    assert seen[1].texts == (["seg_x", "seg_y"] if word_segment else ["x", "y"])
