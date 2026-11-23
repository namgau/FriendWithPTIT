# B-app2 — Kiểm thử Swipe với backend

## Môi trường và phạm vi

- Nhánh `feature/b-app2-swipe`, commit `7c7ef83`; Android debug trên emulator
  Pixel_8, bản cài riêng `vn.ptit.fwp.bapp2test` để không thay dữ liệu app sẵn có.
- NestJS và database `bapp2_android_ui_test` trên container PostGIS test riêng;
  Android kết nối backend qua `adb reverse`. Dữ liệu và tài khoản demo được tạo
  trong database test, không sử dụng database phát triển.
- Kiểm thử thủ công bằng nút trên UI; không phải test tự động hay thử thao tác
  kéo thẻ bằng tay, không dùng hai thiết bị.

## Kết quả

1. Đăng nhập backend demo, màn Khám phá hiển thị thẻ đầu tiên `Linh Nấu Ăn`,
   trùng hồ sơ đầu tiên API Discover trả về. Thẻ hiển thị `2 sở thích chung`
   và `Cùng chế độ kết nối` từ backend.
2. Chọn **Bỏ qua**: UI báo đã bỏ qua và chuyển sang `Chi Lang Thang`, khớp hồ sơ
   thứ hai của danh sách API ban đầu. Database test ghi `PASS` cho hồ sơ đầu.
3. Chuẩn bị lượt `LIKE` ngược của `Chi Lang Thang` trực tiếp trong database test,
   sau đó chọn **Thích** trên Android. UI hiện `Đã match`, database ghi hai lượt
   thích và match; chọn **Mở trò chuyện** mở được chat backend với hồ sơ đó.

Kết quả trên xác nhận một tuyến UI–HTTP–PostgreSQL, không thay thế test hai
thiết bị, kiểm tra cử chỉ kéo, các bộ lọc hoặc toàn bộ trường hợp lỗi mạng.
Các kiểm thử backend HTTP và unit test xếp hạng trước đó đã được chạy riêng.
