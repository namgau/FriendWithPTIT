# 02 — Phân công 3 người & lịch làm việc

> **Quyết định phạm vi:** bài nộp môn Học máy = **Module 3 (Conversation Health Monitor)**, xem `06_*`. Module 1, 2 chỉ làm bản luật/đơn giản phục vụ app mobile (không nộp môn ML).
> Ước lượng theo **người-ngày** (1 ngày ≈ 4–5 giờ làm thực tế); chỉnh lại sau tuần 1.

| | Người A | Người B | Người C |
|---|---|---|---|
| Vai trò | Đánh giá & mô hình bảng | Dữ liệu & đặc trưng | NLP, mô hình nâng cao & tích hợp |
| Họ tên | ________ | ________ | ________ |

## 1. Bảng phân công chi tiết (Module 3)

| Mã | Hạng mục | Người | Ngày |
|---|---|---|---|
| A1 | Khung đánh giá `src/eval/`: forward-chaining time split, GroupKFold theo hội thoại/người đóng góp, nested CV, cluster bootstrap, calibration, chọn τ1/τ2 theo chi phí, `report.py`, ledger + test chống rò rỉ | **A** | 5 |
| A2 | Baseline (đa số, rule gốc, `time_since_last`) + LR + RF + LightGBM/XGBoost ; 5 phương án mất cân bằng (X2) | **A** | 4 |
| A3 | Ablation T1→T4 (X1), độ nhạy Δ/cửa sổ/chi phí (X5) | **A** | 3 |
| A4 | Phân tích lỗi E1–E5 (SHAP, slice, ≥ 10 ca sai) + 1 sửa đổi trước/sau | **A** | 3 |
| A5 | Ghép báo cáo cuối theo `01_*` §4, duyệt chéo | **A** | 3 |
| | **Tổng A** | | **18 + 2 dự phòng** |
| B1 | Tải, parse, chuẩn hóa E1 (Ubuntu) + E2 (Twitter CSKH) (+ E3 nếu kịp) ; Data Card | **B** | 4 |
| B2 | Sinh mẫu (c, t), nhãn Δ, cờ censoring ; thống kê prevalence/N hiệu dụng | **B** | 2 |
| B3 | Đặc trưng **T1** (hành vi thời gian) + **T2** (bề mặt văn bản, từ điển Anh/Việt) trong `src/m3_health/features.py` | **B** | 4 |
| B4 | Thu dữ liệu tiếng Việt V1 theo `08_*`: Form đồng ý, script trích đặc trưng local, phát hành, thu, Data Card V1 | **B** | 5 |
| B5 | Tập 200 câu Việt gán nhãn 2 người + đánh giá bộ phát hiện câu hỏi/tin cụt (X6) | **B** | 2 |
| B6 | Duyệt chéo báo cáo, phần dữ liệu trong báo cáo | **B** | 1 |
| | **Tổng B** | | **18 + 2 dự phòng** |
| C1 | Đặc trưng **T3** (LSM, mirroring, turn-taking ; danh sách từ chức năng tiếng Việt) | **C** | 3 |
| C2 | Đặc trưng **T4** (embedding đa ngữ MiniLM/LaBSE, topic drift/novelty, cảm xúc) + PCA cố định cho V1 | **C** | 3 |
| C3 | Mô hình chuỗi GRU (X1) | **C** | 3 |
| C4 | Survival: Cox PH, Random Survival Forest ; C-index, time-dep AUC, IBS ; so với nhị phân (X3) | **C** | 3 |
| C5 | Thí nghiệm cross-domain E1↔E2→V1 (X4) + phân tích lệch miền | **C** | 2 |
| C6 | Serving `POST /health/score` + job im lặng + gợi ý riêng tư ; nối app mobile | **C** | 3 |
| C7 | Duyệt chéo báo cáo, slide | **C** | 1 |
| | **Tổng C** | | **18 + 2 dự phòng** |

