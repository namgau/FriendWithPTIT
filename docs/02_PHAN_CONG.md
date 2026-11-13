# 02 — Phân công 3 người & deadline (hạn nộp: **Thứ Ba 01/12/2026**)

> **Phạm vi:**
> - **Môn Học máy:** Module 3 — Conversation Intelligence (Head A: hội thoại sắp tắt ; Head B: hội thoại sắp trở nên độc hại), xem `06_*`. Chia 3 phần.
> - **Môn Mobile:** Module 1 + Module 2 **bản luật** (không ML) trong app. Gộp lại, chia 3 phần.
> - **Dữ liệu:** **Chỉ dùng dataset công khai.** Nhóm **không thu dữ liệu thật**: không khảo sát, không thu chat, không log người dùng app, không tự gán nhãn dữ liệu mới.
> - **Người A = trưởng nhóm, chịu trách nhiệm chất lượng** (khung đánh giá, tập test, review/merge, tái lập, báo cáo cuối).
>
> 5 tuần, mỗi tuần tính từ Thứ Ba đến Thứ Hai. Mỗi người ~4–5 giờ/ngày làm việc.

| | Người A (trưởng nhóm) | Người B | Người C |
|---|---|---|---|
| Module 3 | Khung đánh giá, **định nghĩa nhãn cả 2 head**, Head A dạng bảng + survival, **toàn bộ thí nghiệm imbalance**, phân tích lỗi, báo cáo | Tải & làm sạch dữ liệu, đặc trưng hành vi + phong cách (T1–T3) | Đọc nội dung (NLP), Head B, API |
| Module 1+2 (app) | Duyệt API (không code app) | **Backend luật** (lọc/xếp hạng M1 + luật blur M2) + **màn Swipe** (M1) | Màn chat: ảnh rõ dần + quả cầu độ hợp (M2), nối API Module 3 |
| Họ tên | Vũ Minh (`namgau`) | Ngọc Vĩ (`NgocVixx`) | Thắng |

## 0. Cắt bớt so với kế hoạch đầy đủ (vì còn 5 tuần + làm app)

| Giữ (bắt buộc) | Giữ nếu kịp (P1) | **Cắt** (ghi vào báo cáo là hướng phát triển) |
|---|---|---|
| Head A trên E1 + E2 ; Head B trên G1 + H1 ; baseline ; imbalance ; calibration ; bootstrap ; phân tích lỗi ; cross-domain E1 ↔ E2 | Survival, TabPFN, LSM (T3), H2 (ViCTSD), cross-lingual X14, conformal survival | **Tự thu chat tiếng Việt (V1)** — không có bộ công khai phù hợp, tự thu quá tốn thời gian ; Multi-task (X13), G2 Reddit CMV, E3 Reddit, GRU cho Head A, XLM-R fine-tune tiền tố, weighted conformal, LLM trích đặc trưng (X10), Optuna, so sánh 3 embedding (X9 → chỉ dùng 1 mô hình) |

## 1. Mốc chung (cả nhóm)

| Ngày | Mốc | Ai kiểm tra |
|---|---|---|
| **Thứ Sáu 30/10** | Chốt **định dạng chung** (`06_*` §1.4): bảng tin nhắn chung mọi dataset, chữ ký `extract_features()`, `encode_messages()`, API `/health/score`, `/safety/score` | A |
| **Thứ Hai 02/11** | Họp tuần 1: dữ liệu E1, E2, G1, H1 tải xong & kiểm tra ; khung split + bootstrap có test | A |
| **Thứ Sáu 06/11** | Đóng băng mô hình embedding (C1) ; tập test của mọi dataset đã tạo & khóa (A3) | A |
| **Thứ Hai 09/11** | Baseline cả 2 head chạy end-to-end | A |
| **Thứ Hai 16/11** | **Giữa kỳ:** bảng CV mean ± SD cả 2 head ; app có Swipe + chat chạy được | A |
| **Thứ Năm 19/11** | **Đóng băng cấu hình** mọi mô hình (không đổi gì nữa) | A |
| **Thứ Sáu 20/11** | **A mở tập test đúng 1 lần**, chạy lại toàn bộ từ config + seed | A |
| **Thứ Hai 23/11** | Đủ bảng kết quả R1–R9 ; app nối API Module 3 | A |
| **Thứ Sáu 27/11** | **Bản nháp báo cáo đầy đủ** ; duyệt chéo bằng checklist `01_*` §5 bắt đầu | A + cả nhóm |
| **Chủ Nhật 29/11** | Sửa xong theo duyệt chéo ; slide ; quay video demo dự phòng | A |
| **Thứ Hai 30/11** | Chạy thử thuyết trình, kiểm tra lần cuối | Cả nhóm |
| **Thứ Ba 01/12** | **NỘP** | A |

