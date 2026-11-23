# Kế hoạch C-app2 và C7 — 23/11/2026

Nhánh: `codex/c-app2-c7`, bắt đầu từ `origin/main` (`5df9dc1`).
Yêu cầu gốc: `02_PHAN_CONG.md` §4. Không chạy lại thí nghiệm hay mở tập test khóa.

## C-app2 — nhắc an toàn theo cuộc chat

1. Lưu số lần xác nhận “Vẫn gửi” bằng Preferences DataStore, khóa theo nguồn
   demo/backend, tài khoản và `matchId`. Trạng thái tồn tại qua mở lại app;
   không lưu nội dung tin nhắn, không gửi bộ đếm lên server.
2. Hai xác nhận đầu vẫn nhắc; xác nhận thứ ba tắt nhắc riêng cuộc chat.
   Tin thứ tư bỏ qua cả API safety lẫn fallback. Hủy/Sửa lại không tăng đếm.
3. Menu chat cho bật/tắt thủ công; bật lại đặt đếm về 0. Hiện thông báo khi
   tự tắt và khi đổi trạng thái. M2 blur/reveal và Head A giữ luồng riêng.
4. Dùng cùng cơ chế cho chat demo và backend. Backend giữ UUID/retry hiện có;
   retry một tin đã duyệt không tăng đếm hoặc tạo thêm lời nhắc.
5. Tránh gửi kép khi bấm nhanh, không gửi từ kết quả AI của phiên đã hết hạn.
6. Test: 0→1→2→3, gửi sau khi tắt, hủy, bật lại, hai chat/tài khoản, phục hồi
   từ file DataStore, nhấn lặp và kết quả AI trả về sau khi chat mất hiệu lực.
7. Chạy unit test và build debug Android; ghi kết quả thực tế và ca demo thủ công.

## C7 — báo cáo Head B và T3–T5, duyệt chéo B

1. Đối chiếu `08_BAO_CAO_M3.md` với code đặc trưng T3/T4, config C4,
   các bảng `HEAD_B_CV`, `HEAD_B_CRAFT`, `HEAD_B_C2_PHOBERT_X12`,
   `HEAD_B_X14`, `HEAD_A_ABLATION`, B6/B10 và ledger đã có.
2. Mô tả rõ G1 dự báo tương lai còn H1/H2 phân loại bình luận hiện tại;
   PCA đóng băng từ DailyDialog, T5 chỉ đánh giá G1, không có ablation T5 Head A.
3. Phân biệt CV mean ± SD với OOF bootstrap; FPR 5% là mục tiêu chọn trên
   calibration, không phải bảo đảm trên miền mới; tránh kết luận thắng từ SD.
4. Duyệt phần dữ liệu/B7 hiện có: nhãn, group/split, nguồn, giới hạn B6/B10;
   ghi checklist và việc cần A/B hoàn tất, không tự xác nhận bản B7 chưa gửi.
5. Cập nhật Practical limits bằng hành vi C-app2 đã triển khai, sửa placeholder
   lỗi thời thuộc Head B/T3–T5 và cập nhật dòng phân công với bằng chứng.

## Điều kiện bàn giao

- Code, unit test và APK debug build được trên môi trường hiện tại.
- Báo cáo không bổ sung số thực nghiệm mới; mọi số trích dẫn có nguồn trong repo.
- Nêu rõ giới hạn kiểm thử thiết bị/HTTP và các phần test cuối do A phụ trách.
- Chưa push, merge hay tự ghi nhận đã có người khác duyệt PR.

## Kết quả thực hiện

Đã triển khai các bước code/báo cáo và kiểm thử tự động: 81 test Android pass,
APK debug build pass; 52 test Python pass, 3 test cần cache G1 được skip.
Chi tiết và các ca UI/HTTP chưa chạy: `C_APP2_C7_VERIFICATION.md`.
Phần B hiện có được duyệt tại `C7_REVIEW_B.md`; bản B7 cuối/A8 vẫn do B/A bàn giao.
