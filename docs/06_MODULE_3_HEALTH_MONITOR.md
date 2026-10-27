# 06 — Module 3: Conversation Health Monitor — **BÀI NỘP CHÍNH MÔN HỌC MÁY**

> Cả 3 người cùng làm module này (phân công ở `02_PHAN_CONG.md`). Module 1, 2 chỉ làm bản đơn giản phục vụ app mobile (xem `04_*`, `05_*`).
> Chuẩn báo cáo: `01_CHUAN_BAO_CAO_AUDITABLE.md`. Thu dữ liệu chat tiếng Việt: `08_THU_DU_LIEU_CHAT_VIET.md`.

## 0. Tóm tắt
Dự đoán một cuộc chat 1–1 có sắp "tắt" (không ai nhắn tiếp trong Δ giờ tới) hay không, chỉ dùng dữ liệu tới thời điểm hiện tại. Đặc trưng gồm **4 tầng** (hành vi thời gian → bề mặt văn bản → phong cách tương tác → ngữ nghĩa đa ngữ); mô hình gồm **4 họ** (tuyến tính → boosting → chuỗi → survival). Hai câu hỏi nghiên cứu:
- **RQ1:** Tầng đặc trưng nào thật sự giúp dự đoán? (ablation có CI)
- **RQ2:** Mô hình học trên hội thoại tiếng Anh có chuyển sang chat sinh viên Việt Nam được không, và lệch ở đâu? (cross-domain + phân tích lỗi)

Ba mức hiển thị trong app suy ra từ xác suất đã hiệu chỉnh p: `p < τ1` khỏe ; `τ1 ≤ p < τ2` hời hợt ; `p ≥ τ2` nguy cơ. (Không có nhãn thật cho "hời hợt" nên không học 3 lớp trực tiếp.)

---

## 1. Objective and data

**Deployment target:** Trong app FwP, tại mỗi tin nhắn (và mỗi giờ với hội thoại đang im lặng), ước lượng P(không có tin mới trong Δ tới | lịch sử tới t), để hiện gợi ý riêng tư cho người đang "rời đi". Δ chính = 48h; độ nhạy Δ ∈ {24h, 72h}.

### 1.1. Bộ dataset

| ID | Dataset | Ngôn ngữ | Timestamp | Vai trò | Đơn vị độc lập |
|---|---|---|---|---|---|
| **E1** | Ubuntu Dialogue Corpus (bản có timestamp, lọc hội thoại 2 người) | Anh | Có | Train/CV chính tiếng Anh | hội thoại (group thêm theo user nếu có) |
| **E2** | Customer Support on Twitter (Kaggle `thoughtvector/customer-support-on-twitter`) | Anh | Có | **Bộ thứ 2 cùng bài toán**, so sánh & cross-domain | hội thoại (group theo khách hàng) |
| E3 | Reddit qua ConvoKit (vd `reddit-corpus-small`), chỉ chuỗi trả lời qua lại giữa 2 người | Anh | Có | Tùy chọn, bộ thứ 3 | thread / cặp user |
| E4 | DailyDialog | Anh | **Không** | Chỉ để đánh giá bộ phát hiện câu hỏi/cảm xúc (có nhãn dialog act) | hội thoại |
| **V1** | **Chat Messenger/Telegram tiếng Việt do sinh viên đóng góp** (chỉ đặc trưng số + embedding, không văn bản) | Việt | Có | **Test đúng miền**; train trong miền nếu đủ | **người đóng góp** (các hội thoại cùng người phụ thuộc nhau) |
| V2 | Log chat app FwP lúc demo môn mobile | Việt | Có | Bổ sung / theo dõi shift | cặp người dùng |
| S2 | Simulator (`src/synth/chat.py`) | — | Có | **Chỉ** để unit-test pipeline, không báo cáo như kết quả | — |

> Link/license/cột timestamp của E1–E3 **phải xác minh** trước tuần 2 và ghi Data Card (`data_cards/`). Nếu E2, E3 không dùng được thì phương án tối thiểu vẫn đủ 2 bộ có timestamp cùng bài toán: E1 + V1.
> Khác biệt miền phải nêu trong báo cáo: E1 là hỗ trợ kỹ thuật, E2 là CSKH (hội thoại "tắt" thường vì đã giải quyết xong). Chính khác biệt này là chất liệu cho RQ2.

