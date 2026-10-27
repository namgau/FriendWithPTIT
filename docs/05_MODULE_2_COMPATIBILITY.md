# 05 — Module 2: Compatibility Reconstruction (độ hợp → % unblur ảnh) — Người B

> **Cập nhật phạm vi:** Module 2 **không nộp môn ML**. App mobile: blur giảm theo số tin nhắn & số ngày chat (luật), quả cầu chỉ hiển thị. Thiết kế ML bên dưới giữ lại để tham khảo/làm sau.

> Đọc kèm `01_CHUAN_BAO_CAO_AUDITABLE.md`, `03_MODULE_0_DATA_EVAL_SERVING.md`.
> Module này gồm **4 bài toán con**, mỗi bài có dataset/nhãn/metric riêng, và mỗi bài phải có đủ 5 mục báo cáo (dạng rút gọn cho 2B, 2C).

## Mức ưu tiên: **điểm cộng (bonus)**, làm sau M1 và M3
- **Bắt buộc:** 2B (emotion tiếng Việt, PhoBERT vs TF-IDF) + 2D phiên bản tối giản (aggregation + blur đơn điệu, đánh giá trên S2).
- **Làm nếu còn thời gian:** 2A (personality), 2C (topic), P1, khảo sát UX, UMAP.
- Lý do: phần lõi khó kiểm chứng nhất (không có nhãn thật), nên không để kéo chậm cả nhóm. Nếu cắt, ghi rõ trong báo cáo.

## 0. Cảnh báo khoa học (phải đưa vào báo cáo)
- Suy luận Big Five từ văn bản ngắn có tương quan với nhãn tự báo cáo rất thấp (thường AUC ~0.55–0.65 trên Essays). **Không** được trình bày như "đo được tính cách thật".
- Không tồn tại dataset công khai có nhãn "hai người trong hội thoại ẩn danh có hợp nhau không". Vì vậy 2D được đánh giá bằng **simulator có ground-truth (S2)** và **pilot (P1)**, nói rõ đây là kiểm chứng *cơ chế*, không phải kiểm chứng hiệu quả thực.
- Hàm unblur là quyết định sản phẩm; có tính đạo đức/riêng tư (xem §5).

## 1. Objective and data

**Deployment target:** Sau mỗi cụm N = 5 tin nhắn trong chat ẩn danh, tính vector độ hợp tích lũy `V_t` và trả về `blur_level ∈ [0,1]` (1 = mờ hoàn toàn) đơn điệu giảm theo độ hợp, kèm tọa độ 3D (x: tính cách, y: cảm xúc, z: sở thích) để hiển thị.

### 1.1. Bộ dataset theo bài toán con

| Bài toán con | Dataset (≥3) | Nhãn | Đơn vị độc lập | Prevalence/phân phối |
|---|---|---|---|---|
| **2A Personality** (văn bản → 5 trục OCEAN) | **D4 Essays**, **D5 MBTI Kaggle** (4 trục), **D6 PANDORA** (nếu xin được) | 5 nhãn nhị phân (D4), 4 nhãn (D5) | **tác giả** (1 essay/người ở D4; nhiều post/người ở D5, D6 → group theo user) | Báo từng trục (D4 ≈ cân bằng ~50%; D5 lệch mạnh ở I/N) |
| **2B Emotion/Sentiment** (câu chat tiếng Việt) | **D7 UIT-VSMEC** (7 lớp), **D8 UIT-VSFC** (3 lớp), **D9 GoEmotions** (27 lớp; tiền huấn luyện/so sánh) | lớp cảm xúc | câu (D7, D8: kiểm tra trùng lặp/near-duplicate); D9: comment | Đa lớp lệch → macro-F1 |
| **2C Topic/interest overlap** | **D10 PersonaChat**, **D11 DailyDialog** (topic), **S2** | overlap persona ↔ nội dung hội thoại | **hội thoại** | mô tả |
| **2D Aggregation & blur mapping** | **S2 Synth-Chat**, **D10** (dựng cặp), **P1 Pilot** (20–30 cặp chat thật) | độ hợp tiềm ẩn (S2), tự báo cáo 1–5 (P1) | **cặp/hội thoại** | S2: phân phối c ∈ [0,1] do nhóm quy định |

Ghi Data Card: tiền xử lý tiếng Việt (chuẩn hóa teencode/emoji, tách từ bằng `underthesea`/VnCoreNLP cho PhoBERT), ngôn ngữ từng dataset (tiếng Anh vs tiếng Việt → dùng mô hình đa ngữ hoặc dịch, ghi rõ giới hạn).

## 2. Evaluation design

