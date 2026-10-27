# 06 — Module 3: Conversation Intelligence (sức khỏe + an toàn hội thoại) — **BÀI NỘP CHÍNH MÔN HỌC MÁY**

> Cả 3 người cùng làm module này (phân công ở `02_PHAN_CONG.md`). Module 1, 2 chỉ làm bản đơn giản phục vụ app mobile (xem `04_*`, `05_*`).
> Chuẩn báo cáo: `01_CHUAN_BAO_CAO_AUDITABLE.md`.
> **Quyết định 27/10/2026:** không tự thu chat tiếng Việt (không có bộ chat 1–1 tiếng Việt công khai có timestamp ; tự thu tốn thời gian và vướng riêng tư). Tiếng Việt chỉ xuất hiện ở Head B qua ViHSD / UIT-ViCTSD.

## 0. Tóm tắt
Một **bộ mã hóa hội thoại** đọc cả *hành vi nhắn tin* lẫn *nội dung*, rồi trả lời 2 câu hỏi dự báo, chỉ dùng dữ liệu tới thời điểm hiện tại:

| Đầu ra | Câu hỏi | Ưu tiên |
|---|---|---|
| **Head A — Disengagement** | Hội thoại có sắp "tắt" (không ai nhắn tiếp trong Δ giờ) không? Còn bao lâu? | P0 (lõi) |
| **Head B — Derailment / an toàn** | Hội thoại có sắp chuyển sang công kích/xúc phạm **trước khi** điều đó xảy ra không? | P1 |

Đặc trưng gồm **5 tầng** (hành vi thời gian → bề mặt văn bản → phong cách tương tác → ngữ nghĩa đa ngữ → bộ mã hóa nội dung); mô hình đi từ tuyến tính → boosting → TabPFN → chuỗi (CRAFT-like) → survival → multi-task. Câu hỏi nghiên cứu:
- **RQ1:** Tầng đặc trưng nào thật sự giúp dự đoán? (ablation có CI)
- **RQ2:** Mô hình có chuyển được sang miền khác không, và lệch ở đâu? Head A: giữa 2 nền tảng (Ubuntu ↔ Twitter CSKH). Head B: giữa ngôn ngữ — bộ chấm độc hại học trên tiếng Việt (ViHSD) qua encoder đa ngữ có giúp dự báo hội thoại tiếng Anh không ; và giữa 2 bộ tiếng Việt (ViHSD ↔ ViCTSD). (cross-domain + phân tích lỗi)
- **RQ3:** Học chung 2 nhiệm vụ (multi-task, chung bộ mã hóa) có tốt hơn học riêng không?

Head A trong app: 3 mức suy ra từ xác suất đã hiệu chỉnh p: `p < τ1` khỏe ; `τ1 ≤ p < τ2` hời hợt ; `p ≥ τ2` nguy cơ. (Không có nhãn thật cho "hời hợt" nên không học 3 lớp trực tiếp.)
Head B trong app: chỉ **nhắc nhẹ người gửi** ("Bạn có chắc muốn gửi?") và hỗ trợ người nhận (nút báo cáo), **không tự động phạt**.

Bộ mã hóa nội dung của Module 3 về sau được **dùng lại cho Module 2** (độ hợp) — đọc chat một lần, phục vụ cả hai.

---

## 1. Objective and data

**Deployment target:**
- **Head A:** trong app FwP, tại mỗi tin nhắn (và mỗi giờ với hội thoại đang im lặng), ước lượng P(không có tin mới trong Δ tới | lịch sử tới t), để hiện gợi ý riêng tư cho người đang "rời đi". Δ chính = 48h; độ nhạy Δ ∈ {24h, 72h}.
- **Head B:** tại mỗi tin nhắn (trước khi tin kế tiếp được gửi), ước lượng P(hội thoại sẽ có tin công kích/xúc phạm trong các lượt tới | lịch sử tới t) và điểm độc hại của tin đang soạn, để nhắc nhẹ người gửi.

### 1.1. Bộ dataset

| ID | Dataset | Ngôn ngữ | Timestamp | Vai trò | Đơn vị độc lập |
|---|---|---|---|---|---|
| **E1** | Ubuntu Dialogue Corpus (bản có timestamp, lọc hội thoại 2 người) | Anh | Có | Train/CV chính tiếng Anh | hội thoại (group thêm theo user nếu có) |
| **E2** | Customer Support on Twitter (Kaggle `thoughtvector/customer-support-on-twitter`) | Anh | Có | **Bộ thứ 2 cùng bài toán**, so sánh & cross-domain | hội thoại (group theo khách hàng) |
| E3 | Reddit qua ConvoKit (vd `reddit-corpus-small`), chỉ chuỗi trả lời qua lại giữa 2 người | Anh | Có | Tùy chọn, bộ thứ 3 | thread / cặp user |
| E4 | DailyDialog | Anh | **Không** | Chỉ để đánh giá bộ phát hiện câu hỏi/cảm xúc (có nhãn dialog act) | hội thoại |
| V2 | Log chat app FwP lúc demo môn mobile | Việt | Có | Bổ sung / theo dõi shift | cặp người dùng |
| S2 | Simulator (`src/synth/chat.py`) | — | Có | **Chỉ** để unit-test pipeline, không báo cáo như kết quả | — |

