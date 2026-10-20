# 02 — Phân công 3 người & lịch làm việc

> Ước lượng theo **người-ngày** (1 ngày ≈ 4–5 giờ làm thực tế); chỉnh lại sau tuần 1.
> **A** = Module 1 + khung đánh giá (thống kê, mô hình dạng bảng) ; **B** = Module 2 + dữ liệu (NLP/deep learning) ; **C** = Module 3 + serving/demo + dữ liệu hội thoại/pilot.

| | Người A | Người B | Người C |
|---|---|---|---|
| Họ tên | ________ | ________ | ________ |

## 1. Bảng phân công chi tiết

| Mã | Hạng mục | Người | Ngày |
|---|---|---|---|
| 0B | Khung đánh giá dùng chung (splits, imbalance, calibration, bootstrap, report) | **A** | 4 |
| 1A | M1: EDA D1, feature, baseline, lưới candidate, calibration, ngưỡng | **A** | 4 |
| 1B | M1: Collaborative filtering trên D2 (Libimseti) | **A** | 4 |
| 1C | M1: Bio-embedding trên D3 (OkCupid) | **A** | 3 |
| 1D | M1: Ensemble, đánh giá S1/P1, viết báo cáo M1 | **A** | 3 |
| INT-A | Duyệt chéo báo cáo M3, ghép báo cáo tổng | **A** | 2 |
| | **Tổng A** | | **20** |
| 0A | Dữ liệu chung: Data Card D1–D12, schema, S1, thiết kế khảo sát P1 | **B** | 4 |
| 2A | M2: Personality (D4/D5/D6) | **B** | 4 |
| 2B | M2: Emotion PhoBERT (D7/D8/D9) | **B** | 4 |
| 2C | M2: Topic/interest (D10/D11) | **B** | 2 |
| 2D | M2: Aggregation + hàm blur + quả cầu độ hợp + đánh giá S2/P1 + báo cáo M2 | **B** | 4 |
| INT-B | Duyệt chéo báo cáo M1, slide, thuyết trình | **B** | 2 |
| | **Tổng B** | | **20** |
| 0C | Serving FastAPI + registry + demo (Swipe mờ, chat, độ hợp, cảnh báo) | **C** | 4 |
| 3A | M3: Simulator S2 (dùng chung với M2) | **C** | 3 |
| 3B | M3: Parser D12/D11, định nghĩa nhãn, rule baseline | **C** | 2 |
| 3C | M3: Feature, LR/GBM/GRU, imbalance, calibration, ngưỡng, báo cáo M3 | **C** | 5 |
| 3D | M3: Gợi ý hành động + UI riêng tư | **C** | 2 |
| P1-run | Thu và làm sạch dữ liệu pilot (khảo sát + 20–30 cặp chat) | **C** | 2 |
| INT-C | Duyệt chéo báo cáo M2 | **C** | 2 |
| | **Tổng C** | | **20** |

Mỗi người sở hữu đúng 1 module ML và có phần việc "khó" riêng: A (ranking/CF), B (NLP, PhoBERT), C (chuỗi thời gian + hạ tầng).

### Mức ưu tiên (để không đuối khi thiếu thời gian)
| Tầng | Nội dung |
|---|---|
| **P0 – lõi, bắt buộc** | 0A–0C ; M1 (1A, 1D, 4b phân tích lỗi, 4c hai bộ cùng bài toán) ; M3 (3A, 3B, 3C LR/GBM, 4b) |
| **P1 – nên có** | 1C bio-embedding ; 3D gợi ý ; 2B emotion + 2D tối giản ; mục kiến thức mở rộng (≥ 2/module) |
| **P2 – bonus** | 1B CF ; 2A/2C ; GRU ; UMAP ; pilot chat ; quả cầu 3D |

Phân tích lỗi (E1–E5) tính vào ngày của từng module ; dành ≥ 1 ngày/module (lấy từ dự phòng tuần 5–6).

## 2. Phụ thuộc giữa các người (điểm dễ nghẽn)

```
B:0A (schema, S1) ─────────────► A:1A/1D
A:0B (khung đánh giá) ─────────► B:2A..2D, C:3B/3C   ← A làm trước, tuần 1
C:3A (simulator S2) ───────────► B:2D (M2 đánh giá), C:3C
B:2B (emotion score) ──────────► C:3C (đặc trưng sentiment; dùng lexicon tạm nếu chưa xong)
A:models/m1, B:models/m2 ──────► C:0C (serving)
```
Interface chốt bằng hợp đồng hàm/JSON trong `serving/schemas.py` từ tuần 1. Họp đồng bộ 15 phút mỗi 2 ngày.

## 3. Lịch gợi ý 6 tuần (chưa biết hạn nộp thật — điều chỉnh sau)

| Tuần | A | B | C | Mốc chung |
|---|---|---|---|---|
| 1 | 0B khung đánh giá ; 1A-EDA | 0A Data Card + schema ; bắt đầu S1 | dựng repo, FastAPI khung ; 3A simulator ; gửi khảo sát P1 | Chốt schema + API + ledger ; xác minh link/license |
| 2 | 1A candidate + imbalance + calibration | S1 xong ; 2B PhoBERT baseline | 3B parser D12 ; 3A xong | Baseline mọi module chạy qua khung 0B |
| 3 | 1B CF ; 1C bio-embedding | 2B hoàn thiện ; 2A ; 2C | 3C features + LR/GBM | **Giữa kỳ:** mọi baseline có CV mean ± SD |
| 4 | 1D ensemble | 2D aggregation + blur | 3C GRU + ngưỡng ; 0C serving | Đóng băng cấu hình mô hình |
| 5 | Mở test M1 1 lần ; bootstrap | Mở test M2 ; P1 đánh giá | Mở test M3 ; 3D ; demo | `report.py` sinh bảng |
| 6 | Báo cáo M1 + duyệt M3 | Báo cáo M2 + slide + duyệt M1 | Báo cáo M3 + duyệt M2 | Duyệt chéo bằng checklist `01_*` §5 ; nộp |

## 4. Quy tắc làm việc
- Git: nhánh `a/…`, `b/…`, `c/…`; PR nhỏ, một người khác review.
- Mọi thí nghiệm chạy từ `configs/*.yaml`, ghi `experiments/ledger.csv`.
- **Không mở tập test trước tuần 5**; lỡ mở thì ghi vào Practical limits.
- Số liệu dataset chỉ đưa vào báo cáo sau khi in ra từ code.
- Dữ liệu pilot: có đồng ý, ẩn danh (hash id), không đưa file thô lên repo công khai.

## 5. Rủi ro & dự phòng
| Rủi ro | Dự phòng |
|---|---|
| D6 PANDORA / D2 Libimseti không tải được hoặc license không cho | Thay D5 MBTI (M2) / bản mirror khác ; ghi Data Card. Vẫn đủ ≥ 3 dataset nhờ S1/S2/P1 |
| Không có GPU cho PhoBERT | Colab/Kaggle, DistilBERT đa ngữ, hoặc TF-IDF+LR làm chính |
| Pilot P1 ít phản hồi (<50) | Vẫn dùng, báo CI rộng, không kết luận mạnh |
| Thiếu thời gian | Cắt: GRU (M3), UMAP, CF D2. **Không cắt**: split group/time, bootstrap, calibration, baseline |
