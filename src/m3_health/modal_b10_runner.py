"""Run one B10 PhoBERT H2 fit in Modal, only with explicit train-only inputs."""
from pathlib import Path
import subprocess

import modal

volume = modal.Volume.from_name('b8-friendwithptit-20261110', create_if_missing=False)
if modal.is_local():
    root = Path(__file__).resolve().parents[2]
    image = (modal.Image.debian_slim(python_version='3.11')
             .pip_install('torch==2.5.1', 'transformers==4.46.3', 'sentencepiece==0.2.0', 'underthesea==6.8.4',
                          'lightgbm==4.6.0', 'imbalanced-learn==0.13.0', 'numpy==1.26.4',
                          'pandas==2.2.3', 'pyarrow==19.0.1', 'PyYAML==6.0.2', 'scikit-learn==1.6.1',
                          'scipy==1.14.1', 'huggingface-hub==0.28.1')
             .add_local_dir(root / 'src', '/root/project/src', copy=True)
             .add_local_dir(root / 'configs', '/root/project/configs', copy=True))
else:
    image = modal.Image.debian_slim(python_version='3.11')
app = modal.App('b10-phobert-h2')


@app.function(image=image, gpu='T4', cpu=4, memory=16384, timeout=5400, volumes={'/b8': volume})
def run_one(fold: int, seed: int, strategy: str):
    import os
    import shutil
    import sys

    if fold not in range(5) or seed not in (42, 43, 44) or strategy not in ('none', 'weighted', 'oversampling'):
        raise ValueError('invalid job')
    base = Path('/root/project')
    folder = base / 'data/processed/H2'
    folder.mkdir(parents=True, exist_ok=True)
    for name, source in [('messages.parquet', Path('/b8/data/processed/H2/messages.parquet')),
                         ('labels_headB_train.parquet', Path('/b8/b10/labels/H2/labels_headB_train.parquet'))]:
        if not source.is_file():
            raise FileNotFoundError(source)
        (folder / name).symlink_to(source)
    target = Path('/b8/b10/results') / f'fold{fold}_seed{seed}_{strategy}.json'
    if target.exists():
        raise FileExistsError(target)
    temp = Path('/tmp/b10') / target.name
    os.environ['HF_HOME'] = '/b8/b10/model-cache'
    os.environ['PYTHONPATH'] = str(base)
    subprocess.run([sys.executable, '-m', 'src.m3_health.run_b10_h2', '--fold', str(fold), '--seed', str(seed),
                    '--strategy', strategy, '--output', str(temp)], cwd=base, check=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(temp, target)
    volume.commit()
    print(f'Completed: {target}', flush=True)


@app.local_entrypoint()
def main(fold: int, seed: int, strategy: str):
    run_one.remote(fold, seed, strategy)
