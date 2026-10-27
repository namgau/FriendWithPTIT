# 01 — Chuẩn báo cáo thực nghiệm "Auditable" (áp dụng cho MỌI module)

> Nguồn: slide môn học *"An auditable experimental report"* (`reference/tieuchuan.jpg`) + ghi chú bảng (undersampling / oversampling / SMOTE / bias–variance trade-off).
> Nguyên tắc: người ngoài đọc báo cáo phải **tái lập lại** được thí nghiệm và **kiểm tra** được mọi con số mà không cần hỏi nhóm.

> **Yêu cầu của thầy (đề bài mẫu):** dữ liệu tự tìm ≥ 2 bộ · tổng hợp kiến thức đã học · **phân tích kết quả và lỗi của mô hình** · điểm cộng: kiến thức mới ngoài chương trình. Mọi module phải có mục **X.4b Phân tích lỗi** (xem §6) và mục **"Kiến thức mở rộng"** (xem §7).

## 1. Bảng chuẩn → việc phải làm

| Report element | Thông tin bắt buộc (theo slide) | Nhóm phải ghi/làm cụ thể |
|---|---|---|
| **Objective and data** | Deployment target, sample size, prevalence, independent unit | (a) Câu "dự đoán gì, cho ai, lúc nào trong app" ; (b) N dòng **và** N đơn vị độc lập ; (c) tỉ lệ lớp dương (prevalence) từng dataset & từng split ; (d) đơn vị độc lập là gì (người / wave / hội thoại) |
| **Evaluation design** | Split roles, K, repetitions, seeds, group/time constraints | Vai trò từng phần dữ liệu (train / validation / calibration / test) ; K ; số lần lặp ; danh sách seed ; ràng buộc group & thời gian chống leakage |
| **Learning procedure** | Pipeline, candidate configurations, threshold, calibration | Sơ đồ pipeline (imputer→scaler→resampler→model) ; lưới siêu tham số ; cách chọn ngưỡng ; cách hiệu chỉnh xác suất |
| **Results** | CV mean ± SD, test metrics, bootstrap method and B | Bảng CV mean ± SD ; số đo trên test **chạm test đúng 1 lần** ; bootstrap (loại, B, mức CI) |
| **Practical limits** | Resources, dependence, representativeness, distribution shift | Tài nguyên tính toán ; phụ thuộc dữ liệu (lặp người, chuỗi thời gian) ; dữ liệu có đại diện cho sinh viên PTIT không ; lệch phân phối khi triển khai |

## 2. Giao thức mặc định dùng chung (mọi module phải theo, chỉ được đổi nếu ghi lý do)

