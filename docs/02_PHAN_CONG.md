# 02 — Phân công 3 người & deadline (hạn nộp: **Thứ Ba 01/12/2026**)

> **Phạm vi:**
> - **Môn Học máy:** Module 3 — Conversation Intelligence (Head A: hội thoại sắp tắt ; Head B: hội thoại sắp trở nên độc hại), xem `06_*`. Chia 3 phần.
> - **Môn Mobile:** Module 1 + Module 2 **bản luật** (không ML) trong app. Gộp lại, chia 3 phần.
> - **Người A = trưởng nhóm, chịu trách nhiệm chất lượng** (khung đánh giá, tập test, review/merge, tái lập, báo cáo cuối).
>
> 5 tuần, mỗi tuần tính từ Thứ Ba đến Thứ Hai. Mỗi người ~4–5 giờ/ngày làm việc.

| | Người A (trưởng nhóm) | Người B | Người C |
|---|---|---|---|
| Module 3 | Khung đánh giá, **định nghĩa nhãn cả 2 head**, Head A dạng bảng + survival, **toàn bộ thí nghiệm imbalance**, phân tích lỗi, báo cáo | Tải & làm sạch dữ liệu, đặc trưng hành vi + phong cách (T1–T3) | Đọc nội dung (NLP), Head B, API |
| Module 1+2 (app) | Hợp đồng API (không code app) | **Backend luật** (lọc/xếp hạng M1 + luật blur M2) + **màn Swipe** (M1) | Màn chat: ảnh rõ dần + quả cầu độ hợp (M2), nối API Module 3 |
| Họ tên | ________ | ________ | ________ |

## 0. Cắt bớt so với kế hoạch đầy đủ (vì còn 5 tuần + làm app)

| Giữ (bắt buộc) | Giữ nếu kịp (P1) | **Cắt** (ghi vào báo cáo là hướng phát triển) |
|---|---|---|
| Head A trên E1 + E2 ; Head B trên G1 + H1 ; baseline ; imbalance ; calibration ; bootstrap ; phân tích lỗi ; cross-domain E1 ↔ E2 | Survival, TabPFN, LSM (T3), H2 (ViCTSD), cross-lingual X14, conformal survival | **Tự thu chat tiếng Việt (V1)** — không có bộ công khai phù hợp, tự thu quá tốn thời gian ; Multi-task (X13), G2 Reddit CMV, E3 Reddit, GRU cho Head A, XLM-R fine-tune tiền tố, weighted conformal, LLM trích đặc trưng (X10), Optuna, so sánh 3 embedding (X9 → chỉ dùng 1 mô hình) |

## 1. Mốc chung (cả nhóm)

| Ngày | Mốc | Ai kiểm tra |
|---|---|---|
| **Thứ Sáu 30/10** | Chốt **hợp đồng**: định dạng dữ liệu tin nhắn, `extract_features()`, `encode_messages()`, API `/health/score`, `/safety/score`, API app (M1/M2) | A |
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
| A1 | **Hợp đồng dữ liệu & interface** (định dạng tin nhắn, hàm đặc trưng, API) trong `serving/schemas.py`, `src/m3_health/` | 30/10 | Ba người code khớp nhau ngay từ đầu |
| A2 | **Khung đánh giá** `src/eval/`: split theo thời gian/nhóm, nested CV, cluster + paired bootstrap, calibration, chọn ngưỡng ; **test chống rò rỉ** chạy trong CI | split + bootstrap 02/11 ; còn lại 06/11 | Rò rỉ dữ liệu là lỗi mất điểm nặng nhất |
| A3 | **Tạo & khóa tập test** cho mọi dataset (chỉ A giữ index test) ; **chỉ A mở test, đúng 1 lần, ngày 20/11** | tạo 06/11 ; mở 20/11 | Không ai vô tình chọn mô hình trên test |
| A4 | **Review & merge mọi PR** theo checklist rò rỉ (`01_*` §3) ; kiểm tra mỗi thí nghiệm có dòng trong `experiments/ledger.csv` | liên tục, ≤ 24h mỗi PR | Một cửa kiểm soát |
| A2b | **Nhãn Head A** (nhận từ B): sinh mẫu (c, t), nhãn Δ = 48h, cờ censoring, thống kê N hiệu dụng & prevalence | 06/11 | Định nghĩa nhãn sai = toàn bộ kết quả sai ; dễ rò rỉ tương lai vào đặc trưng |
| A2c | **Nhãn Head B** (nhận từ B): cắt tiền tố G1 **trước** lượt công kích, giữ cặp ghép cùng phần, gộp nhãn H1 (OFFENSIVE + HATE) | 06/11 | Cắt tiền tố sai = mô hình "nhìn thấy" lượt công kích → kết quả ảo |
| A5 | Head A: baseline (đa số, rule gốc, `time_since_last`) + LR + LightGBM ; 5 phương án mất cân bằng (X2) ; calibration + τ1/τ2 | 13/11 | Kết quả chính của bài |
| A5b | **Imbalance trên H2 (UIT-ViCTSD)** — phần **chạy CPU**: 5 phương án none / class_weight / under / over / SMOTE với TF-IDF + LR và với embedding đã cache (do B tính) + LR ; tổng hợp cùng kết quả GPU của B (B10) ; chọn **τ_B theo FPR** cho Head B | 18/11 | Under/oversampling, SMOTE là nội dung thầy nhấn mạnh → A thiết kế & kết luận thống nhất cho cả 2 head |
| A6 | Head A: survival (Cox, Random Survival Forest) (X3) ; conformal (X8) nếu kịp — đều chạy CPU | 18/11 | Điểm cộng |
| A7 | Ablation T1→T5 (X1), độ nhạy Δ (X5) | 19/11 | Trả lời RQ1 |
| A8 | **Chạy lại toàn bộ từ config + seed** sau khi mở test ; đối chiếu số với báo cáo | 22/11 | Tiêu chí "auditable" |
| A9 | Phân tích lỗi cả 2 head (`06_*` §5b) + **Practical limits** (`06_*` §6) | 25/11 | Thầy yêu cầu phân tích lỗi & điểm yếu |
| A10 | **Ghép báo cáo cuối** theo mẫu `01_*` §4 + checklist §5 ; slide | nháp 27/11 ; bản cuối 29/11 | Một người chịu trách nhiệm tổng thể |
> **Nguyên tắc tài nguyên:** việc nào **tốn GPU hoặc token (API LLM)** giao cho B/C ; A chỉ chạy việc CPU để dành thời gian kiểm soát chất lượng. Mọi lần chạy GPU vẫn phải ghi config + seed vào `experiments/ledger.csv` để A kiểm tra.
> Ước lượng khối lượng: **A ≈ 25 người-ngày**, B ≈ 24, C ≈ 20 (5 tuần ≈ 25 ngày làm việc).

