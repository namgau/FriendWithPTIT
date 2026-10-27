# 02 — Phân công 3 người & lịch làm việc

> **Quyết định phạm vi:** bài nộp môn Học máy = **Module 3 — Conversation Intelligence** (Head A: hội thoại sắp tắt ; Head B: hội thoại sắp trở nên độc hại), xem `06_*`. Module 1, 2 chỉ làm bản luật/đơn giản phục vụ app mobile (không nộp môn ML).
> Ước lượng theo **người-ngày** (1 ngày ≈ 4–5 giờ làm thực tế); chỉnh lại sau tuần 1. Tổng khối lượng tăng ~25% so với bản chỉ có Head A → mỗi người ~20 ngày, dự phòng mỏng: bám sát mức ưu tiên ở §2.

| | Người A | Người B | Người C |
|---|---|---|---|
| Vai trò | Đánh giá, mô hình bảng, survival | Dữ liệu (Anh + Việt) & đặc trưng hành vi | Đọc nội dung (NLP), Head B, multi-task & tích hợp |
| Họ tên | ________ | ________ | ________ |

## 1. Bảng phân công chi tiết

| Mã | Hạng mục | Người | Ngày |
|---|---|---|---|
| A1 | Khung đánh giá `src/eval/`: forward-chaining time split, GroupKFold (hội thoại / người đóng góp / cặp ghép G1), nested CV, cluster + paired bootstrap, calibration (+ hiệu chỉnh prior shift cho Head B), chọn τ theo chi phí/FPR, `report.py`, ledger, test chống rò rỉ | **A** | 5 |
| A2 | Head A: baseline (đa số, rule gốc, `time_since_last`) + LR + RF + LightGBM + **TabPFN v2 (X7)** ; 5 phương án mất cân bằng (X2) | **A** | 4 |
| A3 | Head A: **survival** (Cox PH, Random Survival Forest ; C-index, IBS) (X3) + **conformal survival** (X8) | **A** | 4 |
| A4 | Ablation T1→T5 (X1), độ nhạy Δ/cửa sổ/chi phí (X5) | **A** | 2 |
| A5 | Phân tích lỗi E1–E5 cho cả 2 head (SHAP, slice, ≥ 10 ca sai mỗi head, FP "buộc tội oan") + 1 sửa đổi trước/sau | **A** | 3 |
| A6 | Ghép báo cáo cuối theo `01_*` §4 | **A** | 2 |
| | **Tổng A** | | **20** |
| B1 | Tải, parse, chuẩn hóa E1 (Ubuntu) + E2 (Twitter CSKH) (+ E3 nếu kịp) ; Data Card | **B** | 3 |
| B2 | Head A: sinh mẫu (c, t), nhãn Δ, cờ censoring ; thống kê prevalence / N hiệu dụng | **B** | 2 |
| B3 | Đặc trưng **T1** (hành vi thời gian) + **T2** (bề mặt văn bản, từ điển Anh/Việt) | **B** | 3 |
| B4 | Thu dữ liệu tiếng Việt V1 theo `08_*`: Form đồng ý, script trích đặc trưng local (gồm chạy mô hình T5 tại máy), phát hành, thu, Data Card V1 | **B** | 5 |
| B5 | Dữ liệu Head B: G1, G2 qua ConvoKit (tiền tố trước lượt công kích, cặp ghép) + H1 ViHSD, H2 ViCTSD (dedup, split) ; Data Card | **B** | 3 |
| B6 | Tập 200 câu Việt gán nhãn 2 người (câu hỏi / tin cụt / độc hại) + Cohen's kappa + đánh giá bộ phát hiện (X6) | **B** | 2 |
| B7 | Duyệt chéo báo cáo, phần dữ liệu trong báo cáo | **B** | 1 |
| | **Tổng B** | | **19 + 1 dự phòng** |
| C1 | Đặc trưng **T3** (LSM, mirroring, turn-taking ; từ chức năng tiếng Việt) | **C** | 2 |
| C2 | Đặc trưng **T4** (embedding đa ngữ, topic drift/novelty) + so sánh MiniLM / BGE-M3 / multilingual-e5 (X9) + PCA cố định | **C** | 2 |
| C3 | **Bộ phân loại độc hại tiếng Việt** (TF-IDF+LR → PhoBERT/XLM-R) trên H1, H2 ; cross-dataset ; imbalance trên H2 (X12) | **C** | 3 |
| C4 | **Head B**: baseline "tin cuối", mô hình bảng, **CRAFT-like** (encoder + GRU ngữ cảnh), XLM-R tiền tố ; G1, G2, cross G1↔G2 (X11) | **C** | 4 |
| C5 | **Multi-task** chung encoder T5 cho Head A + B (X13) ; cross-domain sang V1 (X4, X14) | **C** | 3 |
| C6 | Serving `POST /health/score`, `POST /safety/score`, job im lặng, gợi ý riêng tư, nhắc nhẹ người gửi ; nối app mobile | **C** | 3 |
| C7 | Duyệt chéo báo cáo, slide | **C** | 2 |
| | **Tổng C** | | **19 + 1 dự phòng** |

Ai cũng có phần "điểm cộng": A (TabPFN, survival + conformal, thiết kế đánh giá), B (dữ liệu tiếng Việt thật, giữ riêng tư), C (đọc nội dung đa ngữ, dự báo độc hại, multi-task).