**Dữ liệu cho Head B (derailment / độc hại):**

| ID | Dataset | Ngôn ngữ | Quy mô (theo tài liệu, cần in lại từ code) | Nhãn | Vai trò | Đơn vị độc lập |
|---|---|---|---|---|---|---|
| **G1** | Conversations Gone Awry — Wikipedia (ConvoKit `conversations-gone-awry-corpus`) | Anh | 4,188 hội thoại, ~30k lượt | hội thoại có dẫn tới công kích cá nhân (crowd gán nhãn) | Train/test chính Head B | **cặp hội thoại** (thiết kế ghép cặp: mỗi hội thoại "hỏng" được ghép với 1 hội thoại "lành" cùng trang) |
| **G2** | Conversations Gone Awry — Reddit CMV (ConvoKit `conversations-gone-awry-cmv-corpus`) | Anh | 6,842 hội thoại (bản mở rộng 2025 ~20k — kiểm tra phiên bản) | có bị moderator can thiệp vì vi phạm | **Bộ thứ 2 cùng bài toán** ; cross-domain G1↔G2 | cặp hội thoại / thread |
| **H1** | ViHSD (UIT) | **Việt** | ~33.4k bình luận | CLEAN / OFFENSIVE / HATE | Bộ phân loại độc hại **từng tin** tiếng Việt | bình luận (dedup) |
| **H2** | UIT-ViCTSD | **Việt** | 10k bình luận | toxic / non-toxic (rất mất cân bằng) + constructive | Bộ thứ 2 tiếng Việt ; cross-dataset H1↔H2 ; **bài thực hành imbalance/SMOTE** | bình luận |

> Link/license/cột timestamp của E1–E3 **phải xác minh** trước tuần 2 và ghi Data Card (`data_cards/`). Head A cần **≥ 2 bộ có timestamp**: E1 + E2 ; nếu E2 hỏng thì thay bằng E3 (Reddit/ConvoKit). Với Head B, tối thiểu G1 + H1.
> Khác biệt miền phải nêu trong báo cáo: E1 là hỗ trợ kỹ thuật, E2 là CSKH (hội thoại "tắt" thường vì đã giải quyết xong); G1/G2 là tranh luận công khai, H1/H2 là bình luận mạng xã hội công khai — **không phải chat riêng hẹn hò**. Chính khác biệt này là chất liệu cho RQ2.

### 1.2. Mẫu, nhãn, censoring
- **Mẫu** = (hội thoại c, mốc t) tại mỗi tin nhắn thứ ≥ 6 (lấy mỗi 2 tin để giảm tự tương quan).
- **Đặc trưng**: chỉ từ các tin ≤ t.
- **Nhãn** y = 1 nếu không có tin nào trong (t, t + Δ].
- **Right-censoring**: mốc t có t + Δ vượt quá thời điểm cuối của dữ liệu → nhãn không xác định → loại khỏi bài toán nhị phân (ghi số lượng), nhưng **giữ lại** cho survival model (ưu điểm của survival, xem 3.2).
- **Prevalence**: báo theo mẫu và theo hội thoại, cho từng dataset và từng split.
- **N hiệu dụng** = số hội thoại (E1, E2), không phải số mẫu.

### 1.3. Nhãn Head B
- **G1, G2 (dự báo):** đầu vào là tiền tố hội thoại **trước** lượt công kích (bỏ lượt công kích và mọi lượt sau). Dự báo ở mức hội thoại: báo "sẽ hỏng" nếu điểm của một tiền tố bất kỳ vượt τ_B. Theo giao thức của Chang & Danescu-Niculescu-Mizil (2019).
- **Horizon:** báo trước được bao nhiêu lượt so với lượt công kích.
- **H1, H2 (từng tin):** nhãn có sẵn; H1 gộp OFFENSIVE + HATE = độc hại (và thử 3 lớp).

---

## 2. Đặc trưng: 5 tầng (cửa sổ 10 tin gần nhất + lịch sử đến t)

> T1–T4 dùng cho cả 2 head (G1/G2 không có timestamp chính xác ở mọi lượt → Head B chủ yếu dùng T2–T5). T5 là "đọc nội dung" thật sự.

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

