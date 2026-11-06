# FwP Backend (NestJS + PostgreSQL/PostGIS)

API cho FriendWithPTIT: xác thực OTP demo, hồ sơ sinh viên, khám phá theo bộ lọc,
swipe/match, chat, thông báo, báo cáo, chặn và Campus Feed ẩn danh.

## Chạy nhanh bằng Docker (khuyến nghị)

```powershell
cd backend
docker compose up --build
```

- API: `http://localhost:3000`
- PostgreSQL/PostGIS: `localhost:5432` (`fwp` / `fwp`, DB `fwp`)
- Health: `GET http://localhost:3000/healthz`

## Chạy trực tiếp

Cần PostgreSQL 16 + PostGIS (hoặc dùng `docker compose up db`).

```powershell
cd backend
Copy-Item .env.example .env
npm install
npm run start:dev
```

`SEED_DEMO=true` tự tạo 20 hồ sơ demo và tài khoản:

- Email: `demo@ptit.edu.vn`
- OTP: `123456`
- Mật khẩu (nếu đăng nhập mật khẩu): `Fwp@123456`

## Endpoint chính

| Method | Path | Mô tả |
|---|---|---|
| POST | `/auth/register` | Đăng ký email + mật khẩu |
| POST | `/auth/login` | Đăng nhập mật khẩu |
| POST | `/auth/otp/verify` | Xác thực OTP demo |
| POST | `/auth/demo` | Tạo/đăng nhập tài khoản demo |
| GET | `/auth/me` | Người dùng + hồ sơ hiện tại |
| GET/PUT | `/profiles/me` | Xem/cập nhật hồ sơ |
| GET | `/profiles/discover` | Tìm kiếm, lọc mode/GPA/khoa/campus/khoảng cách |
| GET | `/profiles/incoming-likes` | Những người đã thích mình |
| GET | `/profiles/nearby` | Hồ sơ trong bán kính (lat/lng/radiusKm) |
| GET | `/profiles/:id` | Chi tiết hồ sơ |
| POST | `/swipes` | LIKE / PASS / SUPER_LIKE, tự tạo match hai chiều |
| GET | `/matches` | Danh sách match còn hoạt động |
| GET | `/matches/:id` | Chi tiết match, trạng thái reveal |
| POST | `/matches/:id/reveal` | Bật/tắt đồng ý mở ảnh |
| POST | `/matches/:id/unmatch` | Hủy match |
| POST | `/matches/:id/report` | Báo cáo kèm lý do |
| POST | `/users/:id/block` | Chặn người dùng |
| GET/POST | `/matches/:id/messages` | Lịch sử và gửi tin nhắn |
| GET | `/notifications` | Thông báo + số chưa đọc |
| POST | `/notifications/read` | Đánh dấu đã đọc |
| GET/POST | `/campus/posts` | Feed ẩn danh / tạo bài viết (tab `for-you`, `saved`) |
| GET/DELETE | `/campus/posts/:id` | Chi tiết / xóa bài của mình |
| POST/DELETE | `/campus/posts/:id/like` | Thích / bỏ thích |
| POST/DELETE | `/campus/posts/:id/bookmark` | Lưu / bỏ lưu |
| GET/POST | `/campus/posts/:id/comments` | Danh sách / thêm bình luận |
| DELETE | `/campus/posts/:id/comments/:commentId` | Xóa bình luận của mình |
| POST | `/campus/posts/:id/report` | Báo cáo bài viết |
| GET | `/campus/authors/:token` | Hồ sơ ẩn danh tối giản |
| POST | `/campus/authors/:token/swipes` | Like/Pass ẩn danh, dùng match logic hiện có |
| GET | `/campus/media/:file` | Ảnh Campus công khai (không cần JWT) |

Mọi endpoint (trừ auth, health và `/campus/media/:file`) dùng header
`Authorization: Bearer <token>`.

## Ghi chú kỹ thuật

- TypeORM `synchronize=true` cho demo; production nên dùng migration.
- Campus Feed có migration riêng: `npm run migration:run`. Nếu vẫn giữ
  `DB_SYNCHRONIZE=true`, TypeORM sẽ tự tạo schema Campus khi khởi động; không nên
  chạy đồng thời migration trên cùng database đã được synchronize.
- Trường `location` là `geometry(Point,4326)`; extension PostGIS được tạo bởi
  `docker/init.sql`.
- Khoảng cách dùng Haversine trong service để API chạy được cả khi client chưa có
  PostGIS query chuyên biệt; có thể thay bằng `ST_DWithin` khi cần tối ưu.
- Ảnh đại diện trong demo là seed/avatar phía client; backend chỉ giữ metadata.
- Ảnh Campus nhận base64 data URL, lưu file dưới `CAMPUS_UPLOAD_DIR` (mặc định
  `uploads/campus`), không lưu blob trong PostgreSQL. Giới hạn mặc định 4 MB/ảnh.
- Campus API không trả `userId`, profile id, tên thật hay trường học. Bài
  `FULLY_ANONYMOUS` không có author token; chỉ bài `DISCOVERABLE` mới trả opaque
  token cho hồ sơ tối giản và Swipe.
- Swipe Campus ẩn danh tạo match thật qua `SocialService`, nhưng notification
  không chứa `profileId` và không lộ tên thật.
- Chạy test privacy/match: `npm run test:campus`.
- `npm run build` đã được kiểm tra; runtime đầy đủ cần PostgreSQL đang chạy.