### Ngoài môn ML (làm cho môn mobile, không tính ở trên)
| Việc | Cách làm đơn giản |
|---|---|
| Module 1 (thứ tự Swipe) | Lọc cứng bằng luật + sắp theo số tag sở thích trùng ; ML để sau |
| Module 2 (ảnh rõ dần) | blur giảm theo số tin nhắn & số ngày chat (luật) ; quả cầu chỉ hiển thị ; sau này dùng lại encoder T5 |
| Tín hiệu hành vi app (xem hồ sơ, đọc lại chat, thu hồi tin, thời gian soạn) | Ghi log có đồng ý, luật giới hạn tần suất ; **không** đưa vào đánh giá ML (`06_*` §7.1) |

## 2. Mức ưu tiên
| Tầng | Nội dung |
|---|---|
| **P0 – bắt buộc** | A1, A2 (không cần TabPFN), A5 ; B1 (≥ E1 + 1 bộ), B2, B3, B4 (≥ 10 người) ; C6 bản GBM ; X1 với T1+T2, X2, X4 ; phân tích lỗi Head A |
| **P1 – nên có** | **Head B tối thiểu: G1 + H1** (B5, C3, C4 bản baseline + CRAFT-like) ; survival + conformal (A3) ; TabPFN (X7) ; T3, T4 (C1, C2) ; X5, X6, X9 |
| **P2 – bonus** | Multi-task (C5/X13), G2 + H2 cross-dataset, XLM-R fine-tune tiền tố, GRU cho Head A, E3 Reddit, weighted conformal dưới shift, LLM trích đặc trưng (X10), Optuna |

## 3. Phụ thuộc (điểm dễ nghẽn)
```
B1, B2 (dữ liệu + nhãn Head A) ─► A2, A3, C5
B5 (G1, G2, H1, H2) ────────────► C3, C4
A1 (khung đánh giá) ────────────► mọi thí nghiệm          ← A làm trước, tuần 1–2
B3 (T1, T2) ────────────────────► A2, A4 ; C1/C2 thêm T3, T4 cùng interface
C2 + C3 (embedding, bộ phân loại độc hại, PCA cố định) ─► B4 (script V1 phải dùng đúng mô hình này) ← chốt trước tuần 3
B4 (V1) ────────────────────────► C5 (cross-domain), A5 (slice theo người đóng góp)
```
Interface chung: `extract_features(messages) -> DataFrame` trong `src/m3_health/features.py` (mỗi tầng 1 hàm con) và `encode_messages(texts) -> ndarray` cho T5, chốt trong tuần 1.

## 4. Lịch 6 tuần (điều chỉnh theo hạn nộp thật)
| Tuần | A | B | C | Mốc chung |
|---|---|---|---|---|
| 1 | A1 splits + bootstrap | B1 tải/parse E1, E2 ; tải G1 (ConvoKit), H1 ; xác minh link | C2 chọn embedding ; thử PhoBERT/XLM-R trên H1 (GPU?) ; FastAPI khung | Chốt interface ; Form đồng ý V1 |
| 2 | A1 xong ; A2 baseline Head A | B2 nhãn/censoring ; B3 T1 ; B5 G1/H1 sạch | C3 bộ phân loại độc hại H1 ; C1 T3 | Baseline Head A end-to-end trên E1 |
| 3 | A2 mô hình bảng + imbalance + TabPFN | B3 T2 ; B4 phát script V1 (đã gắn mô hình C2/C3) | C4 Head B baseline + CRAFT-like trên G1 | **Giữa kỳ:** bảng CV mean ± SD cả 2 head |
| 4 | A3 survival + conformal ; A4 ablation | B4 thu V1 ; B5 G2/H2 ; B6 gán nhãn 200 câu | C4 cross G1↔G2 ; C5 multi-task (nếu kịp) | Đóng băng cấu hình |
| 5 | Mở test 1 lần ; A5 phân tích lỗi | Data Card V1 ; X6 | C5 cross-domain → V1 ; C6 serving + app | `report.py` sinh bảng |
| 6 | A6 ghép báo cáo | Duyệt chéo | Slide, demo | Checklist `01_*` §5 ; nộp |

## 5. Quy tắc làm việc
- Git: nhánh `a/…`, `b/…`, `c/…` ; PR nhỏ, một người khác review.
- Mọi thí nghiệm chạy từ `configs/*.yaml`, ghi `experiments/ledger.csv`.
- **Không mở tập test trước tuần 5** ; lỡ mở thì ghi vào Practical limits.
- Số liệu dataset chỉ đưa vào báo cáo sau khi in ra từ code.
- Dữ liệu V1: không commit, không văn bản, xóa sau khi chấm.

## 6. Rủi ro & dự phòng
| Rủi ro | Dự phòng |
|---|---|
| E2/E3 không dùng được (link, thiếu timestamp, license) | E1 + V1 vẫn đủ 2 bộ cùng bài toán ; thay E2 bằng corpus ConvoKit khác có timestamp |
| G2/H2 không kịp | Head B tối thiểu G1 + H1 (vẫn đủ 1 bộ Anh + 1 bộ Việt) |
| V1 ít người đóng góp (< 10) | Chỉ dùng V1 làm test, leave-one-donor-out, nói rõ CI rộng ; bổ sung V2 log app |
| Không có GPU | Colab/Kaggle cho PhoBERT/XLM-R ; nếu không: embedding đóng băng + LR/GRU nhỏ (vẫn chạy CPU) |
| Thiếu thời gian | Cắt P2, rồi rút Head B về "bộ phân loại độc hại H1 + baseline tin cuối trên G1". **Không cắt**: time/group split, bootstrap, calibration, baseline, phân tích lỗi |
