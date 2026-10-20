# 06 — Module 3: Conversation Health Monitor — Người C

> Đọc kèm `01_CHUAN_BAO_CAO_AUDITABLE.md`, `03_MODULE_0_DATA_EVAL_SERVING.md`.

## 0. Đề xuất chỉnh so với `reference/AI_ML_FWP.md` (cần cả nhóm đồng ý)
Tài liệu gốc chia 3 lớp: Khỏe mạnh / Hời hợt / Nguy cơ toang. **Không có nhãn thật cho lớp "Hời hợt"** (chỉ suy ra được "hội thoại có chết hay không"). Đề xuất:
1. Mô hình học **nhị phân có giám sát**: `y = 1` nếu hội thoại "chết" trong Δ tới (định nghĩa §1.2).
2. Ba mức hiển thị suy ra từ **xác suất đã hiệu chỉnh** p: `p < τ1` khỏe ; `τ1 ≤ p < τ2` hời hợt ; `p ≥ τ2` nguy cơ toang. τ1, τ2 chọn trên validation theo chi phí (báo trong 3.3).
→ Khớp đúng mục "threshold, calibration" của tiêu chuẩn và tránh bịa nhãn.

## 1. Objective and data

**Deployment target:** Tại mỗi thời điểm sau tin nhắn thứ ≥ 6 của một cuộc chat, dự đoán xác suất hội thoại **không có phản hồi từ cả hai phía trong Δ tiếp theo** (Δ chọn theo phân phối khoảng cách tin nhắn, vd 48 giờ hoặc ≥ 5× khoảng cách trung vị của hội thoại đó), chỉ dùng dữ liệu tới thời điểm hiện tại.

### 1.1. Bộ dataset

| Dataset | Có gì | Vai trò | Đơn vị độc lập | Hạn chế |
|---|---|---|---|---|
| **D12 Ubuntu Dialogue Corpus v2** | timestamp thật, hội thoại 2 người (lọc 2-party) | Học đặc trưng độ trễ + nhãn "chết" thật | **hội thoại** (và nên lọc theo cặp người) | Miền hỗ trợ kỹ thuật, không phải hẹn hò ; tiếng Anh |
| **D11 DailyDialog** | dialog act (câu hỏi/thông tin/chỉ thị), cảm xúc, không timestamp | Đặc trưng: tỉ lệ câu hỏi, xu hướng cảm xúc, độ dài | hội thoại | Hội thoại ngắn, không có nhãn chết |
| **D10 PersonaChat** | hội thoại 2 người có persona | Kiểm tra topic-overlap (dùng chung 2C) | hội thoại | Không timestamp |
| **S2 Synth-Chat** (chính cho nhãn) | Simulator sinh cả timestamp, độ dài, câu hỏi, cảm xúc, có **tham số hứng thú tiềm ẩn h(t)** | Có ground-truth; kiểm tra pipeline; chọn Δ, τ | hội thoại | Chỉ khôi phục giả định của simulator |
| **P1 Pilot chat** | 20–30 cặp SV chat thật vài ngày (có đồng ý) | Kiểm tra phân phối thực | cặp | n nhỏ |

Thực tế: hai nguồn có timestamp thật là D12 và (nhỏ) P1 ; D11/D10 cung cấp đặc trưng nội dung. Báo cáo phải nói rõ nhãn "chết" thật chỉ có ở D12 (proxy) và S2 (mô phỏng).

### 1.2. Định nghĩa mẫu & nhãn (tránh leakage)
- Mẫu = (hội thoại c, mốc t) với t tại mỗi tin nhắn thứ ≥ 6 (hoặc mỗi 2 tin để giảm tự tương quan).
- Đặc trưng chỉ từ 10 tin gần nhất **≤ t**.
- Nhãn y = 1 nếu không có tin nhắn nào trong (t, t+Δ]. Loại mốc t sao cho t+Δ vượt quá thời điểm kết thúc quan sát của dữ liệu (right-censoring) — hoặc ghi là "chưa biết" và bỏ; **ghi số mẫu bị loại**.
- **Đơn vị độc lập = hội thoại**, không phải mẫu (các mốc trong 1 hội thoại tương quan mạnh). N hiệu dụng ≈ số hội thoại. Prevalence báo cả theo mẫu và theo hội thoại.

## 2. Evaluation design

