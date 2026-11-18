# C5 / X14 — điểm độc hại H1 trên dự báo G1

> Chỉ dùng H1/G1 train; tập test khóa chưa mở. Sinh bởi `python -m src.m3_health.run_x14`.

## Thiết kế

BGE-M3 đa ngữ đóng băng theo C1 mã hóa bình luận H1 và tiền tố G1. LR học nhãn độc hại chỉ trên H1 train; điểm độc hại dự đoán trên G1 là một đặc trưng bổ sung cho baseline TF-IDF + LR G1. Không dùng nhãn G1 khi fit bộ phân loại H1. Tiền tố G1 dừng trước lượt công kích. So sánh ghép cặp cùng 5 group fold theo trang Wikipedia, Platt và τ_B chọn trong train/cal của từng fold.

H1 held-out group PR-AUC của bộ phân loại embedding + LR: **0.673**; sau kiểm tra này, fit lại trên toàn H1 train trước khi tạo điểm G1. Đây là mô hình phụ X14, không thay thế PhoBERT C2.

## Kết quả CV trên G1 train

| Mô hình | PR-AUC mean ± SD |
|---|---:|
| TF-IDF + LR | 0.631 ± 0.031 |
| TF-IDF + LR + X14 | 0.632 ± 0.031 |

Paired cluster bootstrap Δ(PR-AUC, +X14 − base) = **+0.002**, CI 95% **[-0.002, +0.006]**, p = 0.370 (2000 lần lặp hợp lệ).

X14 là kiểm tra chuyển ngôn ngữ gián tiếp: nhãn H1 đo độc hại của một bình luận tiếng Việt, còn G1 đo khả năng công kích *trong tương lai* của hội thoại tiếng Anh. Hiệu năng G1 không chứng minh chất lượng cảnh báo trên chat tiếng Việt của app.

Provenance: commit `6618cbd`; input hash `13038c5b2223`; seed 42; OOF tại `reports/x14_g1_oof.parquet` (không commit).
