# Data Card H1 — ViHSD

> Sinh tự động bởi `python -m src.data.cards` từ `data/processed/H1/stats.json`. Không sửa số bằng tay.

- **Dùng cho:** Head B — bộ phân loại độc hại từng tin tiếng Việt
- **Nguồn:** HuggingFace `htdung167/ViHSD` — bản sao trùng kích thước từng file với `uitnlp/vihsd` (bản gốc bị gated). Luu et al. 2021.
- **License:** HuggingFace `uitnlp/vihsd` ghi MIT ; chỉ dùng cho nghiên cứu.
- **Checksum file gốc:** `data/checksums.txt`
- **Tiền xử lý** (`src/data/prepare.py`): Gộp train/dev/test gốc (giữ `orig_split`) ; `toxic` = OFFENSIVE hoặc HATE.
- **Đơn vị độc lập:** nội dung (`group_id` = băm văn bản đã chuẩn hóa): bình luận trùng nhau cùng phía split.
- **Lưu ý / hạn chế:** Bình luận mạng xã hội công khai, không phải chat riêng ; có teencode, tiếng lóng.
- **Riêng tư:** tên người dùng được băm (sha1, 12 ký tự) ; không commit dữ liệu.

## Số liệu (in từ code)

| Chỉ số | Giá trị |
|---|---|
| `n_messages` | 33400 |
| `n_conversations` | 33400 |
| `n_groups` | 30568 |
| `n_senders` | 0 |
| `turns_per_conversation` | {"median": 1.0, "p90": 1.0, "max": 1} |
| `conversations_with_6plus_turns` | 0 |
| `empty_text` | 2 |
| `dist_toxic` | {"0": 27624, "1": 5776} |
| `dist_label_3class` | {"0": 27624, "1": 2262, "2": 3514} |
| `dist_orig_split` | {"dev": 2672, "test": 6680, "train": 24048} |