## 2. Người A (trưởng nhóm) — việc quyết định chất lượng

| Mã | Việc | Hạn | Vì sao A phải giữ |
|---|---|---|---|
| A0 | Bật **branch protection** cho `main` (bắt buộc PR + 1 review + CI xanh) ; mời B, C làm collaborator | 28/10 | Không ai đẩy thẳng code chưa kiểm tra |
| A1 | **Chốt định dạng chung** (`06_*` §1.4): cột của bảng tin nhắn mà B xuất ra, chữ ký hàm đặc trưng, API — là quy ước kỹ thuật trong nhóm, **không** phải thu dữ liệu | 30/10 — **xong** (06 §1.4) | Ba người code khớp nhau ngay từ đầu |
| A2 | **Khung đánh giá** `src/eval/`: split theo thời gian/nhóm, nested CV, cluster + paired bootstrap, calibration, chọn ngưỡng ; **test chống rò rỉ** chạy trong CI | split + bootstrap 02/11 — **xong 30/10** (`src/eval/splits.py`, `bootstrap.py`, 13 test) ; còn lại 06/11 — **xong 31/10** (imbalance, calibration, threshold, report ; 11 test) | Rò rỉ dữ liệu là lỗi mất điểm nặng nhất |
| A3 | **Tạo & khóa tập test** cho mọi dataset (chỉ A giữ index test) ; **chỉ A mở test, đúng 1 lần, ngày 20/11** | tạo 06/11 — **xong 30/10** (`configs/test_lock.json`) ; mở 20/11 | Không ai vô tình chọn mô hình trên test |
| A4 | **Review & merge mọi PR** theo checklist rò rỉ (`01_*` §3) ; kiểm tra mỗi thí nghiệm có dòng trong `experiments/ledger.csv` | liên tục, ≤ 24h mỗi PR | Một cửa kiểm soát |
| A2b | **Nhãn Head A** (nhận từ B): sinh mẫu (c, t), nhãn Δ = 48h, cờ censoring, thống kê N hiệu dụng & prevalence | 06/11 — **xong 30/10** (`src/m3_health/labels.py`) | Định nghĩa nhãn sai = toàn bộ kết quả sai ; dễ rò rỉ tương lai vào đặc trưng |
| A2c | **Nhãn Head B** (nhận từ B): cắt tiền tố G1 **trước** lượt công kích, giữ cặp ghép cùng phần, gộp nhãn H1 (OFFENSIVE + HATE) | 06/11 — **xong 30/10** (`src/m3_health/labels.py`) | Cắt tiền tố sai = mô hình "nhìn thấy" lượt công kích → kết quả ảo |
| A5 | Head A: baseline (đa số, rule gốc, `time_since_last`) + LR + LightGBM ; 5 phương án mất cân bằng (X2) ; calibration + τ1/τ2 | 13/11 — **bản đầu xong 31/10** (`src/m3_health/run_head_a.py`, kết quả `docs/results/HEAD_A_CV.md`) | Kết quả chính của bài |
| A5b | **Imbalance trên H2 (UIT-ViCTSD)** — phần **chạy CPU**: 5 phương án none / class_weight / under / over / SMOTE với TF-IDF + LR và với embedding đã cache (do B tính) + LR ; tổng hợp cùng kết quả GPU của B (B10) ; chọn **τ_B theo FPR** cho Head B | 18/11 — **phần TF-IDF xong 02/11, phần embedding BGE-M3 + LR xong 11/11** (`docs/results/IMBALANCE_B.md`) ; chờ kết quả GPU (B10) | Under/oversampling, SMOTE là nội dung thầy nhấn mạnh → A thiết kế & kết luận thống nhất cho cả 2 head |
| A6 | Head A: survival (Cox, Random Survival Forest) (X3) ; conformal (X8) nếu kịp — đều chạy CPU | 18/11 — **xong 03/11**: Cox + RSF (`docs/results/HEAD_A_SURVIVAL.md`) ; conformal CQR + weighted khi chuyển miền (`HEAD_A_CONFORMAL.md`) | Điểm cộng |
| A7 | Ablation T1→T5 (X1), độ nhạy Δ (X5) | 19/11 — **T1/T2 + X4 cross-domain E1 ↔ E2 xong 03/11** (`HEAD_A_ABLATION.md`) ; X5 có trong `HEAD_A_SURVIVAL.md` ; chờ T3 (B5), T4–T5 (C1, B8) ; **+T4 xong 11/11** (E1 +0.015, E2 +0.054 PR-AUC ; `HEAD_A_ABLATION.md`) ; **+T3 xong 13/11** (T3: E1 +0.007, E2 +0.035 ; T3+T4: E1 +0.017, E2 +0.076) ; còn T5 (C4) | Trả lời RQ1 |
| A8 | **Chạy lại toàn bộ từ config + seed** sau khi mở test ; đối chiếu số với báo cáo | 22/11 | Tiêu chí "auditable" |
| A9 | Phân tích lỗi cả 2 head (`06_*` §5b) + **Practical limits** (`06_*` §6) | 25/11 — **bản trên OOF train xong 04/11** (`docs/results/ERROR_ANALYSIS.md`) ; chạy lại trên test sau 20/11 ; còn E5 (sửa lỗi + so trước/sau) | Thầy yêu cầu phân tích lỗi & điểm yếu |
| A10 | **Ghép báo cáo cuối** theo mẫu `01_*` §4 + checklist §5 ; slide | nháp 27/11 ; bản cuối 29/11 — **khung + số CV/OOF train viết 06/11** (`docs/08_BAO_CAO_M3.md`), ô `[TEST 20/11]` / `[CHỜ …]` điền dần | Một người chịu trách nhiệm tổng thể |
> **Nguyên tắc tài nguyên:** việc nào **tốn GPU hoặc token (API LLM)** giao cho B/C ; A chỉ chạy việc CPU để dành thời gian kiểm soát chất lượng. Mọi lần chạy GPU vẫn phải ghi config + seed vào `experiments/ledger.csv` để A kiểm tra.
> Ước lượng khối lượng: **A ≈ 25 người-ngày**, B ≈ 24, C ≈ 20 (5 tuần ≈ 25 ngày làm việc).

