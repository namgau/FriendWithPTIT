import pandas as pd
import pytest

from src.m3_health.bootstrap_tabpfn_b9 import paired_ci


def test_paired_cluster_bootstrap_and_validation():
    frame = pd.DataFrame([{'fold': f, 'row': i, 'group_id': f'g{i//2}', 'y': i % 2,
                           'model': model, 'probability': (0.8 if i % 2 else .2) if model == 'tabpfn_v2' else .5}
                          for f in range(2) for i in range(20) for model in ('tabpfn_v2', 'lightgbm')])
    result = paired_ci(frame, repeats=100, seed=7)
    assert result['estimate'] == pytest.approx(.5)
    assert result['ci_low'] == pytest.approx(.5)
    assert result['ci_high'] == pytest.approx(.5)
    assert result == paired_ci(frame, repeats=100, seed=7)
    with pytest.raises(ValueError, match='unpaired'):
        paired_ci(frame.iloc[1:], repeats=100)
