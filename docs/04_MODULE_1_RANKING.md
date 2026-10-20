# 04 — Module 1: ML Ranking (thứ tự Swipe) — Người A

> Đọc kèm `01_CHUAN_BAO_CAO_AUDITABLE.md` và `03_MODULE_0_DATA_EVAL_SERVING.md`.

## 1. Objective and data

**Deployment target (1 câu):** Với mỗi viewer, trong danh sách ứng viên đã qua bộ lọc cứng (trường, khoa, tầng GPA, bán kính, tuổi, trạng thái tốt nghiệp), xếp thứ tự giảm dần theo xác suất **hai bên cùng thích** (mutual like → match), dùng *chỉ thông tin có trước khi swipe* (ảnh đang mờ nên **không dùng điểm hấp dẫn ngoại hình**).

### 1.1. Bộ dataset (3 nguồn thật + 2 nguồn tự tạo)

| Dataset | Vai trò | Nhãn | Đơn vị độc lập | Ghi chú kỹ thuật |
|---|---|---|---|---|
| **D1 Speed Dating** | Mô hình content-based chính, có nhãn match thật | `match` | **wave** (nhóm chính) ; người (phân tích độ nhạy) | Mỗi cặp (iid,pid) xuất hiện **2 chiều** với cùng `match` → cùng fold. Các wave dùng thang điểm khác nhau (6–9 thang 1–10, còn lại phân bổ 100 điểm) → chuẩn hóa trong wave |
| **D2 Libimseti** | Collaborative filtering (matrix factorization) — thí nghiệm so sánh phương pháp | rating≥ngưỡng (vd ≥7) | **người chấm (rater)** | Không cần map sang FwP (id khác) → là *thí nghiệm phương pháp*, không đưa vào production ensemble (xem §5) |
| **D3 OkCupid** | Học **bio-embedding** (TF-IDF+SVD / Sentence-BERT) dùng lại cho FwP | không có | hồ sơ | Không có nhãn → đánh giá gián tiếp: chất lượng embedding qua truy hồi hồ sơ cùng nhóm sở thích/nghề (proxy), không báo AUC match |
| **S1 Synth-Profiles** | Kiểm tra pipeline + đặc trưng riêng FwP (trường, tốt nghiệp, tầng GPA) | mô phỏng | cặp sinh ra (nhóm theo viewer) | Kết quả chỉ chứng minh pipeline, không chứng minh giả định |
| **P1 Pilot** | **Test phân phối triển khai** (sinh viên thật) | like giả lập | người | n nhỏ → CI rộng, báo trung thực |

### 1.2. Cột bị cấm (leakage) trong D1
`dec`, `dec_o` (match = dec ∧ dec_o), `like`, `like_o`, `prob`, `prob_o`, `met`, mọi điểm đánh giá **sau** buổi hẹn (`attr`, `sinc`, `intel`, `fun`, `amb`, `shar` của bảng sau hẹn, `*_o`), `match_es`. Ảnh mờ ⇒ cũng loại điểm `attr` các dạng. Chỉ giữ: đặc trưng hồ sơ khai trước (tuổi, khoa `field_cd`, `undergra`, `career_c`, `goal`, `date`, `go_out`, 17 mức độ quan tâm sở thích, `attr1_1`… thuộc "điều tôi tìm kiếm" nếu đo trước sự kiện) và đặc trưng cặp tính từ chúng (`age_diff`, `same_field`, `same_school≈undergra`, `interest_corr`, `goal_match`…). Liệt kê danh sách cột cuối cùng vào Data Card.

## 2. Evaluation design

| Hạng mục | Thiết kế |
|---|---|
| Test held-out | ~20% **wave** (≈4 wave), seed 42, khóa lại |
| Outer CV | GroupKFold theo **wave**, K = 5, R = 3 (xáo thứ tự wave theo seed 43,44,45); SD báo trên 15 fold |
| Inner CV | GroupKFold theo wave, K = 3 (tuning trong outer) |
| Calibration | Cross-fit trong train (hoặc 1 wave-group riêng làm calib set) |
| Phân tích độ nhạy | Person-disjoint split (bỏ cặp có người ở cả 2 phía) để chứng minh không phụ thuộc cách chia |
| Ranking eval | Nhóm theo viewer (`iid`) : P@K (K = 3, 5, 10), NDCG@K, MRR ; bootstrap theo **viewer** |
| Nhị phân | ROC-AUC, PR-AUC (quan trọng vì prevalence ≈16%), Brier, ECE |
| CF (D2) | Leave-k-out theo người chấm (giữ 20% rating mỗi người) + kịch bản **cold-start** (user chưa thấy) ; RMSE/MAE, AUC nhị phân, NDCG@10 |

