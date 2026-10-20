# 07 — Hướng dẫn bắt đầu (đọc file này đầu tiên)

## 1. Cài môi trường (cả 3 người, 1 lần)
```bash
git clone <URL repo> && cd <repo>
python -m venv .venv
.venv\Scripts\activate            # Windows (Git Bash: source .venv/Scripts/activate)
pip install -r requirements.txt
pytest                            # phải chạy được (hiện 1 test skip là bình thường)
```
Cần Python ≥ 3.10. Module 2 cần thêm `torch`, `transformers` (bỏ comment trong `requirements.txt`) — nên chạy trên Colab/Kaggle nếu không có GPU.

## 2. Quy trình làm việc mỗi task
1. `git checkout main && git pull`, tạo nhánh `a/<task>` (hoặc `b/`, `c/`), ví dụ `a/eval-splits`.
2. Làm theo mục "Công việc chi tiết" của module (file `04`–`06`) và mã việc trong `02_PHAN_CONG.md`.
3. Mọi thí nghiệm: tạo/sửa file trong `configs/`, chạy, rồi **thêm 1 dòng vào `experiments/ledger.csv`** (module, config hash, data hash, seeds, metric mean/sd).
4. Mở Pull Request nhỏ → 1 người khác review → merge vào `main`.
5. Khi xong 1 module: viết báo cáo theo mẫu `01_*` §4, để người khác duyệt bằng checklist §5.

## 3. Việc cần làm ngay trong tuần 1

### Người A — khung đánh giá + EDA Module 1
1. `src/eval/splits.py`: `held_out_group_split`, `repeated_group_kfold(K, R, seeds)`, `time_split`, `nested_cv`. **Viết `tests/test_splits_no_leak.py` thật** (bỏ `skip`): assert không group nào ở cả train và test.
2. `src/eval/imbalance.py` (5 chiến lược), `calibration.py`, `threshold.py`, `bootstrap.py`, `report.py` — thứ tự ưu tiên: splits → bootstrap → imbalance → calibration → report.
3. Tải Speed Dating (Kaggle `annavictoria/speed-dating-experiment`), notebook EDA: in N dòng, số wave, số người, prevalence `match`, missing; lập danh sách cột **cho phép/cấm** (xem `04_*` §1.2).
4. Chốt giao diện hàm của `src/eval` trong 2 ngày đầu và báo B, C vì họ phụ thuộc.

### Người B — dữ liệu chung + chuẩn bị Module 2
1. Tải và kiểm tra D1–D12 (`03_*` §0A.1). Với mỗi bộ viết `docs/data_cards/DATA_CARD_<id>.md`: nguồn, license, N, đơn vị độc lập, prevalence, sha256 → `data/checksums.txt`. Đánh dấu bộ nào không tải được.
2. `src/schema.py` theo `03_*` §0A.3; bảng mapping cột D1/D3 → schema.
3. Bắt đầu `src/synth/profiles.py` (S1) theo quy tắc §0A.4 (ghi rõ β nào là "assumption").
4. Soạn Google Form khảo sát P1 + phiếu đồng ý ẩn danh (nhờ C cùng thu).
5. Thử fine-tune/chạy baseline TF-IDF+LR trên UIT-VSMEC để biết có cần GPU.

### Người C — hạ tầng + chuẩn bị Module 3
1. `serving/schemas.py`: hợp đồng JSON cho `/rank`, `/compat/update`, `/health/score` (chốt cùng A, B).
2. `serving/app.py`: FastAPI khung + `/healthz`; chạy `uvicorn serving.app:app`.
3. `src/synth/chat.py` (S2): simulator hội thoại có timestamp, độ dài, câu hỏi, hứng thú tiềm ẩn h(t) (`06_*` §6, mục 3A); tham số ghi trong config.
4. Tải Ubuntu Dialogue Corpus v2 + DailyDialog, viết Data Card; định nghĩa mẫu/nhãn/censoring (`06_*` §1.2).

## 4. Mốc cần đạt
Xem lịch 6 tuần trong `02_PHAN_CONG.md` §3. Mốc tuần 1: schema + hợp đồng API + khung đánh giá chạy được + Data Card đầu tiên.

## 5. Quy tắc cứng (dễ mất điểm nếu vi phạm)
- Không fit bất cứ gì (scaler, SMOTE, embedding, PCA…) ngoài train fold.
- Không mở tập test trước tuần 5; mở rồi thì ghi vào Practical limits.
- Không commit dữ liệu thô/pilot; không đưa tên thật hay thông tin cá nhân vào repo.
- Số liệu dataset chỉ chép vào báo cáo sau khi in ra từ code.
- Mỗi bảng kết quả phải sinh lại được từ config + seed.

## 6. Khi bí
- Chọn gì cho việc X? → xem mục "Công việc chi tiết" của module và `01_*` §2 (giao thức mặc định).
- Thiếu thời gian? → xem tầng P0/P1/P2 trong `02_*`.
- Dataset không tải được? → xem bảng rủi ro `02_*` §5.
