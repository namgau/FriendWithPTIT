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
| `06_MODULE_3_HEALTH_MONITOR.md` | **BÀI NỘP CHÍNH:** Module 3 — 2 head (tắt / độc hại), 5 tầng đặc trưng, survival + conformal, CRAFT-like, cross-domain (Ubuntu ↔ Twitter ; tiếng Việt ↔ tiếng Anh ở Head B) |
| `07_HUONG_DAN_BAT_DAU.md` | **Đọc đầu tiên:** hướng dẫn từng bước cho từng người, việc tuần 1, cách chạy, quy trình làm việc |
| `data_cards/` | Data Card từng dataset (Người B/C viết khi tải dữ liệu) |
| `reference/` | Báo cáo tổng quan sản phẩm (`BAO_CAO_FWP.md`), mô tả lõi AI gốc (`AI_ML_FWP.md`) và ảnh tiêu chuẩn của thầy (`tieuchuan.jpg`) |

## Đối chiếu yêu cầu thầy giao
| Yêu cầu | Đã đáp ứng bằng |
|---|---|
| Dữ liệu tự tìm, ≥ 2 bộ | Head A: Ubuntu, Twitter CSKH (+ Reddit dự phòng) ; Head B: Conversations Gone Awry (Wiki), ViHSD, UIT-ViCTSD (`06_*` §1.1) |
| Tổng hợp kiến thức đã học | LR/RF/GBM, CV theo thời gian & nhóm, imbalance (under/over/SMOTE), calibration, ngưỡng, bootstrap, bias–variance |
| Phân tích kết quả, lỗi mô hình | `06_*` §5b + khung `01_*` §6 ; ablation 5 tầng đặc trưng, cross-domain, cross-lingual |
| Metric đánh giá | `06_*` §5c: PR-AUC (chính), ROC-AUC, Brier/ECE, lead time, C-index/IBS, coverage conformal ; mean ± SD + CI bootstrap |
| Điểm cộng: kiến thức mới | `06_*` §5d: TabPFN v2 (Nature 2025), conformalized survival (ICLR/ICML 2025), dự báo hội thoại độc hại (CRAFT → 2025–2026), BGE-M3/VN-MTEB, PhoBERT/XLM-R, LSM, SHAP (multi-task, LLM trích đặc trưng: đã cắt, ghi là hướng phát triển) |
| Lưu ý | Đề tài ngoài danh sách mẫu → **đã được thầy chấp nhận** ; bài nộp = Module 3 |

## Bản đồ tiêu chuẩn ↔ tài liệu

| Report element | Ở đâu |
|---|---|
| Objective and data | `06_*` §1 |
| Evaluation design | `06_*` §4 ; giao thức mặc định ở `01_*` §2 |
| Learning procedure | `06_*` §2–§3 |
| Results | `06_*` §5, §5b, §5c ; sinh tự động bằng `src/eval/report.py` |
| Practical limits (điểm yếu project) | `06_*` §6: bảng 4 mục bắt buộc (resources, dependence, representativeness, distribution shift) + 11 điểm yếu khác, mỗi điểm có bằng chứng định lượng và cách giảm thiểu |
| Ghi chú bảng (under/oversampling, SMOTE, bias–variance) | `01_*` §2 ; `06_*` §3.3–3.5 |

## Yêu cầu "≥ 2 dataset" được đáp ứng như thế nào (bài nộp = Module 3)

| Nhiệm vụ | Bộ dữ liệu cùng bài toán | Ghi chú |
|---|---|---|
| Head A — hội thoại sắp tắt | **Ubuntu Dialogue Corpus**, **Customer Support on Twitter** (dự phòng: Reddit qua ConvoKit) | Có timestamp ; cross-domain giữa 2 nền tảng |
| Head B — hội thoại sắp độc hại | **Conversations Gone Awry (Wikipedia)** ; bộ phân loại tin: **ViHSD**, **UIT-ViCTSD** (tiếng Việt) | Cross-dataset ViHSD ↔ ViCTSD ; cross-lingual Việt → Anh |
| Phụ trợ | DailyDialog | Đánh giá bộ phát hiện câu hỏi, fit PCA cố định |

Module 1, 2 không nộp môn Học máy (xem `04_*`, `05_*` để tham khảo).

## Quyết định đã chốt

| Vấn đề | Quyết định |
|---|---|
| Bài nộp | Module 3, 2 head |
| Hạn nộp | Thứ Ba 01/12/2026 (`02_*`) |
| Module 3 3 mức hiển thị | Học nhị phân + 3 mức theo xác suất hiệu chỉnh (`06_*` §0) |
| Chat tiếng Việt tự thu | **Bỏ** (không có bộ công khai phù hợp, tự thu tốn thời gian & vướng riêng tư) |
| GPU | Việc cần GPU giao B/C, dùng Colab/Kaggle ; có phương án CPU dự phòng |

## Cảnh báo trung thực đã đưa vào tài liệu
- Không có dữ liệu chat hẹn hò thật (càng không có tiếng Việt) có nhãn → kết quả trên dữ liệu công khai không chứng minh hiệu quả trong app ; xem `06_*` §6.
- Các con số quy mô dataset trong tài liệu là ước lượng; phải xác minh bằng code trước khi đưa vào báo cáo.