Ai cũng có 1 phần "khó/điểm cộng": A (thiết kế đánh giá & phân tích lỗi), B (dữ liệu tiếng Việt thật), C (survival + đa ngữ).

### Ngoài môn ML (làm cho môn mobile, không tính ở trên)
| Việc | Cách làm đơn giản |
|---|---|
| Module 1 (thứ tự Swipe) | Lọc cứng bằng luật + sắp theo số tag sở thích trùng ; ML để sau |
| Module 2 (ảnh rõ dần) | blur giảm theo số tin nhắn & số ngày chat (luật) ; quả cầu chỉ hiển thị |

## 2. Mức ưu tiên
| Tầng | Nội dung |
|---|---|
| **P0 – bắt buộc** | A1, A2, A4 ; B1 (ít nhất E1 + 1 bộ nữa), B2, B3, B4 (≥ 10 người) ; C6 bản GBM ; X1 với T1+T2, X2, X4, phân tích lỗi |
| **P1 – nên có** | T3 (C1), T4 (C2), survival (C4), X5, X6 ; **TabPFN v2 (X7, A)**, **conformal survival (X8, C)**, so sánh embedding (X9, C) |
| **P2 – bonus** | GRU (C3), E3 Reddit, PhoBERT cảm xúc, Optuna, weighted conformal dưới shift, LLM trích đặc trưng (X10) |

## 3. Phụ thuộc (điểm dễ nghẽn)
```
B1, B2 (dữ liệu + nhãn) ─────► A2, C3, C4 (mọi mô hình)
A1 (khung đánh giá) ─────────► A2..A4, C3..C5            ← A làm trước, tuần 1–2
B3 (T1, T2) ─────────────────► A2, A3 ; C1/C2 thêm T3, T4 trên cùng interface
C2 (embedding + PCA cố định) ► B4 (script V1 phải dùng đúng bộ này) ← chốt trước tuần 3
B4 (V1) ─────────────────────► C5 (cross-domain), A4 (slice theo người đóng góp)
```
Interface chung: hàm `extract_features(messages) -> DataFrame` trong `src/m3_health/features.py`, mỗi tầng 1 hàm con, chốt trong tuần 1.

## 4. Lịch 6 tuần (điều chỉnh theo hạn nộp thật)
| Tuần | A | B | C | Mốc chung |
|---|---|---|---|---|
| 1 | A1 splits + bootstrap | B1 tải/parse E1, E2 ; xác minh link | C2 chọn mô hình embedding ; dựng FastAPI khung | Chốt interface đặc trưng ; Form đồng ý V1 |
| 2 | A1 xong ; A2 baseline | B2 nhãn/censoring ; B3 T1 | C1 T3 ; C2 T4 + PCA cố định | Baseline chạy end-to-end trên E1 |
| 3 | A2 mô hình bảng + imbalance | B3 T2 ; B4 phát script V1 | C4 survival | **Giữa kỳ:** bảng CV mean ± SD |
| 4 | A3 ablation, độ nhạy | B4 thu V1 ; B5 gán nhãn 200 câu | C3 GRU ; C5 cross-domain | Đóng băng cấu hình |
| 5 | Mở test 1 lần ; A4 phân tích lỗi | Data Card V1 ; X6 | C6 serving + app | `report.py` sinh bảng |
| 6 | A5 ghép báo cáo | Duyệt chéo | Slide, demo | Checklist `01_*` §5 ; nộp |

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
| V1 ít người đóng góp (< 10) | Chỉ dùng V1 làm test, leave-one-donor-out, nói rõ CI rộng ; bổ sung V2 log app |
| Không có GPU | MiniLM chạy CPU được ; bỏ PhoBERT/GRU (P2) |
| Thiếu thời gian | Cắt P2, rồi P1. **Không cắt**: time/group split, bootstrap, calibration, baseline, phân tích lỗi |