### 1.2. Mẫu, nhãn, censoring
- **Mẫu** = (hội thoại c, mốc t) tại mỗi tin nhắn thứ ≥ 6 (lấy mỗi 2 tin để giảm tự tương quan).
- **Đặc trưng**: chỉ từ các tin ≤ t.
- **Nhãn** y = 1 nếu không có tin nào trong (t, t + Δ].
- **Right-censoring**: mốc t có t + Δ vượt quá thời điểm cuối của dữ liệu → nhãn không xác định → loại khỏi bài toán nhị phân (ghi số lượng), nhưng **giữ lại** cho survival model (ưu điểm của survival, xem 3.2).
- **Prevalence**: báo theo mẫu và theo hội thoại, cho từng dataset và từng split.
- **N hiệu dụng** = số hội thoại (E1, E2) / số người đóng góp (V1), không phải số mẫu.

---

## 2. Đặc trưng: 4 tầng (cửa sổ 10 tin gần nhất + lịch sử đến t)

### Tầng T1 — Hành vi thời gian (không phụ thuộc ngôn ngữ)
| Đặc trưng | Ghi chú |
|---|---|
| latency: mean, median, max, std, last, **slope** | dùng log-latency |
| **latency tương đối** = latency gần nhất / median latency của chính cặp này | chuẩn hóa theo "nhịp" riêng từng cặp, quan trọng khi đổi miền |
| `time_since_last` tại mốc t | t là thời điểm dự đoán, không nhìn tin sau |
| burstiness B = (σ−μ)/(σ+μ) của khoảng cách tin | nhắn dồn hay đều |
| giờ trong ngày, cuối tuần, latency điều chỉnh giờ ngủ (00h–07h) | phục vụ phân tích lỗi "im lặng vì ngủ" |
| số phiên (cắt khi im > 30 phút), tin/phiên, xu hướng tin/phiên | |
| double-texting, ai kết thúc phiên | |
| bất đối xứng 2 bên: tỉ lệ số tin, latency, độ dài; ai mở lời sau mỗi lần im | |

### Tầng T2 — Bề mặt văn bản (từ điển nhỏ cho mỗi ngôn ngữ)
| Đặc trưng | Tiếng Việt | Tiếng Anh |
|---|---|---|
| độ dài (từ, ký tự), slope độ dài | | |
| tỉ lệ **tin cụt** | `ừ, uk, ok, oke, ờ, ừm, hihi, haha, hì, vâng, dạ`, chỉ emoji/sticker | `ok, k, lol, yeah, sure, haha` |
| tỉ lệ **câu hỏi** mỗi bên; câu hỏi có được đáp lại không | `?`, `không, chưa, à, hả, nhỉ, nhé, sao, gì, nào, đâu, bao giờ, mấy` | `?`, wh-words, đảo trợ động từ |
| emoji, tiếng cười, dấu chấm than, viết hoa | | |
| đa dạng từ vựng (type–token ratio) | tách âm tiết / `underthesea` | |

> Bộ phát hiện câu hỏi/tin cụt tự viết phải được **đánh giá riêng**: trên E4 (có nhãn dialog act) và ~200 câu tiếng Việt nhóm tự gán nhãn → báo precision/recall (thí nghiệm X6).

### Tầng T3 — Phong cách tương tác (điểm nhấn học thuật)
| Đặc trưng | Ý nghĩa |
|---|---|
| **Language Style Matching (LSM)** giữa 2 bên theo cửa sổ: độ tương đồng tỉ lệ từ chức năng (đại từ, trợ từ, liên từ…) | Ireland et al. (2011), *Psychological Science* 22(1):39–44: trong speed dating, LSM cao làm tăng khả năng cả hai muốn gặp lại (OR ≈ 3.05); với tin nhắn IM của 86 cặp đôi, LSM cao dự đoán còn hẹn hò sau 3 tháng (OR ≈ 1.95) |
| **Lexical mirroring**: độ trùng từ giữa tin trả lời và tin trước | có "bắt lời" nhau không |
| xu hướng LSM / mirroring theo thời gian (slope) | đang lệch nhịp dần? |
| turn-taking: độ dài lượt trung bình, tỉ lệ đổi lượt | |

> Từ chức năng tiếng Việt: nhóm tự lập danh sách (đại từ: tớ/cậu/mình/bạn/anh/em…; trợ từ: nhé/nha/à/ơi/thôi…; liên từ). Ghi rõ là danh sách tự xây.

