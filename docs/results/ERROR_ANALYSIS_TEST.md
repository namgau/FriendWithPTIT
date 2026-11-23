# Phân tích lỗi (A9) — trên TEST

> Sinh bởi `python -m src.m3_health.run_error_analysis_test` từ điểm test của `run_test` (A8). Không fit lại, không chọn lại ngưỡng trên test ; τ lấy từ `experiments/test_taus.json`. Bảng không chứa nội dung tin nhắn ; ví dụ có văn bản ở `reports/error_examples_test.md` (gitignored).

## Head A — E1 (test, lgbm_T1-T4)

497,796 mẫu, prevalence 0.102 ; τ1 = 0.1342, τ2 = 0.2842 chọn trên OOF train.

| | Báo (p ≥ τ2) | Không báo |
|---|---|---|
| Tắt thật | TP 19,168 | FN 31,372 |
| Không tắt | FP 33,447 | TN 413,809 |

Precision 0.364, recall 0.379, FPR 0.075. Mức hời hợt (τ1): recall 0.765, FPR 0.247.

| Mức | Số ca TP | Tỉ lệ lead = 0 | Trung vị lead nếu > 0 (giờ) | P90 lead (giờ) |
|---|---|---|---|---|
| τ1 hời hợt | 38,674 | 0.628 | 0.08 | 0.27 |
| τ2 nguy cơ | 19,168 | 0.800 | 0.12 | 0.13 |

Slice (recall/FPR tại τ2 ; lift = PR-AUC / prevalence):

| slice | giá trị | n | prevalence | PR-AUC | lift | recall@τ2 | FPR@τ2 |
|---|---|---|---|---|---|---|---|
| giờ | 0–5h | 138,246 | 0.102 | 0.342 | 3.362 | 0.373 | 0.071 |
| giờ | 6–11h | 119,075 | 0.097 | 0.329 | 3.377 | 0.362 | 0.071 |
| giờ | 12–17h | 103,914 | 0.102 | 0.335 | 3.274 | 0.395 | 0.079 |
| giờ | 18–23h | 136,561 | 0.104 | 0.340 | 3.256 | 0.388 | 0.079 |
| cuối tuần | cuối tuần | 143,373 | 0.101 | 0.332 | 3.294 | 0.381 | 0.077 |
| cuối tuần | ngày thường | 354,423 | 0.102 | 0.339 | 3.326 | 0.379 | 0.074 |
| số tin | 6–10 | 269,557 | 0.065 | 0.353 | 5.397 | 0.407 | 0.047 |
| số tin | 11–20 | 149,020 | 0.182 | 0.337 | 1.852 | 0.399 | 0.159 |
| số tin | 21–50 | 59,120 | 0.084 | 0.291 | 3.470 | 0.226 | 0.036 |
| số tin | >50 | 20,099 | 0.043 | 0.145 | 3.368 | 0.072 | 0.011 |
| tuổi hội thoại | < 1h | 421,614 | 0.093 | 0.338 | 3.618 | 0.353 | 0.061 |
| tuổi hội thoại | 1–24h | 27,348 | 0.113 | 0.326 | 2.874 | 0.398 | 0.095 |
| tuổi hội thoại | > 24h | 48,834 | 0.166 | 0.337 | 2.035 | 0.498 | 0.194 |

## Head A — E2 (test, lgbm_T1-T4)

25,259 mẫu, prevalence 0.332 ; τ1 = 0.2491, τ2 = 0.6404 chọn trên OOF train.

| | Báo (p ≥ τ2) | Không báo |
|---|---|---|
| Tắt thật | TP 1,890 | FN 6,499 |
| Không tắt | FP 671 | TN 16,199 |

Precision 0.738, recall 0.225, FPR 0.040. Mức hời hợt (τ1): recall 0.838, FPR 0.521.

| Mức | Số ca TP | Tỉ lệ lead = 0 | Trung vị lead nếu > 0 (giờ) | P90 lead (giờ) |
|---|---|---|---|---|
| τ1 hời hợt | 7,029 | 0.752 | 0.82 | 1.52 |
| τ2 nguy cơ | 1,890 | 0.949 | 0.97 | 0.00 |

Slice (recall/FPR tại τ2 ; lift = PR-AUC / prevalence):

