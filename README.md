# FwP — Bài tập lớn Huấn luyện mô hình học máy

App kết nối bạn bè/hẹn hò giữa sinh viên nhiều trường (đang học / đã tốt nghiệp).
**Bài nộp môn Học máy: Module 3 — Conversation Intelligence**: dự báo hội thoại sắp "tắt" (survival + conformal) và sắp trở nên độc hại (CRAFT-like, PhoBERT/XLM-R), chung bộ mã hóa đọc hành vi + nội dung, có thí nghiệm chuyển miền và chuyển ngôn ngữ (Anh ↔ Việt). Module 1 (xếp hạng swipe) và Module 2 (ảnh rõ dần) làm bản luật cho app mobile.

- Thiết kế bài nộp: [`docs/06_MODULE_3_HEALTH_MONITOR.md`](docs/06_MODULE_3_HEALTH_MONITOR.md)

- **Bắt đầu từ đây:** [`docs/07_HUONG_DAN_BAT_DAU.md`](docs/07_HUONG_DAN_BAT_DAU.md)
- Kế hoạch & tiêu chuẩn báo cáo: [`docs/00_README.md`](docs/00_README.md)
- Phân công 3 người: [`docs/02_PHAN_CONG.md`](docs/02_PHAN_CONG.md)

## Cấu trúc
```
docs/            TẤT CẢ tài liệu md: kế hoạch, hướng dẫn, data_cards/, reference/ (tài liệu gốc)
data/            raw/interim/processed/pilot (KHÔNG commit dữ liệu)
configs/         YAML cấu hình thí nghiệm (siêu tham số + seeds)
src/schema.py    schema hồ sơ & đặc trưng cặp dùng chung
src/synth/       sinh dữ liệu tổng hợp S1 (profiles), S2 (chat)          [B: S1, C: S2]
src/eval/        splits, imbalance, calibration, bootstrap, report        [A]
src/m1_rank/     Module 1                                                  [A]
src/m2_compat/   Module 2                                                  [B]
src/m3_health/   Module 3                                                  [C]
serving/         FastAPI + model registry                                  [C]
demo/            demo Swipe mờ / chat / độ hợp / cảnh báo                  [C, B]
experiments/     ledger.csv (mỗi lần chạy 1 dòng)
models/          model + metadata.json theo version
notebooks/       EDA (đặt tên: <module>_<mô tả>_<tên viết tắt>.ipynb)
tests/           pytest (bắt buộc cho src/eval: test không rò rỉ group)
```

## Bắt đầu
```bash
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt
pytest
```

## Quy tắc làm việc
- Nhánh: `a/<task>`, `b/<task>`, `c/<task>`; PR nhỏ, người khác review; `main` chỉ merge qua PR.
- Mọi thí nghiệm chạy từ `configs/*.yaml` và ghi `experiments/ledger.csv`.
- Chỉ Người A mở tập test, đúng 1 lần, ngày 20/11/2026. Không commit dữ liệu thô.
- Hạn nộp: **Thứ Ba 01/12/2026** — mốc chi tiết trong `docs/02_PHAN_CONG.md`.
- Số liệu dataset chỉ đưa vào báo cáo sau khi in ra từ code.