### Tầng T4 — Ngữ nghĩa đa ngữ (cầu nối Anh ↔ Việt)
| Đặc trưng | Cách làm |
|---|---|
| Embedding câu đa ngữ (`paraphrase-multilingual-MiniLM-L12-v2` hoặc LaBSE) | một không gian chung cho tiếng Anh & Việt |
| Độ liên quan giữa tin trả lời và tin trước (cosine) | trả lời lạc đề/qua loa |
| **Topic drift**, **topic novelty** (khoảng cách embedding giữa cửa sổ hiện tại và trước đó) | hội thoại cạn chủ đề |
| Cảm xúc/độ nhiệt tình: mô hình đa ngữ có sẵn, hoặc PhoBERT fine-tune UIT-VSMEC | ablation: có giúp không? |

> Với V1, embedding tính **trên máy người đóng góp** (xem `08_*`); nhóm chỉ nhận vector/điểm số, không nhận văn bản.
> Nếu giảm chiều embedding bằng PCA: **fit 1 lần trên E4 (DailyDialog)** — bộ không dùng để đánh giá — rồi đóng băng, dùng chung cho E1, E2, V1.

---

## 3. Learning procedure

### 3.1. Pipeline
```
raw messages → chuẩn hóa (timezone, gộp tin cùng người trong 1 phút, lọc tin hệ thống)
→ sinh mẫu (c, t) + nhãn + cờ censoring
→ trích đặc trưng T1..T4 (chỉ dữ liệu ≤ t)
→ imputer → scaler (mô hình tuyến tính/NN) → [resampler: chỉ train fold]
→ model → calibrator (Platt/isotonic trên phần chưa resample) → τ1, τ2
```

### 3.2. Candidate configurations
| Họ | Mô hình | Lưới |
|---|---|---|
| Baseline | (0) đa số ; (1) **rule** của tài liệu gốc: >48h không phản hồi & độ dài giảm 3 lượt ; (2) chỉ `time_since_last` | ngưỡng chọn trên val |
| Tuyến tính | Logistic Regression (L1/L2) | C ∈ {0.01, 0.1, 1, 10}, class_weight |
| Cây | Random Forest ; **LightGBM/XGBoost** | depth ∈ {3,5,7}, lr ∈ {0.03,0.1}, n ∈ {200,500}; Optuna nếu kịp |
| **Foundation model bảng** | **TabPFN v2** (Nature 2025): transformer huấn luyện sẵn trên hàng triệu bộ dữ liệu tổng hợp, không cần tuning ; mạnh với ≤ 10k mẫu → hợp V1 nhỏ | mặc định ; so với LightGBM đã tuning |
| Chuỗi | GRU 1–2 lớp trên chuỗi vector đặc trưng theo từng tin (+ embedding T4); tùy chọn Transformer nhỏ | hidden ∈ {32,64}, dropout ∈ {0.1,0.3}, early stopping |
| **Survival** | **Cox PH**, **Random Survival Forest** / gradient-boosted survival (`scikit-survival`, `lifelines`): dự đoán *thời gian tới khi tắt*, xử lý đúng censoring | penalizer, n_estimators, min_leaf |

Survival là phần "kiến thức ngoài chương trình" chính: dùng được cả mẫu bị censor, cho đường cong rủi ro theo thời gian (một mô hình trả lời mọi Δ). Đánh giá bằng **C-index**, **time-dependent AUC**, **Integrated Brier Score**; so với mô hình nhị phân tại Δ = 48h.

### 3.3. Mất cân bằng (ghi chú bảng: under/oversampling, SMOTE)
5 phương án: none / class_weight / random undersampling / random oversampling / SMOTE, **chỉ trên vector đặc trưng của train fold**. Không SMOTE cho GRU (dùng weighted loss). Lưu ý: các mốc cùng hội thoại tự tương quan → SMOTE dễ nội suy giữa các mẫu gần trùng; so sánh và kết luận bằng số liệu.

### 3.4. Ngưỡng & calibration
- Calibration: Platt vs isotonic, chọn theo Brier trên calib set; reliability diagram cho từng dataset.
- τ1, τ2 chọn trên validation theo chi phí `C_FN : C_FP` (bỏ sót vs làm phiền), mặc định 1 : 2, kèm phân tích độ nhạy. Test chỉ báo cáo.

