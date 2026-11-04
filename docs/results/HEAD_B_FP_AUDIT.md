# Head B — đọc tay 100 ca buộc tội oan (FP) trên H1 (A9, E3)

> **Viết tay, không sinh bằng script.** Mẫu: 100 FP chọn ngẫu nhiên (seed 42) trong OOF của `tfidf_lr` base tại τ_B
> (`python -m src.m3_health.run_head_b_e5`, commit 1127336) ; danh sách + văn bản ở `reports/h1_fp_audit.csv`
> (gitignored, **không commit** vì chứa nội dung bình luận). Phân loại sơ bộ do Claude (trợ lý AI) đề xuất theo yêu
> cầu của A, **A duyệt lại** trước khi đưa số vào báo cáo. Chỉ dùng cho phân tích lỗi: **không** sửa nhãn, không dùng
> để train hay tính lại metric. CI 95% Wilson (n = 100).

| Nhóm | Định nghĩa | Số ca | CI 95% |
|---|---|---|---|
| A. Nhãn có thể sai | Câu thực sự xúc phạm / thù ghét nhắm vào người hoặc nhóm (giới, LGBT, người nước ngoài, xúc phạm trực tiếp) nhưng nhãn là sạch | 16 | [0.10, 0.24] |
| B. Chửi thề cảm thán / thân mật | Từ chửi dùng để nhấn mạnh ("hay vcl", "xinh vl") hoặc đùa giữa bạn bè, không nhắm ai | 26 | [0.18, 0.35] |
| C. Vùng xám | Chỉ trích gay gắt, chính trị, mỉa mai, chê bai không thô tục — người gán nhãn có thể bất đồng | 34 | [0.25, 0.44] |
| D. Lỗi rõ của mô hình | Câu bình thường (than mạng lag, hỏi han, câu không dấu / teencode khó đọc) | 22 | [0.15, 0.31] |
| E. Rác dữ liệu | Spam gộp hàng trăm bình luận, câu 1 chữ | 2 | [0.01, 0.07] |

## Nhận xét

- Chỉ khoảng **1/6 FP là nhãn sai rõ** (A) → phần lớn lỗi không đổ cho dữ liệu được. Confident learning báo 67% FP
  "nghi nhiễu" (`HEAD_B_E5.md`) — cao hơn nhiều so với đọc tay, vì nó gộp cả lỗi tự tin của mô hình.
- **Buộc tội oan thật (B) chiếm khoảng 1/4 FP** — đúng rủi ro đã nêu ở `06_*` §6.2. Thêm đặc trưng chửi có / không
  đối tượng **không** giảm được loại này (FPR trên slice có từ chửi tăng 0.50 → 0.71, `HEAD_B_E5.md`): trong ViHSD
  phần lớn câu có từ chửi được gán OFFENSIVE (recall trên slice này 0.86 → 0.97), nên mô hình học đúng định nghĩa của
  dataset. Đây là **lệch định nghĩa** giữa dataset và nhu cầu app, không sửa được bằng đặc trưng → xử lý ở tầng sản
  phẩm (chỉ nhắc nhẹ người gửi, không phạt ; cân nhắc không nhắc khi chỉ có chửi cảm thán) và ghi vào Practical limits.
- Nhóm C lớn nhất → ranh giới "xúc phạm" vốn mơ hồ ; báo cáo nên nói rõ trần hiệu năng do bất đồng người gán nhãn.
- Trong nhóm A có câu **mang tính tình dục / phân biệt giới** bị gán sạch — đúng loại rủi ro chính của app hẹn hò mà
  dataset không bao phủ tốt (`06_*` §6.2 "Định nghĩa độc hại lệch nhu cầu app").
