# 02 — Phân công 3 người & deadline (hạn nộp: **Thứ Ba 01/12/2026**)

> **Phạm vi:**
> - **Môn Học máy:** Module 3 — Conversation Intelligence (Head A: hội thoại sắp tắt ; Head B: hội thoại sắp trở nên độc hại), xem `06_*`. Chia 3 phần.
> - **Môn Mobile:** Module 1 + Module 2 **bản luật** (không ML) trong app. Gộp lại, chia 3 phần.
> - **Người A = trưởng nhóm, chịu trách nhiệm chất lượng** (khung đánh giá, tập test, review/merge, tái lập, báo cáo cuối).
>
> 5 tuần, mỗi tuần tính từ Thứ Ba đến Thứ Hai. Mỗi người ~4–5 giờ/ngày làm việc.

| | Người A (trưởng nhóm) | Người B | Người C |
|---|---|---|---|
| Module 3 | Khung đánh giá, Head A dạng bảng + survival, phân tích lỗi, báo cáo | Dữ liệu Anh + Việt, nhãn, đặc trưng hành vi | Đọc nội dung (NLP), Head B, API |
| Module 1+2 (app) | Luật lọc/xếp hạng + luật blur ở backend, hợp đồng API | Màn Swipe (M1) | Màn chat: ảnh rõ dần + quả cầu độ hợp (M2), nối API Module 3 |
| Họ tên | ________ | ________ | ________ |

## 0. Cắt bớt so với kế hoạch đầy đủ (vì còn 5 tuần + làm app)

| Giữ (bắt buộc) | Giữ nếu kịp (P1) | **Cắt** (ghi vào báo cáo là hướng phát triển) |
|---|---|---|
| Head A trên E1 + E2 + V1 ; Head B trên G1 + H1 ; baseline ; imbalance ; calibration ; bootstrap ; phân tích lỗi ; cross-domain → V1 | Survival, TabPFN, LSM (T3), H2 (ViCTSD) cho thí nghiệm imbalance, conformal survival | Multi-task (X13), G2 Reddit CMV, E3 Reddit, GRU cho Head A, XLM-R fine-tune tiền tố, weighted conformal, LLM trích đặc trưng (X10), Optuna, so sánh 3 embedding (X9 → chỉ dùng 1 mô hình) |

## 1. Mốc chung (cả nhóm)

| Ngày | Mốc | Ai kiểm tra |
|---|---|---|
| **Thứ Sáu 30/10** | Chốt **hợp đồng**: định dạng dữ liệu tin nhắn, `extract_features()`, `encode_messages()`, API `/health/score`, `/safety/score`, API app (M1/M2). Gửi form đồng ý V1 | A |
| **Thứ Hai 02/11** | Họp tuần 1: dữ liệu E1, E2, G1, H1 tải xong & kiểm tra ; khung split + bootstrap có test | A |
| **Thứ Sáu 06/11** | **Đóng băng mô hình embedding + bộ phân loại độc hại bản 1** (để B gắn vào script V1) | A duyệt |
| **Thứ Hai 09/11** | Baseline cả 2 head chạy end-to-end ; script V1 phát hành | A |
| **Thứ Hai 16/11** | **Giữa kỳ:** bảng CV mean ± SD cả 2 head ; app có Swipe + chat chạy được | A |
| **Thứ Năm 19/11** | **Đóng băng cấu hình** mọi mô hình (không đổi gì nữa) ; dừng thu V1 vào 20/11 | A |
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
| A5 | Head A: baseline (đa số, rule gốc, `time_since_last`) + LR + LightGBM ; 5 phương án mất cân bằng (X2) ; calibration + τ1/τ2 | 13/11 | Kết quả chính của bài |
| A6 | Head A: survival (Cox, Random Survival Forest) (X3) ; TabPFN (X7) ; conformal (X8) nếu kịp | 18/11 | Điểm cộng |
| A7 | Ablation T1→T5 (X1), độ nhạy Δ (X5) | 19/11 | Trả lời RQ1 |
| A8 | **Chạy lại toàn bộ từ config + seed** sau khi mở test ; đối chiếu số với báo cáo | 22/11 | Tiêu chí "auditable" |
| A9 | Phân tích lỗi cả 2 head (`06_*` §5b) + **Practical limits** (`06_*` §6) | 25/11 | Thầy yêu cầu phân tích lỗi & điểm yếu |
| A10 | **Ghép báo cáo cuối** theo mẫu `01_*` §4 + checklist §5 ; slide | nháp 27/11 ; bản cuối 29/11 | Một người chịu trách nhiệm tổng thể |
| A-app | Backend app: **luật lọc cứng + xếp hạng** M1 (trường, khoa, tầng GPA, tuổi, khoảng cách, trạng thái tốt nghiệp ; sắp theo số tag trùng) + **hàm luật blur** M2 (theo số tin & số ngày chat) + test đơn vị | 16/11 | Nhẹ, để A còn sức kiểm soát chất lượng |

## 3. Người B — dữ liệu & đặc trưng hành vi