| slice | giá trị | n | prevalence | PR-AUC | lift | recall@τ2 | FPR@τ2 |
|---|---|---|---|---|---|---|---|
| giờ | 0–5h | 4,591 | 0.325 | 0.600 | 1.842 | 0.253 | 0.043 |
| giờ | 6–11h | 5,021 | 0.303 | 0.567 | 1.873 | 0.184 | 0.029 |
| giờ | 12–17h | 7,792 | 0.337 | 0.598 | 1.775 | 0.223 | 0.037 |
| giờ | 18–23h | 7,855 | 0.350 | 0.599 | 1.711 | 0.236 | 0.049 |
| cuối tuần | cuối tuần | 5,982 | 0.212 | 0.452 | 2.131 | 0.225 | 0.041 |
| cuối tuần | ngày thường | 19,277 | 0.369 | 0.628 | 1.700 | 0.225 | 0.039 |
| số tin | 6–10 | 21,786 | 0.351 | 0.608 | 1.731 | 0.245 | 0.047 |
| số tin | 11–20 | 3,042 | 0.220 | 0.382 | 1.734 | 0.016 | 0.001 |
| số tin | 21–50 | 418 | 0.146 | 0.329 | 2.258 | 0.000 | 0.000 |
| tuổi hội thoại | < 1h | 8,773 | 0.318 | 0.591 | 1.862 | 0.210 | 0.031 |
| tuổi hội thoại | 1–24h | 12,188 | 0.341 | 0.597 | 1.750 | 0.243 | 0.044 |
| tuổi hội thoại | > 24h | 4,298 | 0.336 | 0.583 | 1.737 | 0.204 | 0.044 |

## Head B — G1 (test, tfidf_lr)

840 mẫu, prevalence 0.500 ; τ_B = 0.6892 chọn trên train/calibration. FP = 22, FN = 377. CI 95% cluster bootstrap theo group (B = 1000).

| slice | giá trị | n lành | FPR (buộc tội oan) | n độc hại | recall |
|---|---|---|---|---|---|
| toàn bộ |  | 420 | 0.052 [0.033, 0.073] | 420 | 0.102 [0.073, 0.130] |
| có từ trong lexicon độc hại | có | 7 | — | 13 | — |
| có từ trong lexicon độc hại | không | 413 | 0.051 [0.031, 0.073] | 407 | 0.096 [0.069, 0.125] |
| dài (> trung vị số từ) | có | 190 | 0.095 [0.055, 0.138] | 230 | 0.152 [0.107, 0.196] |
| dài (> trung vị số từ) | không | 230 | 0.017 [0.004, 0.035] | 190 | 0.042 [0.015, 0.072] |

## Head B — H1 (test, phobert_c2)

6,681 mẫu, prevalence 0.173 ; τ_B = 0.4890 chọn trên train/calibration. FP = 449, FN = 267. CI 95% cluster bootstrap theo group (B = 1000).

| slice | giá trị | n lành | FPR (buộc tội oan) | n độc hại | recall |
|---|---|---|---|---|---|
| toàn bộ |  | 5,525 | 0.081 [0.074, 0.089] | 1,156 | 0.769 [0.743, 0.793] |
| có từ trong lexicon độc hại | có | 108 | 0.333 [0.240, 0.439] | 163 | 0.902 [0.850, 0.940] |
| có từ trong lexicon độc hại | không | 5,417 | 0.076 [0.069, 0.084] | 993 | 0.747 [0.717, 0.774] |
| dài (> trung vị số từ) | có | 2,422 | 0.114 [0.101, 0.126] | 721 | 0.788 [0.758, 0.817] |
| dài (> trung vị số từ) | không | 3,103 | 0.056 [0.048, 0.065] | 435 | 0.738 [0.698, 0.779] |
| có teencode / viết tắt | có | 900 | 0.122 [0.100, 0.147] | 287 | 0.815 [0.768, 0.859] |
| có teencode / viết tắt | không | 4,625 | 0.073 [0.065, 0.081] | 869 | 0.754 [0.723, 0.783] |
| không dấu tiếng Việt | có | 464 | 0.108 [0.079, 0.141] | 81 | 0.765 [0.658, 0.855] |
| không dấu tiếng Việt | không | 5,061 | 0.079 [0.071, 0.087] | 1,075 | 0.769 [0.743, 0.794] |

## Head B — H2 (test, tfidf_lr)

1,999 mẫu, prevalence 0.110 ; τ_B = 0.2526 chọn trên train/calibration. FP = 100, FN = 135. CI 95% cluster bootstrap theo group (B = 1000).

| slice | giá trị | n lành | FPR (buộc tội oan) | n độc hại | recall |
|---|---|---|---|---|---|
| toàn bộ |  | 1,780 | 0.056 [0.037, 0.076] | 219 | 0.384 [0.242, 0.489] |
| có từ trong lexicon độc hại | có | 1 | — | 0 | — |
| có từ trong lexicon độc hại | không | 1,779 | 0.056 [0.037, 0.076] | 219 | 0.384 [0.242, 0.489] |
| dài (> trung vị số từ) | có | 873 | 0.077 [0.047, 0.110] | 108 | 0.398 [0.257, 0.531] |
| dài (> trung vị số từ) | không | 907 | 0.036 [0.020, 0.056] | 111 | 0.369 [0.200, 0.503] |
| có teencode / viết tắt | có | 158 | 0.120 [0.063, 0.185] | 18 | — |
| có teencode / viết tắt | không | 1,622 | 0.050 [0.031, 0.071] | 201 | 0.373 [0.244, 0.470] |
| không dấu tiếng Việt | có | 4 | — | 0 | — |
| không dấu tiếng Việt | không | 1,776 | 0.056 [0.037, 0.076] | 219 | 0.384 [0.242, 0.489] |

