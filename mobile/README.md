# FwP — App Android (Kotlin + Jetpack Compose)

App môn Mobile. Đã chạy được end-to-end: **Khám phá (Swipe) → match → chat có ảnh rõ dần, gợi ý cứu hội thoại, nhắc trước khi gửi tin gây khó chịu → Hồ sơ & Cài đặt**.

## Chạy

1. **Android Studio** → *Open* → chọn thư mục `mobile/` → chờ Gradle sync → chọn máy ảo → ▶ Run.
   (Dùng AGP 9.0.1, Gradle 9.3.0, compileSdk 36, minSdk 26.)
2. Mặc định app dùng **dữ liệu giả trong máy** (không cần server).
3. Muốn dùng server AI/backend demo: ở thư mục gốc repo chạy
   ```bash
   .venv\Scripts\activate
   uvicorn serving.app:app --port 8000
   ```
   rồi trong app: tab **Hồ sơ → bật "Dùng server AI"**. Máy ảo gọi tới `http://10.0.2.2:8000/`.
   Máy thật: đổi `AI_BASE_URL` trong `app/build.gradle.kts` thành IP LAN của máy chạy server và thêm IP đó vào `res/xml/network_security_config.xml`.
   Tài liệu API: http://localhost:8000/docs

## Cấu trúc

```
app/src/main/java/vn/ptit/fwp/
├─ MainActivity.kt          Activity duy nhất, gọi FwpApp()
├─ ServiceLocator.kt        chọn nguồn dữ liệu (giả / server) — đổi ở 1 chỗ
├─ data/
│  ├─ model/Models.kt       model khớp JSON của serving/schemas.py
│  ├─ remote/FwpApi.kt      Retrofit: /app/*, /safety/score
│  ├─ FwpRepository.kt      interface + RemoteFwpRepository
│  ├─ FakeFwpRepository.kt  dữ liệu giả để làm UI không cần server
│  └─ Settings.kt           SharedPreferences (cài đặt người dùng)
└─ ui/
   ├─ theme/Theme.kt        màu, chữ, khoảng cách (Spacing) — KHÔNG viết mã màu trong màn hình
   ├─ components/           BlurredAvatar (Module 2), CompatOrb (quả cầu), TagRow...
   ├─ swipe/                Module 1 — màn Swipe        (Người B)
   ├─ matches/              danh sách match
   ├─ chat/                 Module 2 + Module 3 — chat  (Người C)
   ├─ profile/              hồ sơ + cài đặt
   └─ FwpApp.kt             thanh điều hướng + NavHost
```

## "Cắm" module vào thế nào

| Module | Hiện tại | Khi có bản thật |
|---|---|---|
| M1 xếp hạng | luật trong `serving/rules.py` | sửa `rank_candidates` (hoặc thêm mô hình) — app không đổi |
| M2 độ mờ ảnh | luật trong `serving/rules.py` | truyền `compat` từ mô hình vào `blur_level` — app không đổi |
| M3 Head A (hội thoại nhạt) | baseline luật `RuleHealthPredictor` | viết `ModelHealthPredictor` cùng hàm `score()`, đổi `get_health_predictor()` trong `serving/predictors.py` |
| M3 Head B (độc hại) | baseline từ điển `RuleSafetyPredictor` | tương tự, đổi `get_safety_predictor()` |
| Backend thật (đăng nhập, DB, chat realtime) | `serving/demo_backend.py` (bộ nhớ tạm) | thay server nhưng **giữ nguyên JSON** của `/app/*` → chỉ đổi `AI_BASE_URL` |

Nguyên tắc: **hợp đồng JSON là cố định** (`serving/schemas.py`, `serving/demo_backend.py`, `data/model/Models.kt`). Đổi hợp đồng phải báo cả nhóm, Người A duyệt.

## Đối chiếu giáo trình môn Mobile
| Nội dung giáo trình | Trong app |
|---|---|
| Activity, vòng đời (Ch. 2.1–2.2) | `MainActivity` ; ViewModel giữ trạng thái qua xoay màn hình |
| Giao diện, xử lý sự kiện (2.3) | Compose (thay View/XML) ; vuốt thẻ bằng cử chỉ kéo |
| Shared Preferences (2.4.1) | `data/Settings.kt` |
| CSDL quan hệ / SQLite (2.4.3) | *Chưa có* — gợi ý: Room lưu tin nhắn offline |
| Networking (3.1) | Retrofit + OkHttp |
| Vị trí / bản đồ (3.2) | *Chưa có* — gợi ý: lấy GPS để tính khoảng cách thật cho bộ lọc |
| Camera (3.3) | *Chưa có* — gợi ý: chụp ảnh đại diện |

## Việc tiếp theo (gợi ý chia theo `docs/02_PHAN_CONG.md`)
- **B:** màn Swipe (thêm bộ lọc tuổi/khoảng cách/trường/GPA ở Hồ sơ → gửi `Preferences`), lấy vị trí GPS.
- **C:** màn chat (ảnh thật thay ảnh giả, quả cầu 3D), nối mô hình Module 3 khi có.
- Chung: đăng nhập/đăng ký, Room cache, ảnh đại diện.
