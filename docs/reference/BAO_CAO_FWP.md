# Báo cáo đề xuất Bài tập lớn
## Friend With PTIT (FwP) — Ứng dụng hẹn hò/kết bạn dành cho sinh viên

---

## 1. Mô tả tóm tắt ứng dụng

### 1.1. Mục đích
FwP (Friend With PTIT) là ứng dụng di động giúp sinh viên (khởi điểm là sinh viên PTIT, có thể mở rộng liên trường sau này) kết nối, làm quen, hẹn hò hoặc tìm bạn học dựa trên trường/khoa và mức độ tương đồng về học lực (GPA). Ứng dụng giải quyết vấn đề: sinh viên khó làm quen người mới ngoài lớp/CLB của mình, đồng thời tạo môi trường kết bạn **an toàn** (xác thực bằng email trường) và **giảm áp lực lệch trình độ học tập** thông qua cơ chế ghép đôi theo GPA.

### 1.2. Đối tượng người dùng
Sinh viên đại học đã xác thực bằng email trường (`@ptit.edu.vn` ở giai đoạn đầu).

### 1.3. Tính năng chính

| # | Tính năng | Mô tả |
|---|-----------|-------|
| 1 | Đăng ký / Xác thực | Đăng ký bằng email trường (OTP/verify link), đảm bảo người dùng là sinh viên thật |
| 2 | Hồ sơ cá nhân | Ảnh, giới thiệu bản thân, sở thích, **trường** (chọn từ danh sách có tìm kiếm/autocomplete), khoa, năm học, **GPA** (có thể chọn ẩn/hiện số cụ thể, chỉ hiện "tầng") |
| 3 | Lướt kiểu Tinder (ẩn danh từ đầu) | Vuốt phải (thích) / vuốt trái (bỏ qua) dựa trên **ảnh làm mờ (blur)** + thông tin phi hình ảnh (trường, khoa, tầng GPA, sở thích, bio); match khi cả 2 bên cùng thích nhau |
| 4 | **Matching theo GPA ("gió tầng nào, mây tầng đấy")** | Thuật toán ưu tiên gợi ý những người có **GPA cùng khoảng (bucket)**: ví dụ Giỏi (≥3.6), Khá (3.2–3.59), Trung bình khá (2.5–3.19), Trung bình (<2.5). Người dùng có thể tùy chỉnh nới rộng phạm vi tầng muốn gặp |
| 5 | **Ghép đôi theo vị trí (Map/GPS)** | Lấy vị trí hiện tại của người dùng (GPS), tính khoảng cách và ưu tiên gợi ý người ở gần (bán kính tùy chỉnh, vd 1–50km); có thể xem match trên bản đồ |
| 6 | Chat ẩn danh, ảnh rõ dần theo độ hợp | Sau khi match, 2 người chat với **danh tính ẩn (biệt danh, ảnh vẫn mờ)**; ảnh **rõ dần tự động** khi độ hợp AI tính được tăng lên, không cần bấm nút xin phép (chi tiết ở mục 6.4) |
| 7 | **Compatibility Reconstruction (mô hình 3D điều khiển độ rõ ảnh)** | Mỗi tin nhắn trong giai đoạn chat ẩn danh được AI phân tích và xem như 1 "lát cắt" dữ liệu; hệ thống cộng dồn dần thành một **mô hình độ hợp 3D**, giá trị này **trực tiếp điều khiển mức độ mờ của ảnh thật** — không chỉ để xem, mà là cơ chế vận hành reveal (chi tiết kỹ thuật ở mục 6.4) |
| 8 | **Cảnh báo hội thoại hời hợt / sắp "toang"** | AI theo dõi nhịp độ, độ dài, thời gian phản hồi và nội dung tin nhắn để phát hiện hội thoại đang nhạt dần/có nguy cơ bị bỏ lửng, chủ động gợi ý câu hỏi hoặc cảnh báo nhẹ nhàng cho người dùng (chi tiết ở mục 6.5) |
| 9 | Bộ lọc tìm kiếm | Theo trường, khoa, năm sinh, khoảng cách địa lý (bán kính trên map), khoảng GPA |
| 10 | Báo cáo & chặn người dùng | Report/block, kiểm duyệt nội dung vi phạm |
| 11 | Thông báo đẩy | Báo có match mới, tin nhắn mới |

