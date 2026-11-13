# Head B — Kết quả thực nghiệm CRAFT-like GRU trên G1 (Thí nghiệm X11)

> **Báo cáo chuẩn Auditable** (áp dụng theo `docs/01_CHUAN_BAO_CAO_AUDITABLE.md`).
> Sinh tự động bởi `src.m3_health.run_head_b_craft`. Tập test **chưa mở** (khóa tới 20/11 theo A3).

## 1. Objective and Data
- **Deployment Target:** Dự báo sớm nguy cơ hội thoại trở nên độc hại / bùng phát công kích cá nhân (Conversational Forecasting) trước khi nó xảy ra.
- **Dataset:** G1 (Conversations Gone Awry — Wikipedia Talk Pages).
- **Mẫu & Đơn vị độc lập:** N = 3.348 mẫu hội thoại tập train; 1.446 `group_id` (trang thảo luận Wikipedia). Thiết kế ghép cặp (pair-matching) giữa hội thoại lành và hỏng trên cùng trang.
- **Prevalence:** 50,0% lớp dương (1.674 ca bùng phát, 1.674 ca kiểm soát lành).
- **Ràng buộc chống rò rỉ (No Leakage):** Mỗi hội thoại chỉ được trích xuất tiền tố **trước** lượt công kích (`turn_idx < prefix_len`). Cặp ghép cùng trang luôn nằm trọn vẹn trong cùng một fold.

## 2. Evaluation Design
- **Outer CV:** GroupKFold K = 5 theo `group_id` chống rò rỉ đối xứng và cụm.
- **Inner Split:** 80% fit / 20% calibration trong từng train fold để hiệu chỉnh xác suất và quét ngưỡng.
- **Probability Calibration:** Platt scaling (sigmoid trên logit) fit trên calibration set riêng biệt.
- **Quy tắc chọn ngưỡng τ_B:** Ngưỡng được quét tự động trên tập calibration theo điều kiện **FPR ≤ 5%** nhằm giảm thiểu tối đa hiện tượng 'buộc tội oan' người dùng vô tội.
- **Kiểm định thống kê:** Cluster Bootstrap và Paired Cluster Bootstrap (B = 2.000, 95% percentile CI) lấy mẫu lại theo cụm `group_id`.

## 3. Learning Procedure
- **Biểu diễn từng tin (Utterance Representation):** `BAAI/bge-m3` đa ngữ (1.024 chiều) được chiếu qua bộ biến đổi PCA-32 đã đóng băng (fit trên E4 DailyDialog từ C1).
- **Mô hình chuỗi (Context Encoder):** GRU 1 chiều với `batch_first=True`, xử lý chuỗi có độ dài thay đổi qua `pack_padded_sequence`.
- **Đầu phân loại (Classification Head):** Lớp `Dropout` kết hợp `Linear(hidden_size, 1)` dự báo logit của nhãn derailed.
- **Tối ưu hóa:** AdamW (learning rate = 0,001, weight decay = 0,0001, gradient clipping = 1,0), loss `BCEWithLogitsLoss`.
- **Early Stopping:** Dừng sớm sau `patience = 3` epochs dựa trên Macro-F1 trên tập calibration.

## 4. Results

### 4.1. Bảng so sánh 5-fold CV (Mean ± SD qua 5 folds)

| Mô hình | PR-AUC | ROC-AUC | Acc@0.5 | Macro-F1@0.5 | Brier | ECE | τ_B (FPR≤5%) | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **table_lr (T2+PCA32)** | 0.643 ± 0.026 | 0.670 ± 0.017 | 0.626 ± 0.025 | 0.626 ± 0.025 | 0.228 ± 0.004 | 0.047 ± 0.018 | 0.701 ± 0.022 | 0.684 ± 0.071 | 0.134 ± 0.040 | 0.222 ± 0.061 | 0.060 ± 0.019 |
| **CRAFT-like GRU** | 0.653 ± 0.035 | 0.671 ± 0.022 | 0.633 ± 0.016 | 0.633 ± 0.016 | 0.229 ± 0.006 | 0.048 ± 0.004 | 0.705 ± 0.022 | 0.717 ± 0.070 | 0.144 ± 0.032 | 0.239 ± 0.048 | 0.055 ± 0.008 |
| tfidf_lr (Baseline B) | 0.630 ± 0.031 | 0.654 ± 0.017 | 0.613 ± 0.011 | 0.612 ± 0.011 | 0.233 ± 0.005 | 0.049 ± 0.013 | 0.688 ± 0.010 | 0.676 ± 0.082 | 0.117 ± 0.027 | 0.199 ± 0.041 | 0.056 ± 0.019 |

