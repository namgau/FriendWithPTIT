# FwP — FriendWithPTIT

Sản phẩm kết nối bạn bè/hẹn hò/học nhóm giữa sinh viên nhiều trường (đang học / đã tốt nghiệp),
gồm **Android app**, **NestJS backend** và **FastAPI ML serving**.
3 module ML: **M1** xếp hạng swipe · **M2** độ hợp → unblur ảnh (bonus) · **M3** sức khỏe hội thoại.

- **Bắt đầu từ đây:** [`docs/07_HUONG_DAN_BAT_DAU.md`](docs/07_HUONG_DAN_BAT_DAU.md)
- Kế hoạch & tiêu chuẩn báo cáo: [`docs/00_README.md`](docs/00_README.md)
- Phân công 3 người: [`docs/02_PHAN_CONG.md`](docs/02_PHAN_CONG.md)
- Android: [`android/README.md`](android/README.md)
- Backend: [`backend/README.md`](backend/README.md)

## Trạng thái hiện tại

- Android: Kotlin + Jetpack Compose, giao diện tiếng Việt PTIT đỏ – vàng, 20 hồ sơ demo,
  M1/M2/M3 fallback cục bộ, gọi FastAPI khi cấu hình `FWP_AI_BASE_URL`, màn hình
  **Quanh bạn** dùng Google Maps khi có `MAPS_API_KEY` và radar mô phỏng khi không có.
  `:app:assembleDebug` đã build thành công; APK ở
  `android/app/build/outputs/apk/debug/app-debug.apk`.
- FastAPI: `/healthz`, `/m1/rank`, `/m2/compat`, `/m3/health` (và alias `/rank`,
  `/compat/update`, `/health/score`), model registry đọc `models/<module>/<version>`.
  Test: `pytest` (7 passed, 1 skipped).
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

# 3) Android
cd ..\android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:assembleDebug -PFWP_AI_BASE_URL=http://10.0.2.2:8000/
```

Tài khoản demo: `demo@ptit.edu.vn` — OTP `123456` — mật khẩu `Fwp@123456`.

## Cấu trúc
```
android/         Ứng dụng Android gốc (Kotlin, Compose, Room)
backend/         NestJS API + PostgreSQL/PostGIS + Docker Compose
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

## Bắt đầu phần ML
```bash
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements.txt
pytest
```

## Quy tắc làm việc
- Nhánh: `a/<task>`, `b/<task>`, `c/<task>`; PR nhỏ, người khác review; `main` chỉ merge qua PR.
- Mọi thí nghiệm chạy từ `configs/*.yaml` và ghi `experiments/ledger.csv`.
- Không mở tập test trước tuần 5. Không commit dữ liệu thô/pilot.
- Số liệu dataset chỉ đưa vào báo cáo sau khi in ra từ code.
