# Head B — phân tích lỗi và horizon trên tập train

> Sinh bởi `python -m src.m3_health.analyze_head_b`. Chỉ dùng out-of-fold của tập train; test **chưa mở**.
> Ngưỡng mô tả: τ_B toàn cục trên OOF theo FPR ≤ 5%. Đây là phân tích chẩn đoán, không dùng để chọn mô hình.

## Tổng quan lỗi

| Tập | N | Prevalence | τ_B | PR-AUC | Precision | Recall | F1 | FPR | FP | FN |
|---|---|---|---|---|---|---|---|---|---|---|
| G1 | 3,348 | 0.500 | 0.698 | 0.626 | 0.668 | 0.100 | 0.174 | 0.050 | 83 | 1507 |
| H1 | 26,719 | 0.173 | 0.406 | 0.706 | 0.710 | 0.583 | 0.641 | 0.050 | 1100 | 1925 |
| H2 | 8,001 | 0.110 | 0.267 | 0.342 | 0.425 | 0.297 | 0.350 | 0.050 | 355 | 620 |

## Ví dụ lỗi

Top 5 FP và top 5 FN mỗi tập (có nội dung bình luận) ở `reports/headB_error_examples.md` — không commit nội dung tin nhắn lên repo.

## Metric theo slice

| Tập | Slice | Giá trị | N | Dương tính | PR-AUC | Precision | Recall | FPR |
|---|---|---|---|---|---|---|---|---|
| G1 | length_bucket | ≤50 | 5 | 1 | 0.333 | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | 51–100 | 2 | 0 | — | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | 101–200 | 43 | 16 | 0.418 | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | 201–400 | 172 | 72 | 0.599 | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | >400 | 3,126 | 1,585 | 0.629 | 0.668 | 0.105 | 0.054 |
| G1 | lexicon_bucket | có từ độc hại | 84 | 51 | 0.655 | 0.650 | 0.255 | 0.212 |
| G1 | lexicon_bucket | không | 3,264 | 1,623 | 0.625 | 0.670 | 0.095 | 0.046 |
| G1 | prefix_bucket | 2 | 315 | 106 | 0.491 | 1.000 | 0.009 | 0.000 |
| G1 | prefix_bucket | 3–4 | 1,043 | 480 | 0.585 | 0.786 | 0.023 | 0.005 |
| G1 | prefix_bucket | 5–9 | 1,929 | 1,069 | 0.677 | 0.706 | 0.133 | 0.069 |
| G1 | prefix_bucket | 10+ | 61 | 19 | 0.549 | 0.382 | 0.684 | 0.500 |
| H1 | length_bucket | ≤50 | 18,976 | 2,544 | 0.687 | 0.734 | 0.526 | 0.030 |
| H1 | length_bucket | 51–100 | 5,326 | 1,145 | 0.711 | 0.710 | 0.559 | 0.063 |
| H1 | length_bucket | 101–200 | 1,837 | 663 | 0.781 | 0.699 | 0.706 | 0.172 |
| H1 | length_bucket | 201–400 | 558 | 256 | 0.820 | 0.626 | 0.930 | 0.470 |
| H1 | length_bucket | >400 | 22 | 12 | 0.573 | 0.571 | 1.000 | 0.900 |
| H1 | lexicon_bucket | có từ độc hại | 1,126 | 686 | 0.880 | 0.721 | 0.926 | 0.559 |
| H1 | lexicon_bucket | không | 25,593 | 3,934 | 0.667 | 0.707 | 0.524 | 0.039 |
| H2 | length_bucket | ≤50 | 2,144 | 219 | 0.356 | 0.500 | 0.274 | 0.031 |
| H2 | length_bucket | 51–100 | 2,396 | 237 | 0.351 | 0.433 | 0.287 | 0.041 |
| H2 | length_bucket | 101–200 | 2,115 | 235 | 0.350 | 0.443 | 0.281 | 0.044 |
| H2 | length_bucket | 201–400 | 1,046 | 138 | 0.295 | 0.328 | 0.275 | 0.086 |
| H2 | length_bucket | >400 | 300 | 53 | 0.467 | 0.400 | 0.566 | 0.182 |
| H2 | lexicon_bucket | có từ độc hại | 7 | 1 | 0.167 | 0.000 | 0.000 | 0.833 |
| H2 | lexicon_bucket | không | 7,994 | 881 | 0.344 | 0.428 | 0.297 | 0.049 |

## Horizon/lead-time trên G1

Chỉ tính trên hội thoại hỏng. `lead = attack_turn_idx - eval_len`; `eval_len = prefix_len` là cảnh báo ngay trước lượt công kích (lead 0). Cảnh báo sớm được chọn theo lần đầu tiên `p ≥ τ_B` trên các tiền tố ngắn hơn.

| Fold | N | Phát hiện sớm | Tỉ lệ | Median lead | TP tại prefix | Median lead TP | τ_B |
|---|---|---|---|---|---|---|---|
| 0 | 333 | 57 | 0.171 | 0.0 | 45 | 0.0 | 0.695 |
| 1 | 333 | 47 | 0.141 | 1.0 | 34 | 0.5 | 0.690 |
| 2 | 335 | 32 | 0.096 | 1.0 | 24 | 1.0 | 0.690 |
| 3 | 334 | 65 | 0.195 | 2.0 | 48 | 1.0 | 0.669 |
| 4 | 339 | 45 | 0.133 | 0.0 | 45 | 0.0 | 0.696 |

- Tổng số hội thoại hỏng: **1674**; cảnh báo sớm ở bất kỳ tiền tố nào: **246** (14.7%).
- Tại tiền tố triển khai (đúng giao thức hiện tại): **196 TP (11.7%)**. Trong số TP này, lead trung vị: **0.0 lượt**; IQR: **0.0–2.0**.
- Trong số TP tại prefix, phát hiện trước ≥1 lượt: **48.0%**; ≥2 lượt: **30.1%**; ≥3 lượt: **16.3%**.
- Trên toàn bộ hội thoại hỏng, cảnh báo trước ≥1 lượt: **8.6%**; ≥2 lượt: **5.4%**; ≥3 lượt: **3.0%**.

> Lưu ý: mô hình được huấn luyện trên tiền tố ngay trước lượt công kích; điểm ở các tiền tố ngắn hơn là ngoài phân phối huấn luyện. Ngưỡng giữ nguyên theo calibration, nên horizon chỉ là phân tích khảo sát, không phải metric chọn mô hình.