### 3.5. Bias–variance
Learning curve theo số hội thoại; so train vs CV; SD giữa các fold thời gian; LR (bias cao) – GBM – GRU (variance cao khi ít dữ liệu, nhất là V1).

---

## 4. Evaluation design

| Hạng mục | Thiết kế |
|---|---|
| Test held-out | E1, E2: 20% hội thoại **bắt đầu muộn nhất**. V1: 20% **người đóng góp** (group). Khóa tới tuần 5 |
| Outer CV | E1/E2: **forward-chaining theo thời gian** (5 khối, train các khối trước → validate khối sau). V1: **GroupKFold theo người đóng góp**, K = 5 (leave-one-donor-out nếu < 15 người) |
| Lặp & seeds | R = 3 cho phần ngẫu nhiên (khởi tạo, SMOTE, NN); seeds 42–46 |
| Inner CV | GroupKFold theo hội thoại trong train, K = 3 |
| Cấm | random split theo mẫu/dòng |
| Metric nhị phân | **PR-AUC** (chính), ROC-AUC, Brier, ECE, recall@precision = 0.5, **lead time** |
| Metric survival | C-index, time-dependent AUC, Integrated Brier Score |
| Bootstrap | cluster theo hội thoại (E1, E2) / người đóng góp (V1), B = 2000, percentile CI 95% ; **so sánh 2 mô hình bằng paired bootstrap** (CI của hiệu số) |
| Metric conformal | coverage thực tế vs mục tiêu (vd 90%), độ rộng khoảng / độ chặt cận dưới |
| Metric vận hành | tỉ lệ cảnh báo/hội thoại/ngày, % cảnh báo đúng trong 48h |

### 4.1. Thí nghiệm
| Mã | Thí nghiệm | Trả lời |
|---|---|---|
| X1 | Mọi họ mô hình × tầng đặc trưng T1 → +T2 → +T3 → +T4 trên E1, E2 | RQ1 (ablation lũy tiến, CI) |
| X2 | 5 phương án mất cân bằng × 2 mô hình tốt nhất | yêu cầu imbalance |
| X3 | Nhị phân Δ = 48h vs survival (cùng đặc trưng) | điểm cộng |
| X4 | **Cross-domain**: E1→E2, E2→E1, (E1+E2)→V1, V1→V1 | RQ2 |
| X5 | Độ nhạy Δ ∈ {24, 48, 72}h, cửa sổ ∈ {5, 10, 20} tin, chi phí FN:FP | độ vững |
| X6 | Đánh giá bộ phát hiện câu hỏi/tin cụt (E4 + 200 câu Việt tự gán) | chất lượng đặc trưng |
| X7 | TabPFN v2 vs LightGBM, theo kích thước train (learning curve) | điểm cộng: foundation model |
| X8 | Conformal: khoảng/cận dưới thời gian tới khi tắt ; kiểm tra coverage khi chuyển miền E→V1 | điểm cộng: bất định có bảo đảm |
| X9 | Embedding đa ngữ: MiniLM vs BGE-M3 vs multilingual-e5 cho T4 | chọn embedding có căn cứ |
| X10 | Bộ phát hiện câu hỏi/tin cụt bằng LLM (chỉ trên E1/E2/E4 hoặc LLM chạy local) vs luật | điểm cộng: LLM làm feature extractor |

---

## 5. Results (khung bảng, sinh bằng `src/eval/report.py`)

| Bảng | Nội dung |
|---|---|
| R1 | CV mean ± SD mọi candidate, mỗi dataset |
| R2 | Ablation T1..T4 (Δ PR-AUC + CI) |
| R3 | Imbalance (X2) |
| R4 | Test 1 lần + bootstrap CI; so với rule baseline của tài liệu gốc |
| R5 | Ma trận cross-domain (X4): hàng = train, cột = test |
| R6 | Survival vs nhị phân (X3) |
| R7 | Độ nhạy (X5); chất lượng bộ phát hiện (X6) |
| Hình | reliability diagram, learning curve, SHAP summary, đường cong survival mẫu |