> Nếu giảm chiều embedding bằng PCA: **fit 1 lần trên E4 (DailyDialog)** — bộ không dùng để đánh giá — rồi đóng băng, dùng chung cho E1, E2.

### Tầng T5 — Bộ mã hóa nội dung (đọc chat thật sự)
| Thành phần | Cách làm |
|---|---|
| **Bộ mã hóa từng tin (utterance encoder)** | transformer đa ngữ: XLM-R-base hoặc PhoBERT (tiếng Việt) ; bản nhẹ: embedding BGE-M3/MiniLM đóng băng |
| **Điểm độc hại từng tin** | fine-tune trên H1 (+ H2) cho tiếng Việt ; trên G1/G2 dùng bộ phân loại đa ngữ cùng kiến trúc → 1 điểm/tin, đưa vào chuỗi |
| **Bộ mã hóa ngữ cảnh (context encoder)** | GRU 1 chiều chạy qua chuỗi vector tin nhắn (kiểu **CRAFT**) → vector trạng thái hội thoại h_t |
| **Dùng chung** | h_t là đầu vào cho cả Head A và Head B (multi-task, RQ3) và sau này cho Module 2 |


---

## 3. Learning procedure

### 3.1. Pipeline
```
raw messages → chuẩn hóa (timezone, gộp tin cùng người trong 1 phút, lọc tin hệ thống)
→ sinh mẫu (c, t) + nhãn + cờ censoring
→ trích đặc trưng T1..T5 (chỉ dữ liệu ≤ t)
→ imputer → scaler (mô hình tuyến tính/NN) → [resampler: chỉ train fold]
→ model → calibrator (Platt/isotonic trên phần chưa resample) → τ1, τ2
```

### 3.2. Candidate configurations
| Họ | Mô hình | Lưới |
|---|---|---|
| Baseline | (0) đa số ; (1) **rule** của tài liệu gốc: >48h không phản hồi & độ dài giảm 3 lượt ; (2) chỉ `time_since_last` | ngưỡng chọn trên val |
| Tuyến tính | Logistic Regression (L1/L2) | C ∈ {0.01, 0.1, 1, 10}, class_weight |
| Cây | Random Forest ; **LightGBM/XGBoost** | depth ∈ {3,5,7}, lr ∈ {0.03,0.1}, n ∈ {200,500}; Optuna nếu kịp |
| **Foundation model bảng** | **TabPFN v2** (Nature 2025): transformer huấn luyện sẵn trên hàng triệu bộ dữ liệu tổng hợp, không cần tuning ; mạnh với ≤ 10k mẫu → hợp khi lấy mẫu nhỏ / learning curve | mặc định ; so với LightGBM đã tuning |
| Chuỗi | GRU 1–2 lớp trên chuỗi vector đặc trưng theo từng tin (+ embedding T4); tùy chọn Transformer nhỏ | hidden ∈ {32,64}, dropout ∈ {0.1,0.3}, early stopping |
| **Survival** | **Cox PH**, **Random Survival Forest** / gradient-boosted survival (`scikit-survival`, `lifelines`): dự đoán *thời gian tới khi tắt*, xử lý đúng censoring | penalizer, n_estimators, min_leaf |

Survival là phần "kiến thức ngoài chương trình" chính: dùng được cả mẫu bị censor, cho đường cong rủi ro theo thời gian (một mô hình trả lời mọi Δ). Đánh giá bằng **C-index**, **time-dependent AUC**, **Integrated Brier Score**; so với mô hình nhị phân tại Δ = 48h.

**Candidate cho Head B:**
| Họ | Mô hình | Ghi chú |
|---|---|---|
| Baseline | (0) đa số ; (1) điểm độc hại của **tin cuối cùng** vượt ngưỡng ; (2) từ điển từ xúc phạm | baseline (1) rất mạnh — phải thắng nó mới có ý nghĩa "dự báo" |
| Bảng | LR / LightGBM trên đặc trưng T2–T4 + thống kê điểm độc hại (max, slope, số tin > ngưỡng) | so sánh với chuỗi |
| Chuỗi | **CRAFT-like**: utterance encoder + GRU ngữ cảnh + đầu phân loại | Chang & Danescu-Niculescu-Mizil, EMNLP 2019 |
| Transformer | fine-tune XLM-R trên tiền tố hội thoại nối chuỗi (cắt 512 token) | so với CRAFT |
| **Multi-task** | chung T5 encoder, 2 đầu: Head A (BCE/ survival loss) + Head B (BCE), trọng số λ ∈ {0.3, 0.5, 0.7} | RQ3 ; huấn luyện xen kẽ batch từ E1/E2 và G1/G2 |
| Bộ phân loại tin tiếng Việt | TF-IDF + LR (baseline) → PhoBERT / XLM-R fine-tune trên H1, H2 | cross-dataset H1→H2, H2→H1 |

