# Data Card G1 — Conversations Gone Awry — Wikipedia

> Sinh tự động bởi `python -m src.data.cards` từ `data/processed/G1/stats.json`. Không sửa số bằng tay.

- **Dùng cho:** Head B (hội thoại sắp trở nên công kích)
- **Nguồn:** ConvoKit `conversations-gone-awry-corpus` (zissou.infosci.cornell.edu/convokit/datasets/). Zhang et al. 2018 ; Chang & Danescu-Niculescu-Mizil 2019.
- **License:** Nội dung trang thảo luận Wikipedia (CC BY-SA) ; nhãn của tác giả corpus, dùng cho nghiên cứu.
- **Checksum file gốc:** `data/checksums.txt`
- **Tiền xử lý** (`src/data/prepare.py`): Bỏ dòng tiêu đề mục (`is_section_header`) như CRAFT ; sắp theo timestamp. Cột `is_attack` đánh dấu lượt công kích (A dùng để cắt tiền tố, A2c).
- **Đơn vị độc lập:** trang Wikipedia (`group_id` = trang): giữ nguyên cặp ghép hỏng/lành cùng trang.
- **Lưu ý / hạn chế:** Thiết kế ghép cặp → cân bằng 50/50, ngoài đời tỉ lệ thấp hơn nhiều (prior shift).
- **Riêng tư:** tên người dùng được băm (sha1, 12 ký tự) ; không commit dữ liệu.

## Số liệu (in từ code)

| Chỉ số | Giá trị |
|---|---|
| `n_messages` | 26188 |
| `n_conversations` | 4188 |
| `n_groups` | 1816 |
| `n_senders` | 8056 |
| `turns_per_conversation` | {"median": 6.0, "p90": 9.0, "max": 19} |
| `conversations_with_6plus_turns` | 2465 |
| `empty_text` | 352 |
| `time_range_utc` | ["2002-09-11 19:22:24", "2018-06-29 09:48:43"] |
| `gap_between_messages_s` | {"median": 3349.5, "p90": 182723.30000000016} |
| `dist_has_attack` | {"False": 2094, "True": 2094} |
| `dist_orig_split` | {"test": 840, "train": 2508, "val": 840} |
| `attack_utterances` | 2094 |
