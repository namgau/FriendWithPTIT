# C-app2 / C7 — Bằng chứng kiểm chứng 23/11/2026

Nhánh làm việc: `codex/c-app2-c7`, nền `origin/main` tại `5df9dc1`.
Kế hoạch: [C_APP2_C7_PLAN.md](C_APP2_C7_PLAN.md).

## C-app2 — Android

Lệnh thực tế (PowerShell, từ `android/`):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\Admin\AppData\Local\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Kết quả: **BUILD SUCCESSFUL**, **81 test, 0 failure, 0 error, 0 skip**,
đối chiếu XML tại `android/app/build/test-results/testDebugUnitTest/`.
9 test mới: 2 `ChatNudgeStoreTest`, 6 `ChatSafetyPromptTest`, 1 ca tích hợp thêm
trong `ChatSafetyTest` (tổng lớp này 11 test). Các test session/social/chat cũ vẫn pass.

| Trường hợp | Bằng chứng tự động |
|---|---|
| Hai lần đầu vẫn nhắc, lần thứ ba tự tắt | Prompt test + ChatViewModel test |
| Tin tiếp theo gửi thẳng, không gọi safety | Đếm số lần gọi risk trong prompt test |
| Mở lại chat/ViewModel giữ trạng thái | Prompt + ChatViewModel recreation |
| Lưu bền qua tạo lại DataStore từ cùng file | Store test thực sự ghi/đọc file preferences trong thư mục tạm JVM |
| Cách ly chat, tài khoản và nguồn demo/backend | Store test theo cùng/khác `matchId` và owner |
| Bật lại reset đếm; tắt thủ công | Prompt + Store tests |
| Tin an toàn/Sửa lại/đóng nhắc không tăng đếm | Prompt test và ChatSafetyTest |
| Bấm gửi/xác nhận lặp không gửi kép | Prompt test giữ pending và busy |
| AI trả về sau khi phiên/chat không còn hiệu lực | Prompt test với kết quả bất đồng bộ và canSend=false |
| Coroutine bị hủy dù client nuốt cancellation | Prompt test: không được rơi xuống bước gửi |
| Không ghi được bộ đếm | Prompt giữ draft, không gửi, không tăng đếm |
| Không đọc được tùy chọn | Prompt trả draft để khôi phục ô nhập, không gửi |

APK debug: `android/app/build/outputs/apk/debug/app-debug.apk`, **23.381.704 byte**.
Build sử dụng JBR OpenJDK 25.0.3 có sẵn. Gradle báo cảnh báo Kotlin target 25
và deprecation của dependency; target app vẫn cấu hình JVM 17, build/test đã pass.

Backend chat dùng cùng prompt và menu; chỉ khi người dùng duyệt xong mới đi vào
outbox hiện có. Retry giữ nguyên `clientMessageId`, không chạy lại lời nhắc hoặc
tăng đếm. Test shared prompt không thay thế kiểm thử tích hợp BackendChatViewModel
với phiên thật, Compose hay HTTP thực tế.

## C7 — Kiểm chứng phần mô hình/báo cáo

Lệnh từ gốc repo:

```powershell
python -m pytest tests/test_features_t3.py tests/test_ablation_t3.py tests/test_features_t4.py tests/test_run_craft.py tests/test_craft_dataset.py tests/test_c2_pipeline.py tests/test_b6_dailydialog.py tests/test_serving_m3.py -q
```

Kết quả: **52 passed, 3 skipped**. Ba test tích hợp G1 bỏ qua vì cache embedding
B8 G1 không có trên máy. Test C2 có smoke fallback nhỏ (30 mẫu train, 2 fold,
output thư mục tạm, `write_ledger=False`), không tái lập lượt PhoBERT đầy đủ.
Các kiểm tra serving dùng test client trong process, không phải Android–HTTP thật.

- Đối chiếu số liệu với báo cáo kết quả và ledger đang có; giữ nguyên metric gốc.
- Sửa phần diễn giải C2/C4 và template sinh narrative C4 để lần chạy sau không
  tự khôi phục khẳng định chưa có căn cứ về FPR/độ trễ/nguồn lợi ích.
- Kiểm tra phương pháp T3/T4 và phần so sánh CRAFT/X12/X14; chi tiết duyệt chéo
  B và checklist 15 mục tại [C7_REVIEW_B.md](C7_REVIEW_B.md).
- Không chạy `run_test --open`, không đọc index/tập `data/locked/`, không tạo
  metric hay dòng thí nghiệm mới trong ledger. Không xác nhận đã tái lập kết quả GPU.

## Ca nghiệm thu trên thiết bị còn cần thực hiện

`adb devices` chưa có thiết bị/emulator kết nối trong lần làm này. Các ca dưới đây
là hướng dẫn bàn giao, **chưa đánh dấu đã chạy**:

1. Mở chat demo, gửi câu bị từ khóa offline nhắc; lần lượt chọn Vẫn gửi 3 lần.
   Cả 3 tin đều gửi; lần thứ 3 hiện thông báo đã tắt nhắc riêng cuộc chat.
2. Gửi câu tương tự lần thứ 4: không có hộp nhắc. Thoát/force-stop app rồi mở lại
   cùng chat: vẫn không nhắc. Chat demo khác vẫn nhắc.
3. Menu ⋮ → Bật lại nhắc an toàn cuộc chat: câu tiếp theo lại có hộp nhắc;
   chọn Sửa lại hoặc đóng hộp: nội dung về ô nhập, không tăng bộ đếm.
4. Lặp lại trên chat backend thật với `FWP_BACKEND_BASE_URL`; trước tiên dùng
   fallback offline, sau đó với `FWP_AI_BASE_URL` để kiểm tra response should_nudge.
5. Trong backend, đổi tài khoản/đăng xuất khi request AI đang chờ: không gửi
   draft bằng phiên mới. Đăng nhập lại cùng tài khoản vẫn giữ lựa chọn đúng chat.
6. Mất kết nối backend khi gửi một draft đã duyệt rồi retry: chỉ một bubble
   server theo UUID ban đầu; retry không xuất hiện lời nhắc mới.
7. Xác nhận M2 blur/reveal, Head A health và các thao tác report/block/unmatch
   vẫn theo nguồn dữ liệu/luật đang dùng, không bị tùy chọn Head B làm thay đổi.

## Trạng thái bàn giao

C-app2 hoàn tất code/unit test/build; nghiệm thu UI/HTTP còn theo danh sách trên.
C7 hoàn tất cập nhật và duyệt phần B hiện có; chờ phần thay đổi B7 cuối khi B gửi,
A8/test cuối, A9 ví dụ lỗi và A duyệt/merge PR. Commit/push nhánh không thay thế bước duyệt của A.
