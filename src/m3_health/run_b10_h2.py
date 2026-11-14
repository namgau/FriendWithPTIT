"""B10: one H2 fit/cal/validation fold and seed, using C2 PhoBERT."""
from __future__ import annotations

import argparse
import hashlib
import json
import time
from pathlib import Path

import numpy as np
from sklearn.metrics import accuracy_score, average_precision_score, brier_score_loss, f1_score, precision_score, recall_score, roc_auc_score

from src.eval.calibration import ece, fit_calibrator
from src.eval.threshold import confusion_at, threshold_by_fpr
from src.m3_health.b10_h2 import h2_splits
from src.m3_health.labels import PROC, load_config
from src.m3_health.run_head_b import load

REVISION = '86cd7fd4c148980922ac11a2cf5e257f2ba639e1'


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(8 * 1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fold', type=int, choices=range(5), required=True)
    parser.add_argument('--seed', type=int, choices=[42, 43, 44], required=True)
    parser.add_argument('--strategy', choices=['none', 'weighted', 'oversampling'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error('output already exists')
    import torch
    from importlib.metadata import version
    from src.m3_health.train_c2_transformer import PhoBERTClassifier
    if not torch.cuda.is_available():
        raise RuntimeError('CUDA GPU required; no silent CPU or embedding fallback')
    cfg = load_config()
    frame = load('H2')
    y, groups = frame.y.to_numpy(int), frame.group_id.to_numpy()
    fit, cal, val = list(h2_splits(y, groups, folds=cfg['head_b']['cv_folds'],
                                   split_seed=cfg['test_seed'], calibration_fraction=cfg['head_b']['calibration_fraction']))[args.fold]
    torch.manual_seed(args.seed)
    torch.cuda.manual_seed_all(args.seed)
    np.random.seed(args.seed)
    model = PhoBERTClassifier(seed=args.seed, imbalance=args.strategy, revision=REVISION,
                              model_name='vinai/phobert-base-v2', epochs=2, max_length=128, batch_size=16, lr=2e-5, device='cuda')
    start = time.perf_counter()
    model.fit(frame.text.iloc[fit].tolist(), y[fit])
    fit_seconds = time.perf_counter() - start
    raw_cal = model.predict_proba(frame.text.iloc[cal].tolist())
    raw_val = model.predict_proba(frame.text.iloc[val].tolist())
    calibrator = fit_calibrator('platt', raw_cal, y[cal])
    pcal, pval = calibrator(raw_cal), calibrator(raw_val)
    tau = threshold_by_fpr(y[cal], pcal, cfg['head_b']['threshold_fpr'])['threshold']
    at = confusion_at(y[val], pval, tau)
    default = raw_val >= .5
    output = {'dataset': 'H2', 'fold': args.fold, 'seed': args.seed, 'strategy': args.strategy,
              'split_seed': cfg['test_seed'], 'n_fit': len(fit), 'n_cal': len(cal), 'n_val': len(val),
              'n_fit_pos': int(y[fit].sum()), 'n_fit_neg': int((1-y[fit]).sum()),
              'fit_seconds': fit_seconds, 'pr_auc': float(average_precision_score(y[val], pval)),
              'roc_auc': float(roc_auc_score(y[val], pval)), 'brier': float(brier_score_loss(y[val], pval)),
              'ece': ece(y[val], pval), 'ece_uncalibrated': ece(y[val], raw_val),
              'macro_f1_default': float(f1_score(y[val], default, average='macro', zero_division=0)),
              'recall_default': float(recall_score(y[val], default, zero_division=0)),
              'fpr_default': float(((default == 1) & (y[val] == 0)).sum() / (y[val] == 0).sum()),
              'threshold': float(tau), 'recall_at_tau': float(at['recall']), 'fpr_at_tau': float(at['fpr']),
              'precision_at_tau': float(at['precision']), 'f1_at_tau': float(at['f1']),
              'accuracy_at_tau': float(accuracy_score(y[val], pval >= tau)),
              'gpu': torch.cuda.get_device_name(0), 'model': model.model_name, 'revision': REVISION,
              'epochs': 2, 'batch_size': 16, 'max_length': 128, 'lr': 2e-5,
              'torch': version('torch'), 'transformers': version('transformers'),
              'input_sha256': {n: sha256(PROC / 'H2' / n) for n in ('messages.parquet', 'labels_headB_train.parquet')},
              'runner_sha256': sha256(__file__), 'c2_sha256': sha256(Path(__file__).with_name('train_c2_transformer.py'))}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('x', encoding='utf-8') as f:
        json.dump(output, f, indent=2)
    print(json.dumps({k: output[k] for k in ('fold', 'seed', 'strategy', 'pr_auc', 'fit_seconds')}), flush=True)


if __name__ == '__main__':
    main()