---

## 2. Công nghệ dự kiến sử dụng

| Thành phần | Công nghệ |
|---|---|
| Mobile app (Frontend) | **Android native — Kotlin + Jetpack Compose**, phát triển trên **Android Studio** |
| Kiến trúc app | MVVM (ViewModel + StateFlow) + Repository pattern |
| Networking | Retrofit + OkHttp (REST), OkHttp WebSocket hoặc Socket.IO client (chat realtime) |
| Local cache/offline | Room (SQLite) |
| Ảnh trong app | Coil (tải & cache ảnh) |
| Chọn trường (autocomplete) | Danh sách trường lưu sẵn trong DB, tìm kiếm bằng ô search có gợi ý (autocomplete) trên Compose — hoặc AutocompleteTextView; không cần API ngoài vì phạm vi giới hạn các trường đã đăng ký |
| Vị trí & bản đồ | **Google Maps SDK for Android** (hiển thị bản đồ, vị trí match) + **FusedLocationProviderClient** (Google Play Services Location — lấy GPS người dùng) |
| Backend API | Node.js (NestJS) hoặc Spring Boot — REST API |
| Realtime chat | WebSocket (Socket.IO) |
| Truy vấn khoảng cách (server) | PostgreSQL + PostGIS (hoặc extension `earthdistance`/`cube`) để tính khoảng cách & lọc người dùng theo bán kính hiệu quả |
| Cơ sở dữ liệu (server) | PostgreSQL (user, match, profile, tọa độ GPS) + Redis (cache, session, hàng đợi realtime) |
| Xác thực | JWT + xác minh email trường (OTP) |
| Lưu trữ ảnh (server) | Cloudinary hoặc AWS S3 |
| Thông báo đẩy | Firebase Cloud Messaging (FCM) |
| Triển khai | Docker; hosting demo trên Render/Railway hoặc AWS/GCP; build/test app qua Android Studio + Emulator/thiết bị thật |
| Thuật toán ghép đôi | Rule-based filter + scoring (bucket GPA, sở thích chung, khoảng cách); có thể mở rộng học máy (ML) ở giai đoạn sau |

---

## 3. Ứng dụng tham khảo & điểm khác biệt/mới

### 3.1. Đã tham khảo
- **Tinder** — cơ chế lướt (swipe) và match cơ bản
- **Bumble** — luồng match, giới hạn thời gian nhắn tin đầu
- **DateMySchool / Fressh** — mô hình dating app giới hạn trong phạm vi sinh viên/trường học

### 3.2. Điểm khác biệt / mới của FwP
1. **Giới hạn & xác thực theo trường đại học** (email `@ptit.edu.vn`) → đúng đối tượng, an toàn hơn app hẹn hò đại trà.
2. **Cơ chế ghép đôi theo GPA ("gió tầng nào, mây tầng đấy")** — yếu tố độc đáo chưa phổ biến ở các app hẹn hò hiện có: giúp giảm áp lực lệch trình độ học tập, đồng thời tạo yếu tố thú vị/gamification gắn với đời sống học đường.
3. **Ẩn danh xuyên suốt từ lúc lướt đến lúc chat** (ảnh mờ ngay từ màn hình Swipe, rõ dần theo độ hợp AI đo được qua hội thoại, không lộ mặt ngay từ đầu như Tinder) — giảm áp lực ngoại hình/phán xét ban đầu thực sự, khuyến khích trò chuyện vì tính cách trước khi biết mặt.
4. **Gắn với ngữ cảnh sinh viên** — lọc theo khoa/lớp/CLB, có thể tích hợp sự kiện trường trong tương lai.

---

## 4. Luồng hoạt động (User Flow)

### 4.1. Luồng đăng ký & xác thực

```
Mở app
  │
  ▼
Nhập email trường (@ptit.edu.vn)
  │
  ▼
Hệ thống gửi OTP / link xác thực
  │
  ▼
Người dùng nhập OTP ─── sai ──▶ Yêu cầu nhập lại (tối đa N lần)
  │ đúng
  ▼
Tạo mật khẩu / đăng nhập bằng OTP
  │
  ▼
Chuyển sang màn hình Tạo hồ sơ
```