## 3. Người B — dữ liệu, đặc trưng hành vi, backend + Swipe

| Mã | Việc | Hạn |
|---|---|---|
| B1 | Tải, kiểm tra, parse **E1 Ubuntu + E2 Twitter CSKH** ; Data Card (N, đơn vị độc lập, prevalence, license, sha256) | 02/11 |
| B2 | Tải, làm sạch **G1 (Conversations Gone Awry – Wiki)** + **H1 (ViHSD)** + **H2 (ViCTSD)** ; Data Card. Giao dữ liệu sạch cho A gán nhãn (A2b, A2c) | 04/11 |
| B4 | Đặc trưng **T1** (hành vi thời gian) + **T2** (bề mặt văn bản, từ điển Anh/Việt) | 10/11 |
| B5 | Đặc trưng **T3** (LSM, mirroring, turn-taking) | 13/11 |
| B6 | Đánh giá bộ phát hiện câu hỏi/tin cụt trên DailyDialog (có nhãn dialog act) (X6) | 18/11 |
| B8 | **[GPU]** Tính & **cache embedding** (mô hình C1 chọn) cho mọi tin nhắn của E1, E2, G1, H1, H2 → file `.npy` dùng chung (C cho T4/T5, A cho imbalance) ; ghi thời gian chạy, phần cứng | 09/11 |
| B9 | **[GPU]** **TabPFN v2** cho Head A (X7) — nhận từ A ; so với LightGBM của A theo kích thước train ; dùng split/khung đánh giá của A | 18/11 |
| B10 | **[GPU]** Phần imbalance cần GPU trên H2: fine-tune PhoBERT/XLM-R (pipeline của C2) với weighted loss và với oversampling ; giao kết quả cho A tổng hợp (A5b) | 18/11 |
| B7 | Viết phần **Objective and data** + mô tả dữ liệu trong báo cáo ; duyệt chéo phần của C | 27/11 |
| B-app1 | **Backend luật** (nhận từ A): lọc cứng + xếp hạng M1 (trường, khoa, tầng GPA, tuổi, khoảng cách, trạng thái tốt nghiệp ; sắp theo số tag trùng) + hàm luật blur M2 (theo số tin & số ngày chat) + test đơn vị, theo hợp đồng API của A | 16/11 |
| B-app2 | **Màn Swipe (M1):** thẻ hồ sơ ảnh mờ, thích/bỏ qua, tạo match, gọi API xếp hạng | 23/11 |

## 4. Người C — đọc nội dung, Head B, tích hợp

| Mã | Việc | Hạn |
|---|---|---|
| C1 | Chọn **1** mô hình embedding đa ngữ (MiniLM hoặc BGE-M3, lý giải bằng VN-MTEB) ; đặc trưng **T4** ; PCA fit trên DailyDialog | 06/11 (đóng băng) |
| C2 | **Bộ phân loại độc hại tiếng Việt** trên H1: TF-IDF+LR → PhoBERT/XLM-R ; cross-dataset H1 ↔ H2 (X12, phần imbalance do A làm) | 13/11 |
| C4 | **Head B** trên G1: baseline "tin cuối", mô hình bảng, **CRAFT-like** (embedding + GRU) ; ngưỡng theo FPR (X11) | 18/11 |
| C5 | **Cross-lingual Head B** (X14): điểm độc hại từ bộ phân loại tiếng Việt làm đặc trưng cho G1 ; hỗ trợ A chạy cross-domain Head A E1 ↔ E2 (X4) | 23/11 |
| C6 | API `POST /health/score`, `POST /safety/score` (FastAPI) + job kiểm tra hội thoại im lặng | 20/11 |
| C7 | Viết phần Head B + T3–T5 trong báo cáo ; duyệt chéo phần của B | 27/11 |
| C-app | **Màn chat (M2):** ảnh rõ dần theo luật blur của B (Gaussian blur trên máy), quả cầu độ hợp (hiển thị), gợi ý/nhắc nhẹ từ API Module 3 | 25/11 |

## 5. Phụ thuộc (điểm dễ nghẽn — A theo dõi)
```
A1 hợp đồng (30/10) ──► mọi người
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
