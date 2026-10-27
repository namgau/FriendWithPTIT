# 07 — Hướng dẫn bắt đầu (đọc file này đầu tiên)

## 1. Cài môi trường (cả 3 người, 1 lần)
```bash
git clone <URL repo> && cd <repo>
python -m venv .venv
.venv\Scripts\activate            # Windows (Git Bash: source .venv/Scripts/activate)
pip install -r requirements.txt
pytest                            # phải chạy được (hiện 1 test skip là bình thường)
```
Cần Python ≥ 3.10. Tầng T4 cần thêm `sentence-transformers`; survival cần `scikit-survival` hoặc `lifelines`; GRU cần `torch` (bỏ comment trong `requirements.txt`). Không có GPU vẫn chạy được MiniLM trên CPU.

## 2. Quy trình làm việc mỗi task
1. `git checkout main && git pull`, tạo nhánh `a/<task>` (hoặc `b/`, `c/`), ví dụ `a/eval-splits`.
2. Làm theo mã việc trong `02_PHAN_CONG.md` (A1…, B1…, C1…) và thiết kế trong `06_MODULE_3_HEALTH_MONITOR.md`.
3. Mọi thí nghiệm: tạo/sửa file trong `configs/`, chạy, rồi **thêm 1 dòng vào `experiments/ledger.csv`** (module, config hash, data hash, seeds, metric mean/sd).
4. Mở Pull Request nhỏ → 1 người khác review → merge vào `main`.
5. Báo cáo cuối viết theo mẫu `01_*` §4, duyệt chéo bằng checklist `01_*` §5.

## 3. Việc cần làm ngay trong tuần 1 (bài nộp = Module 3, đọc `06_*` trước)

### Người A — khung đánh giá (+ chuẩn bị survival)
1. `src/eval/splits.py`: `held_out_time_split` (20% hội thoại bắt đầu muộn nhất), `forward_chaining(K=5)`, `group_kfold(groups, K)` (theo hội thoại / người đóng góp), `nested_cv`. **Viết `tests/test_splits_no_leak.py` thật** (bỏ `skip`): assert không group nào ở cả train và test, và hội thoại train bắt đầu sớm hơn validation.
2. `src/eval/bootstrap.py` (cluster bootstrap, B = 2000), rồi `imbalance.py` (5 chiến lược), `calibration.py`, `threshold.py` (τ1, τ2 theo chi phí FN:FP), `report.py`.
3. Chốt giao diện hàm của `src/eval` trong 2 ngày đầu, báo B và C.

### Người B — dữ liệu
1. Tải & kiểm tra **E1 Ubuntu Dialogue Corpus** (bản có timestamp) và **E2 Customer Support on Twitter**: có đúng cột timestamp, người gửi, nối được thành hội thoại 2 người không? Viết `docs/data_cards/DATA_CARD_E1.md`, `..._E2.md` (nguồn, license, N, đơn vị độc lập, sha256 → `data/checksums.txt`). Bộ nào không dùng được → báo nhóm ngay.
2. Bản nháp `src/m3_health/labels.py`: sinh mẫu (c, t), nhãn Δ = 48h, cờ censoring (`06_*` §1.2).
3. Head B: `pip install convokit`, tải `conversations-gone-awry-corpus` (G1); tải ViHSD (H1) từ GitHub/HuggingFace. In N hội thoại/bình luận, prevalence, kiểm tra cấu trúc cặp ghép của G1.

### Người C — đặc trưng ngữ nghĩa + hạ tầng
1. Chốt cùng B interface `extract_features(messages) -> DataFrame` trong `src/m3_health/features.py` (mỗi tầng T1..T4 một hàm con).
2. Thử `paraphrase-multilingual-MiniLM-L12-v2` trên vài câu Anh/Việt: tốc độ CPU, kích thước; chọn mô hình embedding. PCA cố định: **fit trên DailyDialog (E4)** — bộ không dùng để đánh giá — rồi đóng băng, dùng chung cho E1, E2.
3. `serving/schemas.py` + `serving/app.py`: FastAPI khung với `/healthz` và `/health/score` trả giá trị giả; chạy `uvicorn serving.app:app`.
4. Bản nháp danh sách từ chức năng tiếng Việt cho LSM (đại từ, trợ từ, liên từ).
5. Head B: chạy thử TF-IDF+LR trên ViHSD (H1) làm baseline ; thử fine-tune PhoBERT/XLM-R 1 epoch trên Colab để biết thời gian/GPU cần.

## 4. Mốc cần đạt
Hạn nộp **Thứ Ba 01/12/2026**. Mốc theo ngày và deadline từng việc: `02_PHAN_CONG.md` §1–§4. Mốc đầu tiên: **Thứ Sáu 30/10** chốt hợp đồng dữ liệu/API ; **Thứ Hai 02/11** dữ liệu E1, E2, G1, H1 tải xong, khung split + bootstrap có test.

## 5. Quy tắc cứng (dễ mất điểm nếu vi phạm)
- Không fit bất cứ gì (scaler, SMOTE, embedding, PCA…) ngoài train fold.
- Chỉ Người A mở tập test, đúng 1 lần, ngày 20/11 ; không ai khác truy cập index test.
- Không commit dữ liệu thô; không đưa tên thật, nội dung tin nhắn hay thông tin cá nhân vào repo.
- Số liệu dataset chỉ chép vào báo cáo sau khi in ra từ code.
- Mỗi bảng kết quả phải sinh lại được từ config + seed.

## 6. Khi bí
- Chọn gì cho việc X? → `06_*` (thiết kế Module 3) và `01_*` §2 (giao thức mặc định).
- Thiếu thời gian? → xem tầng P0/P1/P2 trong `02_*`.
- Dataset không tải được? → xem bảng rủi ro `02_*` §7. Trễ hạn > 2 ngày → báo A để cắt theo `02_*` §0.