### 4.2. Luồng tạo hồ sơ (bao gồm chọn trường, xin quyền vị trí & nhập GPA)

```
Nhập thông tin cơ bản (tên hiển thị, ngày sinh, giới tính, ảnh)
  │
  ▼
Chọn Trường
  - Gõ ô tìm kiếm ──▶ Gợi ý (autocomplete) danh sách trường khớp
  - Chọn 1 trường từ danh sách
  │
  ▼
Chọn Khoa / Năm học (danh sách phụ thuộc theo Trường đã chọn)
  │
  ▼
Nhập GPA hiện tại
  │
  ▼
Hệ thống xếp vào "tầng GPA" tương ứng
  (Giỏi ≥3.6 | Khá 3.2–3.59 | TB khá 2.5–3.19 | TB <2.5)
  │
  ▼
Chọn hiển thị GPA công khai hay chỉ hiện "tầng"
  │
  ▼
Chọn sở thích / mô tả bản thân
  │
  ▼
Xin quyền truy cập Vị trí (Location Permission)
  │
  ├── Từ chối ──▶ Không dùng được gợi ý theo khoảng cách
  │                (vẫn match theo Trường/GPA bình thường)
  │
  └── Đồng ý
        │
        ▼
      Lấy tọa độ GPS hiện tại (FusedLocationProviderClient)
        │
        ▼
      Gửi tọa độ lên server, lưu kèm hồ sơ
  │
  ▼
Thiết lập tiêu chí tìm kiếm (bán kính khoảng cách trên map, độ tuổi, phạm vi tầng GPA muốn gặp)
  │
  ▼
Hoàn tất hồ sơ → vào màn hình Lướt (Swipe)
```

### 4.3. Luồng lướt & ghép đôi (Swipe & Match)

```
Vào màn hình Swipe
  │
  ▼
Backend lọc danh sách ứng viên theo:
  - Trường/khoa (nếu bật lọc)
  - Tầng GPA phù hợp (mặc định: cùng tầng, có thể nới theo cài đặt)
  - Khoảng cách (truy vấn geo qua PostGIS trong bán kính đã chọn), độ tuổi, giới tính mong muốn
  - Sắp xếp ưu tiên: gần nhất trước (nếu đã cấp quyền vị trí)
  │
  ▼
Hiển thị thẻ hồ sơ (card) lần lượt:
  - Ảnh đại diện ở trạng thái LÀM MỜ (chưa lộ mặt)
  - Thông tin phi hình ảnh: trường, khoa, tầng GPA, sở thích, bio, khoảng cách ước tính
  │
  ├── Vuốt trái / bấm "Bỏ qua" ──▶ Chuyển thẻ tiếp theo
  │
  └── Vuốt phải / bấm "Thích"
        │
        ▼
      Lưu lượt "Like" vào hệ thống
        │
        ▼
      Đối phương đã Like mình trước đó? 
        │
   ┌────┴────┐
  Có          Không
   │            │
   ▼            ▼
 MATCH!      Chờ đối phương lướt tới lượt
   │
   ▼
 Thông báo đẩy "Bạn có match mới!" cho cả 2 bên
   │
   ▼
 Tạo phòng Chat ẩn danh
```

### 4.4. Luồng chat ẩn danh, ảnh rõ dần theo độ hợp & cảnh báo hội thoại nhạt

