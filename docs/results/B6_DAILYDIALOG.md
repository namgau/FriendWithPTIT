# B6 / X6 — Đánh giá quy tắc T2 trên DailyDialog

## Phạm vi và phương pháp

- Đánh giá **quy tắc hiện đang dùng** tại `src/m3_health/features.py::_message_table`, không huấn luyện hay thay quy tắc. Câu hỏi = tin có ký tự `?`. Tin cụt = văn bản sau chuẩn hóa thuộc `SHORT_REPLIES` **hoặc** có tối đa một từ theo regex `\w+`; quy tắc này gồm cả tin trống/emoji. T2 hiện chưa triển khai nhận diện wh-word hay đảo trợ động từ như danh sách ý tưởng trong tài liệu thiết kế.
- Dữ liệu: [DailyDialog](https://huggingface.co/datasets/roskoN/dailydialog) split **validation**, 1.000 hội thoại, 8.069 lượt thoại; act 2 = question, các act 1/3/4 = không-question. Đây là nhãn **ý định giao tiếp**, không nhất thiết trùng hình thức câu hỏi. Dùng validation là lựa chọn đánh giá cố định, không sử dụng split test DailyDialog; không đụng tập `data/locked/` của dự án.
- Nguồn: bản [roskoN/dailydialog](https://huggingface.co/datasets/roskoN/dailydialog/tree/5214b2a66405abf87fd229e5c1007985501ffe3e), revision `5214b2a66405abf87fd229e5c1007985501ffe3e`, `validation.zip`, SHA-256 `1dbcb00a0b4fe709788ce6604b561ba76557c88fe41f049d7899287838f0ec6a`. Mapping act đối chiếu [dataset card DailyDialog](https://huggingface.co/datasets/li2017dailydialog/daily_dialog). Giấy phép nguồn CC BY-NC-SA 4.0, chỉ sử dụng phi thương mại.
- Mỗi utterance được chấm một lần; không lấy mẫu, không seed, không hiệu chỉnh hay chọn ngưỡng trên nhãn. Code thực thi đã push trước khi chạy: commit `86c12ed`. Python 3.13.1, pandas 3.0.2, NumPy 2.4.4; chạy CPU.

## Kết quả câu hỏi

| | Act question | Act không-question |
|---|---:|---:|
| Có dấu `?` | TP 2.206 | FP 754 |
| Không có `?` | FN 38 | TN 5.071 |

| Số lượt | Tỷ lệ question | Precision | Recall | F1 |
|---:|---:|---:|---:|---:|
| 8.069 | 2.244 / 8.069 = 27,81% | 0,7453 | 0,9831 | 0,8478 |

Quy tắc bắt được phần lớn lượt có act question, nhưng gần một phần tư lượt chứa dấu hỏi mang act khác. Đây là số mô tả trên validation, không phải ước lượng độc lập cho hội thoại thực tế hay ngôn ngữ khác; các lượt trong cùng hội thoại không độc lập.

## Tin cụt: chưa thể tính precision/recall

DailyDialog **không có nhãn ground truth cho “tin cụt”**; bốn act inform/question/directive/commissive không tương đương tin cụt. Do đó không coi bất kỳ act nào là nhãn tin cụt và **không báo precision/recall giả**. Quy tắc hiện tại đánh dấu 148/8.069 lượt (1,83%); phân bố act trong 148 lượt: inform 74, question 30, directive 6, commissive 38. Đây chỉ là mô tả đầu ra, không đo độ chính xác; nhiều câu trả lời một từ có thể là phản hồi tự nhiên chứ không biểu thị hời hợt.

Bản tiếng Việt chưa được đánh giá, và DailyDialog là hội thoại tiếng Anh được viết tương đối sạch, khác chat sinh viên thực tế. Cần dữ liệu công khai có nhãn tin cụt phù hợp để đánh giá riêng, không tự gán nhãn mới trái phạm vi dự án.

## Tái lập

Tải `validation.zip` từ đường dẫn revision ở trên, kiểm tra SHA-256 rồi chạy từ gốc repo:

```bash
python -m src.m3_health.evaluate_b6_dailydialog --input /path/to/validation.zip --split validation --output /path/to/new-result.json
python -m pytest tests/test_b6_dailydialog.py -q
```

Lệnh đọc trực tiếp ZIP, kiểm tra số lượt và nhãn khớp từng hội thoại, từ chối nhãn lạ và chỉ xuất thống kê gộp. Output không được ghi đè. Dữ liệu gốc không commit vào Git.