### 5b. Phân tích lỗi (theo `01_*` §6)
- **E1** confusion matrix tại τ2; FN (tắt mà không báo) vs FP (báo nhầm); phân phối lead time.
- **E2 slice**: theo dataset, ngôn ngữ, giờ trong ngày, cuối tuần, độ dài hội thoại, tuổi hội thoại, người đóng góp (V1).
- **E3** ≥ 10 hội thoại sai tự tin cao, kèm SHAP. Giả thuyết kiểu lỗi cần kiểm chứng: im lặng vì ngủ/thi cử; "tắt" vì đã xong việc (E2); chuyển sang gọi điện/gặp mặt; nhắn cụt nhưng thân ("ừ" giữa bạn thân); câu hỏi tiếng Việt không có dấu `?` bị bỏ sót.
- **E4 nguồn lỗi**: nhiễu nhãn proxy, censoring, lệch miền (R5), thiếu đặc trưng (R2), variance (learning curve).
- **E5 hành động**: thử ≥ 1 sửa đổi (vd latency điều chỉnh giờ ngủ) và báo trước/sau trên CV.

## 5c. Metric đánh giá — tóm tắt & lý do chọn

| Metric | Vì sao dùng | Báo ở |
|---|---|---|
| **PR-AUC** (chính) | lớp "tắt" là thiểu số; PR-AUC nhạy với lớp dương hơn ROC-AUC | R1–R5 |
| ROC-AUC | so sánh với tài liệu khác, không phụ thuộc prevalence | R1–R5 |
| Brier, ECE + reliability diagram | ngưỡng τ1/τ2 dựa trên xác suất → xác suất phải đúng | R1, R4 |
| Precision/Recall/F-β tại τ2, recall@precision = 0.5 | đúng quyết định triển khai (cảnh báo hay không) | R4 |
| **Lead time** | báo trước bao lâu mới có giá trị cho người dùng | R4, 5b |
| C-index, time-dependent AUC, Integrated Brier Score | đánh giá survival, dùng được dữ liệu bị censor | R6 |
| Coverage + độ rộng khoảng (conformal) | bất định có bảo đảm | X8 |
| Precision/Recall + Cohen's kappa (bộ phát hiện) | chất lượng đặc trưng tự viết | R7 |
| Mọi metric: mean ± SD (CV) + 95% CI (cluster bootstrap B = 2000); so sánh mô hình: paired bootstrap | yêu cầu chuẩn auditable | tất cả |

## 5d. Điểm cộng — kiến thức mới (2024–2026) đưa vào bài

| Kỹ thuật | Nguồn | Dùng thế nào trong bài | Ưu tiên |
|---|---|---|---|
| **TabPFN v2** — foundation model cho dữ liệu bảng | Hollmann et al., *Nature* 637:319–326 (2025); TabPFN v2.5 (11/2025) mở rộng tới ~50k dòng | Thêm 1 candidate (X7). Đặc biệt có lợi cho V1 (ít mẫu). So với LightGBM đã tuning, theo kích thước train. Giới hạn: kém hơn khi dữ liệu lớn/nhiều chiều → giảm mẫu hoặc chọn đặc trưng | P1 |
| **Conformalized survival analysis** — cận dưới thời gian tới khi tắt có bảo đảm phân phối tự do, xử lý right-censoring | Davidov et al., ICLR 2025; Sesia & Svetnik, ICML 2025 (doubly robust); Holmes & Marandon, arXiv 2024 (hai phía) | X8: "hội thoại này còn ít nhất ~T giờ với xác suất 90%". Báo coverage thực tế | P1 |
| **Weighted conformal dưới covariate shift** | Shin, Lee & Kang, arXiv 12/2025 (survival + covariate shift) | Mở rộng X8 cho chuyển miền E1/E2 → V1: coverage có giữ không khi đổi miền? (khớp đúng RQ2) | P2 |
| **Embedding đa ngữ hiện đại + benchmark tiếng Việt** | BGE-M3 (BAAI, 2024); multilingual-e5; **VN-MTEB** (arXiv 2507.21500, 2025) — benchmark embedding tiếng Việt | X9: chọn embedding cho T4 có căn cứ, trích VN-MTEB khi lý giải | P1 |
| **LLM làm bộ trích đặc trưng / gán nhãn yếu** | FeatLLM (arXiv 2404.09491, 2024); LLM sinh labeling function (arXiv 2408.16173, 2024) | X10: LLM phát hiện câu hỏi/tin cụt/độ nhiệt tình → so với luật trên tập 200 câu. **Không gửi chat V1 lên API** (riêng tư) — chỉ dùng trên E1/E2/E4 hoặc LLM chạy local | P2 |
| **Language Style Matching** (nền tảng tâm lý học, không mới nhưng ít gặp trong ML) | Ireland et al., *Psychological Science* 22(1):39–44 (2011) | Tầng T3; kiểm tra lại giả thuyết "khớp phong cách → hội thoại bền" trên dữ liệu chat | P1 |
| **Survival analysis** (ngoài chương trình) | `scikit-survival`, `lifelines` | X3, R6 | P1 |
| **SHAP**, **domain shift (PSI)** | — | phân tích lỗi, theo dõi khi triển khai | P1 |