```
Vào phòng chat sau khi match
  │
  ▼
Hiển thị: biệt danh ẩn danh + ảnh vẫn LÀM MỜ (giống lúc Swipe)
  │
  ▼
Hai bên nhắn tin qua lại (realtime qua WebSocket)
  │
  ▼
Sau mỗi N tin nhắn: gửi nội dung sang AI Service phân tích song song 2 việc
  │
  ├──▶ [A] Compatibility Engine (mục 6.4)
  │        - Trích đặc trưng NLP (tính cách, cảm xúc, chủ đề) từ "lát" tin nhắn này
  │        - Cộng dồn vào mô hình độ hợp V_t
  │        - Map V_t → % độ rõ ảnh (0–100%)
  │        │
  │        ▼
  │      Cập nhật độ mờ ảnh đại diện theo % mới
  │        - V_t thấp  → ảnh vẫn mờ nhiều
  │        - V_t tăng dần → ảnh nét dần theo thời gian thực
  │        - V_t đạt 100% → ảnh hiện rõ hoàn toàn (reveal tự nhiên,
  │          KHÔNG cần bấm nút xin phép — khác luồng Bumble/Tinder)
  │
  └──▶ [B] Conversation Health Monitor (mục 6.5)
           - Phân tích nhịp độ: độ dài tin nhắn, thời gian phản hồi,
             tỉ lệ câu hỏi/câu trả lời cột lốc, độ lặp chủ đề
           │
           ▼
         Phân loại trạng thái hội thoại: Khỏe mạnh / Hời hợt / Có nguy cơ "toang"
           │
           ├── Khỏe mạnh ──▶ Không làm gì, tiếp tục theo dõi
           │
           └── Hời hợt / Nguy cơ toang
                 │
                 ▼
               Hiện gợi ý nhẹ nhàng cho người dùng đang có nguy cơ bỏ lửng
               (vd: gợi ý chủ đề chung, gợi ý câu hỏi mở, nhắc "đã 2 ngày
               chưa trả lời" — không làm phiền, có thể tắt trong Settings)
  │
  ▼
Cả 2 bên tiếp tục chat, ảnh tiếp tục rõ dần / hội thoại tiếp tục được theo dõi
  │
  ▼
Khi ảnh đã hiện rõ hoàn toàn (100%): tên thật cũng được hiện kèm
  │
  ▼
Có thể tiếp tục trò chuyện, trao đổi liên hệ ngoài app
```

### 4.5. Luồng báo cáo / chặn người dùng

```
Trong màn hình chat hoặc hồ sơ
  │
  ▼
Bấm "Báo cáo" hoặc "Chặn"
  │
  ▼
Chọn lý do (spam, quấy rối, thông tin giả, không phù hợp,...)
  │
  ▼
Gửi báo cáo lên hệ thống kiểm duyệt (admin/backend)
  │
  ▼
Người bị báo cáo bị ẩn khỏi danh sách Swipe của người báo cáo
  │
  ▼
Đội kiểm duyệt xem xét ─── vi phạm ──▶ Khóa/tạm ngưng tài khoản vi phạm
```

---

## 5. Kiến trúc tổng quan (tóm tắt)

```
[Android App: Kotlin + Jetpack Compose (MVVM)]
            │  REST API (HTTPS, Retrofit) + WebSocket
            ▼
[Backend: NestJS/Spring Boot]
     │              │                │
     ▼              ▼                ▼
[PostgreSQL]     [Redis]        [FCM Push]
(user, profile,  (cache,        (thông báo
match, message)  session,        match/chat)
                  realtime queue)
            │
            ▼
     [Cloudinary/S3]
     (lưu trữ ảnh)
```

---

## 6. Mô hình học máy làm lõi gợi ý ghép đôi (ML Matching & Ranking)

### 6.1. Vì sao cần ML thay vì chỉ rule-based?
Bộ lọc rule-based (trường, tầng GPA, khoảng cách) chỉ trả lời **"ai đủ điều kiện để hiện ra"**, nhưng không biết **"trong số đủ điều kiện, nên hiện ai trước"**. Vì vậy kiến trúc đề xuất là **lai (hybrid)**:

```
Ứng viên toàn hệ thống
      │
      ▼
[1] Rule-based hard filter  ← Trường/khoa, tầng GPA, bán kính vị trí, độ tuổi
      │  (danh sách ứng viên hợp lệ, có thể vẫn còn hàng trăm người)
      ▼
[2] ML Ranking Model  ← chấm điểm xác suất "sẽ match" / độ tương thích cho từng cặp
      │  (sắp xếp giảm dần theo điểm)
      ▼
Hiển thị thứ tự thẻ trong màn hình Swipe
```

Mô hình ML đóng vai trò **re-ranking**: input là cặp (hồ sơ A, hồ sơ B) + đặc trưng (sở thích chung, khoảng cách, chênh lệch tuổi, chênh lệch tầng GPA...), output là **xác suất match** (bài toán phân loại nhị phân) hoặc **điểm tương thích** (regression/similarity score). Đây là cách các app thật (Tinder's ELO/Gale–Shapley, Hinge's Gale-Shapley) và các paper học thuật vẫn dùng: rule để đảm bảo constraint cứng, ML để tối ưu trải nghiệm.

