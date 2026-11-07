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

## Hợp đồng Social/Chat — Phase 3B-III.A

### Gửi tin nhắn và retry

`POST /matches/:id/messages` nhận:

```json
{
  "clientMessageId": "930d2775-6bde-4c54-8174-6bab04994b9f",
  "text": "Xin chào",
  "type": "TEXT",
  "meta": null
}
```

- Client tạo UUID **một lần cho mỗi lần gửi logic**, giữ nguyên UUID/payload khi
  timeout hoặc retry. Unique key là `(matchId, senderId, clientMessageId)`.
- Retry cùng nội dung trả `201` với message ID/thời gian gốc; không thêm message,
  notification hoặc cập nhật lại thời gian tin cuối. Text được trim; mặc định
  `type=TEXT`, `meta=null`; thứ tự key object trong meta không ảnh hưởng so sánh.
- Tái dùng UUID với text/type/meta khác trả `409`. Gửi lại sau unmatch/block hoặc
  từ tài khoản không thuộc match vẫn bị từ chối, không bỏ qua authorization.
- Message/notification/thời gian tin cuối commit trong cùng transaction. Gửi đồng
  thời trong một conversation được tuần tự hóa bằng row lock và unique index DB.
- `clientMessageId` trong response/history chỉ trả cho chính người gửi; đối phương
  không nhận UUID này. Quy tắc che sender trong match ẩn danh được giữ nguyên.
- Client cũ có thể bỏ `clientMessageId` (hoặc dùng null); vẫn gửi được nhưng **không
  được bảo đảm chống gửi trùng khi retry**. Android mới phải luôn cấp UUID.

### Phân trang lịch sử

`GET /matches/:id/messages?limit=50&before=<cursor>`:

- `limit` là số nguyên 1..100, mặc định 50; sai định dạng trả `400`.
- Trang lấy các tin mới nhất trước cursor và trả `items` theo thứ tự tăng dần
  `(createdAt, id)`, phù hợp hiển thị chronological. Khi ghép lịch sử, prepend cả
  trang cũ; không đảo lại hoặc chỉ sắp xếp theo timestamp phía client.
- Response giữ `{ total, hasMore, nextBefore, items }`. `nextBefore` nay là chuỗi
  opaque `v1.…` hoặc null, chứa match ID + timestamp đầy đủ microsecond + message
  UUID. Client phải lưu/truyền lại nguyên chuỗi (URL encode), không parse thành Date.
- Cursor sai định dạng/khác conversation trả `400`. Cursor không phải token cấp
  quyền: server vẫn kiểm tra thành viên và match đang hoạt động.
- ISO timestamp `before` kiểu cũ vẫn được chấp nhận, nhưng không có secondary ID
  nên không bảo đảm đầy đủ ở ranh giới cùng timestamp. Chỉ cursor mới có bảo đảm đó.
- Tin mới ở đầu lịch sử được lấy bằng refresh không cursor. Việc tải trang cũ
  không phải snapshot toàn bộ cuộc trò chuyện tại một thời điểm.

### Incoming likes và migration

- `GET /profiles/incoming-likes` loại block theo cả hai chiều phía server; tiếp tục
  ẩn lượt thích Campus anonymous, PASS và người viewer đã swipe. `total` khớp items.
- Migration mới: `1762600000000-SocialChatContracts.ts`, thêm nullable UUID và hai
  index; không sửa/xóa message cũ. Chạy migration lên DB đích trước khi dùng code
  mới nếu `DB_SYNCHRONIZE=false`; không chạy trên DB dev chỉ để thử nghiệm.
- Test hữu hạn: `npm run test:social-chat`. Đặt `CAMPUS_TEST_ADMIN_URL` tới PostgreSQL
  localhost riêng có quyền tạo DB và PostGIS; test tạo/xóa DB `social_chat_it_*`,
  mở HTTP trên cổng loopback ngẫu nhiên và tự đóng sau khi chạy. Không đặt biến này
  thì các test DB/HTTP sẽ skip; cần có DB cô lập để xác minh đầy đủ.

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
