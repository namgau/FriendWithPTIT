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
- ML (Module 3, cập nhật 03/11): dữ liệu 5 bộ đã chuẩn hóa, nhãn + tập test khóa, khung đánh giá đủ ;
  kết quả CV trên train ở `docs/results/` (Head A: `HEAD_A_CV.md`, `HEAD_A_SURVIVAL.md` ;
  Head B: `HEAD_B_CV.md`, `IMBALANCE_B.md`). Tập test mở 1 lần ngày 20/11.

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
android/              App Android sản phẩm (Kotlin, Compose, Room, Google Maps)
backend/              NestJS API + PostgreSQL/PostGIS + Docker Compose
serving/              FastAPI: API cho app android/ (luật M1/M2/M3) + chỗ cắm mô hình Module 3
docs/                 Kế hoạch, hướng dẫn, data_cards/, results/ (bảng kết quả sinh từ code), reference/
src/data/             Tải về → bảng tin nhắn chung (prepare.py) + Data Card (cards.py)
src/eval/             splits, bootstrap, imbalance, calibration, threshold, report
src/m3_health/        Module 3: labels (nhãn + tập test khóa), features, run_head_a, run_head_b,
                      run_imbalance_b, run_survival
configs/              default.yaml (siêu tham số, seed, Δ, chi phí) + test_lock.json (băm tập test)
data/                 raw/ processed/ locked/ (KHÔNG commit ; locked/ chỉ A mở) + checksums.txt
experiments/          ledger.csv (mỗi lần chạy 1 dòng, ghi commit + băm config/dữ liệu)
tests/                pytest: chống rò rỉ split, nhãn, đặc trưng không nhìn tương lai, eval, serving
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
