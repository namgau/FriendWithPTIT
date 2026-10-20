# 03 — Module 0: Dữ liệu chung, khung đánh giá, hạ tầng phục vụ

> Module nền, cả 3 module ML phụ thuộc vào đây. Chia thành 3 phần: **0A Dữ liệu** (Người B), **0B Khung đánh giá** (Người A), **0C Serving + demo** (Người C).
> Xem phân công tại `02_PHAN_CONG.md`, chuẩn báo cáo tại `01_CHUAN_BAO_CAO_AUDITABLE.md`.

## Ghi chú phạm vi bài toán (đọc trước)

- Ứng dụng: hẹn hò/kết bạn **giữa các trường đại học** (không chỉ PTIT), người dùng có trạng thái **đang học / đã tốt nghiệp**.
- Mở rộng so với `reference/AI_ML_FWP.md`: thêm đặc trưng `school_id`, `same_school`, `graduation_status` (student/alumni), `years_since_graduation`, `cohort_gap`. Không dataset công khai nào có các cột này → **bắt buộc dùng dữ liệu tổng hợp + khảo sát thí điểm** (0A).
- Trung thực trong báo cáo: các dataset công khai chỉ dùng để dạy mô hình *cách tương thích hoạt động nói chung*; kết quả trên dataset công khai **không** chứng minh chất lượng trên sinh viên Việt Nam (mục Practical limits).

---

## 0A. Dữ liệu (Người B)

### 0A.1. Danh mục dataset toàn dự án (mỗi module ≥ 3 nguồn, kể cả nguồn tổng hợp)

| ID | Dataset | Module | Nguồn | Quy mô (cần xác nhận lại bằng code rồi ghi vào báo cáo) | Nhãn | Ghi chú |
|---|---|---|---|---|---|---|
| D1 | Speed Dating Experiment | M1 | Kaggle `annavictoria/speed-dating-experiment` | ≈8.4k dòng, ≈551 người, 21 wave, ≈195 cột | `match` 0/1 (prevalence ≈16.5%) | Dataset chính M1, nhãn thật |
| D2 | Libimseti.cz ratings | M1 | Brozovsky & Petricek 2007 / mirror Konect, Kaggle | ≈17M rating (1–10), hàng trăm nghìn người dùng | rating → nhị phân (≥ ngưỡng) | Cần **kiểm tra link + license** trước; subsample nếu nặng |
| D3 | OkCupid Profiles | M1 | Kaggle `andrewmvd/okcupid-profiles` | ≈60k hồ sơ | **Không có nhãn match** | Chỉ dùng học embedding bio, không dùng làm nhãn |
| D4 | Essays Big-Five (Pennebaker & King) | M2 | GitHub/Kaggle "essays.csv" | ≈2.5k bài luận | 5 nhãn nhị phân OCEAN | Nhãn tự báo cáo, tín hiệu yếu |
| D5 | Kaggle MBTI (Personality Cafe) | M2 | Kaggle `datasnaek/mbti-type` | ≈8.7k người | 16 loại MBTI → 4 trục nhị phân | Nguồn thay thế/bổ sung D4 |
| D6 | PANDORA (Reddit, Big Five) | M2 | Yêu cầu truy cập (nếu được) | lớn | Big Five liên tục | Tùy chọn; nếu không xin được thì bỏ, ghi vào báo cáo |
| D7 | UIT-VSMEC | M2 | UIT NLP group | ≈7k câu tiếng Việt | 7 cảm xúc | Fine-tune PhoBERT |
| D8 | UIT-VSFC | M2 | UIT NLP group | ≈16k câu tiếng Việt (feedback sinh viên) | sentiment 3 lớp | Gần miền "sinh viên VN" |
| D9 | GoEmotions | M2 | Google, HuggingFace | ≈58k comment | 27 cảm xúc | Cảm xúc tiếng Anh, dùng pre-train/so sánh |
| D10 | PersonaChat | M2, M3 | HuggingFace `bavard/personachat_truecased` | ≈10k hội thoại 2 người, persona 4–5 câu/người | persona (dùng làm "sở thích") | Kiểm tra topic-overlap |
| D11 | DailyDialog | M3 | HuggingFace `daily_dialog` | ≈13k hội thoại | dialog act (có question), emotion | Đặc trưng câu hỏi/cảm xúc |
| D12 | Ubuntu Dialogue Corpus v2 | M3 | GitHub/Kaggle | hàng triệu tin, **có timestamp** | không có; nhãn suy ra | Nguồn duy nhất thật có độ trễ phản hồi |
| S1 | **FwP-Synth-Profiles** | M1 | Tự sinh | 20–50k cặp | nhãn mô phỏng | Có `school`, `graduation_status` |
| S2 | **FwP-Synth-Chat** | M2, M3 | Tự sinh (simulator + LLM) | 3–5k hội thoại | nhãn tiềm ẩn biết trước | Có ground-truth để kiểm tra mô hình |
| P1 | **FwP-Pilot** (khảo sát + chat thử) | M1, M2, M3 | Nhóm tự thu (Google Form + 20–30 cặp chat thử) | 100–200 hồ sơ; 20–30 cặp | tự báo cáo | **Dữ liệu duy nhất đại diện sinh viên**; dùng làm test phân phối triển khai |