| Hạng mục | Thiết kế |
|---|---|
| Test held-out | 20% hội thoại **muộn nhất theo thời gian** bắt đầu (D12, P1) ; S2: seed sinh khác. Khóa lại |
| Outer CV | **Time-aware group CV**: sắp hội thoại theo thời điểm bắt đầu, chia K = 5 khối liên tiếp (expanding window: train các khối trước, validate khối kế) ; lặp R = 3 chỉ áp cho phần ngẫu nhiên (khởi tạo mô hình, SMOTE) vì thứ tự thời gian cố định ; seeds 42–44 |
| Inner CV | GroupKFold theo hội thoại trong train, K = 3 |
| Không random split | Random split theo dòng bị cấm (leakage, đã ghi trong tài liệu gốc §7) |
| Metric | PR-AUC (chính, vì lớp "chết" thiểu số), ROC-AUC, Brier, ECE, recall@precision cố định, **lead time** (dự báo sớm hơn thời điểm chết bao lâu), F-β |
| Bootstrap | Cluster theo **hội thoại**, B = 2000, CI 95% |

## 3. Learning procedure

### 3.1. Đặc trưng (cửa sổ trượt 10 tin)
- Độ trễ: mean/median/last/độ dốc hồi quy của log-latency ; tỉ lệ phản hồi ≥ 6h.
- Độ dài: mean, slope, tỉ lệ tin ≤ 3 từ ("ok", "ừ").
- Câu hỏi: open-question ratio (dấu ?, từ hỏi tiếng Việt/Anh; D11 dialog act để tinh chỉnh).
- Bất đối xứng: chênh lệch số tin, latency, độ dài giữa 2 bên ; người nhắn mở đầu.
- Cảm xúc: xu hướng sentiment (điểm từ Module 2B, hoặc lexicon nếu chưa có).
- Thời gian: giờ trong ngày, đã bao lâu từ tin cuối tới hiện tại (`time_since_last`) — **cẩn thận**: dùng "thời điểm hiện tại" phải là mốc dự đoán, không lấy tin tiếp theo.

### 3.2. Candidate configurations

| Nhóm | Mô hình |
|---|---|
| Baseline | (0) luôn dự đoán đa số ; (1) **rule** : >48h không phản hồi & độ dài giảm 3 lượt (đúng luật ở tài liệu gốc) ; (2) chỉ `time_since_last` với ngưỡng |
| Tuyến tính | Logistic Regression (C ∈ {0.01, 0.1, 1, 10}, balanced/none) |
| Cây | XGBoost/LightGBM (depth ∈ {3,5}, lr ∈ {0.03,0.1}, n ∈ {200,500}) |
| Chuỗi | GRU 1 lớp (hidden ∈ {16, 32}), input = chuỗi đặc trưng theo tin ; dropout, early-stopping trên validation |

### 3.3. Mất cân bằng, ngưỡng, calibration
- Prevalence có thể thấp/cao tùy Δ → chạy độ nhạy Δ ∈ {24h, 48h, 72h}.
- 5 phương án: none / class_weight / RUS / ROS / SMOTE (SMOTE **chỉ** trên vector đặc trưng thống kê của train fold; không SMOTE trên chuỗi thô của GRU — dùng weighted loss). Lưu ý cửa sổ cùng hội thoại tự tương quan → SMOTE nội suy giữa các mẫu cùng hội thoại làm tăng trùng lặp ; ghi nhận.
- Calibration: isotonic vs Platt trên phần validation **chưa resample** ; báo ECE/reliability.
- Ngưỡng τ1, τ2: chọn trên validation bằng hàm chi phí `C_FN` (bỏ sót hội thoại sắp chết) vs `C_FP` (làm phiền người dùng), vd `C_FN : C_FP = 1 : 2`, nhóm chốt và ghi lý do ; báo độ nhạy tỉ lệ này. Test chỉ báo cáo.

### 3.4. Bias–variance
Learning curve theo số hội thoại ; SD giữa fold thời gian (fold đầu ít dữ liệu → variance cao) ; so rule (bias cao) – GBM – GRU (variance cao trên n nhỏ).

## 4. Results (khung bảng)