## 3. Learning procedure

### 3.1. Pipeline
```
raw pair rows → clean/missing (median/mode, cờ missing) → feature build (§0A.3)
→ scaler (chỉ mô hình tuyến tính) → [resampler: chỉ train fold] → model
→ calibrator (Platt/isotonic) → threshold/top-K → (ensemble với bio-sim, CF nếu có)
```

### 3.2. Candidate configurations (lưới tối thiểu)

| Mô hình | Siêu tham số |
|---|---|
| Baseline 0 | random ; luôn dự đoán lớp đa số ; **rule-only** (sắp xếp theo `n_common_tags` hoặc `interest_corr`) |
| Logistic Regression (L2 / L1 / elastic-net) | C ∈ {0.01, 0.1, 1, 10} ; class_weight ∈ {none, balanced} |
| Random Forest | n_estimators ∈ {200, 500}, max_depth ∈ {6, 12, None}, min_samples_leaf ∈ {1, 5} |
| XGBoost / LightGBM | lr ∈ {0.03, 0.1}, max_depth ∈ {3, 5, 7}, n_estimators ∈ {200, 500}, subsample ∈ {0.7, 1}, scale_pos_weight thử/không |
| Ranker (LambdaMART, LightGBM `lambdarank`) | nhóm theo viewer ; so sánh với pointwise classifier |
| CF (D2): SVD / ALS / `surprise.SVD` | n_factors ∈ {20, 50, 100}, reg ∈ {0.02, 0.1} |

### 3.3. Mất cân bằng lớp (ghi chú bảng: undersampling majority / oversampling minority / SMOTE)
So sánh 5 phương án × 2 mô hình mạnh nhất: none, class_weight, RandomUnderSampler, RandomOverSampler, SMOTE (k ∈ {3,5}; **chỉ trên cột số/đã mã hóa; SMOTE-NC nếu có cột phân loại**). Cách báo cáo: bảng CV mean ± SD của PR-AUC, Brier, ECE cho từng phương án → kết luận phương án nào **không** làm tệ calibration. Lưu ý: resample làm xác suất lệch → luôn calibrate lại trên phần chưa resample.

### 3.4. Ngưỡng & calibration
- Xếp hạng không cần ngưỡng cho thứ tự, nhưng cần ngưỡng cho "**gợi ý ưu tiên/nổi bật**" (badge "hợp nhau") → chọn ngưỡng trên validation để precision ≥ 0.4 (giá trị mục tiêu ví dụ, nhóm chốt) hoặc tối đa F-β (β = 0.5).
- Calibration: Platt vs isotonic (chọn theo Brier trên calib set); báo reliability diagram. Xác suất được hiệu chỉnh cần vì Module 2/UI có thể hiển thị "% hợp".

### 3.5. Bias–variance
Learning curve (10%…100% train, theo group), so điểm train vs CV; SD giữa các fold; so LR (bias cao/variance thấp) với XGBoost. Kết luận phải nêu rõ.

## 4. Results (cần điền khi chạy — khung bảng)

| Bảng | Nội dung |
|---|---|
| R1 | CV mean ± SD (15 fold) mọi candidate : ROC-AUC, PR-AUC, Brier, ECE, NDCG@5 |
| R2 | Bảng 5 phương án mất cân bằng |
| R3 | Test (chốt 1 mô hình, mở test 1 lần) + bootstrap cluster-viewer B = 2000, CI 95% |
| R4 | Baseline vs mô hình, có/không đặc trưng bio-sim, có/không đặc trưng FwP-specific (trên S1/P1) |
| R5 | Đánh giá **P1 (pilot)** — mô hình huấn luyện trên D1/S1, test trên sinh viên thật: đo mức suy giảm (distribution shift) |
| R6 | Ablation nhóm đặc trưng (tuổi / học lực / địa lý / sở thích) + permutation importance / SHAP |