| Hạng mục | Mặc định | Ghi chú |
|---|---|---|
| Held-out **test** | 20% **theo group**, tách trước tiên, khóa lại, chỉ mở khi chốt mô hình cuối | Không dùng test để chọn ngưỡng/siêu tham số/mô hình |
| Cross-validation ngoài | **Group** K-fold, **K = 5**, lặp **R = 3** (seed khác nhau) | Chỉ dùng CV lặp để ước lượng mean ± SD ; SD tính trên 15 fold-score và ghi rõ có phụ thuộc nhau |
| CV trong (tuning) | Group K-fold, K = 3 (nested CV) | Tuning nằm **bên trong** mỗi outer fold để tránh lạc quan |
| Seeds | `[42, 43, 44, 45, 46]` ; seed 42 cho split test ; seed khác cho repeat/model | Ghi seed của mọi thành phần ngẫu nhiên (split, SMOTE, model, init NN) |
| Ràng buộc group | Không để cùng 1 đơn vị độc lập xuất hiện ở 2 phía train/test | Xem từng module |
| Ràng buộc thời gian | Dữ liệu có thời gian → split theo thời gian (train quá khứ, test tương lai) | Module 3 bắt buộc ; Module 1 (Speed Dating) dùng wave làm group |
| Xử lý mất cân bằng | So sánh: không xử lý / class_weight / random **undersampling** / random **oversampling** / **SMOTE** | Mọi resampler **nằm trong pipeline** (`imblearn.Pipeline`), chỉ áp lên train fold — không bao giờ lên validation/test |
| Hiệu chỉnh xác suất | Platt (sigmoid) vs isotonic, fit trên **calibration set** riêng (hoặc cross-fit) | Bắt buộc sau khi resample (vì resample làm lệch xác suất) ; báo Brier + ECE + reliability diagram |
| Chọn ngưỡng | Chọn trên validation theo mục tiêu triển khai (vd P@K, F-β, chi phí) | Ghi công thức & giá trị ngưỡng ; test chỉ *báo cáo* |
| Bootstrap | **Cluster (group) bootstrap** trên test, resample theo đơn vị độc lập, **B = 2000**, percentile CI 95% | Ghi B, loại bootstrap, seed. Với n nhỏ (<200 đơn vị) ghi rõ CI rộng |
| Baseline bắt buộc | Random / đa số / rule-only / mô hình tuyến tính đơn giản | Mô hình phức tạp chỉ được coi là tốt nếu thắng baseline **ngoài khoảng CI** |
| Bias–variance | Learning curve (train size), so train-score vs CV-score, SD giữa các fold | Kết luận rõ: mô hình đang high-bias hay high-variance |
| Tái lập | Config YAML + hash dữ liệu + git commit + `requirements.txt` pin phiên bản | Mỗi lần chạy ghi 1 dòng vào `experiments/ledger.csv` |

## 3. Chống rò rỉ dữ liệu (leakage) — checklist

- [ ] Mọi bước fit (imputer, scaler, PCA/UMAP, TF-IDF, SMOTE, embedding) chỉ fit trên train fold.
- [ ] Không dùng đặc trưng chỉ có **sau** thời điểm dự đoán (post-hoc). Mỗi module liệt kê "cột bị cấm".
- [ ] Cặp đối xứng (A→B và B→A) luôn cùng fold.
- [ ] Cùng một người / cùng một hội thoại không nằm ở hai phía.
- [ ] Nhãn được định nghĩa bằng thông tin **tương lai** thì đặc trưng chỉ lấy từ **quá khứ** của mốc dự đoán.
- [ ] Siêu tham số & ngưỡng chọn ngoài test.

## 4. Mẫu tiêu đề mục báo cáo cho mỗi module (copy dùng lại)

```
## Module X — <tên>
### X.1 Objective and data
- Deployment target:
- Bảng dataset: tên | nguồn/license | N dòng | N đơn vị độc lập | prevalence | vai trò
- Đơn vị độc lập & lý do:
### X.2 Evaluation design
- Vai trò các phần: train / val / calib / test (tỉ lệ, cách tách)
- K, R, seeds:
- Ràng buộc group/time:
### X.3 Learning procedure
- Pipeline (sơ đồ):
- Candidate configurations (bảng lưới):
- Xử lý mất cân bằng (so sánh 5 phương án):
- Ngưỡng:
- Hiệu chỉnh:
### X.4 Results
- CV mean ± SD (bảng, tất cả candidate)
- Test metrics (mô hình chốt, 1 lần) + bootstrap 95% CI (B = 2000, cluster)
- Baseline comparison, learning curve, reliability diagram, ablation
### X.4b Phân tích lỗi (bắt buộc, xem §6 của file 01)
### X.5 Practical limits
- Tài nguyên (RAM/GPU/thời gian train/latency inference):
- Phụ thuộc (lặp người, chuỗi thời gian):
- Tính đại diện (so với sinh viên PTIT):
- Distribution shift & kế hoạch giám sát:
```

## 5. Checklist duyệt chéo (reviewer = người KHÔNG viết module đó)