| Bảng | Nội dung |
|---|---|
| R3-1 | CV mean ± SD (PR-AUC, ROC-AUC, Brier, ECE, recall@P) mọi candidate, mỗi dataset (D12, S2) |
| R3-2 | Bảng 5 phương án mất cân bằng × 2 mô hình |
| R3-3 | Test (1 lần) + bootstrap cluster-hội thoại B = 2000, CI 95% ; so rule baseline của tài liệu gốc |
| R3-4 | Độ nhạy Δ, tỉ lệ chi phí, K cửa sổ (5/10/20 tin) |
| R3-5 | Chuyển miền: train D12 → test S2/P1 ; train S2 → test D12 (đo distribution shift) |
| R3-6 | Ablation nhóm đặc trưng (độ trễ / độ dài / câu hỏi / bất đối xứng / cảm xúc) + SHAP |

### 4b. Phân tích lỗi (theo `01_*` §6)
- E1: confusion matrix tại τ2 ; FN (hội thoại chết không báo) vs FP (báo nhầm) ; lead time.
- E2: slice theo độ dài hội thoại, giờ trong ngày, hội thoại ít/nhiều tin, dataset (D12/S2/P1) ; fold thời gian đầu vs cuối.
- E3: ≥ 10 hội thoại sai tự tin cao (vd im lặng vì ngủ/cuối tuần, hoặc "chết" vì vấn đề đã giải quyết xong).
- E4: nhiễu nhãn proxy, right-censoring, tự tương quan, domain shift IRC→hẹn hò.
- E5: đề xuất (latency điều chỉnh giờ ngủ, đặc trưng mới…).
- Kiến thức mở rộng: survival analysis / hazard model (thời gian đến khi "chết" thay vì nhị phân), conformal, SHAP, Optuna.

## 5. Practical limits
- **Đại diện:** Ubuntu IRC ≠ hội thoại hẹn hò ; "chết" ở đó có thể vì vấn đề đã giải quyết xong (không phải mất hứng thú). Nhãn proxy không đo ý nghĩa xã hội.
- **Phụ thuộc:** tự tương quan các mốc trong 1 hội thoại ; nhiều hội thoại cùng 1 người → nếu có user_id, group thêm theo người.
- **Right-censoring** cuối dữ liệu ; giờ ngủ (latency đêm không phải mất hứng thú) → đặc trưng "latency điều chỉnh theo giờ".
- **Distribution shift:** văn hóa nhắn tin sinh viên VN (teencode, sticker) ; kế hoạch giám sát tỉ lệ cảnh báo/tuần và tỉ lệ người dùng hưởng ứng gợi ý.
- **Phản hồi vòng kín:** gợi ý làm thay đổi hành vi → nhãn tương lai bị ảnh hưởng (log cả trạng thái có/không hiển thị gợi ý, giữ nhóm đối chứng nhỏ nếu có người dùng thật).
- **Riêng tư:** chỉ dùng số liệu hành vi + điểm cảm xúc, không lưu văn bản thô ; opt-out.
- **Tài nguyên:** đo latency (GBM ≪ GRU) ; suy luận < 50ms mục tiêu.

## 6. Công việc chi tiết

| # | Việc | Người | Ước lượng |
|---|---|---|---|
| 3A | **Simulator S2**: sinh hội thoại có timestamp, độ dài, câu hỏi, cảm xúc từ quá trình hứng thú tiềm ẩn h(t) (suy giảm + nhiễu + giờ ngủ), tham số ghi rõ ; xuất cho cả M2 (thêm nội dung tin nhắn bằng template/LLM) ; xác thực phân phối | C | 3 ngày |
| 3B | Parser D12 (lọc 2-party, timestamp), D11 (dialog act), định nghĩa mẫu/nhãn/censoring, rule baseline | C | 2 ngày |
| 3C | Feature extractor + LR/GBM/GRU + 5 phương án mất cân bằng + calibration + ngưỡng τ1/τ2 + time-aware CV (dùng khung 0B) + báo cáo M3 | C | 5 ngày (gồm báo cáo) |
| 3D | Gợi ý hành động : chủ đề chung (từ tag + kết quả 2C), câu hỏi mở (template hoặc LLM), giao diện riêng tư + opt-out, đo tỉ lệ chấp nhận trong pilot | C | 2 ngày |
| 3E | Chạy P1 chat (phối hợp thu dữ liệu chung với 2D) | C | (gộp 2D) |

**Bàn giao:** `POST /health/score` nhận danh sách tin (thời gian, độ dài, người gửi, cảm xúc) → trả `{p, level, top_factors, suggestions}`.
