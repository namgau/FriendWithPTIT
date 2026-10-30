# Data Card H2 — UIT-ViCTSD

> Sinh tự động bởi `python -m src.data.cards` từ `data/processed/H2/stats.json`. Không sửa số bằng tay.

- **Dùng cho:** Head B — bộ tiếng Việt thứ 2 ; bài thực hành imbalance/SMOTE
- **Nguồn:** HuggingFace `tarudesu/ViCTSD`. Nguyen et al. 2021.
- **License:** Không ghi trên HuggingFace ; dùng cho nghiên cứu (cần kiểm tra lại với bài báo gốc).
- **Checksum file gốc:** `data/checksums.txt`
- **Tiền xử lý** (`src/data/prepare.py`): Gộp train/valid/test gốc (giữ `orig_split`) ; `toxic` = cột Toxicity.
- **Đơn vị độc lập:** bài báo (`group_id` = băm tiêu đề bài): bình luận cùng bài phụ thuộc nhau.
- **Lưu ý / hạn chế:** Rất mất cân bằng ; bình luận báo điện tử, văn phong khác chat.
- **Riêng tư:** tên người dùng được băm (sha1, 12 ký tự) ; không commit dữ liệu.

## Số liệu (in từ code)

| Chỉ số | Giá trị |
|---|---|
| `n_messages` | 10000 |
| `n_conversations` | 10000 |
| `n_groups` | 825 |
| `n_senders` | 0 |
| `turns_per_conversation` | {"median": 1.0, "p90": 1.0, "max": 1} |
| `conversations_with_6plus_turns` | 0 |
| `empty_text` | 0 |
| `dist_toxic` | {"0": 8899, "1": 1101} |
| `dist_constructive` | {"0": 6404, "1": 3596} |
| `dist_orig_split` | {"test": 1000, "train": 7000, "valid": 2000} |