### 3.3. Mất cân bằng (ghi chú bảng: under/oversampling, SMOTE)
5 phương án: none / class_weight / random undersampling / random oversampling / SMOTE, **chỉ trên vector đặc trưng của train fold**. Không SMOTE cho GRU/transformer (dùng weighted loss). Lưu ý: các mốc cùng hội thoại tự tương quan → SMOTE dễ nội suy giữa các mẫu gần trùng; so sánh và kết luận bằng số liệu.
**H2 (ViCTSD) rất mất cân bằng ở nhãn toxic** → đây là nơi so sánh 5 phương án rõ nhất (SMOTE trên embedding câu, không trên chữ thô). G1 cân bằng do thiết kế ghép cặp → báo cáo prevalence thực tế ngoài đời thấp hơn nhiều, nên phải **hiệu chỉnh lại xác suất theo prevalence triển khai** (prior shift) trước khi chọn ngưỡng.

### 3.4. Ngưỡng & calibration
- Calibration: Platt vs isotonic, chọn theo Brier trên calib set; reliability diagram cho từng dataset.
- τ1, τ2 (Head A) chọn trên validation theo chi phí `C_FN : C_FP` (bỏ sót vs làm phiền), mặc định 1 : 2, kèm phân tích độ nhạy. Test chỉ báo cáo.
- τ_B (Head B): **báo nhầm = "buộc tội oan"** → ưu tiên precision; chọn τ_B sao cho FPR ≤ 5% trên validation (giá trị nhóm chốt), báo recall đạt được.

### 3.5. Bias–variance
Learning curve theo số hội thoại; so train vs CV; SD giữa các fold thời gian; LR (bias cao) – GBM – GRU (variance cao khi ít dữ liệu).

---

## 4. Evaluation design

| Hạng mục | Thiết kế |
|---|---|
| Test held-out | E1, E2: 20% hội thoại **bắt đầu muộn nhất**. G1: split chuẩn ConvoKit. H1/H2: split gốc. Khóa tới 20/11 (chỉ Người A mở) |
| Outer CV | E1/E2: **forward-chaining theo thời gian** (5 khối, train các khối trước → validate khối sau) |
| Lặp & seeds | R = 3 cho phần ngẫu nhiên (khởi tạo, SMOTE, NN); seeds 42–46 |
| Inner CV | GroupKFold theo hội thoại trong train, K = 3 |
| Cấm | random split theo mẫu/dòng |
| Metric nhị phân | **PR-AUC** (chính), ROC-AUC, Brier, ECE, recall@precision = 0.5, **lead time** |
| Metric survival | C-index, time-dependent AUC, Integrated Brier Score |
| Bootstrap | cluster theo hội thoại (E1, E2) / cặp ghép (G1), B = 2000, percentile CI 95% ; **so sánh 2 mô hình bằng paired bootstrap** (CI của hiệu số) |
| Metric conformal | coverage thực tế vs mục tiêu (vd 90%), độ rộng khoảng / độ chặt cận dưới |
| Metric vận hành | tỉ lệ cảnh báo/hội thoại/ngày, % cảnh báo đúng trong 48h |
| **Head B — split** | G1/G2: dùng **split chuẩn của ConvoKit** (để so với bài báo) + giữ nguyên cặp ghép trong cùng phần ; CV trong train theo group = cặp. H1/H2: split gốc + CV theo bình luận đã dedup |
| **Head B — metric** | accuracy, precision, recall, F1, **FPR** (buộc tội oan), PR-AUC ; **horizon** (báo trước bao nhiêu lượt) ; H1/H2: macro-F1 |

