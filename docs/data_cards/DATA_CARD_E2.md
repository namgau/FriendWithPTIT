# Data Card E2 — Customer Support on Twitter

> Sinh tự động bởi `python -m src.data.cards` từ `data/processed/E2/stats.json`. Không sửa số bằng tay.

- **Dùng cho:** Head A — bộ thứ 2 (cross-domain E1 ↔ E2)
- **Nguồn:** Kaggle `thoughtvector/customer-support-on-twitter`, file `twcs/twcs.csv`.
- **License:** CC BY-NC-SA 4.0 (phi thương mại).
- **Checksum file gốc:** `data/checksums.txt`
- **Tiền xử lý** (`src/data/prepare.py`): Nối tweet thành cây theo `in_response_to_tweet_id` ; giữ cây có đúng 1 khách hàng + 1 tài khoản CSKH và ≥ 2 tin. Thứ tự theo `created_at` rồi `tweet_id`.
- **Đơn vị độc lập:** khách hàng (`group_id` = khách hàng): mọi hội thoại của một khách nằm cùng phía split.
- **Lưu ý / hạn chế:** Hội thoại thường "tắt" vì đã giải quyết xong, không phải vì mất hứng thú ; phần lớn hội thoại rất ngắn (xem số hội thoại ≥ 6 tin).
- **Riêng tư:** tên người dùng được băm (sha1, 12 ký tự) ; không commit dữ liệu.

## Số liệu (in từ code)

| Chỉ số | Giá trị |
|---|---|
| `n_messages` | 2360317 |
| `n_conversations` | 741110 |
| `n_groups` | 601752 |
| `n_senders` | 601860 |
| `turns_per_conversation` | {"median": 2.0, "p90": 6.0, "max": 448} |
| `conversations_with_6plus_turns` | 75756 |
| `empty_text` | 0 |
| `time_range_utc` | ["2011-11-28 18:39:16", "2017-12-03 23:13:02"] |
| `gap_between_messages_s` | {"median": 799.0, "p90": 30618.0} |
| `dropped_not_dyadic` | 57087 |
