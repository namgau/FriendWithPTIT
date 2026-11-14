# B10 — PhoBERT và mất cân bằng trên H2

Kết quả **train/CV**, chưa mở test khóa. Pipeline dựa trên C2 (`e85c6a7`), mở rộng weighted BCE / oversampling độc lập, không dùng fallback. Model `vinai/phobert-base-v2`, revision `86cd7fd4c148980922ac11a2cf5e257f2ba639e1`; 2 epoch, max length 128, batch 16, AdamW lr 2e-5; GPU Tesla T4, torch 2.5.1, transformers 4.46.3. C2 dùng text thô, **không tách từ tiếng Việt trước PhoBERT**; đây là giới hạn quan trọng so với yêu cầu tiền xử lý PhoBERT, cần A/C review, không tự coi là pipeline tối ưu.

Split H2 như A5b: 5 fold phân nhóm theo bài báo (split seed 42), tách 20% group outer-train làm calibration, không có group chung giữa fit/cal/validation. Trên mỗi fold chạy seed 42, 43, 44 cho khởi tạo và oversampling, tổng 45 fit. Weighted dùng BCE `pos_weight=N_neg/N_pos` trên fit; oversampling chỉ lặp các mẫu fit tới cân bằng và dùng BCE không trọng số; none dùng BCE thường. Do oversampling tăng số batch mỗi epoch, số bước cập nhật nhiều hơn, nên khác biệt không chỉ do cân bằng nhãn.

Platt fit trên calibration chưa resample; chọn τ_B với FPR ≤ 5% trên chính calibration; validation chỉ đánh giá. Khác A5b CPU, ở đây **không áp prior shift tường minh** trước Platt; cần A cân nhắc khi so sánh. PR-AUC dưới đây là sau Platt. Mean ± SD trên 15 kết quả (5 fold × 3 seed); các kết quả cùng fold không độc lập, SD không phải CI.

| Phương án | PR-AUC | ROC-AUC | Macro-F1 mặc định | FPR mặc định | Recall @ τ_B | FPR @ τ_B | Precision @ τ_B | Fit giây |
|---|---:|---:|---:|---:|---:|---:|---:|
| none | 0.4305 ± 0.0903 | 0.8260 ± 0.0318 | 0.6339 ± 0.0713 | 0.0305 ± 0.0233 | 0.3202 ± 0.0870 | 0.0363 ± 0.0118 | 0.5117 ± 0.0982 | 210.6 ± 20.6 |
| weighted | 0.4486 ± 0.0711 | 0.8459 ± 0.0172 | 0.6633 ± 0.0440 | 0.1627 ± 0.0790 | 0.3525 ± 0.0982 | 0.0394 ± 0.0125 | 0.5136 ± 0.0534 | 207.1 ± 13.8 |
| oversampling | 0.4598 ± 0.0665 | 0.8313 ± 0.0154 | 0.6862 ± 0.0334 | 0.0585 ± 0.0404 | 0.3697 ± 0.0926 | 0.0387 ± 0.0154 | 0.5392 ± 0.0489 | 381.2 ± 52.9 |

Oversampling có PR-AUC trung bình cao nhất trong thử nghiệm này, nhưng không khẳng định khác biệt có ý nghĩa thống kê hoặc tổng quát ra test. Weighted tăng FPR mặc định; τ_B được chọn trên calibration **không đảm bảo** FPR validation ≤5%: none 2/15, weighted 3/15, oversampling 5/15 vượt 5%. A quyết định ngưỡng và tổng hợp kết quả CPU/GPU.

Chi tiết từng lượt: `B10_H2_folds.csv`. Hash inputs: messages `2c419e2b9082f38bf92fadf0460dd5d6812e4e88d789d1ea051091e952155670`, nhãn train `0d56b8c703d1270444d0d0c13cc1a4df5e3fe1f82a10b6e122ebd7a965635409`. Hash runner `e8c3b50b797d995cc3a8f6e2407ea3b9a9497e15586eb69336c607f8874df24e`; classifier C2 đã sửa `e23a83cc98e1791fd2b7cba58f139042798a49640b84ab085507a04886020d8e`. Code chưa commit khi chạy, không gán nhầm commit C2 làm mã thực thi. Không ghi text, nhãn từng mẫu hay trọng số model vào Git.
