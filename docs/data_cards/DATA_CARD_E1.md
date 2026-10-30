# Data Card E1 — Ubuntu Dialogue Corpus

> Sinh tự động bởi `python -m src.data.cards` từ `data/processed/E1/stats.json`. Không sửa số bằng tay.

- **Dùng cho:** Head A (hội thoại sắp tắt) — bộ tiếng Anh chính
- **Nguồn:** Kaggle `rtatman/ubuntu-dialogue-corpus`, file `dialogueText.csv` + `dialogueText_196.csv` (không dùng `dialogueText_301.csv` 1.8 GB). Gốc: Lowe et al. 2015, log IRC #ubuntu.
- **License:** Kaggle ghi "other" ; log IRC công khai, dùng cho nghiên cứu.
- **Checksum file gốc:** `data/checksums.txt`
- **Tiền xử lý** (`src/data/prepare.py`): Giữ hội thoại có đúng 2 người gửi ; sắp theo thời gian rồi theo thứ tự trong file. Timestamp gốc làm tròn tới phút.
- **Đơn vị độc lập:** hội thoại (`group_id` = hội thoại). Một người có thể ở nhiều hội thoại → còn phụ thuộc giữa hội thoại, nêu ở Practical limits.
- **Lưu ý / hạn chế:** Hỗ trợ kỹ thuật, không phải chat làm quen ; khoảng cách tin rất ngắn (phiên IRC) → Δ = 48h gần như chỉ đánh dấu tin cuối, A cần cân nhắc Δ theo miền (X5).
- **Riêng tư:** tên người dùng được băm (sha1, 12 ký tự) ; không commit dữ liệu.

## Số liệu (in từ code)

| Chỉ số | Giá trị |
|---|---|
| `n_messages` | 9145313 |
| `n_conversations` | 999648 |
| `n_groups` | 999648 |
| `n_senders` | 254588 |
| `turns_per_conversation` | {"median": 5.0, "p90": 17.0, "max": 760} |
| `conversations_with_6plus_turns` | 450272 |
| `empty_text` | 1228 |
| `time_range_utc` | ["2004-07-05 12:45:00", "2012-11-30 20:03:00"] |
| `gap_between_messages_s` | {"median": 60.0, "p90": 120.0} |
| `dropped_not_dyadic` | 8743 |