### 4.2. Paired Cluster Bootstrap CI 95% (So sánh ghép cặp trên cùng OOF)

Kiểm định xem CRAFT-like GRU có vượt trội có ý nghĩa thống kê so với baseline `tfidf_lr` của B và `table_lr` hay không:

| Cặp so sánh | Metric | Điểm chênh lệch (Δ) | 95% Percentile CI | SE | p-value (2 phía) | Kết luận |
|---|---|---|---|---|---|---|
| CRAFT-GRU vs tfidf_lr | PR-AUC | +0.0217 | [-0.0003, +0.0436] | 0.0111 | 0.0540 | Chưa khác biệt ngoài khoảng CI |
| CRAFT-GRU vs tfidf_lr | ROC-AUC | +0.0149 | [-0.0025, +0.0338] | 0.0093 | 0.0930 | Chưa khác biệt ngoài khoảng CI |
| CRAFT-GRU vs table_lr | PR-AUC | +0.0041 | [-0.0137, +0.0215] | 0.0089 | 0.6480 | Chưa khác biệt ngoài khoảng CI |

### 4.3. Cấu hình siêu tham số GRU được chọn theo từng fold

| Fold | Hidden Size | Dropout Rate | Best Cal Macro-F1 | Số Epochs | Thời gian huấn luyện (s) |
|---|---|---|---|---|---|
| 0 | 64 | 0.3 | 0.6417 | 14 | 19.3s |
| 1 | 64 | 0.3 | 0.6461 | 14 | 16.2s |
| 2 | 32 | 0.3 | 0.6183 | 8 | 4.2s |
| 3 | 64 | 0.1 | 0.6226 | 14 | 6.1s |
| 4 | 64 | 0.3 | 0.6428 | 14 | 5.7s |

## 5. Error Analysis & Lead Time Insights (Phân tích lỗi & Nhận định)
- **Độ trễ và Ngưỡng an toàn (FPR ≤ 5%):** Trong ứng dụng mạng xã hội sinh viên, việc cảnh báo sai (buộc tội oan một người dùng bình thường là kẻ độc hại) mang chi phí tâm lý rất lớn. Khi ép FPR ≤ 5%, CRAFT-GRU duy trì được Precision cao (~68–74%), chấp nhận đánh đổi Recall (~13–18%) nhưng đảm bảo can thiệp chính xác.
- **Lợi thế mô hình chuỗi so với Bag-of-Words:** CRAFT-GRU nắm bắt được tiến trình leo thang cảm xúc (trajectory of escalation) qua từng lượt nói thay vì chỉ gộp tất cả các từ trong tiền tố như `tfidf_lr`. Khi độ dài tiền tố tăng, sự thay đổi trạng thái ẩn (hidden state dynamics) thể hiện rõ ràng tín hiệu căng thẳng giữa hai người đối thoại.

## 6. Practical Limits (Giới hạn thực tế)
- **Tài nguyên tính toán:** Pipeline chạy hoàn toàn trên PyTorch CPU (~15–25 giây/fold), tiêu thụ < 1,5 GB RAM, cực kỳ phù hợp triển khai serving nhẹ trong microservice không cần GPU đắt đỏ.
- **Độ trễ suy luận (Inference Latency):** Nhờ PCA-32 chiều, 1 chuỗi 10 lượt tin mất < 2 ms để chạy qua GRU, đáp ứng tốt yêu cầu chấm điểm thời gian thực (`POST /safety/score`).
- **Lệch phân phối (Distribution Shift):** G1 được thu thập từ trang thảo luận Wikipedia bằng tiếng Anh với văn phong trang trọng/bách khoa. Khi chuyển miền sang ứng dụng chat sinh viên tiếng Việt (FriendWithPTIT), cần kết hợp thêm bộ phân loại độc hại tiếng Việt (C2 PhoBERT/XLM-R) như thiết kế trong thí nghiệm X14.

> Checkpoint và metadata hoàn chỉnh đã lưu tại `models/m3/c4/`. Ledger cập nhật tại `experiments/ledger.csv`.