### 6.2. Bộ dataset đề xuất (≥ 2 bộ, dùng kết hợp)

| # | Dataset | Nguồn | Vai trò trong huấn luyện |
|---|---------|-------|---------------------------|
| 1 | **Speed Dating Experiment** (Columbia Business School, ~8,378 dòng, có trên Kaggle: `annavictoria/speed-dating-experiment`) | Dữ liệu thật từ các buổi speed-dating: mỗi dòng là 1 lượt gặp giữa 2 người, có nhãn `match` (0/1), điểm đánh giá đối phương (hấp dẫn, chân thành, thông minh, tham vọng, sở thích chung), tuổi, chênh lệch tuổi, sở thích cá nhân | **Dataset chính** để huấn luyện mô hình phân loại "khả năng match" (binary classification) — vì có nhãn match thật, phù hợp nhất về bản chất bài toán |
| 2 | **OkCupid Profiles Dataset** (~60,000 hồ sơ, Kaggle: `andrewmvd/okcupid-profiles`) | Hồ sơ người dùng thật từ OkCupid: tuổi, trình độ học vấn (education — dùng làm proxy tương tự "tầng học lực"), sở thích, lối sống, essay giới thiệu bản thân | Dùng để **học biểu diễn hồ sơ (profile embedding)** bằng NLP đơn giản (TF-IDF/embedding trên phần giới thiệu + sở thích) và để **mô phỏng phân bố đặc trưng người dùng thực tế** khi không đủ dữ liệu người dùng thật của FwP trong giai đoạn đầu |
| 3 | **Libimseti.cz Dating Ratings Dataset** (~17 triệu lượt đánh giá, ~220,000 người dùng — dataset công khai cho nghiên cứu recommender system) | Ma trận đánh giá (rating 1–10) giữa các cặp người dùng trên mạng hẹn hò Czech | Dùng để huấn luyện thành phần **Collaborative Filtering** (Matrix Factorization) mô hình hóa "gu" chung của người dùng tương tự, bổ trợ cho mô hình content-based ở trên (giải quyết cold-start ngược lại) |

> **Lưu ý quan trọng:** không có dataset công khai nào gắn nhãn match theo đúng cơ chế "tầng GPA" đặc thù của FwP (vì đây là ý tưởng riêng của nhóm). Do đó nhóm sẽ:
> 1. Dùng 3 dataset trên để huấn luyện mô hình **tương thích tổng quát** (sở thích, tuổi, lối sống…).
> 2. **Tự sinh dữ liệu tổng hợp (synthetic data augmentation)** mô phỏng hành vi swipe có gắn tầng GPA, dựa trên giả định nghiên cứu ("người có tầng học lực gần nhau có xu hướng match cao hơn"), để fine-tune mô hình cho đúng đặc thù bài toán.
> 3. Khi app có dữ liệu người dùng thật (sau khi launch), retrain mô hình định kỳ bằng dữ liệu swipe/match thật để thay thế dần phần synthetic.

### 6.3. Công nghệ huấn luyện & phục vụ mô hình

| Thành phần | Công nghệ |
|---|---|
| Huấn luyện mô hình | Python — scikit-learn (Logistic Regression/Gradient Boosting: XGBoost/LightGBM) cho bài toán match classification |
| Collaborative filtering | Thư viện `surprise` hoặc `implicit` (Matrix Factorization trên dataset Libimseti) |
| Embedding sở thích/bio | TF-IDF hoặc Sentence-BERT (đa ngôn ngữ) cho phần văn bản giới thiệu (OkCupid) |
| Phục vụ mô hình (serving) | Đóng gói mô hình bằng FastAPI (Python) thành một microservice riêng, backend Node/Spring gọi sang qua REST khi cần xếp hạng ứng viên |
| Theo dõi & tái huấn luyện | Lưu log lượt like/pass/match thật của FwP vào PostgreSQL → định kỳ export, retrain, đánh giá lại (offline evaluation: AUC, precision@k) |

### 6.4. Compatibility Reconstruction — mô hình độ hợp điều khiển cơ chế unblur ảnh

