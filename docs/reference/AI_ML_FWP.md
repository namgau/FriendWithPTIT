# Tài liệu kỹ thuật: Lõi AI/Machine Learning của Friend With PTIT (FwP)

> Tài liệu này tách riêng toàn bộ phần huấn luyện mô hình AI/ML khỏi báo cáo tổng quan (`BAO_CAO_FWP.md`), đi sâu vào: mục đích từng mô hình, dataset, pipeline huấn luyện, luồng hoạt động lúc chạy thực tế (inference), và toàn bộ ý tưởng kỹ thuật liên quan.

---

## Mục lục
1. [Tổng quan kiến trúc AI](#1-tổng-quan-kiến-trúc-ai)
2. [Module 1 — ML Ranking Model (gợi ý thứ tự Swipe)](#2-module-1--ml-ranking-model-gợi-ý-thứ-tự-swipe)
3. [Module 2 — Compatibility Reconstruction (điều khiển unblur ảnh)](#3-module-2--compatibility-reconstruction-điều-khiển-unblur-ảnh)
4. [Module 3 — Conversation Health Monitor (cảnh báo hội thoại nhạt)](#4-module-3--conversation-health-monitor-cảnh-báo-hội-thoại-nhạt)
5. [Hạ tầng phục vụ mô hình (ML Serving)](#5-hạ-tầng-phục-vụ-mô-hình-ml-serving)
6. [Vòng đời dữ liệu & tái huấn luyện](#6-vòng-đời-dữ-liệu--tái-huấn-luyện)
7. [Đánh giá mô hình (Evaluation)](#7-đánh-giá-mô-hình-evaluation)
8. [Rủi ro & hạn chế](#8-rủi-ro--hạn-chế)

---

## 1. Tổng quan kiến trúc AI

FwP có **3 module AI độc lập nhưng dùng chung 1 hạ tầng phục vụ**, mỗi module giải quyết một bài toán khác nhau trong vòng đời một lượt match:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         VÒNG ĐỜI 1 LƯỢT MATCH                        │
└─────────────────────────────────────────────────────────────────────┘

 Giai đoạn SWIPE                Giai đoạn CHAT ẨN DANH
 ────────────────                ─────────────────────────────────────
 [Module 1]                      [Module 2]           [Module 3]
 ML Ranking Model                Compatibility         Conversation
 "nên hiện ai trước"             Reconstruction        Health Monitor
                                  "ảnh rõ dần           "hội thoại có
                                  thế nào"              đang nhạt không"
        │                              │                      │
        ▼                              ▼                      ▼
  Sắp xếp thứ tự thẻ           Điều khiển % unblur     Gợi ý cứu hội thoại
  trong màn hình Swipe         ảnh đại diện thật        trước khi bị "toang"
```

Cả 3 module đều được đóng gói thành **microservice Python riêng** (gọi chung là **AI Service**), giao tiếp với backend chính (NestJS/Spring Boot) qua REST nội bộ. Backend không tự làm ML — chỉ gọi sang AI Service và nhận kết quả.

```
[Android App]
      │ REST/WebSocket
      ▼
[Backend chính: NestJS/Spring Boot]
      │ REST nội bộ (gRPC/HTTP)
      ▼
[AI Service: FastAPI (Python)]
      │
      ├── Module 1: Ranking Model      (scikit-learn/XGBoost)
      ├── Module 2: Compatibility Engine (NLP + accumulation)
      └── Module 3: Health Monitor      (time-series classifier)
      │
      ▼
[Model Registry + Feature Store]
(lưu model đã train, lưu đặc trưng đã tính sẵn)
```

---

## 2. Module 1 — ML Ranking Model (gợi ý thứ tự Swipe)

### 2.1. Mục đích
Bộ lọc rule-based (trường, tầng GPA, bán kính vị trí, độ tuổi) chỉ trả lời **"ai đủ điều kiện để hiện ra"**. ML Ranking Model trả lời câu hỏi tiếp theo: **"trong số đủ điều kiện, nên hiện ai trước"** — tăng tỉ lệ match và thời gian sử dụng app.

### 2.2. Vị trí trong hệ thống (kiến trúc lai — hybrid)

```
Ứng viên toàn hệ thống
      │
      ▼
[1] Rule-based hard filter
    ← Trường/khoa, tầng GPA, bán kính vị trí (PostGIS), độ tuổi, giới tính mong muốn
      │  (danh sách ứng viên hợp lệ — có thể vẫn còn hàng trăm người)
      ▼
[2] ML Ranking Model
    ← Input: cặp đặc trưng (hồ sơ người xem, hồ sơ ứng viên)
    ← Output: điểm xác suất "sẽ match" hoặc điểm tương thích
      │  (sắp xếp giảm dần theo điểm)
      ▼
Hiển thị thứ tự thẻ trong màn hình Swipe
```

Đây là cách các app thật dùng (Tinder ELO, Hinge Gale–Shapley): **rule đảm bảo constraint cứng** (ai được phép gặp ai), **ML tối ưu trải nghiệm** (ai nên gặp trước).

### 2.3. Đặc trưng đầu vào (feature engineering)

| Nhóm đặc trưng | Ví dụ cụ thể |
|---|---|
| Đặc trưng nhân khẩu học | chênh lệch tuổi, cùng trường/khác trường, cùng khoa/khác khoa |
| Đặc trưng học lực | chênh lệch tầng GPA (0 = cùng tầng, 1 = lệch 1 tầng...) |
| Đặc trưng địa lý | khoảng cách (km) tính từ toạ độ GPS qua PostGIS |
| Đặc trưng sở thích | số lượng tag sở thích trùng nhau, cosine similarity giữa 2 vector embedding bio |
| Đặc trưng hành vi (khi đã có dữ liệu thật) | tỉ lệ like trung bình của từng người, thời điểm hoạt động gần nhất |

### 2.4. Bộ dataset dùng để huấn luyện (≥ 2 bộ, dùng kết hợp)

| # | Dataset | Nguồn | Quy mô | Vai trò |
|---|---------|-------|--------|---------|
| 1 | **Speed Dating Experiment** | Columbia Business School — Kaggle: `annavictoria/speed-dating-experiment` | ~8,378 dòng | **Dataset chính**: mỗi dòng là 1 lượt gặp giữa 2 người, có nhãn `match` (0/1) thật, điểm đánh giá đối phương (hấp dẫn, chân thành, thông minh, tham vọng, sở thích chung), tuổi, chênh lệch tuổi → huấn luyện **binary classifier** dự đoán khả năng match |
| 2 | **OkCupid Profiles Dataset** | Kaggle: `andrewmvd/okcupid-profiles` | ~60,000 hồ sơ | Hồ sơ thật: tuổi, trình độ học vấn (proxy cho "tầng học lực"), sở thích, lối sống, essay giới thiệu → dùng học **profile embedding** (TF-IDF/Sentence-BERT trên bio) và mô phỏng phân bố đặc trưng người dùng thật khi FwP chưa có đủ dữ liệu |
| 3 | **Libimseti.cz Dating Ratings Dataset** | Dataset công khai cho nghiên cứu recommender system | ~17 triệu lượt đánh giá, ~220,000 người dùng | Ma trận rating (1–10) giữa các cặp người dùng → huấn luyện **Collaborative Filtering** (Matrix Factorization), mô hình hóa "gu" chung, bổ trợ cho mô hình content-based, giải quyết cold-start |

> **Lưu ý quan trọng:** Không dataset công khai nào có nhãn theo đúng cơ chế "tầng GPA" đặc thù của FwP (vì đây là ý tưởng riêng của nhóm). Do đó:
> 1. Dùng 3 dataset trên để huấn luyện mô hình **tương thích tổng quát** (sở thích, tuổi, lối sống…).
> 2. **Sinh dữ liệu tổng hợp (synthetic data augmentation)** mô phỏng hành vi swipe có gắn tầng GPA, dựa trên giả định nghiên cứu ("người cùng tầng học lực có xu hướng match cao hơn"), dùng để fine-tune cho đúng đặc thù bài toán.
> 3. Khi app có dữ liệu người dùng thật, **retrain định kỳ** bằng dữ liệu swipe/match thật để thay dần phần synthetic.

### 2.5. Công nghệ huấn luyện

| Thành phần | Công nghệ |
|---|---|
| Mô hình chính | scikit-learn (Logistic Regression) → XGBoost/LightGBM (Gradient Boosting) khi cần độ chính xác cao hơn |
| Collaborative filtering | thư viện `surprise` hoặc `implicit` (Matrix Factorization trên Libimseti) |
| Embedding bio/sở thích | TF-IDF (baseline) hoặc Sentence-BERT đa ngôn ngữ (nâng cao) |
| Kết hợp kết quả | Weighted ensemble giữa điểm content-based (Speed Dating + OkCupid) và điểm collaborative (Libimseti) |

---

## 3. Module 2 — Compatibility Reconstruction (điều khiển unblur ảnh)

### 3.1. Mục đích & bối cảnh thiết kế
Xuất phát từ một lỗi thiết kế ban đầu: nếu màn hình Swipe hiện ảnh thật rõ mặt (kiểu Tinder chuẩn) thì khi match xong, hai bên **đã biết mặt nhau từ trước** — làm cho khái niệm "chat ẩn danh" trở nên vô nghĩa. Vì vậy FwP thiết kế lại:

- **Màn hình Swipe**: ảnh đại diện **luôn ở trạng thái làm mờ (blur)**; người dùng quyết định thích/bỏ qua dựa trên thông tin phi hình ảnh (trường, khoa, tầng GPA, sở thích, bio).
- **Giai đoạn chat sau match**: ảnh **rõ dần tự động theo thời gian thực**, được điều khiển bởi một mô hình AI đo "độ hợp" tích lũy qua hội thoại — không cần người dùng bấm nút "xin reveal" thủ công.

**Ý tưởng lấy cảm hứng từ kỹ thuật tái tạo ảnh 3D theo lát cắt (slice-based 3D reconstruction, như dựng mô hình 3D từ các lát ảnh cắt lớp y khoa):** thay "lát cắt ảnh y khoa" bằng đơn vị dữ liệu tự nhiên của app hẹn hò — **mỗi tin nhắn trao đổi trong giai đoạn ẩn danh**. Mô hình tích lũy dần các "lát" này để dựng nên một biểu diễn độ hợp ngày càng đầy đủ, hiển thị dưới dạng một khối 3D trực quan (mỗi trục là 1 khía cạnh: tính cách / cảm xúc / sở thích) — và quan trọng nhất, **giá trị độ hợp này là tham số đầu vào thật sự cho công thức tính % làm mờ ảnh**, không chỉ là hình ảnh trang trí.

### 3.2. Pipeline kỹ thuật chi tiết

```
Tin nhắn mới (tích lũy theo cụm mỗi N tin nhắn, vd N = 5)
      │
      ▼
┌───────────────────────────────────────────────────────────┐
│ BƯỚC 1 — Trích đặc trưng NLP (tạo "lát cắt" dữ liệu)        │
├───────────────────────────────────────────────────────────┤
│ a) Personality inference (Big Five / OCEAN)                │
│    - Dự đoán 5 trục: Openness, Conscientiousness,           │
│      Extraversion, Agreeableness, Neuroticism từ văn bản    │
│    - Huấn luyện/fine-tune trên dataset học thuật gán nhãn   │
│      tính cách từ văn bản (vd Essays Big-Five Corpus,       │
│      PANDORA, MyPersonality)                                │
│                                                               │
│ b) Sentiment/emotion analysis                                │
│    - Cảm xúc câu chat: tích cực/tiêu cực, mức độ nhiệt tình  │
│    - Mô hình: fine-tune BERT/PhoBERT (tiếng Việt) cho        │
│      classification cảm xúc                                  │
│                                                               │
│ c) Topic/interest extraction                                 │
│    - Chủ đề đang nói tới có trùng sở thích khai báo không    │
│    - Keyword/topic modeling đơn giản (LDA) hoặc zero-shot    │
│      classification theo danh sách tag sở thích có sẵn       │
└───────────────────────────────────────────────────────────┘
      │
      ▼
    Vector đặc trưng v_i ∈ R^d cho "lát" thứ i
      │
      ▼
┌───────────────────────────────────────────────────────────┐
│ BƯỚC 2 — Cộng dồn có trọng số theo thời gian                │
├───────────────────────────────────────────────────────────┤
│  V_t = Σ (w_i · v_i),  w_i = decay function theo thời gian  │
│  (tin nhắn gần đây có trọng số cao hơn tin nhắn cũ,          │
│   giống cách các lát cắt gần nhau trong không gian được      │
│   trọng số hoá khi nội suy khối 3D liên tục)                 │
└───────────────────────────────────────────────────────────┘
      │
      ▼
┌───────────────────────────────────────────────────────────┐
│ BƯỚC 3 — Giảm chiều & ánh xạ sang không gian 3D              │
├───────────────────────────────────────────────────────────┤
│  - UMAP / t-SNE (hoặc PCA cho baseline) chiếu V_t từ R^d     │
│    xuống R^3, mỗi trục diễn giải được:                       │
│    trục X = độ hợp tính cách, Y = độ hợp cảm xúc,            │
│    Z = độ hợp sở thích                                       │
│  - Toạ độ (x, y, z) + "độ đầy" (càng nhiều dữ liệu,          │
│    khối càng đặc/rõ)                                          │
└───────────────────────────────────────────────────────────┘
      │
      ▼
┌───────────────────────────────────────────────────────────┐
│ BƯỚC 4 — Render tương tác trên app (lớp trực quan phụ)       │
├───────────────────────────────────────────────────────────┤
│  - Android: SceneView / Filament (render 3D real-time,       │
│    mã nguồn mở)                                               │
│  - Khối mờ/rỗng khi ít dữ liệu → rõ nét, đặc, có màu          │
│    khi đủ dữ liệu; người dùng xoay/tương tác được             │
└───────────────────────────────────────────────────────────┘
      │
      ▼
┌───────────────────────────────────────────────────────────┐
│ BƯỚC 5 — Ánh xạ độ hợp sang % unblur ảnh (chức năng chính)   │
├───────────────────────────────────────────────────────────┤
│  blur_level = f(‖V_t‖)   — hàm đơn điệu giảm:                │
│    độ hợp càng cao → độ mờ càng thấp                          │
│  - Áp dụng Gaussian blur với bán kính giảm dần lên ảnh thật,  │
│    xử lý ngay trên client (on-device) — ảnh gốc chỉ tải về    │
│    dạng mã hoá, tránh lộ ảnh gốc qua cache/debug               │
│  - ‖V_t‖ đạt ngưỡng tối đa → blur_level = 0 → ảnh + tên thật  │
│    hiện hoàn toàn (reveal tự nhiên, không cần bấm nút)         │
└───────────────────────────────────────────────────────────┘
```

### 3.3. Vì sao đủ độ sâu kỹ thuật (không phải chỉ gọi API có sẵn)
- Tự huấn luyện/fine-tune mô hình personality inference trên dataset học thuật riêng.
- Tự thiết kế công thức cộng dồn có trọng số theo thời gian (weighted temporal aggregation).
- Tự chọn & tinh chỉnh phép chiếu giảm chiều diễn giải được (UMAP/t-SNE/PCA).
- Tự thiết kế hàm ánh xạ độ hợp → % unblur (`blur_level = f(‖V_t‖)`), có thể thử nghiệm nhiều dạng hàm (tuyến tính, sigmoid, log) và đánh giá dạng nào cho trải nghiệm mượt nhất.

### 3.4. Đánh giá định lượng
So sánh độ hợp dự đoán bởi mô hình với **proxy label** (2 người có tiếp tục chat lâu dài / trao đổi liên hệ ngoài app hay không), đo bằng correlation hoặc AUC — tương tự cách đánh giá độ chính xác tái tạo 3D so với ảnh gốc trong các bài toán reconstruction cổ điển.

---

## 4. Module 3 — Conversation Health Monitor (cảnh báo hội thoại nhạt)

### 4.1. Bài toán
Nhiều cuộc match trên các app hẹn hò "chết yểu" vì hội thoại nhạt dần mà không ai nhận ra để cứu kịp thời (ghosting). Module này theo dõi "sức khỏe" hội thoại theo thời gian thực và chủ động gợi ý trước khi cuộc trò chuyện bị bỏ lửng hẳn.

### 4.2. Đặc trưng đầu vào (trích theo cửa sổ trượt, vd 10 tin nhắn gần nhất)

| Đặc trưng | Ý nghĩa |
|---|---|
| Độ trễ phản hồi (response latency) | Thời gian trung bình giữa tin nhắn và câu trả lời, xu hướng tăng dần theo thời gian là dấu hiệu xấu |
| Xu hướng độ dài tin nhắn (message length trend) | Tin nhắn ngắn dần (từ câu dài → "ok"/"ừ"/emoji) là dấu hiệu giảm hứng thú |
| Tỉ lệ câu hỏi mở (open-question ratio) | Hội thoại khỏe mạnh có tỉ lệ câu hỏi qua lại cao; hội thoại hời hợt thường chỉ 1 bên hỏi |
| Độ lệch phản hồi giữa 2 bên (asymmetry) | 1 bên trả lời nhanh, 1 bên trả lời rất chậm/thưa dần |
| Xu hướng cảm xúc (sentiment trend) | Cảm xúc tích cực giảm dần theo thời gian (tái sử dụng output từ Module 2) |

### 4.3. Mô hình
Bài toán **phân loại chuỗi thời gian (time-series classification)** 3 lớp:

```
Đặc trưng theo cửa sổ trượt (10 tin nhắn gần nhất)
      │
      ▼
 Mô hình phân loại
 (Gradient Boosting trên đặc trưng thống kê — baseline nhẹ
  hoặc LSTM/GRU nhỏ nếu học trực tiếp từ chuỗi embedding tin nhắn)
      │
      ▼
   Khỏe mạnh  /  Hời hợt  /  Nguy cơ "toang"
```

**Gán nhãn huấn luyện (vì không có dataset công khai "hội thoại sắp toang" cho dating app):**
- Dùng **proxy label từ hành vi thật**: hội thoại được gán nhãn "toang" nếu sau X ngày không có tin nhắn mới và chưa từng reveal.
- Giai đoạn đầu (chưa đủ dữ liệu thật): khởi động bằng **rule-based threshold** (vd: >48h không phản hồi + độ dài tin nhắn giảm liên tục 3 lượt → "nguy cơ toang"), sau đó chuyển dần sang mô hình học có giám sát khi đã tích lũy đủ dữ liệu từ người dùng thật của FwP.

### 4.4. Hành động khi phát hiện rủi ro

```
Phát hiện "Hời hợt" hoặc "Nguy cơ toang"
      │
      ▼
Hiện gợi ý RIÊNG TƯ (chỉ người có dấu hiệu rời đi nhìn thấy, không chen
ngang giữa 2 người):
  - Gợi ý chủ đề chung dựa trên sở thích/trường/khoa 2 bên
  - Gợi ý câu hỏi mở
  - Nhắc nhẹ "đã lâu chưa trả lời"
      │
      ▼
Người dùng có thể tắt tính năng này trong Cài đặt
```

---

## 5. Hạ tầng phục vụ mô hình (ML Serving)

| Thành phần | Công nghệ | Vai trò |
|---|---|---|
| AI Service | FastAPI (Python) | Microservice độc lập, đóng gói cả 3 module, expose REST nội bộ cho backend chính |
| Model registry | Lưu file model đã train (pickle/ONNX) kèm version, có thể dùng MLflow nếu mở rộng | Quản lý version mô hình, rollback khi cần |
| Feature store nhẹ | Bảng trong PostgreSQL hoặc Redis (cache đặc trưng tính sẵn) | Tránh tính lại đặc trưng tốn kém mỗi lần gọi |
| Giao tiếp Backend ↔ AI Service | REST/HTTP nội bộ (có thể nâng cấp gRPC nếu cần độ trễ thấp) | Backend gửi dữ liệu thô/đặc trưng, nhận lại điểm số/nhãn |
| Render 3D phía client | SceneView / Filament (Android) | Hiển thị khối 3D độ hợp (Module 2, bước 4) |
| Xử lý blur ảnh phía client | On-device image processing (Gaussian blur theo bán kính nhận từ AI Service) | Đảm bảo ảnh gốc không bị lộ qua cache/debug |

---

## 6. Vòng đời dữ liệu & tái huấn luyện

```
Người dùng thật sử dụng app
      │
      ▼
Log hành vi (like/pass/match, tin nhắn, thời gian phản hồi, reveal...)
      │
      ▼
Lưu vào PostgreSQL (ẩn danh hoá / tách khỏi dữ liệu định danh khi cần)
      │
      ▼
Định kỳ (vd hàng tuần/hàng tháng khi đủ dữ liệu):
  - Export dữ liệu
  - Retrain 3 module với dữ liệu thật trộn cùng dataset gốc
    (Speed Dating / OkCupid / Libimseti / synthetic)
  - Đánh giá offline (xem mục 7)
      │
      ▼
Model mới đạt ngưỡng chất lượng? 
  │
  ├── Không đạt ──▶ Giữ nguyên model cũ, ghi log để điều tra
  │
  └── Đạt ──▶ Deploy model mới vào AI Service (có version, có thể rollback)
```

---

## 7. Đánh giá mô hình (Evaluation)

| Module | Chỉ số đánh giá | Cách đo |
|---|---|---|
| Module 1 — Ranking | AUC, Precision@K, NDCG | So khớp thứ tự gợi ý với nhãn match thật (offline trên tập test tách riêng) |
| Module 2 — Compatibility | Correlation giữa độ hợp dự đoán và proxy label (tiếp tục chat/trao đổi liên hệ) | Pearson/Spearman correlation, hoặc AUC nếu nhị phân hoá |
| Module 3 — Health Monitor | Precision/Recall cho lớp "Nguy cơ toang", đo trên nhãn proxy | Time-series train/test split theo thời gian (không random split để tránh leakage) |

---

## 8. Rủi ro & hạn chế

- **Thiếu dữ liệu thật ở giai đoạn đầu (cold-start):** toàn bộ mô hình ban đầu dựa vào dataset công khai + dữ liệu tổng hợp, độ chính xác thực tế có thể lệch cho tới khi có đủ dữ liệu thật từ sinh viên PTIT.
- **Thiên lệch dữ liệu (bias):** các dataset công khai (OkCupid, Libimseti, Speed Dating) đến từ văn hóa/đối tượng khác sinh viên Việt Nam — cần đánh giá lại và hiệu chỉnh khi có dữ liệu thật.
- **Chi phí tính toán NLP theo thời gian thực:** phân tích mỗi N tin nhắn cần tối ưu để không làm chậm trải nghiệm chat (cân nhắc batch nhỏ, mô hình nhẹ cho bước đầu, model nặng hơn chạy nền).
- **Quyền riêng tư:** dữ liệu hội thoại dùng để suy luận tính cách là dữ liệu nhạy cảm — cần nêu rõ trong chính sách quyền riêng tư, cho phép người dùng tắt các tính năng AI phân tích hội thoại.