> Kiểm tra bắt buộc cho MỖI dataset trước khi dùng: license, đường dẫn tải, checksum (sha256), N dòng, N đơn vị độc lập, prevalence. Ghi vào `docs/data_cards/DATA_CARD_<id>.md`.
> Con số ở cột "Quy mô" là ước lượng theo trí nhớ/tài liệu — **không được chép vào báo cáo cho tới khi đã in ra từ code**.

### 0A.2. Việc cần làm

| # | Việc | Đầu ra | Ước lượng |
|---|---|---|---|
| A1 | Tải & xác minh D1–D12, viết Data Card từng bộ (license, N, đơn vị độc lập, prevalence, cột cấm) | `docs/data_cards/DATA_CARD_*.md`, `data/checksums.txt` | 1.5 ngày |
| A2 | Thiết kế **schema chung** hồ sơ FwP (xem 0A.3) và bảng mapping cột từ D1/D3 → schema | `src/schema.py`, `docs/schema_mapping.md` | 0.5 ngày |
| A3 | Bộ sinh dữ liệu **S1** (0A.4) + kiểm định mô phỏng hợp lý | `src/synth/profiles.py`, notebook kiểm tra phân phối | 1 ngày |
| A4 | Khảo sát thí điểm **P1** (Google Form: trường, khoa, GPA-tầng, tốt nghiệp?, sở thích, bio; + thu "like" giả lập trên hồ sơ mờ). Có **đồng ý tham gia + ẩn danh**. | `data/pilot/*.csv` (ẩn danh) | 1 ngày + thời gian chờ phản hồi |

### 0A.3. Schema hồ sơ FwP

```
user_id (hash), school_id, faculty_id, gpa_tier (0..3), graduation_status {student, alumni},
enroll_year, grad_year (null nếu student), age, gender, seeking_gender,
interest_tags (multi-hot ~30 tag), bio_text, lat, lon, created_at
```
Đặc trưng cặp (viewer u, candidate v): `age_diff`, `same_school`, `same_faculty`, `gpa_tier_diff`, `both_alumni`, `alumni_student_mix`, `cohort_gap = |enroll_year_u − enroll_year_v|`, `dist_km`, `n_common_tags`, `jaccard_tags`, `bio_cos_sim`, `seeking_match` (cả 2 chiều).

### 0A.4. Sinh S1 (synthetic profiles) — quy tắc tường minh