**Ý tưởng cốt lõi:** lấy đúng nguyên lý *slice-based 3D reconstruction* (nhiều lát dữ liệu rời rạc → dựng dần một khối 3D hoàn chỉnh, như tái tạo mô hình 3D từ các lát ảnh cắt lớp y khoa), nhưng thay "lát cắt" bằng đơn vị dữ liệu tự nhiên của app hẹn hò: **mỗi tin nhắn trong giai đoạn chat ẩn danh**.

**Khác với bản nháp trước:** khối 3D không chỉ để xem cho vui — nó là **cơ chế vận hành thật** của tính năng ẩn danh: giá trị độ hợp tính được **trực tiếp điều khiển % độ rõ của ảnh đại diện thật** (ảnh mờ dần rõ ra theo thời gian thực khi chat, thay vì người dùng tự bấm nút "xin reveal"). Điều này giải quyết đúng mâu thuẫn đã nêu ở lượt trao đổi trước: ảnh bị mờ **ngay từ màn hình Swipe** (mục 4.3) nên "ẩn danh" xuyên suốt và có ý nghĩa thật, không mâu thuẫn với cơ chế lướt kiểu Tinder. Khối 3D vẫn hiển thị như một lớp trực quan phụ (để người dùng thấy "độ hợp" đang tăng ở khía cạnh nào — tính cách/cảm xúc/sở thích), nhưng chức năng chính của nó là **tham số đầu vào cho công thức tính % unblur**.

**Pipeline kỹ thuật:**

```
Tin nhắn mới (mỗi N tin nhắn tích lũy)
      │
      ▼
[1] Trích đặc trưng bằng NLP ("lát cắt" dữ liệu)
    - Personality inference: dự đoán 5 trục Big Five (OCEAN)
      từ văn bản (mô hình fine-tune trên bộ dữ liệu văn bản gán nhãn tính cách,
      vd Essays/PANDORA/MyPersonality — dataset học thuật cho Big Five từ text)
    - Sentiment/emotion analysis: cảm xúc trong hội thoại (tích cực/tiêu cực, mức nhiệt tình)
    - Topic/interest extraction: chủ đề đang nói tới có trùng sở thích khai báo không
      ▼
    Vector đặc trưng v_i ∈ R^d cho "lát" thứ i
      │
      ▼
[2] Cộng dồn có trọng số theo thời gian (giống tích lũy lát cắt theo độ sâu ảnh)
    V_t = Σ (w_i · v_i)   — tin nhắn gần đây có trọng số cao hơn (decay theo thời gian)
      │
      ▼
[3] Giảm chiều & ánh xạ sang không gian 3D
    - UMAP/t-SNE (hoặc PCA cho baseline) chiếu V_t từ R^d xuống R^3
    - Mỗi trục ứng với 1 nhóm đặc trưng diễn giải được
      (vd: trục X = độ hợp tính cách, trục Y = độ hợp cảm xúc, trục Z = độ hợp sở thích)
      ▼
    Toạ độ (x, y, z) + bán kính "độ đầy" (càng nhiều dữ liệu, khối càng đặc/rõ)
      │
      ▼
[4] Render tương tác trên app
    - Android: SceneView / Filament (render 3D thời gian thực, engine đồ hoạ mã nguồn mở)
    - Khối hiển thị dạng mờ khi ít dữ liệu → rõ nét, đặc, có màu sắc khi đủ dữ liệu
    - Người dùng xoay/tương tác được khối 3D ngay trong màn hình chat
      │
      ▼
[5] Ánh xạ độ hợp sang % unblur ảnh (thay vì chỉ mở khoá 1 nút)
    - blur_level = f(‖V_t‖)  — hàm đơn điệu giảm: độ hợp càng cao, độ mờ càng thấp
    - Áp dụng Gaussian blur với bán kính giảm dần lên ảnh thật ở phía client
      (ảnh gốc chỉ tải về dạng mã hoá, mức blur xử lý on-device để tránh lộ ảnh gốc qua debug/cache)
    - ‖V_t‖ đạt ngưỡng tối đa → blur_level = 0 → ảnh + tên thật hiện hoàn toàn (reveal tự nhiên)
```