| Bài | Split | K, R, seeds | Ràng buộc |
|---|---|---|---|
| 2A | test 20% theo tác giả ; outer GroupKFold theo tác giả | K = 5, R = 3, seeds 42–46 | Group = user_id (D5/D6 nhiều post/người); các post cùng người không tách |
| 2B | test 20% (D7/D8 có sẵn train/dev/test → **giữ nguyên split gốc** để so sánh với công bố, thêm CV lặp ở train) | K = 5, R = 3 | Dedup câu trùng giữa các phần |
| 2C | Không huấn luyện lớn (zero-shot/embedding) → đánh giá trên toàn bộ dev, bootstrap theo hội thoại | — | Group = hội thoại |
| 2D | Điều chỉnh tham số (decay λ, hàm f, ngưỡng) trên **S2-train** ; đánh giá trên **S2-test** (seed sinh khác) ; P1 chỉ để kiểm tra, không tuning | R = 5 seed sinh | Group = hội thoại/cặp ; **thời gian:** chỉ dùng tin nhắn ≤ t để tính `V_t` |

Metric: 2A — ROC-AUC + balanced accuracy mỗi trục ; 2B — macro-F1, per-class F1, confusion matrix ; 2C — recall@k của tag đúng, AUC ; 2D — **Spearman(‖V_t‖, c)**, đơn điệu (tỉ lệ cặp t<t' mà độ hợp tăng theo lượng dữ liệu đúng chiều), **thời gian tới reveal** (bao nhiêu tin nhắn để blur=0) theo nhóm c cao/thấp, tương quan với điểm tự báo cáo trên P1.

## 3. Learning procedure

### 2A Personality
- Pipeline: làm sạch → TF-IDF (1–2-gram) + LIWC-like đặc trưng đơn giản → LR/SVM ; so với embedding (SBERT) + LR ; so với fine-tune DistilBERT/PhoBERT.
- Candidate: LR C ∈ {0.1,1,10}; LinearSVC; SBERT+LR; fine-tune (lr ∈ {2e-5,5e-5}, epoch ∈ {3,5}).
- Imbalance: D5 lệch → 5 phương án (none / class_weight / under / over / SMOTE trên embedding, không trên chữ thô). Calibration: Platt. Ngưỡng: theo balanced accuracy trên validation (không dùng test).
- Baseline: đa số, TF-IDF+LR.

### 2B Emotion
- Pipeline: chuẩn hóa → **PhoBERT-base** fine-tune (D7+D8) ; so với TF-IDF+LR (baseline) ; so với multilingual BERT/XLM-R ; GoEmotions dùng làm tiền huấn luyện/so sánh (tiếng Anh).
- Candidate: lr ∈ {2e-5, 3e-5, 5e-5}, batch ∈ {16, 32}, epoch ∈ {3, 5}, max_len ∈ {64, 128}.
- Class imbalance: class_weight / oversampling; báo macro-F1 mean ± SD nhiều seed.
- Thiết bị: nếu không có GPU → Colab/Kaggle; ghi thời gian train.

### 2C Topic/Interest
- Zero-shot (NLI hoặc embedding similarity) giữa câu chat và danh sách tag sở thích ; so với keyword match ; so với LDA. Ngưỡng sim chọn trên dev; hiệu chỉnh score sang [0,1].

### 2D Aggregation → blur
```
slice v_i = [personality_score_i (5d→1d agree), emotion_score_i, interest_overlap_i]  ∈ R^d
V_t = Σ_i w_i v_i , w_i = exp(-λ (t - t_i)) , chuẩn hóa    (candidate λ ∈ {0.0, 0.05, 0.1, 0.3})
3D coords = PCA (baseline) vs UMAP (n_neighbors, min_dist)   # chỉ để trực quan hóa
compat = g(V_t)   (g: trung bình có trọng số các trục; hoặc logistic nhỏ học trên S2)
blur_level = f(compat) ; candidate f: linear, sigmoid(k), log ; bậc thang (4 mức)
```
- **Ràng buộc bắt buộc của hàm f:** đơn điệu giảm, `blur_level = 0` chỉ khi compat ≥ τ_reveal **và** số tin nhắn ≥ n_min (chống reveal quá sớm do vài tin nhắn "may mắn"); τ_reveal, n_min chọn trên S2-train.
- Threshold & calibration: chuẩn hóa compat về xác suất "sẽ tiếp tục trò chuyện" bằng calibrator (S2/P1) → hiển thị được là "% hợp".
- Không dùng UMAP/t-SNE cho quyết định blur (không ổn định, phụ thuộc seed) — chỉ PCA/tuyến tính có thể diễn giải ; UMAP chỉ để hiển thị.
- Baseline 2D: (i) reveal theo số tin nhắn thuần (không AI), (ii) độ hợp ngẫu nhiên. Thắng baseline mới chứng minh AI có giá trị.

## 4. Results (khung bảng)

| Bảng | Nội dung |
|---|---|
| R2A | CV mean ± SD + test ± bootstrap CI (cluster theo tác giả, B = 2000) : AUC 5 trục, so baseline |
| R2B | macro-F1 mean ± SD (≥5 seed), test gốc D7/D8 + CI (bootstrap câu, B = 2000), confusion matrix |
| R2C | recall@k, AUC theo phương pháp |
| R2D-1 | Spearman(‖V_t‖, c) trên S2-test theo λ, f ; đường cong compat theo t |
| R2D-2 | So sánh với baseline "reveal theo số tin nhắn" : tỉ lệ reveal sớm sai (c thấp nhưng blur=0), tỉ lệ reveal muộn (c cao nhưng vẫn mờ) |
| R2D-3 | P1: Spearman với điểm tự báo cáo + CI bootstrap theo cặp (n nhỏ, CI rộng) |
| R2D-4 | Khảo sát trải nghiệm nhỏ (≥ 10 người): hàm f nào "mượt/hợp lý" nhất (Likert) |

### 4b. Phân tích lỗi (theo `01_*` §6)
- 2B: confusion matrix 7 lớp, cặp cảm xúc hay nhầm, lỗi theo độ dài câu/teencode/emoji/mỉa mai ; ≥ 10 câu sai tự tin cao.
- 2D: ca "c thấp nhưng blur = 0" (reveal sớm sai) và ca "c cao nhưng vẫn mờ" ; phân tích theo số tin nhắn và chủ đề.
- Kiến thức mở rộng: PhoBERT/transformer, zero-shot NLI, SHAP cho văn bản hoặc attention, đo calibration của mô hình transformer.

## 5. Practical limits
- **Độ tin cậy:** Big Five từ văn bản yếu; sentiment tiếng Việt có miền khác (UIT-VSFC là feedback môn học, không phải chat hẹn hò).
- **Đại diện & shift:** Essays (sinh viên Mỹ, văn viết dài) khác chat ngắn tiếng Việt teencode → mô hình 2A dự kiến suy giảm ; báo bằng P1.
- **Phụ thuộc:** nhiều post/người; các tin nhắn trong 1 hội thoại tương quan → cluster bootstrap.
- **Tài nguyên:** PhoBERT cần GPU để fine-tune ; suy luận thời gian thực: đo latency/CPU cho mỗi cụm 5 tin ; giải pháp: DistilPhoBERT/ONNX, batch.
- **Riêng tư & đạo đức:** suy luận tính cách từ hội thoại là dữ liệu nhạy cảm → opt-out, không lưu văn bản thô, chỉ lưu vector; ảnh gốc chỉ giải mã trên client. Nguy cơ thao túng (người dùng "diễn" để mở ảnh nhanh) → n_min, phát hiện spam.
- **Định kiến:** độ hợp có thể phạt phong cách ngôn ngữ vùng miền/nhóm; cần kiểm tra chênh lệch theo giới, trường.

## 6. Công việc chi tiết (Người B)

| # | Việc | Đầu ra | Ước lượng |
|---|---|---|---|
| 2A | Data Card D4/D5(/D6), pipeline TF-IDF vs SBERT vs fine-tune, đánh giá theo §2, báo cáo | `src/m2_compat/personality.py`, R2A | 4 ngày |
| 2B | Chuẩn hóa tiếng Việt, fine-tune PhoBERT trên D7/D8, so baseline & GoEmotions | model + R2B | 4 ngày |
| 2C | Zero-shot topic/interest, ngưỡng, đánh giá PersonaChat | R2C | 2 ngày |
| 2D | Simulator S2 do Người C làm (xem `06_*.md`), aggregation + hàm f, chọn λ/τ/n_min trên S2-train, chạy P1, khảo sát UX, báo cáo | R2D-1..4 | 4 ngày |
| 2E | Quả cầu độ hợp (orb: bán kính/độ sáng = compat, 3 vùng = 3 trục, độ trong suốt = độ tin cậy; PCA/trục tự định nghĩa, **không** dùng UMAP cho blur) + client blur; Người C tích hợp vào demo | `demo/` | gộp 2D |

**Bàn giao:** `POST /compat/update` nhận cụm tin → trả `{compat, blur_level, xyz, n_slices}`; xuất `emotion_score`, `sentiment_trend` cho Module 3.
