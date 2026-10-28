# FwP — FriendWithPTIT

Sản phẩm kết nối bạn bè/hẹn hò/học nhóm giữa sinh viên nhiều trường (đang học / đã tốt nghiệp),
gồm **Android app**, **NestJS backend** và **FastAPI AI serving**.
3 module ML: **M1** xếp hạng swipe · **M2** độ hợp → unblur ảnh (bonus) · **M3** sức khỏe hội thoại.

**Bài nộp môn Học máy: Module 3 — Conversation Intelligence**: dự báo hội thoại sắp "tắt"
(survival + conformal) và sắp trở nên độc hại (CRAFT-like, PhoBERT/XLM-R), chung bộ mã hóa
đọc hành vi + nội dung, có thí nghiệm chuyển miền và chuyển ngôn ngữ (Anh ↔ Việt).
Module 1 (xếp hạng swipe) và Module 2 (ảnh rõ dần) đang chạy bằng luật trong app `android/`.

- Thiết kế bài nộp: [`docs/06_MODULE_3_HEALTH_MONITOR.md`](docs/06_MODULE_3_HEALTH_MONITOR.md)
- **Bắt đầu từ đây:** [`docs/07_HUONG_DAN_BAT_DAU.md`](docs/07_HUONG_DAN_BAT_DAU.md)
- Kế hoạch & tiêu chuẩn báo cáo: [`docs/00_README.md`](docs/00_README.md)
- Phân công 3 người: [`docs/02_PHAN_CONG.md`](docs/02_PHAN_CONG.md)
- App sản phẩm Android: [`android/README.md`](android/README.md)
- Backend: [`backend/README.md`](backend/README.md)

## Trạng thái hiện tại

- `android/`: Kotlin + Jetpack Compose, giao diện tiếng Việt PTIT đỏ – vàng, 20 hồ sơ demo,
  M1/M2/M3 fallback cục bộ, gọi FastAPI khi cấu hình `FWP_AI_BASE_URL`, màn hình
  **Quanh bạn** dùng Google Maps khi có `MAPS_API_KEY` và radar mô phỏng khi không có.
  `:app:assembleDebug` đã build thành công; APK ở
  `android/app/build/outputs/apk/debug/app-debug.apk`.
- FastAPI: `/m1/rank`, `/m2/compat`, `/m3/health` + model registry `models/<module>/<version>`
  cho app `android/` ; `/health/score` (Head A) và `/safety/score` (Head B) là chỗ cắm mô hình
  Module 3 sau khi huấn luyện (`serving/predictors.py`).
- Backend: NestJS + PostgreSQL/PostGIS, xác thực OTP demo, hồ sơ, discover, swipe/match,
  chat, thông báo, report/block; Docker Compose kèm PostGIS. `npm run build` đã pass.

## Chạy nhanh

```powershell
# 1) ML serving
python -m venv .venv; .\.venv\Scripts\activate
pip install -r requirements.txt
uvicorn serving.app:app --host 0.0.0.0 --port 8000

# 2) Backend + PostGIS (Docker Desktop phải đang chạy)
cd backend; docker compose up --build

# 3) Android (mở thư mục android/ trong Android Studio)
cd ..\android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:assembleDebug -PFWP_AI_BASE_URL=http://10.0.2.2:8000/
```

Tài khoản demo backend: `demo@ptit.edu.vn` — OTP `123456` — mật khẩu `Fwp@123456`.

## Cấu trúc
```
android/         App Android sản phẩm (Kotlin, Compose, Room, Google Maps)
backend/         NestJS API + PostgreSQL/PostGIS + Docker Compose
docs/            TẤT CẢ tài liệu md: kế hoạch, hướng dẫn, data_cards/, reference/ (tài liệu gốc)
data/            raw/interim/processed (KHÔNG commit dữ liệu ; chỉ dataset công khai)
configs/         YAML cấu hình thí nghiệm (siêu tham số + seeds)
src/schema.py    schema hồ sơ & đặc trưng cặp dùng chung
src/synth/       sinh dữ liệu tổng hợp S1 (profiles), S2 (chat)          [B: S1, C: S2]
src/eval/        splits, imbalance, calibration, bootstrap, report        [A]
src/m1_rank/     Module 1                                                  [A]
src/m2_compat/   Module 2                                                  [B]
src/m3_health/   Module 3                                                  [C]
serving/         FastAPI: API cho app android/ (luật M1/M2/M3) + baseline Module 3 (Head A/B)
experiments/     ledger.csv (mỗi lần chạy 1 dòng)
models/          model + metadata.json theo version
notebooks/       EDA (đặt tên: <module>_<mô tả>_<tên viết tắt>.ipynb)
tests/           pytest (bắt buộc cho src/eval: test không rò rỉ group)
```

## Bắt đầu phần ML
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
