# FwP Android

Ứng dụng Android gốc (Kotlin + Jetpack Compose) của FriendWithPTIT: hẹn hò, kết bạn
và học nhóm giữa sinh viên các trường, giao diện tiếng Việt, nhận diện PTIT đỏ – vàng.

## Yêu cầu

- JDK 21 (`JAVA_HOME` trỏ tới JDK 21)
- Android SDK 37 tại `android/local.properties` (`sdk.dir=...`)
- Gradle Wrapper đã được tạo trong repo (`gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`)

## Build

```powershell
cd android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

Các biến cấu hình không bắt buộc (truyền bằng `-P`):

```powershell
.\gradlew.bat :app:assembleDebug `
  -PFWP_AI_BASE_URL=http://10.0.2.2:8000/ `
  -PFWP_BACKEND_BASE_URL=http://10.0.2.2:3000/ `
  -PMAPS_API_KEY=your-google-maps-key
```

Khi `FWP_AI_BASE_URL` trống, app dùng các engine quy tắc cục bộ và hiển thị nhãn
`"Quy tắc FwP (fallback)"`. Khi cấu hình FastAPI, app gọi M1/M2/M3 và hiển thị
`"AI Service · fallback"` hoặc `"AI Service (M1/M2/M3)"` khi serving trả
`model_source = "trained_model"`.

## Chạy demo

- Đăng ký hoặc đăng nhập bằng email và mật khẩu qua backend. Tài khoản này cũng
  được dùng cho Campus Feed; không tự đăng nhập qua OTP demo.
- Trong bản debug có thể chọn **demo trên thiết bị**. Demo local không có phiên
  backend và không truy cập Campus; hồ sơ và lịch sử Room hiện có được giữ lại.
- Dữ liệu demo: 20 hồ sơ, 3 cuộc trò chuyện, thông báo và match có sẵn.
- Chat demo tự trả lời sau vài giây; nút gợi ý mở đầu và phân tích M2/M3 hoạt động offline.
- Mở **Khám phá → Bản đồ** để xem hồ sơ quanh bạn. Khi có `MAPS_API_KEY`, app dùng
  Google Maps; nếu không có key, app hiển thị bản đồ radar mô phỏng và danh sách
  khoảng cách, không crash.
- **Campus Feed** là tab riêng, dùng backend NestJS thật: đăng bài ẩn danh, ảnh,
  like, bookmark, bình luận, báo cáo và Swipe tác giả `DISCOVERABLE`. Cần chạy
  backend và build với `-PFWP_BACKEND_BASE_URL=http://10.0.2.2:3000/`; nếu thiếu
  cấu hình, tab hiển thị hướng dẫn thay vì dữ liệu giả.
- Session backend được mã hóa bằng Android Keystore; khi server trả 401, cần đăng
  nhập lại. Bản release yêu cầu URL backend HTTPS.
- Backend hiện vẫn cung cấp `/auth/demo` và `/auth/otp/verify` demo có thể cấp
  token không an toàn. Không triển khai backend này cho production trước khi
  vô hiệu hóa hoặc thay các endpoint demo bằng xác thực thực sự; Android không
  gọi chúng trong luồng đăng nhập mới. Backend cũng chưa có thu hồi JWT khi logout.
- Hồ sơ server hiển thị từ `/auth/me`; bản Room demo không được tự động đồng bộ
  hay ghi đè. Ảnh hồ sơ từ thiết bị chỉ dành cho demo, backend chưa hỗ trợ upload.
- Match tạo từ Campus đã được backend ghi nhận thật. Trong phiên backend, các tab
  **Hôm nay/Khám phá/Match/Chat** thông báo chưa tích hợp; không hiển thị hồ sơ,
  lịch sử, số lượt thích/tin nhắn hoặc thành tích Room như dữ liệu của tài khoản.
- Các route con hồ sơ demo, chat demo, Nearby, Double Date và thông báo đều kiểm
  tra phiên trước khi dựng màn hình. `authorToken` và `anon:<matchId>` không được
  dùng để tra cứu hồ sơ/chat Room. Thông báo match Campus chưa mở chat backend.
- Repository ngừng quan sát dữ liệu demo và từ chối thao tác social Room ngoài
  phiên demo local. Đổi phiên/đăng xuất không xóa Room; trở lại demo dùng lịch sử
  đã lưu. State Campus và hồ sơ được phân biệt theo tài khoản và thế hệ phiên.

## Kiểm thử hồi quy Phase 3B-II

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

- 34 unit test: session/profile (16), cách ly luồng/route/badge/định danh (6),
  state thống kê/thành tích hồ sơ (3), Campus contract (7), Settings policy (2).
- Có kiểm tra demo → backend, hai tài khoản backend, logout/login, kết quả request
  tài khoản cũ và trở lại demo mà lịch sử nguồn vẫn còn nguyên.
- Đây là test JVM với nguồn dữ liệu/API giả; chưa phải kiểm thử Room SQLite,
  Compose/back stack, Android Keystore hoặc HTTP end-to-end trên thiết bị thật.

## Kiến trúc

```
app/src/main/java/vn/ptit/fwp/
  core/model        Domain models, filters, catalog trường/khoa
  core/data         Room entities/DAO, repository, DemoSeed, SettingsStore,
                    CampusRepository (NestJS Campus Feed)
  core/data/remote  CampusApi/Retrofit + auth/profile sync
  core/ai           RankingEngine (M1), CompatibilityEngine (M2), HealthEngine (M3),
                    ConversationAssistant, RemoteAiClient (FastAPI)
  core/design       Theme PTIT, components, avatar, compatibility orb, celebration
  feature/*         Splash, onboarding, auth, setup, discover, explore, nearby,
                    detail, matches, chats, chat, profile, settings,
                    notifications, double date, campus
  navigation        Routes + NavHost
```

## Lưu ý

- `local.properties` không commit.
- Ảnh chọn từ thiết bị lưu URI cục bộ; dữ liệu demo và lịch sử chat lưu trong Room.
- App chưa được chạy trên thiết bị/emulator trong môi trường hiện tại; `:app:assembleDebug`
  đã build thành công.