## 3. Người B — dữ liệu, đặc trưng hành vi, backend + Swipe

| Mã | Việc | Hạn |
|---|---|---|
| B1 | Tải, kiểm tra, parse **E1 Ubuntu + E2 Twitter CSKH** ; Data Card (N, đơn vị độc lập, prevalence, license, sha256) | 02/11 — **xong 30/10, A làm hộ** (`src/data/prepare.py`, `docs/data_cards/`) |
| B2 | Tải, làm sạch **G1 (Conversations Gone Awry – Wiki)** + **H1 (ViHSD)** + **H2 (ViCTSD)** ; Data Card. Giao dữ liệu sạch cho A gán nhãn (A2b, A2c) | 04/11 — **xong 30/10, A làm hộ** |
| B4 | Đặc trưng **T1** (hành vi thời gian) + **T2** (bề mặt văn bản, từ điển Anh/Việt) | 10/11 — A đã làm bản tối thiểu 21 đặc trưng (`src/m3_health/features.py`) ; B mở rộng |
| B5 | Đặc trưng **T3** (LSM, mirroring, turn-taking) | 13/11 — **xong 12/11** (PR #7, `features_t3.py`) |
| B6 | Đánh giá bộ phát hiện câu hỏi/tin cụt trên DailyDialog (có nhãn dialog act) (X6) | 18/11 |
| B8 | **[GPU]** Tính & **cache embedding** (mô hình C1 chọn) cho mọi tin nhắn của E1, E2, G1, H1, H2 → file `.npy` dùng chung (C cho T4/T5, A cho imbalance) ; ghi thời gian chạy, phần cứng. **Cách làm (A chốt 06/11):** (1) `python -m src.m3_health.embed_ids` → `data/processed/<ID>/embed_conversation_ids.txt` ; **chỉ embed tin của các hội thoại trong file này** (E1 2,35 / 9,15 triệu tin ; E2 0,62 / 2,36 triệu ; G1, H1, H2 lấy hết) — file đã gộp train + test, không nhãn, không cần mở `data/locked/`. (2) Encoder: `make_encoder()` + `encode_to_memmap()` trong `src/m3_health/embeddings.py` của C (đọc `configs/embedding.yaml`: BGE-M3 revision đã pin, fp16, `max_length` 256, dense, chuẩn hóa L2). (3) Lưu `data/processed/<ID>/emb_bge-m3.npy` (float16, mỗi dòng 1 tin) + `emb_bge-m3_index.parquet` (`conversation_id, turn_idx` đúng thứ tự dòng) — đúng tên này thì `run_ablation_a` tự thêm T4 (`src/m3_health/embedding_cache.py`). (4) Ghi 1 dòng ledger: GPU, thời gian chạy, số tin. **Không** commit `.npy` | 09/11 — **xong 10/11** (Modal, GPU T4 ; A review PR #5 10/11: 91 test pass, số tin khớp `embed_ids`, ledger trỏ commit `3ecf61e`) ; **còn: chuyển cache cho A, C** (Drive). E1 2.348.495; E2 624.065; G1 26.188; H1 33.400; H2 10.000 tin. GPU T4, 5 dòng ledger; hướng dẫn `docs/B8_EMBEDDING_CACHE.md`. |
| B9 | **[GPU]** **TabPFN v2** cho Head A (X7) — nhận từ A ; so với LightGBM của A theo kích thước train ; dùng split/khung đánh giá của A | 18/11 — **xong 13/11** (PR #8, `B9_TABPFN_CV.md`) |
| B10 | **[GPU]** Phần imbalance cần GPU trên H2: fine-tune PhoBERT/XLM-R (pipeline của C2) với weighted loss và với oversampling ; giao kết quả cho A tổng hợp (A5b) | 18/11 |
| B7 | Viết phần **Objective and data** + mô tả dữ liệu trong báo cáo ; duyệt chéo phần của C | 27/11 |
| B-app1 | **Backend luật** (nhận từ A): lọc cứng + xếp hạng M1 (trường, khoa, tầng GPA, tuổi, khoảng cách, trạng thái tốt nghiệp ; sắp theo số tag trùng) + hàm luật blur M2 (theo số tin & số ngày chat) + test đơn vị, theo `serving/schemas_mobile.py` | 16/11 — **bản đầu có từ 27/10** (`serving/engines.py` `rank()`, `compatibility()` + `android/.../core/ai/CompatibilityEngine.kt`, test `tests/test_serving_mobile.py`). **Còn lệch so với yêu cầu (A kiểm 06/11):** (a) M1 **chưa lọc cứng** — mọi ứng viên đều được chấm điểm, không loại ai (cần loại theo `mode`, khoảng tuổi, khoảng cách tối đa ; người xem chọn trường/khoa) ; (b) `alumni` (đã tốt nghiệp) có trong schema nhưng **chưa dùng** ; (c) sắp theo **điểm gộp có trọng số**, chưa theo số tag trùng — chọn 1 cách và ghi luật + trọng số vào `04_*` ; (d) M2 blur tính từ điểm từ khóa / độ cân bằng, **chưa dùng số ngày chat** (lấy từ `created_at` của tin) ; (e) sửa xong phải sửa **cả bản Python lẫn bản Kotlin** và thêm test để hai bản ra cùng số. **Luật đã chốt 10/11: `04_*` §0 (M1) và `05_*` §0 (M2)** — **xong 10/11 (PR #6)**: Python + Kotlin + backend theo đúng luật, ca kiểm thử chung `tests/fixtures/m1_rank_cases.json`, `m2_blur_cases.json` ; A review 10/11: 111 test Python, 60 test backend, 57 test Android pass |
| B-app2 | **Màn Swipe (M1):** thẻ hồ sơ ảnh mờ, thích/bỏ qua, tạo match, gọi API xếp hạng | 23/11 — **khung có sẵn** (`feature/discover/`: `SwipeCard`, `MatchCelebration`, gọi `m1/rank`). Còn: dùng luật mới của B-app1, hiện lý do xếp hạng, thử end-to-end với backend |

## 4. Người C — đọc nội dung, Head B, tích hợp

| Mã | Việc | Hạn |
|---|---|---|
| C1 | Chọn **1** mô hình embedding đa ngữ (MiniLM hoặc BGE-M3, lý giải bằng VN-MTEB) ; đặc trưng **T4** ; PCA fit trên DailyDialog | 06/11 (đóng băng) — **xong 06/11**: BGE-M3 (VN-MTEB 64,90 so với MiniLM 46,05 — A đối chiếu bài gốc Table 3 ngày 07/11), `configs/embedding.yaml`, `src/m3_health/embeddings.py`, T4 `features_t4.py`, PCA 32 chiều fit trên DailyDialog (`models/m3/t4/`), benchmark `docs/results/C1_EMBEDDING.md`. Còn: dòng ledger cho benchmark + PCA |
| C2 | **Bộ phân loại độc hại tiếng Việt** trên H1: TF-IDF+LR → PhoBERT/XLM-R ; cross-dataset H1 ↔ H2 (X12, phần imbalance do A làm) | 13/11 |
| C4 | **Head B** trên G1: baseline "tin cuối", mô hình bảng, **CRAFT-like** (embedding + GRU) ; ngưỡng theo FPR (X11) | 18/11 — baseline TF-IDF + LR trên G1/H1/H2 **B đã làm 02/11** (`src/m3_health/run_head_b.py`) ; C làm tiếp CRAFT-like |
| C5 | **Cross-lingual Head B** (X14): điểm độc hại từ bộ phân loại tiếng Việt làm đặc trưng cho G1 ; hỗ trợ A chạy cross-domain Head A E1 ↔ E2 (X4) | 23/11 |
| C6 | API `POST /health/score`, `POST /safety/score` (FastAPI) + job kiểm tra hội thoại im lặng | 20/11 |
| C7 | Viết phần Head B + T3–T5 trong báo cáo ; duyệt chéo phần của B | 27/11 |
| C-app | **Màn chat (M2):** ảnh rõ dần theo luật blur của B (Gaussian blur trên máy), quả cầu độ hợp (hiển thị), gợi ý/nhắc nhẹ từ API Module 3 | 25/11 — **khung có sẵn** (`feature/chat/`: blur, `CompatibilityOrb`, `HealthPill`, gọi `m2/compat`, `m3/health`). Còn: (a) **chưa có Head B** — thêm hộp "Bạn có chắc muốn gửi?" gọi `/safety/score` trước khi gửi (không chặn, không phạt) ; (b) hiện 3 mức khỏe / hời hợt / nguy cơ theo τ1, τ2 của A ; (c) dùng luật blur mới của B-app1 |

## 5. Phụ thuộc (điểm dễ nghẽn — A theo dõi)
```
A1 định dạng chung (30/10) ──► mọi người
A2 khung đánh giá (02–06/11) ──► A5, A6, C4
B1, B2 dữ liệu sạch (02–04/11) ──► A2b, A2c nhãn (06/11) ──► A5, A6, C2, C4   ← nghẽn chính: A vừa làm khung đánh giá vừa gán nhãn tuần 1–2
C2 bộ phân loại độc hại (13/11) ──► A5b imbalance, C5 cross-lingual
B-app1 API luật (16/11) ──► B-app2, C-app
C6 API Module 3 (20/11) ──► C-app
```

## 6. Quy tắc kiểm soát chất lượng (A áp dụng)
- **Definition of done** cho mỗi việc: code có test (nếu là hàm), chạy từ `configs/*.yaml`, có dòng trong `experiments/ledger.csv`, PR được A duyệt.
- Mọi bước fit (scaler, SMOTE, PCA, embedding fine-tune) **chỉ trên train fold** — A kiểm tra trong review.
- Không ai ngoài A truy cập index tập test trước 20/11.
- Số liệu dataset chỉ đưa vào báo cáo sau khi in ra từ code.
- Họp nhanh 15 phút **Thứ Hai & Thứ Sáu**: mỗi người báo xong gì / kẹt gì / việc trễ hạn. Trễ > 2 ngày → A quyết định cắt theo bảng §0.

## 7. Rủi ro & dự phòng
| Rủi ro | Dự phòng |
|---|---|
| E2 không dùng được | Thay bằng E3 (Reddit qua ConvoKit, có timestamp) để Head A vẫn đủ 2 bộ |
| Không có GPU cho PhoBERT | Colab/Kaggle ; nếu không: embedding đóng băng + LR |
| C2 trễ | Dùng bản TF-IDF+LR cho X12/X14, PhoBERT để sau |
| App chậm | Ưu tiên demo luồng Swipe → match → chat có blur ; quả cầu 3D cắt trước |
| Thiếu thời gian chung | Cắt theo bảng §0 (cột "giữ nếu kịp" trước). **Không cắt**: split đúng, bootstrap, calibration, baseline, phân tích lỗi |
