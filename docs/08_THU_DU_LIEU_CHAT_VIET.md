# 08 — Thu dữ liệu chat tiếng Việt (V1) giữ riêng tư

> Mục tiêu: có hội thoại tiếng Việt **thật, có timestamp** cho Module 3 mà nhóm **không bao giờ nhìn thấy nội dung tin nhắn**.
> Người phụ trách: Người B (xem `02_PHAN_CONG.md`).

## 1. Nguyên tắc
1. **Tự nguyện + đồng ý bằng văn bản** (Google Form): mục đích, dữ liệu nào được gửi, lưu ở đâu, xóa khi nào, rút lại bất cứ lúc nào.
2. **Văn bản không rời máy người đóng góp.** Script chạy local, chỉ xuất **đặc trưng số + embedding**.
3. **Ẩn danh:** tên người → hash có salt riêng mỗi người đóng góp (nhóm không có salt) → không đảo ngược được, không nối được giữa các người đóng góp.
4. Người đóng góp **tự chọn** hội thoại nào gửi (gợi ý: chat 1–1 với bạn bè/người quen, không gửi nhóm chat, không gửi chat có thông tin nhạy cảm).
5. Người ở đầu bên kia không trực tiếp đồng ý → đây là lý do bắt buộc chỉ gửi đặc trưng số; nêu rõ trong Practical limits.
6. Dữ liệu V1 **không commit lên GitHub** (`data/pilot/` đã bị `.gitignore`), lưu Drive riêng của nhóm, xóa sau khi chấm điểm.

## 2. Cách người đóng góp xuất dữ liệu
| Nền tảng | Cách xuất | Ghi chú kỹ thuật |
|---|---|---|
| Messenger | Facebook → Cài đặt → *Tải xuống thông tin của bạn* → chỉ chọn **Tin nhắn**, định dạng **JSON**, chất lượng thấp | File `message_1.json`: `participants`, `messages[]` có `sender_name`, `timestamp_ms`, `content`. **Lỗi font tiếng Việt**: sửa bằng `s.encode('latin1').decode('utf8')` |
| Telegram | Telegram Desktop → Settings → Advanced → *Export Telegram data* → chọn Personal chats, **JSON** | `messages[]` có `from`, `date`/`date_unixtime`, `text` (có thể là list) |
| Zalo | Không có export chính thức → **không dùng** | |

## 3. Script trích đặc trưng (`tools/extract_features_local.py`, Người B viết)
```
Input : thư mục export (Messenger hoặc Telegram) trên máy người đóng góp
Bước  : 1. Đọc JSON, sửa font, bỏ hội thoại nhóm (> 2 người) và hội thoại < 10 tin
        2. Đổi tên người → hash(salt_local + tên); người đóng góp = "self"
        3. Với mỗi tin: timestamp, sender_hash, n_words, n_chars, is_question, is_short_reply,
           n_emoji, has_laugh, các đếm từ chức năng (cho LSM), điểm độc hại từ bộ phân loại tiếng Việt của nhóm (Head B, `06_*` T5), embedding MiniLM (384 chiều,
           có thể giảm còn 32 chiều bằng PCA cố định do nhóm cung cấp, fit trên DailyDialog), điểm cảm xúc
        4. Ghi ra features_<donor_hash>.parquet — KHÔNG có cột văn bản
        5. In ra màn hình 5 dòng đầu để người đóng góp TỰ KIỂM TRA không có chữ
Output: 1 file parquet gửi cho nhóm qua Google Form upload
```
- Bộ đặc trưng tin nhắn phải **khớp đúng** với bộ trích từ E1/E2 (cùng hàm trong `src/m3_health/features.py`) để thí nghiệm cross-domain hợp lệ.
- Phát hành script dạng 1 file + `requirements` tối thiểu, có hướng dẫn chạy 3 lệnh; hoặc bản notebook Colab chạy với file người đóng góp tự tải lên phiên Colab của chính họ.
- Có test: chạy trên file JSON giả (tạo bằng simulator S2) → assert output không có cột kiểu chuỗi dài.

## 4. Quy mô & tiến độ
| Mục | Mục tiêu tối thiểu | Mục tiêu tốt |
|---|---|---|
| Người đóng góp | 10 | 20+ |
| Hội thoại / người | 10 | 30 |
| Tổng hội thoại | ~100 | 500+ |

- Tuần 1: Form đồng ý + bản nháp script. Tuần 2: thử với 2–3 thành viên nhóm. Tuần 3: phát rộng (lớp, CLB). Tuần 4: chốt dữ liệu.
- Nếu < 10 người: V1 chỉ dùng làm **test** (không train), báo CI rộng, leave-one-donor-out.

## 5. Gán nhãn tay nhỏ cho bộ phát hiện câu hỏi/tin cụt (X6)
~200 câu tiếng Việt do **chính thành viên nhóm** lấy từ chat của mình (đã xin phép, bỏ thông tin cá nhân), 2 người gán nhãn độc lập → báo Cohen's kappa → dùng làm tập đánh giá precision/recall cho luật `is_question`, `is_short_reply`. Tập này được commit (đã ẩn danh) vì nhỏ và đã kiểm tra.

## 6. Ghi vào báo cáo
Data Card `data_cards/DATA_CARD_V1.md`: số người, số hội thoại, phân phối độ dài, khoảng thời gian, prevalence, quy trình đồng ý, những gì **không** thu, giới hạn đại diện.