Chấm ✔/✘ + ghi vị trí trong báo cáo. Mọi ✘ phải sửa trước khi nộp.

| # | Câu hỏi kiểm | ✔/✘ |
|---|---|---|
| 1 | Nêu rõ deployment target 1 câu ? | |
| 2 | Có N dòng **và** N đơn vị độc lập cho mọi dataset ? | |
| 3 | Có prevalence cho train / val / test ? | |
| 4 | Split có group/time constraint và giải thích vì sao ? | |
| 5 | Có K, R, seeds ? | |
| 6 | Pipeline chỉ fit trong train fold (kể cả SMOTE) ? | |
| 7 | Có bảng candidate configs + cách chọn ngưỡng + calibration ? | |
| 8 | CV báo mean ± SD ? Test chỉ mở 1 lần ? | |
| 9 | Có bootstrap: loại + B + CI ? | |
| 10 | Có baseline & so sánh có ý nghĩa thống kê (CI không chồng) ? | |
| 11 | Có mục Practical limits đủ 4 ý (resources, dependence, representativeness, shift) ? | |
| 12 | Chạy lại từ config + seed ra đúng số trong báo cáo ? | |
| 13 | Có mục Phân tích lỗi đủ 5 phần (§6) với ≥ 10 ví dụ lỗi cụ thể ? | |
| 14 | Có nêu kiến thức mở rộng và đối chiếu với baseline kinh điển ? | |
| 15 | Có ≥ 2 bộ dữ liệu cho **cùng một bài toán** (hoặc nói rõ vì sao không có) ? | |

## 6. Phân tích lỗi mô hình (yêu cầu của thầy) — khung chung

Làm trên **dự đoán out-of-fold của CV** (không dùng test để khám lỗi rồi sửa mô hình; nếu phân tích lỗi trên test thì chỉ để báo cáo, không quay lại tuning).

| Phần | Nội dung bắt buộc |
|---|---|
| E1. Tổng quan lỗi | Confusion matrix tại ngưỡng đã chọn ; FP vs FN ; chi phí tương ứng ; PR/ROC curve |
| E2. Lỗi theo nhóm (slice) | Metric theo từng nhóm: trường, giới tính, tầng GPA, đã tốt nghiệp/đang học, wave, độ dài hội thoại… ; chỉ ra nhóm kém nhất + CI (bootstrap) ; nhóm nhỏ → nêu rõ n |
| E3. Ví dụ lỗi định tính | ≥ 10 mẫu sai có độ tự tin cao (high-confidence errors: FP và FN), kèm đặc trưng chính (SHAP/importance) và **giả thuyết nguyên nhân** |
| E4. Nguồn gốc lỗi | Phân loại: nhiễu nhãn / thiếu đặc trưng / lệch phân phối / lệch mất cân bằng / lỗi mô hình (bias) / phương sai (variance). Mỗi loại ≥ 1 bằng chứng (vd learning curve, chênh lệch train-CV, ablation) |
| E5. Hành động | Đề xuất sửa (thêm đặc trưng, thu thêm dữ liệu…) ; nếu đã thử: trước/sau trên CV ; nếu chưa thử: ghi rõ là hướng tương lai |

Bổ sung: độ **calibration theo nhóm**, so sánh lỗi của mô hình chốt với baseline (mô hình thắng ở đâu, thua ở đâu).

## 7. Kiến thức mở rộng (điểm cộng)

Danh sách cụ thể, có nguồn 2024–2026 cho bài nộp (Module 3): xem `06_*` §5d (TabPFN v2, conformalized survival, weighted conformal dưới covariate shift, embedding đa ngữ + VN-MTEB, LLM làm bộ trích đặc trưng, Language Style Matching, survival analysis, SHAP).
Nguyên tắc: mỗi kỹ thuật mới **phải so với baseline kinh điển** và báo CI, nếu không thắng thì vẫn báo cáo trung thực (kết quả âm cũng có giá trị).