### 4b. Phân tích lỗi (theo `01_*` §6)
- E1: confusion matrix tại ngưỡng "hợp nhau" ; FP (gợi ý ưu tiên sai) vs FN (bỏ sót người hợp).
- E2: slice theo **wave**, giới tính, tuổi, `goal`, `field_cd`, đã tốt nghiệp/đang học (S1/P1) ; wave nào mô hình thua baseline?
- E3: ≥ 10 cặp sai tự tin cao (vd interest_corr cao nhưng không match) + SHAP.
- E4: nhiễu nhãn (match phụ thuộc cả 2 phía + may rủi 4 phút) ; chênh lệch train–CV ; ceiling tín hiệu (AUC tối đa có thể đạt).
- E5: đề xuất.
- Kiến thức mở rộng: LambdaMART, SHAP, conformal/khoảng tin cậy xếp hạng, Optuna.

### 4c. Hai bộ dữ liệu cho CÙNG bài toán "dự đoán mutual like"
Yêu cầu thầy "≥ 2 bộ dữ liệu": D1 (Speed Dating, nhãn thật) + **S1/P1** cùng bài toán, dùng để kiểm tra tổng quát hóa: train D1 → test P1, train S1 → test D1, train D1+S1. Ngoài ra **tìm thêm 1 bộ có nhãn reciprocal/match thật** (vd các bộ speed-dating/online-dating khác trên Kaggle/UCI/OSF — *chưa xác minh, cần tìm*); nếu không có, nói rõ trong báo cáo D2/D3 là bài toán phụ chứ không phải "bộ thứ hai cùng nhiệm vụ".

## 5. Practical limits (bắt buộc nêu)

- **Đại diện:** D1 là sinh viên sau đại học Columbia 2002–04, D3 người trưởng thành ở San Francisco 2012, D2 người Séc → **không** đại diện sinh viên Việt Nam nhiều trường.
- **Không chuyển được giữa dataset:** đặc trưng D1 ≠ D3 ≠ D2. Kiến trúc trung thực: (a) content model học từ D1 (+S1); (b) bio-embedding học từ D3 (áp dụng được vì FwP có bio); (c) CF từ D2 chỉ là thí nghiệm phương pháp, production dùng CF **retrain trên dữ liệu FwP** khi có; (d) ensemble trọng số chọn trên **calibration của S1/P1**, không tuyên bố từ D1 một mình.
- **Phụ thuộc:** cặp hai chiều, cùng người xuất hiện nhiều lần, hiệu ứng wave.
- **Selection bias & nhãn:** match trong speed dating là 1 buổi 4 phút, khác dating app; nhãn "like" của P1 giả lập.
- **Distribution shift:** thêm trường, trạng thái tốt nghiệp, tiếng Việt; kế hoạch giám sát: so sánh phân phối đặc trưng (PSI) + tỉ lệ match theo tuần.
- **Công bằng:** kiểm tra hiệu năng theo nhóm (giới tính, trường) ; ranking có thể khuếch đại thiên lệch popularity.
- **Tài nguyên:** ghi thời gian train từng candidate, kích thước model, latency `/rank` cho 200 ứng viên.

## 6. Công việc chi tiết (Người A)

| # | Việc | Đầu ra | Ước lượng |
|---|---|---|---|
| 1A-1 | EDA D1: N, N wave, N người, prevalence, missing ; xây danh sách cột cho phép/cấm | notebook + Data Card | 1 ngày |
| 1A-2 | Feature builder cặp + baseline (rule-only, LR) chạy qua khung 0B | `src/m1_rank/features.py` | 1 ngày |
| 1A-3 | Lưới candidate (LR/RF/XGB/LambdaMART) + 5 phương án mất cân bằng + calibration + ngưỡng + learning curve | bảng R1–R3, R6 | 2 ngày |
| 1B | CF trên D2 (subsample có báo cáo cách chọn), cold-start | bảng riêng + phân tích | 4 ngày tổng (xem `02_PHAN_CONG.md`) |
| 1C | Bio-embedding D3 (TF-IDF+SVD vs SBERT đa ngôn ngữ), đánh giá proxy, xuất hàm `bio_sim(a,b)` | `src/m1_rank/bio_embed.py` | 3 ngày |
| 1D | Ensemble + đánh giá trên S1/P1 (R4, R5) + viết báo cáo Module 1 theo mẫu §4 của `01_*.md` | báo cáo M1 | 3 ngày |

**Bàn giao cho Module 0C:** `models/m1/<ver>/` + hàm `rank(viewer, candidates) → scores`.
