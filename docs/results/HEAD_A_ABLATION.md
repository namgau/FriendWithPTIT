# Head A — ablation đặc trưng (A7, X1 phần T1/T2) và cross-domain (X4)

> Sinh bởi `python -m src.m3_health.run_ablation_a`. Chỉ tập train ; cùng hội thoại và fold forward chaining K = 5 với `HEAD_A_CV.md` ; LightGBM + none cố định, Δ = 48h. Mean ± SD qua 5 fold. T3 (B5), T4–T5 (C1, B8) sẽ thêm dòng khi có.

## X1 — ablation nhóm đặc trưng (PR-AUC)

Nhóm: **T1_rhythm** = n_msgs, conv_age_log, gap_last, gap_mean, gap_max, gap_std, gap_slope, gap_rel, burstiness ; **T1_clock** = hour, weekend, night ; **T1_asym** = share_last_sender, double_text ; **T2_surface** = words_last, words_mean, words_slope, short_share, short_last, question_share, question_last.

| Tập đặc trưng | Số đặc trưng | E1 PR-AUC | E1 Δ so với đủ | E2 PR-AUC | E2 Δ so với đủ |
|---|---|---|---|---|---|
| T1_rhythm | 9 | 0.253 ± 0.003 | -0.068 ± 0.008 | 0.436 ± 0.028 | -0.095 ± 0.002 |
| T1 | 14 | 0.296 ± 0.001 | -0.024 ± 0.006 | 0.474 ± 0.027 | -0.056 ± 0.004 |
| T1+T2 (đủ) | 21 | 0.320 ± 0.005 | — | 0.530 ± 0.029 | — |
| − T1_rhythm | 12 | 0.181 ± 0.016 | -0.140 ± 0.015 | 0.476 ± 0.030 | -0.054 ± 0.003 |
| − T1_clock | 18 | 0.322 ± 0.005 | 0.001 ± 0.002 | 0.529 ± 0.029 | -0.001 ± 0.003 |
| − T1_asym | 19 | 0.280 ± 0.005 | -0.041 ± 0.004 | 0.490 ± 0.028 | -0.040 ± 0.003 |
| T1+T2+T4 | 63 | 0.336 ± 0.009 | 0.015 ± 0.004 | 0.585 ± 0.027 | 0.054 ± 0.005 |

Δ so với đủ tính theo cặp cùng fold (âm = bỏ nhóm làm giảm PR-AUC).

## X4 — cross-domain E1 ↔ E2

Mô hình nguồn học trên **toàn bộ** train của nguồn (60k hội thoại), đánh giá trên các fold validation của đích. Prior shift dùng prevalence của outer-train đích (không cần nhãn từng mẫu đích).

| Đích | Thiết lập | PR-AUC | ROC-AUC | Brier thô | Brier sau prior shift |
|---|---|---|---|---|---|
| E2 | in-domain | 0.530 ± 0.029 | 0.701 ± 0.005 | 0.200 ± 0.006 | — |
| E2 | E1 → E2 | 0.331 ± 0.025 | 0.501 ± 0.005 | 0.302 ± 0.022 | 0.285 ± 0.013 |
| E1 | in-domain | 0.320 ± 0.005 | 0.829 ± 0.003 | 0.079 ± 0.001 | — |
| E1 | E2 → E1 | 0.132 ± 0.003 | 0.602 ± 0.003 | 0.106 ± 0.001 | 0.094 ± 0.001 |
