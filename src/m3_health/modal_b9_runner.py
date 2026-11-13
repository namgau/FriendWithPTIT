"""Explicit B9 GPU runner. Requires a pre-existing B8 volume and uploaded train labels."""
from pathlib import Path
import os
import subprocess

import modal

NAME = 'b8-friendwithptit-20261110'
volume = modal.Volume.from_name(NAME, create_if_missing=False)
if modal.is_local():
    ROOT = Path(__file__).resolve().parents[2]
    image = (modal.Image.debian_slim(python_version='3.11')
             .pip_install('tabpfn==2.0.9', 'torch==2.5.1', 'lightgbm==4.6.0', 'imbalanced-learn==0.13.0',
                          'numpy==1.26.4', 'pandas==2.2.3', 'pyarrow==19.0.1', 'PyYAML==6.0.2',
                          'scikit-learn==1.6.1', 'scipy==1.14.1', 'huggingface-hub==0.28.1')
             .add_local_dir(ROOT / 'src', '/root/project/src', copy=True)
             .add_local_dir(ROOT / 'configs', '/root/project/configs', copy=True))
else:
    image = modal.Image.debian_slim(python_version='3.11')
app = modal.App('b9-tabpfn-v2-head-a')


@app.function(image=image, gpu='T4', cpu=4, memory=32768, timeout=21600, volumes={'/b8': volume})
def run_one(dataset: str, source_commit: str):
    import sys
    import urllib.request

    if dataset not in ('E1', 'E2'):
        raise ValueError('dataset must be E1 or E2')
    base = Path('/root/project')
    dest = base / 'data' / 'processed' / dataset
    dest.mkdir(parents=True, exist_ok=True)
    for name, source in [('messages.parquet', Path('/b8/data/processed') / dataset / 'messages.parquet'),
                         ('labels_headA_train.parquet', Path('/b8/b9/labels') / dataset / 'labels_headA_train.parquet')]:
        if not source.is_file():
            raise FileNotFoundError(source)
        (dest / name).symlink_to(source)
    model = Path('/tmp/tabpfn-v2-classifier.ckpt')
    urllib.request.urlretrieve('https://huggingface.co/Prior-Labs/TabPFN-v2-clf/resolve/main/tabpfn-v2-classifier.ckpt', model)
    output = Path('/tmp/b9-results') / f'b9_{dataset}.csv'
    target = Path('/b8/b9/results-oof') / output.name
    target_oof = target.with_name(target.stem + '_oof.parquet')
    if target.exists() or target.with_suffix('.json').exists() or target_oof.exists():
        raise FileExistsError(target)
    os.environ['PYTHONPATH'] = str(base)
    os.environ['B9_SOURCE_COMMIT'] = source_commit
    subprocess.run([sys.executable, '-m', 'src.m3_health.run_tabpfn_b9', '--dataset', dataset,
                    '--checkpoint', str(model), '--output', str(output), '--device', 'cuda',
                    '--save-predictions'], cwd=base, check=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    import shutil
    shutil.copyfile(output, target)
    shutil.copyfile(output.with_suffix('.json'), target.with_suffix('.json'))
    shutil.copyfile(output.with_name(output.stem + '_oof.parquet'), target_oof)
    volume.commit()
    print(f'Completed {dataset}: {target}', flush=True)


@app.local_entrypoint()
def main(dataset: str):
    if dataset not in ('E1', 'E2'):
        raise ValueError('dataset must be E1 or E2')
    commit = subprocess.run(['git', 'rev-parse', 'HEAD'], capture_output=True, text=True, check=True).stdout.strip()
    run_one.remote(dataset, commit)
