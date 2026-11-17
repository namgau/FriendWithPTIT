# B10 — PhoBERT và mất cân bằng trên H2

Kết quả **train/CV**, chưa mở test khóa. Chạy từ commit đã push `f4b6c4ce701de37d2e77e611b4d9159996aeb7d7`, dùng `vinai/phobert-base-v2` revision `86cd7fd4c148980922ac11a2cf5e257f2ba639e1` và **tách từ tiếng Việt bằng underthesea 9.5.0**, như C2. Cả 45 output đều ghi `word_segment: true`. Mỗi fit dùng 2 epoch, max length 128, batch 16, AdamW lr 2e-5; GPU Tesla T4, torch 2.5.1, transformers 4.46.3. Kết quả này thay thế lượt B10 cũ chưa tách từ.

Split H2 dùng cùng cách chia và seed như A5b: 5 fold phân nhóm theo bài báo (split seed 42), tách 20% group outer-train làm calibration, không có group chung giữa fit/cal/validation. Tuy nhiên **các fold thực tế khác A5b** do B10 chạy scikit-learn 1.6.1 trên Modal còn A5b dùng 1.9.1; `StratifiedGroupKFold` chia khác giữa hai phiên bản. Trên mỗi fold chạy seed 42, 43, 44 cho khởi tạo và oversampling, tổng 45 fit. Weighted dùng BCE `pos_weight=N_neg/N_pos` trên fit; oversampling chỉ lặp các mẫu fit tới cân bằng và dùng BCE không trọng số; none dùng BCE thường. Do oversampling tăng số batch mỗi epoch, số bước cập nhật nhiều hơn, nên khác biệt không chỉ do cân bằng nhãn.

Platt fit trên calibration chưa resample; chọn τ_B với FPR ≤ 5% trên chính calibration; validation chỉ đánh giá. Calibration dùng phân phối gốc nên không cần prior shift. PR-AUC dưới đây là sau Platt. Mean ± SD trên 15 kết quả (5 fold × 3 seed); các kết quả cùng fold không độc lập, SD không phải CI.

| Phương án | PR-AUC | ROC-AUC | Macro-F1 mặc định | FPR mặc định | Recall @ τ_B | FPR @ τ_B | Precision @ τ_B | Fit giây |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| none | 0.4821 ± 0.0949 | 0.8530 ± 0.0154 | 0.6789 ± 0.0456 | 0.0397 ± 0.0309 | 0.3969 ± 0.1185 | 0.0433 ± 0.0134 | 0.5123 ± 0.0922 | 93.4 ± 8.5 |
| weighted | 0.5086 ± 0.0739 | 0.8658 ± 0.0136 | 0.6730 ± 0.0289 | 0.1715 ± 0.0586 | 0.4171 ± 0.0897 | 0.0427 ± 0.0162 | 0.5422 ± 0.0374 | 95.7 ± 9.8 |
| oversampling | 0.4708 ± 0.0770 | 0.8416 ± 0.0225 | 0.6747 ± 0.0474 | 0.0859 ± 0.0783 | 0.3919 ± 0.0778 | 0.0410 ± 0.0134 | 0.5331 ± 0.0409 | 159.2 ± 11.7 |

## So sánh paired theo fold

Mỗi ô là Δ PR-AUC trung bình trên ba seed của cùng fold; Δ dương nghĩa là phương án đứng trước cao hơn. Các fold là đơn vị ghép cặp; không coi 15 lượt là 15 quan sát độc lập.

| So sánh | Fold 0 | Fold 1 | Fold 2 | Fold 3 | Fold 4 | Δ trung bình | Fold dương |
|---|---:|---:|---:|---:|---:|---:|---:|
| oversampling − none | -0.0822 | -0.0018 | +0.0553 | -0.0288 | +0.0010 | -0.0113 | 2/5 |
| weighted − none | -0.0208 | +0.0343 | +0.0876 | -0.0098 | +0.0411 | +0.0265 | 3/5 |
| oversampling − weighted | -0.0614 | -0.0361 | -0.0323 | -0.0190 | -0.0401 | -0.0378 | 0/5 |

Weighted có xu hướng cao hơn none (+0.0265, 3/5 fold); oversampling thấp hơn none trung bình (-0.0113, cao hơn ở 2/5 fold) và thấp hơn weighted trong cả 5 fold. Đây là mô tả kết quả CV, **không khẳng định khác biệt có ý nghĩa thống kê** hoặc tổng quát sang test. Ngưỡng τ_B chọn trên calibration không đảm bảo FPR validation ≤ 5%: none 4/15, weighted 5/15, oversampling 4/15 vượt 5%.

Đối chiếu tham khảo: TF-IDF + LR class-weight trên H2 đạt PR-AUC **0.349 ± 0.044** (5-fold CV, `IMBALANCE_B.md`); PhoBERT X12 zero-shot H1→H2 đạt **0.397** (một lần đánh giá trên H2 train, `HEAD_B_C2_PHOBERT_X12.md`). So sánh B10 với TF-IDF là **không ghép cặp** vì fold khác nhau; so sánh với X12 cũng không ghép cặp do thiết kế đánh giá khác, không kết luận ý nghĩa thống kê.

Chi tiết từng lượt: `B10_H2_folds.csv`. SHA-256 input: messages `2c419e2b9082f38bf92fadf0460dd5d6812e4e88d789d1ea051091e952155670`, nhãn train `0d56b8c703d1270444d0d0c13cc1a4df5e3fe1f82a10b6e122ebd7a965635409`. Hash file thực thi trong output: runner `e34ace8d15edcb3610c48a9f611793f179c5b7a75aa3e9b0bf83391bcf94b0c8`, classifier `b5aa8fcc89db436091d3bee6f88ea8061de0190ecb84429de0d36df20e34f3db`. Classifier checkout Windows có xuống dòng **hỗn hợp 1.005 CRLF và 14 LF**; chuẩn hóa CRLF thành LF cho hash `76a4241d4522468b6beb398e36df6e46493019974a11638c9f5483219cc57add`, khớp byte-for-byte Git blob tại commit đã chạy, không khác logic. Không ghi text, nhãn từng mẫu hay trọng số model vào Git.