Công trình liên quan để viết phần "Related work": Hsu et al., *IJHCI* 2024 — dự đoán người dùng bỏ dở hội thoại với chatbot (đặc trưng thủ công + BERT); HERALD (arXiv 2106.00162) — phát hiện mất hứng thú trong hội thoại xã giao. Điểm khác của nhóm: hội thoại **người–người**, có **thời gian**, **survival + conformal**, **chuyển miền sang tiếng Việt**.

> Mọi nguồn trên đã tra cứu tháng 10/2026 qua tìm kiếm web (tóm tắt/abstract). Trước khi trích vào báo cáo, người phụ trách phải mở bài gốc kiểm tra lại số liệu.

### Nguồn
- TabPFN, *Nature* 2025: https://pubmed.ncbi.nlm.nih.gov/39780007/ ; giới hạn: https://www.alphaxiv.org/abs/2502.17361v2 ; thư viện: https://github.com/PriorLabs/TabPFN
- Conformalized survival (ICLR 2025): https://mlanthology.org/iclr/2025/davidov2025iclr-conformalized
- Doubly robust conformalized survival (ICML 2025): https://proceedings.mlr.press/v267/sesia25a.html
- Two-sided conformalized survival: https://arxiv.org/abs/2410.24136v2
- Weighted conformal survival under covariate shift: https://arxiv.org/abs/2512.03738v1
- VN-MTEB: https://arxiv.org/pdf/2507.21500
- FeatLLM: https://arxiv.org/pdf/2404.09491 ; LLM labeling functions: https://arxiv.org/abs/2408.16173v1
- Ireland et al. 2011 (LSM): https://pubmed.ncbi.nlm.nih.gov/21149854/
- Hsu et al. 2024 (abandonment): https://people.cs.nycu.edu.tw/~armuro/pubs/hsu-et-al-2024-ijhci.pdf
- HERALD: https://arxiv.org/pdf/2106.00162

---

## 6. Practical limits
- **Đại diện:** E1/E2 không phải hội thoại làm quen; V1 là chat của một nhóm nhỏ sinh viên tình nguyện (thiên lệch chọn mẫu: bạn bè của nhóm, chủ yếu 1 trường).
- **Phụ thuộc:** các mốc trong 1 hội thoại; các hội thoại của cùng người đóng góp; cùng một tài khoản CSKH ở nhiều hội thoại (E2).
- **Nhãn proxy:** "tắt" ≠ "mất hứng thú"; Messenger không phải kênh liên lạc duy nhất.
- **Distribution shift:** khác ngôn ngữ, nền tảng, văn hóa nhắn tin; khi lên app: theo dõi phân phối đặc trưng (PSI) và tỉ lệ cảnh báo/tuần.
- **Vòng phản hồi:** gợi ý làm thay đổi hành vi → nhãn tương lai bị ảnh hưởng; log có/không hiển thị gợi ý.
- **Riêng tư:** V1 chỉ nhận đặc trưng số + embedding, không văn bản; người ở đầu bên kia không trực tiếp đồng ý → giảm thiểu bằng trích đặc trưng tại máy, băm id, xóa sau môn học (`08_*`).
- **Tài nguyên:** thời gian train mỗi mô hình; latency suy luận (GBM ≪ GRU); MiniLM đa ngữ chạy CPU được.

---

## 7. Tích hợp app mobile (môn mobile)
- `POST /health/score` nhận danh sách tin (timestamp, người gửi, độ dài, cờ câu hỏi/tin cụt, embedding tùy chọn) → `{p, level, top_factors, suggestions}`.
- Backend gọi khi có tin mới **và** bằng job mỗi giờ cho hội thoại đang im lặng (im lặng chính là tín hiệu).
- Gợi ý riêng tư cho người có dấu hiệu rời đi: chủ đề chung từ tag sở thích, câu hỏi mở theo mẫu, nhắc nhẹ; tắt được trong Cài đặt.
- App dùng GBM đã calibrate (nhẹ); survival/GRU chỉ trong báo cáo nếu chậm.