| Mã | Việc | Hạn |
|---|---|---|
| B1 | Tải, kiểm tra, parse **E1 Ubuntu + E2 Twitter CSKH** ; Data Card (N, đơn vị độc lập, prevalence, license, sha256) | 02/11 |
| B2 | Tải, làm sạch **G1 (Conversations Gone Awry – Wiki)** + **H1 (ViHSD)** (+ H2 ViCTSD nếu kịp) ; Data Card | 04/11 |
| B3 | Head A: sinh mẫu (c, t), nhãn Δ = 48h, cờ censoring ; thống kê N hiệu dụng | 06/11 |
| B4 | Đặc trưng **T1** (hành vi thời gian) + **T2** (bề mặt văn bản, từ điển Anh/Việt) | 10/11 |
| B5 | **V1 — chat tiếng Việt:** form đồng ý (30/10) → script trích đặc trưng local gắn mô hình của C (09/11) → thu (10/11–20/11, mục tiêu ≥ 10 người) → Data Card V1 (22/11) | 22/11 |
| B6 | Gán nhãn 200 câu Việt (2 người, Cohen's kappa) + đánh giá bộ phát hiện câu hỏi/tin cụt (X6) | 18/11 |
| B7 | Viết phần **Objective and data** + mô tả dữ liệu trong báo cáo ; duyệt chéo phần của C | 27/11 |
| B-app | **Màn Swipe (M1):** thẻ hồ sơ ảnh mờ, thích/bỏ qua, tạo match, gọi API xếp hạng luật của A | 23/11 |

## 4. Người C — đọc nội dung, Head B, tích hợp

| Mã | Việc | Hạn |
|---|---|---|
| C1 | Chọn **1** mô hình embedding đa ngữ (MiniLM hoặc BGE-M3, lý giải bằng VN-MTEB) ; đặc trưng **T4** ; PCA fit trên DailyDialog | 06/11 (đóng băng) |
| C2 | **Bộ phân loại độc hại tiếng Việt** trên H1: TF-IDF+LR → PhoBERT ; bản 1 đóng băng 06/11 cho script V1 ; bản tốt + imbalance trên H2 (X12) | bản 1: 06/11 ; xong: 13/11 |
| C3 | Đặc trưng **T3** (LSM, mirroring) — P1 | 13/11 |
| C4 | **Head B** trên G1: baseline "tin cuối", mô hình bảng, **CRAFT-like** (embedding + GRU) ; ngưỡng theo FPR (X11) | 18/11 |
| C5 | Cross-domain sang V1 (X4 cho Head A cùng A, X14 cho Head B) | 23/11 |
| C6 | API `POST /health/score`, `POST /safety/score` (FastAPI) + job kiểm tra hội thoại im lặng | 20/11 |
| C7 | Viết phần Head B + T3–T5 trong báo cáo ; duyệt chéo phần của B | 27/11 |
| C-app | **Màn chat (M2):** ảnh rõ dần theo luật blur của A (Gaussian blur trên máy), quả cầu độ hợp (hiển thị), gợi ý/nhắc nhẹ từ API Module 3 | 25/11 |

## 5. Phụ thuộc (điểm dễ nghẽn — A theo dõi)
```
A1 hợp đồng (30/10) ──► mọi người
A2 khung đánh giá (02–06/11) ──► A5, A6, C4
B1–B3 dữ liệu + nhãn Head A (06/11) ──► A5, A6
B2 G1/H1 (04/11) ──► C2, C4
C1 + C2 bản 1 đóng băng (06/11) ──► B5 script V1 (09/11)   ← nghẽn nguy hiểm nhất
B5 V1 xong (20–22/11) ──► C5, A9
A-app API luật (16/11) ──► B-app, C-app
C6 API Module 3 (20/11) ──► C-app
```

## 6. Quy tắc kiểm soát chất lượng (A áp dụng)
- **Definition of done** cho mỗi việc: code có test (nếu là hàm), chạy từ `configs/*.yaml`, có dòng trong `experiments/ledger.csv`, PR được A duyệt.
- Mọi bước fit (scaler, SMOTE, PCA, embedding fine-tune) **chỉ trên train fold** — A kiểm tra trong review.
- Không ai ngoài A truy cập index tập test trước 20/11.
- Số liệu dataset chỉ đưa vào báo cáo sau khi in ra từ code.
- Dữ liệu V1: không commit, không văn bản, xóa sau khi chấm.
- Họp nhanh 15 phút **Thứ Hai & Thứ Sáu**: mỗi người báo xong gì / kẹt gì / việc trễ hạn. Trễ > 2 ngày → A quyết định cắt theo bảng §0.

## 7. Rủi ro & dự phòng
| Rủi ro | Dự phòng |
|---|---|
| E2 không dùng được | E1 + V1 vẫn đủ 2 bộ cùng bài toán |
| V1 < 10 người đến 20/11 | V1 chỉ làm test, leave-one-donor-out, nói rõ CI rộng |
| Không có GPU cho PhoBERT | Colab/Kaggle ; nếu không: embedding đóng băng + LR |
| C2 trễ mốc 06/11 | Script V1 dùng bản TF-IDF+LR, không đợi PhoBERT |
| App chậm | Ưu tiên demo luồng Swipe → match → chat có blur ; quả cầu 3D cắt trước |
| Thiếu thời gian chung | Cắt theo bảng §0 (cột "giữ nếu kịp" trước). **Không cắt**: split đúng, bootstrap, calibration, baseline, phân tích lỗi |