### 4.1. Thí nghiệm
| Mã | Thí nghiệm | Trả lời |
|---|---|---|
| X1 | Mọi họ mô hình × tầng đặc trưng T1 → +T2 → +T3 → +T4 → +T5 trên E1, E2 | RQ1 (ablation lũy tiến, CI) |
| X2 | 5 phương án mất cân bằng × 2 mô hình tốt nhất | yêu cầu imbalance |
| X3 | Nhị phân Δ = 48h vs survival (cùng đặc trưng) | điểm cộng |
| X4 | **Cross-domain Head A**: train E1 → test E2, train E2 → test E1, so với in-domain | RQ2 |
| X5 | Độ nhạy Δ ∈ {24, 48, 72}h, cửa sổ ∈ {5, 10, 20} tin, chi phí FN:FP | độ vững |
| X6 | Đánh giá bộ phát hiện câu hỏi/tin cụt (E4 + 200 câu Việt tự gán) | chất lượng đặc trưng |
| X7 | TabPFN v2 vs LightGBM, theo kích thước train (learning curve) | điểm cộng: foundation model |
| X8 | Conformal: khoảng/cận dưới thời gian tới khi tắt ; kiểm tra coverage khi chuyển miền E1 → E2 | điểm cộng: bất định có bảo đảm |
| X9 | Embedding đa ngữ: MiniLM vs BGE-M3 vs multilingual-e5 cho T4 | chọn embedding có căn cứ |
| X10 | Bộ phát hiện câu hỏi/tin cụt bằng LLM (chỉ trên E1/E2/E4 hoặc LLM chạy local) vs luật | điểm cộng: LLM làm feature extractor |
| X11 | Head B: baseline "tin cuối" vs bảng vs CRAFT-like vs XLM-R, trên G1 và G2 ; cross G1→G2, G2→G1 | Head B, RQ2 |
| X12 | Bộ phân loại độc hại tiếng Việt: TF-IDF+LR vs PhoBERT vs XLM-R trên H1, H2 ; cross H1↔H2 ; 5 phương án imbalance trên H2 | tiếng Việt, imbalance |
| X13 | **Multi-task vs single-task** (cùng encoder T5), λ ∈ {0.3, 0.5, 0.7} | RQ3 |
| X14 | **Cross-lingual Head B**: điểm độc hại từ bộ phân loại học trên ViHSD (encoder đa ngữ) làm đặc trưng cho dự báo G1 tiếng Anh — có giúp không? ; cross-dataset ViHSD ↔ ViCTSD | RQ2 |

---

## 5. Results (khung bảng, sinh bằng `src/eval/report.py`)

| Bảng | Nội dung |
|---|---|
| R1 | CV mean ± SD mọi candidate, mỗi dataset |
| R2 | Ablation T1..T5 (Δ PR-AUC + CI) |
| R3 | Imbalance (X2) |
| R4 | Test 1 lần + bootstrap CI; so với rule baseline của tài liệu gốc |
| R5 | Ma trận cross-domain (X4): hàng = train, cột = test |
| R6 | Survival vs nhị phân (X3) |
| R7 | Độ nhạy (X5); chất lượng bộ phát hiện (X6) |
| R8 | Head B (X11): bảng so sánh + horizon ; FPR tại τ_B |
| R9 | Phân loại độc hại tiếng Việt (X12) + imbalance trên H2 |
| R10 | Multi-task vs single-task (X13) cho cả 2 head |
| Hình | reliability diagram, learning curve, SHAP summary, đường cong survival mẫu, đường "điểm nguy cơ theo lượt" của vài hội thoại G1 |

### 5b. Phân tích lỗi (theo `01_*` §6)
- **E1** confusion matrix tại τ2; FN (tắt mà không báo) vs FP (báo nhầm); phân phối lead time.
- **E2 slice**: theo dataset, ngôn ngữ, giờ trong ngày, cuối tuần, độ dài hội thoại, tuổi hội thoại.
- **E3** ≥ 10 hội thoại sai tự tin cao, kèm SHAP. Giả thuyết kiểu lỗi cần kiểm chứng: im lặng vì ngủ/thi cử; "tắt" vì đã xong việc (E2); chuyển sang gọi điện/gặp mặt; nhắn cụt nhưng thân ("ừ" giữa bạn thân); câu hỏi tiếng Việt không có dấu `?` bị bỏ sót.
- **E4 nguồn lỗi**: nhiễu nhãn proxy, censoring, lệch miền (R5), thiếu đặc trưng (R2), variance (learning curve).
- **E5 hành động**: thử ≥ 1 sửa đổi (vd latency điều chỉnh giờ ngủ) và báo trước/sau trên CV.
- **Head B riêng**: phân tích **FP (buộc tội oan)** trước tiên — mỉa mai/đùa giữa bạn thân, tranh luận gay gắt nhưng lịch sự, tiếng lóng/teencode, chửi thề "thân mật" tiếng Việt ; FN — công kích tinh vi không có từ xấu. Slice theo độ dài hội thoại, chủ đề, ngôn ngữ ; kiểm tra **thiên lệch** (từ vùng miền, teencode có bị gắn độc hại nhiều hơn không).

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
| Head B: F1, **FPR**, horizon | báo nhầm là "buộc tội oan" nên FPR quan trọng ngang recall ; horizon đo giá trị *dự báo* | R8 |
| Phân loại tin tiếng Việt: macro-F1, F1 lớp độc hại | lớp độc hại thiểu số | R9 |
| Mọi metric: mean ± SD (CV) + 95% CI (cluster bootstrap B = 2000); so sánh mô hình: paired bootstrap | yêu cầu chuẩn auditable | tất cả |

## 5d. Điểm cộng — kiến thức mới (2024–2026) đưa vào bài

