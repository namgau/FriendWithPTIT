# B7 — Duyệt chéo phần C và bàn giao Objective and data

Ngày đối chiếu: 24/11/2026. Phạm vi: `08_BAO_CAO_M3.md` §3.1, §3.3,
R1/R8/R9 và các artifact C1/C2/C4/C5, không chạy lại huấn luyện và không mở
dữ liệu test khóa. Đây là duyệt tài liệu, không thay cho A duyệt cuối hoặc C
duyệt bản B7 mới.

## Đối chiếu phần của C

| Nội dung | Nguồn đối chiếu | Kết quả |
|---|---|---|
| T4 BGE-M3 và PCA-32 | `results/C1_EMBEDDING.md`, §3.3 | Báo cáo phân biệt encoder đóng băng, PCA fit trên DailyDialog train và cache theo tiền tố; không suy ra đã đánh giá trên chat FwP. |
| CRAFT-like G1 | `results/HEAD_B_CRAFT.md`, R8 | GRU 0.653 ± 0.035 là mean ± SD các fold; CI paired so với TF-IDF [−0.0003, +0.0436] chứa 0. Chưa chứng minh GRU hơn hay tương đương baseline; cùng calibration dùng cho nhiều quyết định là hạn chế. |
| PhoBERT H1 | `results/HEAD_B_C2_PHOBERT_X12.md`, R1/R9 | 0.770 ± 0.018 là CV, chưa có paired CI với TF-IDF. FPR 0.053 là validation, mục tiêu ≤5% chỉ áp dụng khi chọn ngưỡng trên calibration. Không dùng như bảo đảm cho app. |
| Zero-shot H1 → H2 | `results/HEAD_B_C2_PHOBERT_X12.md`, R9 | 0.397 trên H2 train và 0.349 mean CV trong H2 khác giao thức, không ghép cặp; không kết luận thắng hoặc tương đương. Ngưỡng X12, artifact full-train và app là ba cấu hình khác nhau. |
| X14 | `results/HEAD_B_X14.md`, R8 | Điểm độc hại H1 từ BGE-M3 + LR thêm vào G1; Δ PR-AUC +0.002, CI [−0.002, +0.006]. Nhãn và ngôn ngữ khác nhau; không suy ra hiệu quả trên chat riêng. |

## Các điểm đề nghị A/C xử lý trước khi chốt báo cáo

1. **Bảng tóm tắt và quyết định mô hình:** G1 ghi TF-IDF là “mô hình chốt” dù
   CRAFT-like có PR-AUC trung bình cao hơn; H2 ghi TF-IDF 0.349 dù B10 weighted
   PhoBERT đạt 0.509 theo fold/thiết kế khác. Đề nghị A xác nhận tiêu chí chọn
   (triển khai, thống kê, tài nguyên hay kế hoạch A8) và ghi rõ, không kết luận
   thắng dựa trên so sánh không ghép cặp. Dòng cột “baseline mạnh nhất” của H2
   cũng cần A đối chiếu các candidate và định nghĩa baseline.
2. **Kết quả test và nhãn “chốt”:** R4 vẫn trống; bảng triển khai hiện gọi mô
   hình báo cáo là “chốt, đánh giá trên test” dù checkout chưa có kết quả test.
   A xác nhận A8 và cấu hình trước khi khẳng định đã đánh giá; không điền kết
   quả CV vào ô test.
3. **Phát biểu nguyên nhân:** tóm tắt cho rằng mọi FP chửi cảm thán do ViHSD
   gán nhãn, và R5 khẳng định nguyên nhân chuyển miền là do cách kết thúc CSKH.
   Đây là diễn giải cần giới hạn bởi audit/giả thuyết, không phải bằng chứng nhân
   quả cho từng lỗi. Đề nghị A/C rà lại câu chữ so với §3.4b và R2.
4. **Prevalence theo fold:** bảng §3.1 có train/test tổng thể từ metadata, chưa
   có bảng fit/calibration/validation cho từng thí nghiệm và fold. Cần người
   phụ trách runner cung cấp artifact tái lập được; không tính từ test khóa hay
   giả định tỉ lệ các fold bằng toàn train.
5. **Quyền dữ liệu:** E1 “other”, H2 chưa xác nhận license; G1 cần xác nhận riêng
   điều khoản nhãn, H1 cần xác nhận quyền của bản sao. A/B kiểm tra nguồn trước
   khi phát hành hoặc sử dụng ngoài phạm vi cho phép.

## Bàn giao

- B7 đã bổ sung mục tiêu, đơn vị mẫu, nhãn, chia dữ liệu, giới hạn đại diện và
  quyền sử dụng tại §3.1; số bảng giữ theo `configs/test_lock.json` và Data Card.
- Nhờ **C duyệt lại phần §3.1 mới**; nhờ **A xác nhận** các quyết định và mục
  còn mở ở trên. Chưa khẳng định B7 hoàn tất review cho đến khi có phản hồi.
- Không tạo metric hay dòng ledger mới; đối chiếu artifact có sẵn, không mở test.
