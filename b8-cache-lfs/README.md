# Cache embedding B8

Archive original : `cache-20261110T094512Z-1-001.zip`. Các phần được quản lý bằng Git LFS; cần cài Git LFS và chạy `git lfs pull` sau khi clone nếu các file là pointer.

Tại thư mục gốc của repository, chạy Python sau để kiểm tra hash từng phần, ghép theo manifest và kiểm tra hash của ZIP. Lệnh không ghi đè ZIP hiện có.

```python
import hashlib
import json
from pathlib import Path

folder = Path('b8-cache-lfs')
manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8'))
output = Path(manifest['file'])
whole = hashlib.sha256()
with output.open('xb') as dst:
    for part in manifest['parts']:
        digest = hashlib.sha256()
        size = 0
        with (folder / part['name']).open('rb') as src:
            while block := src.read(8 * 1024 * 1024):
                digest.update(block)
                whole.update(block)
                dst.write(block)
                size += len(block)
        if size != part['bytes'] or digest.hexdigest() != part['sha256']:
            raise ValueError(f"Phần không hợp lệ: {part['name']}")
if output.stat().st_size != manifest['bytes'] or whole.hexdigest() != manifest['sha256']:
    raise ValueError('ZIP không khớp manifest')
print(f'Đã kiểm tra và ghép: {output}')
```

Nếu việc ghép bị gián đoạn hoặc kiểm tra thất bại, kiểm tra file ZIP chưa hoàn chỉnh trước khi xóa và chạy lại. Nội dung ZIP gồm ba file embedding, index và metadata cho từng bộ E1, E2, G1, H1, H2 tại `data/processed/<ID>/`.