| Kỹ thuật | Nguồn | Dùng thế nào trong bài | Ưu tiên |
|---|---|---|---|
| **TabPFN v2** — foundation model cho dữ liệu bảng | Hollmann et al., *Nature* 637:319–326 (2025); TabPFN v2.5 (11/2025) mở rộng tới ~50k dòng | Thêm 1 candidate (X7). Có lợi khi ít mẫu (xem learning curve). So với LightGBM đã tuning, theo kích thước train. Giới hạn: kém hơn khi dữ liệu lớn/nhiều chiều → giảm mẫu hoặc chọn đặc trưng | P1 |
| **Conformalized survival analysis** — cận dưới thời gian tới khi tắt có bảo đảm phân phối tự do, xử lý right-censoring | Davidov et al., ICLR 2025; Sesia & Svetnik, ICML 2025 (doubly robust); Holmes & Marandon, arXiv 2024 (hai phía) | X8: "hội thoại này còn ít nhất ~T giờ với xác suất 90%". Báo coverage thực tế | P1 |
| **Weighted conformal dưới covariate shift** | Shin, Lee & Kang, arXiv 12/2025 (survival + covariate shift) | Mở rộng X8 cho chuyển miền E1 → E2: coverage có giữ không khi đổi miền? (khớp đúng RQ2) | P2 |
| **Embedding đa ngữ hiện đại + benchmark tiếng Việt** | BGE-M3 (BAAI, 2024); multilingual-e5; **VN-MTEB** (arXiv 2507.21500, 2025) — benchmark embedding tiếng Việt | X9: chọn embedding cho T4 có căn cứ, trích VN-MTEB khi lý giải | P1 |
| **LLM làm bộ trích đặc trưng / gán nhãn yếu** | FeatLLM (arXiv 2404.09491, 2024); LLM sinh labeling function (arXiv 2408.16173, 2024) | X10: LLM phát hiện câu hỏi/tin cụt/độ nhiệt tình → so với luật trên tập 200 câu. chỉ dùng trên dữ liệu công khai E1/E2/E4 | P2 |
| **Language Style Matching** (nền tảng tâm lý học, không mới nhưng ít gặp trong ML) | Ireland et al., *Psychological Science* 22(1):39–44 (2011) | Tầng T3; kiểm tra lại giả thuyết "khớp phong cách → hội thoại bền" trên dữ liệu chat | P1 |
| **Survival analysis** (ngoài chương trình) | `scikit-survival`, `lifelines` | X3, R6 | P1 |
| **SHAP**, **domain shift (PSI)** | — | phân tích lỗi, theo dõi khi triển khai | P1 |
| **Dự báo hội thoại trở nên độc hại** (conversational forecasting) | CRAFT — Chang & Danescu-Niculescu-Mizil, EMNLP 2019 ; Tran et al., arXiv 2507.19470 (2025, mở rộng CGA-CMV) ; ACL 2026 long 1943 (so sánh với người: con người có FPR thấp hơn mô hình SOTA) | Head B (X11). Trích kết quả "con người vs mô hình" khi bàn FPR | P1 |
| **Multi-task learning** (chung bộ mã hóa cho 2 nhiệm vụ) | — | X13, RQ3 | P2 |
| **Transformer tiếng Việt / đa ngữ** cho độc hại | PhoBERT ; XLM-R ; ViHSD (Luu et al. 2021), UIT-ViCTSD (2021) | X12 | P1 |

Công trình liên quan để viết phần "Related work": Hsu et al., *IJHCI* 2024 — dự đoán người dùng bỏ dở hội thoại với chatbot (đặc trưng thủ công + BERT); HERALD (arXiv 2106.00162) — phát hiện mất hứng thú trong hội thoại xã giao; CRAFT và chuỗi bài Conversations Gone Awry — dự báo hội thoại hỏng. Điểm khác của nhóm: hội thoại **người–người**, gộp **mất hứng thú + an toàn** trong một mô hình, có **thời gian**, **survival + conformal**, **chuyển miền sang tiếng Việt**.

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
- CRAFT (EMNLP 2019): https://arxiv.org/pdf/1909.01362
- Conversations Gone Awry, but then? (2025): https://arxiv.org/pdf/2507.19470v1
- ACL 2026 (mô hình vs con người): https://aclanthology.org/2026.acl-long.1943.pdf
- CGA-Wiki: https://convokit.cornell.edu/documentation/awry.html ; CGA-CMV: https://zissou.infosci.cornell.edu/socialkit/documentation/awry_cmv.html
- ViHSD: https://github.com/sonlam1102/vihsd ; paper: https://arxiv.org/pdf/2103.11528v2
- UIT-ViCTSD: https://arxiv.org/abs/2103.10069v2

---

