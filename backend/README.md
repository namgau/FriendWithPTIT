# FwP Backend (NestJS + PostgreSQL/PostGIS)

API cho FriendWithPTIT: xác thực OTP demo, hồ sơ sinh viên, khám phá theo bộ lọc,
swipe/match, chat, thông báo, báo cáo và chặn.

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

Mọi endpoint (trừ auth và health) dùng header `Authorization: Bearer <token>`.

## Ghi chú kỹ thuật

- TypeORM `synchronize=true` cho demo; production nên dùng migration.
- Trường `location` là `geometry(Point,4326)`; extension PostGIS được tạo bởi
  `docker/init.sql`.
- Khoảng cách dùng Haversine trong service để API chạy được cả khi client chưa có
  PostGIS query chuyên biệt; có thể thay bằng `ST_DWithin` khi cần tối ưu.
- Ảnh đại diện trong demo là seed/avatar phía client; backend chỉ giữ metadata.
- `npm run build` đã được kiểm tra; runtime đầy đủ cần PostgreSQL đang chạy.