**Vì sao đủ "nặng đô" về kỹ thuật:**
- Không chỉ gọi API có sẵn: cần tự huấn luyện/fine-tune mô hình personality inference trên dataset học thuật riêng, tự thiết kế công thức cộng dồn có trọng số theo thời gian, tự chọn & tinh chỉnh phép chiếu giảm chiều diễn giải được, tự thiết kế hàm ánh xạ độ hợp → % unblur.
- Có thể đánh giá định lượng: so sánh độ hợp dự đoán bởi mô hình với kết quả thật (2 người có tiếp tục chat lâu dài / trao đổi liên hệ hay không, dùng làm proxy label), đo bằng correlation hoặc AUC — giống cách nhóm kia đánh giá độ chính xác tái tạo 3D so với ảnh gốc.
- Có chức năng vận hành thật (điều khiển unblur), không phải tính năng trang trí — gắn trực tiếp vào trải nghiệm cốt lõi (ẩn danh xuyên suốt từ Swipe đến Chat).

### 6.5. Conversation Health Monitor — phát hiện hội thoại hời hợt / sắp "toang"

**Bài toán:** nhiều cuộc match trên các app hẹn hò chết yểu vì hội thoại nhạt dần mà không ai nhận ra để cứu kịp thời (ghosting). FwP dùng AI theo dõi "sức khỏe" hội thoại theo thời gian thực và chủ động cảnh báo/gợi ý trước khi cuộc trò chuyện bị bỏ lửng hẳn.

**Đặc trưng đầu vào (trích theo cửa sổ trượt, vd 10 tin nhắn gần nhất):**
- **Độ trễ phản hồi** (response latency): thời gian trung bình giữa tin nhắn và câu trả lời, xu hướng tăng dần theo thời gian
- **Độ dài tin nhắn** (message length trend): tin nhắn ngắn dần (từ câu dài → "ok", "ừ", emoji) là dấu hiệu giảm hứng thú
- **Tỉ lệ câu hỏi mở** (open-question ratio): hội thoại khỏe mạnh có tỉ lệ câu hỏi qua lại cao; hội thoại hời hợt thường chỉ có 1 bên hỏi, bên kia chỉ trả lời cụt
- **Độ lệch thời gian trả lời giữa 2 bên** (asymmetry): 1 bên trả lời nhanh, 1 bên trả lời rất chậm/thưa dần
- **Sentiment trend**: cảm xúc tích cực giảm dần theo thời gian (từ mục 6.4 tái sử dụng)

**Mô hình:** bài toán **phân loại chuỗi thời gian** (time-series classification) 3 lớp — *Khỏe mạnh / Hời hợt / Nguy cơ toang* — có thể dùng mô hình nhẹ (Gradient Boosting trên đặc trưng thống kê theo cửa sổ trượt, hoặc LSTM/GRU nhỏ nếu muốn học trực tiếp từ chuỗi embedding tin nhắn). Vì không có dataset dán nhãn sẵn "hội thoại sắp toang" cho dating app, nhóm dùng **proxy label từ chính hành vi thật**: hội thoại được gán nhãn "toang" nếu sau X ngày không có tin nhắn mới và chưa từng reveal — dùng để huấn luyện có giám sát trên dữ liệu tích lũy của FwP theo thời gian (khởi động bằng rule-based threshold ở giai đoạn đầu khi chưa đủ dữ liệu).

**Hành động khi phát hiện rủi ro:** không chen ngang cuộc trò chuyện của 2 người dùng — chỉ hiện gợi ý **riêng tư** (chỉ người đang có dấu hiệu rời đi nhìn thấy): gợi ý chủ đề chung dựa trên sở thích/trường/khoa 2 bên, gợi ý câu hỏi mở, hoặc nhắc nhẹ "đã lâu chưa trả lời". Người dùng có thể tắt tính năng này trong Cài đặt.

---

## 7. Định hướng phát triển tiếp theo (ngoài phạm vi BTL)
- Mở rộng xác thực cho nhiều trường đại học khác (liên trường)
- Cải tiến mô hình ML sang deep learning (two-tower embedding model) khi có đủ dữ liệu thật
- Tích hợp sự kiện/CLB của trường để gợi ý gặp gỡ offline
- Video call trong app sau khi reveal danh tính