## 6. Practical limits — điểm yếu của project (bắt buộc theo chuẩn của thầy)

> Nguyên tắc viết: mỗi điểm yếu phải có **(1) ảnh hưởng tới kết luận nào**, **(2) bằng chứng định lượng trong báo cáo** (bảng/hình nào đo nó), **(3) cách đã giảm thiểu**, và **(4) điều còn chưa giải quyết được**. Không viết chung chung kiểu "dữ liệu còn ít".
> Người phụ trách tổng hợp: A (ghép báo cáo) ; mỗi người viết phần thuộc mảng mình.

### 6.1. Bốn mục bắt buộc trên slide

| Mục | Điểm yếu cụ thể | Ảnh hưởng | Bằng chứng trong báo cáo | Đã giảm thiểu | Còn tồn tại |
|---|---|---|---|---|---|
| **Resources** | Không có GPU riêng ; 3 người, 5 tuần, song song làm app môn Mobile | Không fine-tune được transformer lớn, lưới siêu tham số nhỏ, R chỉ = 3 | Bảng thời gian train + RAM + latency suy luận từng mô hình (R1) ; ghi rõ phần cứng | MiniLM/embedding đóng băng chạy CPU ; Colab/Kaggle cho PhoBERT/XLM-R ; TabPFN không cần tuning | Kết quả transformer có thể chưa tối ưu ; ghi rõ "không tuning đầy đủ" |
| **Dependence** | Mẫu (c, t) cùng hội thoại tự tương quan ; cùng tài khoản CSKH (E2) ; cặp ghép trong G1 | CV/CI bị **lạc quan** nếu coi mẫu độc lập | Báo N mẫu **và** N hiệu dụng (hội thoại/người) ; so sánh CI bootstrap theo mẫu vs theo cụm (CI cụm rộng hơn bao nhiêu) | Split theo group/thời gian ; cluster bootstrap ; lấy mẫu mỗi 2 tin | Vẫn còn phụ thuộc giữa các hội thoại có chung người nhận mà nhóm không biết (id đã băm) |
| **Representativeness** | E1 (hỗ trợ kỹ thuật), E2 (CSKH), G1/G2 (tranh luận công khai), H1/H2 (bình luận công khai) **không phải chat làm quen** ; **Head A không có dữ liệu tiếng Việt** (không có bộ chat 1–1 tiếng Việt công khai có timestamp) | Kết quả **không chứng minh** hiệu quả trong app hẹn hò tiếng Việt | Bảng mô tả & phân phối đặc trưng chính giữa các bộ ; ma trận cross-domain R5 | Đặc trưng hành vi (T1) không phụ thuộc ngôn ngữ ; encoder đa ngữ ; tiếng Việt ở Head B (ViHSD, ViCTSD) | Cần log chat thật của app (V2) để đánh giá đúng miền — hướng phát triển |
| **Distribution shift** | Khác nền tảng (IRC ↔ Twitter ↔ app), ngôn ngữ (Anh ↔ Việt), văn hóa nhắn tin (teencode, sticker), nhịp thời gian | Mô hình tốt trên E1 có thể kém trên E2 và trên chat app thật | **Ma trận cross-domain R5** (độ sụt PR-AUC khi đổi miền) ; PSI từng đặc trưng ; coverage conformal khi đổi miền (X8) | Đặc trưng tương đối (latency so với nhịp riêng cặp) ; embedding đa ngữ ; hiệu chỉnh lại xác suất theo prevalence | Khi lên app cần theo dõi PSI + tỉ lệ cảnh báo/tuần và retrain |

### 6.2. Điểm yếu khác (phải nêu thật thà)

