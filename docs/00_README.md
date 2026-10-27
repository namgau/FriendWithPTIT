# FwP — Bài tập lớn Huấn luyện mô hình học máy

Ứng dụng kết nối bạn bè/hẹn hò giữa sinh viên nhiều trường (có trạng thái **đang học / đã tốt nghiệp**). Mô tả sản phẩm gốc: `reference/AI_ML_FWP.md`. Bộ tài liệu này biến mô tả đó thành kế hoạch thực nghiệm **auditable** theo tiêu chuẩn của môn (`reference/tieuchuan.jpg`).

## Quyết định phạm vi
**Bài nộp môn Học máy = Module 3 — Conversation Intelligence** (`06_*`), cả 3 người cùng làm: **Head A** dự báo hội thoại sắp tắt, **Head B** dự báo hội thoại sắp trở nên độc hại, chung một bộ mã hóa đọc hành vi + nội dung. Module 1, 2 làm bản luật/đơn giản cho app mobile.

## Mục lục tài liệu

| File | Nội dung |
|---|---|
| `01_CHUAN_BAO_CAO_AUDITABLE.md` | Chuẩn 5 mục báo cáo → việc cụ thể; giao thức mặc định (K, R, seeds, bootstrap, imbalance, calibration); checklist chống leakage; checklist duyệt chéo |
| `02_PHAN_CONG.md` | Phân công 3 người (Module 3 + Module 1–2 bản app), **deadline từng việc, hạn nộp 01/12/2026**, Người A kiểm soát chất lượng, phụ thuộc, rủi ro |
| `03_MODULE_0_DATA_EVAL_SERVING.md` | Danh mục dataset, schema, dữ liệu tổng hợp, khung đánh giá dùng chung, serving/demo |
| `04_MODULE_1_RANKING.md` | (Tham khảo, không nộp) Module 1: xếp hạng swipe (Speed Dating, Libimseti, OkCupid + synthetic) |
| `05_MODULE_2_COMPATIBILITY.md` | (Tham khảo, không nộp) Module 2: độ hợp → % unblur (Essays/MBTI/PANDORA, VSMEC/VSFC/GoEmotions, PersonaChat…) |
| `06_MODULE_3_HEALTH_MONITOR.md` | **BÀI NỘP CHÍNH:** Module 3 — 2 head (tắt / độc hại), 5 tầng đặc trưng, survival + conformal, CRAFT-like, multi-task, cross-domain (Ubuntu ↔ Twitter ; tiếng Việt ↔ tiếng Anh ở Head B) |
| `07_HUONG_DAN_BAT_DAU.md` | **Đọc đầu tiên:** hướng dẫn từng bước cho từng người, việc tuần 1, cách chạy, quy trình làm việc |
| `data_cards/` | Data Card từng dataset (Người B/C viết khi tải dữ liệu) |
| `reference/` | Báo cáo tổng quan sản phẩm (`BAO_CAO_FWP.md`), mô tả lõi AI gốc (`AI_ML_FWP.md`) và ảnh tiêu chuẩn của thầy (`tieuchuan.jpg`) |

## Đối chiếu yêu cầu thầy giao
| Yêu cầu | Đã đáp ứng bằng |
|---|---|
| Dữ liệu tự tìm, ≥ 2 bộ | Head A: Ubuntu, Twitter CSKH (+ Reddit dự phòng) ; Head B: Conversations Gone Awry (Wiki), ViHSD, UIT-ViCTSD (`06_*` §1.1) |
| Tổng hợp kiến thức đã học | LR/RF/GBM, CV theo thời gian & nhóm, imbalance (under/over/SMOTE), calibration, ngưỡng, bootstrap, bias–variance |
| Phân tích kết quả, lỗi mô hình | `06_*` §5b + khung `01_*` §6 ; ablation 4 tầng đặc trưng, cross-domain |
| Metric đánh giá | `06_*` §5c: PR-AUC (chính), ROC-AUC, Brier/ECE, lead time, C-index/IBS, coverage conformal ; mean ± SD + CI bootstrap |
| Điểm cộng: kiến thức mới | `06_*` §5d: TabPFN v2 (Nature 2025), conformalized survival (ICLR/ICML 2025), dự báo hội thoại độc hại (CRAFT → 2025–2026), multi-task, BGE-M3/VN-MTEB, PhoBERT/XLM-R, LLM trích đặc trưng, LSM, SHAP |
| Lưu ý | Đề tài ngoài danh sách mẫu → **đã được thầy chấp nhận** ; bài nộp = Module 3 |

## Bản đồ tiêu chuẩn ↔ tài liệu

| Report element | Ở đâu |
|---|---|
| Objective and data | Mục 1 của từng module ; bảng dataset ở `03_*` |
| Evaluation design | Mục 2 của từng module ; giao thức mặc định ở `01_*` §2 |
| Learning procedure | Mục 3 của từng module |
| Results | Mục 4 (khung bảng) ; sinh tự động bằng `report.py` (0B) |
| Practical limits (điểm yếu project) | `06_*` §6: bảng 4 mục bắt buộc (resources, dependence, representativeness, distribution shift) + 11 điểm yếu khác, mỗi điểm có bằng chứng định lượng và cách giảm thiểu |
| Ghi chú bảng (under/oversampling, SMOTE, bias–variance) | `01_*` §2 ; mục 3.3–3.5 của từng module |

## Yêu cầu "≥ 2 dataset" được đáp ứng như thế nào

| Module | Dataset thật | Dataset tự tạo/thu thập |
|---|---|---|
| M1 | Speed Dating, Libimseti, OkCupid | S1 synthetic, P1 pilot |
| M2 | Essays Big-Five, MBTI (± PANDORA), UIT-VSMEC, UIT-VSFC, GoEmotions, PersonaChat | S2 synthetic, P1 pilot |
| M3 | Ubuntu Dialogue v2, DailyDialog, PersonaChat | S2 synthetic, P1 pilot |

## Những điểm cần nhóm thống nhất ngay (quyết định còn mở)

1. **Phạm vi trường**: giả định đa trường (schema có `school_id`) ; số trường mô phỏng 5–10.
2. **Module 3**: đồng ý đổi từ 3 lớp có giám sát sang *nhị phân + 3 mức theo xác suất hiệu chỉnh* (xem `06_*` §0).
3. **Hạn nộp** thật của môn → chỉnh lịch tuần trong `02_*`.
4. **GPU**: có hay không → quyết định PhoBERT hay phương án nhẹ.
5. **Thu dữ liệu pilot**: chấp nhận đối tượng (bạn cùng lớp, câu lạc bộ) và biểu mẫu đồng ý.

## Cảnh báo trung thực đã đưa vào tài liệu
- Không dataset nào có nhãn "tầng GPA", "đã tốt nghiệp", "chat ẩn danh hợp nhau" → phần này chỉ có thể kiểm chứng bằng dữ liệu tổng hợp (chứng minh pipeline, không chứng minh giả thuyết) và pilot nhỏ.
- Dataset công khai không chuyển trực tiếp sang FwP (khác đặc trưng, văn hóa, ngôn ngữ) → mục Practical limits của từng module.
- Các con số quy mô dataset trong tài liệu là ước lượng; phải xác minh bằng code trước khi đưa vào báo cáo.
