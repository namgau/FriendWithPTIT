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

- Mở app → Onboarding → **Dùng chế độ demo** (hoặc đăng nhập OTP).
- Email demo: `demo@ptit.edu.vn`; OTP: `123456`.
- Dữ liệu demo: 20 hồ sơ, 3 cuộc trò chuyện, thông báo và match có sẵn.
- Chat demo tự trả lời sau vài giây; nút gợi ý mở đầu và phân tích M2/M3 hoạt động offline.
- Mở **Khám phá → Bản đồ** để xem hồ sơ quanh bạn. Khi có `MAPS_API_KEY`, app dùng
  Google Maps; nếu không có key, app hiển thị bản đồ radar mô phỏng và danh sách
  khoảng cách, không crash.

## Kiến trúc

```
app/src/main/java/vn/ptit/fwp/
  core/model        Domain models, filters, catalog trường/khoa
  core/data         Room entities/DAO, repository, DemoSeed, SettingsStore
  core/ai           RankingEngine (M1), CompatibilityEngine (M2), HealthEngine (M3),
                    ConversationAssistant, RemoteAiClient (FastAPI)
  core/design       Theme PTIT, components, avatar, compatibility orb, celebration
  feature/*         Splash, onboarding, auth, setup, discover, explore, nearby,
                    detail, matches, chats, chat, profile, settings,
                    notifications, double date
  navigation        Routes + NavHost
```

## Lưu ý

- `local.properties` không commit.
- Ảnh chọn từ thiết bị lưu URI cục bộ; dữ liệu demo và lịch sử chat lưu trong Room.
- App chưa được chạy trên thiết bị/emulator trong môi trường hiện tại; `:app:assembleDebug`
  đã build thành công.