| Điểm yếu | Vì sao là vấn đề | Bằng chứng / cách xử lý trong báo cáo |
|---|---|---|
| **Nhãn proxy (Head A)** | "Tắt" ≠ "mất hứng thú" (có thể chuyển sang gọi điện, gặp mặt, hoặc đã xong việc) | Phân tích lỗi E3 đếm các ca kiểu này ; độ nhạy theo Δ (X5) |
| **Định nghĩa "độc hại" lệch nhu cầu app (Head B)** | Dataset chỉ có xúc phạm/công kích/thù ghét ; **không có nhãn quấy rối tình dục, ép buộc, body shaming** — vốn là rủi ro chính trong app hẹn hò | Nêu rõ phạm vi định nghĩa ; app xử lý các loại này bằng luật + nút báo cáo |
| **Không có chat tiếng Việt thật** | Cả 2 head không được đánh giá trên chat riêng tiếng Việt ; tiếng Việt chỉ có bình luận công khai (H1/H2) | Nêu rõ ; X14 đo khả năng chuyển ngôn ngữ gián tiếp ; hướng phát triển: log app (V2) |
| **Censoring** | Hội thoại cuối dữ liệu chưa biết có tắt không → loại khỏi bài nhị phân gây lệch | Báo số mẫu bị loại ; survival dùng được các mẫu này (X3) |
| **Mất cân bằng & prior shift** | G1 được ghép cặp nên cân bằng 50/50, ngoài đời tỉ lệ độc hại thấp hơn nhiều → precision thực tế thấp hơn số báo cáo | Hiệu chỉnh xác suất theo prevalence giả định ; báo precision ở nhiều mức prevalence |
| **Đặc trưng tự xây** | Từ điển câu hỏi/tin cụt/từ chức năng tiếng Việt do nhóm tự viết, có thể sót | X6: precision/recall trên 200 câu, Cohen's kappa giữa 2 người gán nhãn |
| **Buộc tội oan & thiên lệch** | Teencode, phương ngữ, chửi thề thân mật có thể bị gắn độc hại | FPR theo slice ; ví dụ FP trong 5b ; app chỉ nhắc nhẹ, không phạt tự động |
| **Riêng tư khi triển khai** | App phải đọc nội dung chat để chạy Head B / T5 | Xin đồng ý, cho phép tắt tính năng, không lưu văn bản thô, chỉ lưu điểm số |
| **Vòng phản hồi** | Khi app hiện gợi ý, hành vi thay đổi → nhãn tương lai bị ảnh hưởng | Chưa đo được (chưa có app thật) → hướng phát triển: log có/không hiển thị gợi ý |
| **Phạm vi rộng so với thời gian** | 2 head + nhiều thí nghiệm → một số phần (P2) có thể chưa làm | Ghi rõ phần nào đã làm/chưa làm ; không trình bày kết quả chưa chạy |
| **Bias–variance** | G1 chỉ ~4k hội thoại → mô hình phức tạp (GRU, transformer) dễ high-variance | Learning curve, SD giữa fold, so với LR/TabPFN |

### 6.3. Câu chốt nên có trong báo cáo
"Kết quả cho thấy [mô hình X] dự báo tốt hơn baseline trên [các bộ dữ liệu công khai] với [số liệu + CI]. Tuy nhiên, do [nhãn proxy, khác miền, không có chat tiếng Việt thật], chúng tôi **không** khẳng định mô hình hoạt động tương đương trong app hẹn hò thật ; cần đánh giá lại trên log chat của app."

---

## 7. Tích hợp app mobile (môn mobile)
- `POST /health/score` nhận danh sách tin (timestamp, người gửi, độ dài, cờ câu hỏi/tin cụt, embedding tùy chọn) → `{p, level, top_factors, suggestions}`.
- Backend gọi khi có tin mới **và** bằng job mỗi giờ cho hội thoại đang im lặng (im lặng chính là tín hiệu).
- Gợi ý riêng tư cho người có dấu hiệu rời đi: chủ đề chung từ tag sở thích, câu hỏi mở theo mẫu, nhắc nhẹ; tắt được trong Cài đặt.
- App dùng GBM đã calibrate (nhẹ); survival/GRU chỉ trong báo cáo nếu chậm.
- **Head B:** `POST /safety/score` nhận tin đang soạn + ngữ cảnh → `{toxicity, derail_risk}` ; nếu vượt τ_B: hiện "Tin này có thể khiến người kia khó chịu, bạn có chắc muốn gửi?" (chỉ người gửi thấy) ; người nhận luôn có nút chặn/báo cáo.

### 7.1. Tín hiệu hành vi trong app (môn mobile, **không** đưa vào đánh giá ML)
Ghi log **có sự đồng ý**, chỉ dùng nội bộ, không bao giờ hiển thị cho người kia:
| Tín hiệu | Dùng thế nào | Không dùng để |
|---|---|---|
| Số lần xem hồ sơ người kia | tín hiệu **quan tâm** cho Head A (vẫn xem hồ sơ → chưa hẳn rời đi) ; giới hạn tần suất theo luật nếu bất thường | gắn nhãn "stalker" |
| Cuộn lại đọc tin cũ | tín hiệu quan tâm | cảnh báo |
| Thu hồi / xóa tin | tín hiệu do dự ; nếu tin bị thu hồi có điểm độc hại cao → thống kê cho Head B | phạt |
| Thời gian soạn tin, số lần gõ rồi xóa | tín hiệu do dự | — |

Lý do không đưa vào bài ML: **không có dữ liệu có nhãn** để huấn luyện/đánh giá (chỉ có thể mô phỏng), và dùng để cáo buộc hành vi có rủi ro đạo đức cao. Ghi vào báo cáo ở mục "Hướng phát triển": khi app có log thật (V2) sẽ thêm như tầng đặc trưng T6 và đánh giá lại.