- Hồ sơ: lấy mẫu phân phối tuổi/khoa/tag từ D1/D3 (đã ánh xạ) + thêm `school_id` (5–10 trường), `graduation_status`.
- Xác suất "mutual like" từ mô hình sinh **có tham số giả định ghi rõ** (logit tuyến tính): `logit p = β0 + β1·n_common_tags − β2·|gpa_tier_diff| + β3·same_school + β4·(cohort_gap nhỏ) − β5·dist_km/10 + ε`. β lấy từ **hệ số hồi quy logistic thật fit trên D1** khi cột tương ứng tồn tại (tuổi, sở thích, cùng trường ~`undergra`), còn β cho `gpa_tier`, `graduation_status` là **giả định của nhóm — gắn nhãn "assumption"**.
- Cảnh báo (phải ghi trong báo cáo): mô hình học trên S1 **chỉ khôi phục được giả định đã nhét vào**, nên S1 dùng để kiểm tra *pipeline có hoạt động* và *cân bằng lớp*, **không** dùng để tuyên bố "cùng tầng GPA match cao hơn" như một phát hiện. Muốn có phát hiện thật phải dùng P1.
- Prevalence S1 chỉnh ≈ prevalence D1 (≈16%) cho sát; làm thêm bản 5% để kiểm tra độ nhạy xử lý mất cân bằng.

---

## 0B. Khung đánh giá dùng chung (Người A)

Thư viện `src/eval/` để mọi module gọi cùng một cách → báo cáo nhất quán.

| # | Việc | Đầu ra | Ước lượng |
|---|---|---|---|
| B1 | `splits.py`: `held_out_group_split`, `repeated_group_kfold(K, R, seeds)`, `time_split`, `nested_cv` ; kiểm tra tự động "không group nào rò rỉ" (assert) | test đơn vị (pytest) | 1 ngày |
| B2 | `imbalance.py`: 5 chiến lược (none / class_weight / undersample / oversample / SMOTE) dạng bộ biến đổi tương thích `imblearn.Pipeline` | | 0.5 ngày |
| B3 | `calibration.py`: Platt/isotonic, cross-fit, Brier, ECE, reliability plot ; `threshold.py`: chọn ngưỡng theo P@K / F-β / chi phí trên validation | | 1 ngày |
| B4 | `bootstrap.py`: cluster bootstrap (B = 2000, percentile 95%) cho mọi metric ; hỗ trợ metric xếp hạng theo nhóm (NDCG@K, P@K) | | 0.5 ngày |
| B5 | `report.py`: xuất tự động bảng CV mean ± SD, test + CI, learning curve, reliability diagram → markdown theo mẫu §4 của `01_*.md` ; ghi `experiments/ledger.csv` | | 1 ngày |

**Chỉ số theo loại bài toán**: nhị phân → ROC-AUC, PR-AUC, Brier, ECE, precision/recall tại ngưỡng ; xếp hạng → P@K, NDCG@K, MRR (tính **theo từng viewer** rồi lấy trung bình, bootstrap theo viewer) ; hồi quy/tương quan → Spearman, Pearson, MAE ; đa lớp → macro-F1 + confusion matrix.

---

## 0C. Serving + demo (Người C)

| # | Việc | Đầu ra | Ước lượng |
|---|---|---|---|
| C1 | FastAPI: `/rank`, `/compat/update`, `/health/score`, `/healthz` ; schema pydantic ; đo latency p50/p95 | `serving/app.py` | 1.5 ngày |
| C2 | Model registry đơn giản: `models/<module>/<version>/model.pkl|onnx + metadata.json` (hash dữ liệu, seed, metric, ngưỡng, calibrator) ; hàm load version + rollback | | 1 ngày |
| C3 | Demo tối thiểu (Streamlit hoặc web tĩnh): Swipe với ảnh mờ + chat thử + thanh độ hợp + cảnh báo sức khỏe (**không cần app Android đầy đủ** cho bài tập lớn) | `demo/` | 1.5 ngày |

Phần này thuộc "Practical limits → Resources": báo cáo latency, RAM, thời gian train thực đo được.

---

## Cấu trúc thư mục repo đề xuất

```
project/
├─ docs/                      # các file md này
├─ data/{raw,interim,processed,pilot}/   (Data Card ở docs/data_cards/)
├─ src/{schema.py, synth/, eval/, m1_rank/, m2_compat/, m3_health/}
├─ configs/*.yaml             # mọi siêu tham số + seed
├─ experiments/ledger.csv, reports/
├─ models/<module>/<version>/
├─ serving/  demo/  tests/
└─ requirements.txt
```
