# Phân tích lỗi (A9) — out-of-fold trên tập train

> Sinh bởi `python -m src.m3_health.run_error_analysis`. Test **chưa mở** ; sẽ chạy lại trên test sau 20/11 (A8). Bảng không chứa nội dung tin nhắn ; ví dụ có văn bản để đọc tay nằm ở `reports/error_examples_*.md` (gitignored). Kiểu lỗi định tính (đã xong việc, chuyển kênh, ngủ, đùa thân mật…) cần A đọc tay các ví dụ đó rồi mới ghi vào báo cáo.

## Head A — E1

OOF: 280,892 mẫu, prevalence 0.103 ; LightGBM + none, isotonic. τ1 = 0.140, τ2 = 0.266 (chọn trên chính OOF này ; τ2 = ngân sách cảnh báo top 10%).

**E1 — confusion tại τ2**

| | Báo (p ≥ τ2) | Không báo |
|---|---|---|
| Tắt thật | TP 9,801 | FN 19,038 |
| Không tắt | FP 18,377 | TN 233,676 |

Precision 0.348, recall 0.340, FPR 0.073. Ở mức hời hợt (τ1): recall 0.800, FPR 0.296.

**Lead time** (ca tắt được báo ; lùi theo chuỗi mẫu liên tục được báo trong cùng hội thoại):

| Mức | Số ca TP | Tỉ lệ lead = 0 (chỉ báo ở tin cuối) | Trung vị lead nếu > 0 (giờ) | P90 lead (giờ) |
|---|---|---|---|---|
| τ1 hời hợt | 23,072 | 0.592 | 0.08 | 0.70 |
| τ2 nguy cơ | 9,801 | 0.827 | 0.33 | 0.17 |

**E2 — slice** (recall/FPR tại τ2 ; lift = PR-AUC / prevalence của slice):

| slice | giá trị | n | prevalence | PR-AUC | lift | recall@τ2 | FPR@τ2 |
|---|---|---|---|---|---|---|---|
| giờ | 0–5h | 51,348 | 0.103 | 0.319 | 3.111 | 0.313 | 0.061 |
| giờ | 6–11h | 101,985 | 0.098 | 0.301 | 3.061 | 0.313 | 0.066 |
| giờ | 12–17h | 86,642 | 0.106 | 0.322 | 3.044 | 0.379 | 0.084 |
| giờ | 18–23h | 40,917 | 0.107 | 0.311 | 2.910 | 0.351 | 0.082 |
| cuối tuần | cuối tuần | 83,629 | 0.102 | 0.309 | 3.033 | 0.333 | 0.071 |
| cuối tuần | ngày thường | 197,263 | 0.103 | 0.314 | 3.049 | 0.343 | 0.074 |
| số tin | 6–10 | 149,562 | 0.067 | 0.329 | 4.928 | 0.351 | 0.043 |
| số tin | 11–20 | 82,350 | 0.184 | 0.314 | 1.708 | 0.369 | 0.162 |
| số tin | 21–50 | 35,537 | 0.084 | 0.257 | 3.056 | 0.203 | 0.038 |
| số tin | >50 | 13,443 | 0.055 | 0.184 | 3.348 | 0.141 | 0.022 |
| tuổi hội thoại | < 1h | 229,381 | 0.091 | 0.307 | 3.367 | 0.296 | 0.054 |
| tuổi hội thoại | 1–24h | 17,411 | 0.115 | 0.321 | 2.800 | 0.394 | 0.095 |
| tuổi hội thoại | > 24h | 34,100 | 0.174 | 0.331 | 1.900 | 0.477 | 0.199 |

**E3 — đóng góp SHAP trung bình |log-odds|** (top 8): n_msgs 1.246, share_last_sender 0.465, conv_age_log 0.209, gap_max 0.198, question_last 0.166, gap_mean 0.096, question_share 0.090, words_last 0.075.

**E3 — 10 FP + 10 FN tự tin nhất** (không kèm văn bản ; văn bản trong `reports/`, không commit):

| loại | p | im lặng sau đó (giờ) | số tin | gap_last (phút) | top SHAP |
|---|---|---|---|---|---|
| FP | 1.00 | 4.97 | 10 | 0.00 | share_last_sender +1.96, n_msgs +1.42, words_mean +0.59 |
| FP | 1.00 | 0.05 | 10 | 0.00 | share_last_sender +2.03, n_msgs +1.33, words_mean +0.55 |
| FP | 1.00 | 0.13 | 10 | 1.00 | share_last_sender +2.01, n_msgs +1.44, words_mean +0.53 |
| FP | 1.00 | 0.02 | 12 | 1310379.00 | n_msgs +1.14, gap_max +0.59, conv_age_log +0.57 |
| FP | 1.00 | 0.08 | 20 | 2902924.00 | n_msgs +1.01, gap_max +0.88, conv_age_log +0.67 |
| FP | 0.90 | 0.00 | 10 | 1.00 | n_msgs +0.89, gap_max +0.72, conv_age_log +0.60 |
| FP | 0.90 | 0.02 | 10 | 0.00 | share_last_sender +1.72, n_msgs +1.40, question_last -0.35 |
| FP | 0.90 | 0.00 | 10 | 83486.00 | n_msgs +0.85, gap_max +0.74, conv_age_log +0.50 |
| FP | 0.90 | 0.15 | 10 | 0.00 | share_last_sender +1.51, n_msgs +1.38, words_last +0.29 |
| FP | 0.90 | 0.02 | 10 | 0.00 | n_msgs +1.04, conv_age_log +0.62, gap_max +0.59 |
| FN | 0.00 | 142.63 | 8 | 1.00 | n_msgs -1.62, share_last_sender -1.17, gap_max -0.21 |
| FN | 0.00 | 632.72 | 8 | 0.00 | n_msgs -1.44, share_last_sender -1.02, conv_age_log -0.37 |
| FN | 0.00 | 1777.25 | 8 | 1.00 | n_msgs -1.71, share_last_sender -0.83, question_last -0.28 |
| FN | 0.00 | 192.93 | 8 | 2.00 | n_msgs -1.62, share_last_sender -1.22, conv_age_log -0.24 |
| FN | 0.00 | 36064.77 | 8 | 1.00 | n_msgs -1.74, share_last_sender -0.64, conv_age_log +0.39 |
| FN | 0.00 | 285.78 | 8 | 0.00 | n_msgs -1.64, share_last_sender -0.67, question_last -0.37 |
| FN | 0.00 | 8646.52 | 8 | 0.00 | n_msgs -1.77, share_last_sender -1.04, gap_max -0.18 |
| FN | 0.00 | 118.47 | 8 | 1.00 | n_msgs -1.63, share_last_sender -1.02, gap_mean +0.24 |
| FN | 0.00 | 7919.58 | 6 | 0.00 | n_msgs -1.49, question_last -0.44, share_last_sender +0.33 |
| FN | 0.00 | 1296.53 | 6 | 0.00 | n_msgs -1.50, share_last_sender +0.43, conv_age_log -0.29 |

## Head A — E2

OOF: 98,918 mẫu, prevalence 0.339 ; LightGBM + none, isotonic. τ1 = 0.271, τ2 = 0.563 (chọn trên chính OOF này ; τ2 = ngân sách cảnh báo top 10%).

**E1 — confusion tại τ2**

| | Báo (p ≥ τ2) | Không báo |
|---|---|---|
| Tắt thật | TP 6,201 | FN 27,352 |
| Không tắt | FP 3,813 | TN 61,552 |

Precision 0.619, recall 0.185, FPR 0.058. Ở mức hời hợt (τ1): recall 0.806, FPR 0.540.

**Lead time** (ca tắt được báo ; lùi theo chuỗi mẫu liên tục được báo trong cùng hội thoại):

| Mức | Số ca TP | Tỉ lệ lead = 0 (chỉ báo ở tin cuối) | Trung vị lead nếu > 0 (giờ) | P90 lead (giờ) |
|---|---|---|---|---|
| τ1 hời hợt | 27,042 | 0.740 | 1.22 | 2.86 |
| τ2 nguy cơ | 6,201 | 0.949 | 1.38 | 0.00 |

**E2 — slice** (recall/FPR tại τ2 ; lift = PR-AUC / prevalence của slice):

| slice | giá trị | n | prevalence | PR-AUC | lift | recall@τ2 | FPR@τ2 |
|---|---|---|---|---|---|---|---|
| giờ | 0–5h | 17,473 | 0.349 | 0.540 | 1.546 | 0.215 | 0.072 |
| giờ | 6–11h | 20,741 | 0.302 | 0.473 | 1.563 | 0.118 | 0.034 |
| giờ | 12–17h | 32,158 | 0.338 | 0.522 | 1.543 | 0.170 | 0.050 |
| giờ | 18–23h | 28,546 | 0.361 | 0.542 | 1.503 | 0.223 | 0.078 |
| cuối tuần | cuối tuần | 22,120 | 0.329 | 0.511 | 1.553 | 0.177 | 0.056 |
| cuối tuần | ngày thường | 76,798 | 0.342 | 0.527 | 1.540 | 0.187 | 0.059 |
| số tin | 6–10 | 82,049 | 0.365 | 0.539 | 1.479 | 0.206 | 0.073 |
| số tin | 11–20 | 13,631 | 0.234 | 0.333 | 1.423 | 0.008 | 0.002 |
| số tin | 21–50 | 2,914 | 0.153 | 0.247 | 1.618 | 0.000 | 0.000 |
| số tin | >50 | 324 | 0.034 | 0.036 | 1.050 | 0.000 | 0.000 |
| tuổi hội thoại | < 1h | 29,064 | 0.308 | 0.499 | 1.621 | 0.145 | 0.041 |
| tuổi hội thoại | 1–24h | 46,374 | 0.356 | 0.541 | 1.517 | 0.194 | 0.064 |
| tuổi hội thoại | > 24h | 23,480 | 0.345 | 0.515 | 1.495 | 0.209 | 0.071 |

**E3 — đóng góp SHAP trung bình |log-odds|** (top 8): share_last_sender 0.278, n_msgs 0.210, question_last 0.197, gap_last 0.109, hour 0.104, words_slope 0.101, gap_mean 0.092, words_last 0.088.

**E3 — 10 FP + 10 FN tự tin nhất** (không kèm văn bản ; văn bản trong `reports/`, không commit):

| loại | p | im lặng sau đó (giờ) | số tin | gap_last (phút) | top SHAP |
|---|---|---|---|---|---|
| FP | 1.00 | 3.82 | 6 | 2886.62 | gap_std +0.40, gap_slope +0.34, gap_last +0.32 |
| FP | 1.00 | 17.77 | 10 | 10274.02 | words_last +0.61, gap_slope +0.42, words_mean +0.25 |
| FP | 0.96 | 0.02 | 6 | 5.20 | words_mean +0.66, share_last_sender +0.36, n_msgs +0.34 |
| FP | 0.94 | 0.68 | 6 | 2.63 | words_mean +0.89, n_msgs +0.35, share_last_sender +0.32 |
| FP | 0.93 | 0.05 | 6 | 101.80 | words_slope +0.41, share_last_sender +0.36, n_msgs +0.28 |
| FP | 0.93 | 0.02 | 8 | 1.97 | words_mean +0.49, words_last +0.32, share_last_sender +0.29 |
| FP | 0.93 | 0.01 | 6 | 3.47 | words_mean +0.57, share_last_sender +0.29, words_last +0.26 |
| FP | 0.93 | 0.03 | 6 | 12.65 | words_slope +0.47, share_last_sender +0.31, n_msgs +0.27 |
| FP | 0.93 | 0.15 | 6 | 37.70 | words_slope +0.44, n_msgs +0.31, share_last_sender +0.29 |
| FP | 0.93 | 26.21 | 6 | 21.77 | words_slope +0.57, share_last_sender +0.40, n_msgs +0.29 |
| FN | 0.00 | 780.76 | 14 | 1277.70 | gap_rel -0.53, question_last -0.45, words_last -0.38 |
| FN | 0.00 | 861.40 | 66 | 1.88 | n_msgs -1.39, share_last_sender -0.60, words_last -0.24 |
| FN | 0.00 | 625.52 | 8 | 1.10 | share_last_sender -1.26, gap_max -0.28, gap_last -0.26 |
| FN | 0.00 | 813.56 | 22 | 2.83 | share_last_sender -0.89, n_msgs -0.59, words_last -0.28 |
| FN | 0.00 | 546.49 | 98 | 52.13 | n_msgs -1.75, share_last_sender -0.75, gap_slope +0.19 |
| FN | 0.00 | 444.14 | 86 | 2.00 | n_msgs -1.77, share_last_sender -0.84, gap_std -0.21 |
| FN | 0.00 | 445.81 | 62 | 5.18 | n_msgs -1.73, words_mean -0.45, words_last -0.18 |
| FN | 0.00 | 828.74 | 8 | 2.28 | share_last_sender -0.70, words_mean -0.48, hour -0.20 |
| FN | 0.00 | 1211.20 | 30 | 0.58 | n_msgs -0.98, share_last_sender -0.82, gap_last -0.46 |
| FN | 0.02 | 448.83 | 26 | 11.07 | share_last_sender -0.76, n_msgs -0.63, words_last -0.49 |

## Head A — E4 learning curve (fold cuối, lấy ngẫu nhiên một phần group của outer-train)

| dataset | tỉ lệ train | n_train | PR-AUC train | PR-AUC val |
|---|---|---|---|---|
| E1 | 0.100 | 27,808 | 0.732 | 0.301 |
| E1 | 0.250 | 69,991 | 0.526 | 0.314 |
| E1 | 0.500 | 139,936 | 0.432 | 0.318 |
| E1 | 1.000 | 281,584 | 0.385 | 0.325 |
| E2 | 0.100 | 10,457 | 0.882 | 0.531 |
| E2 | 0.250 | 26,540 | 0.728 | 0.560 |
| E2 | 0.500 | 52,255 | 0.643 | 0.567 |
| E2 | 1.000 | 105,029 | 0.592 | 0.575 |

Khoảng cách train − val lớn và val còn tăng theo n → variance ; val phẳng → thiếu đặc trưng / nhiễu nhãn (bias).

## Head B — G1

OOF tfidf_lr: 3,348 mẫu, prevalence 0.500 ; τ_B theo FPR ≤ 5% trên calibration từng fold. FP = 95, FN = 1,478. CI 95% cluster bootstrap theo group (B = 1000).

| slice | giá trị | n lành | FPR (buộc tội oan) | n độc hại | recall |
|---|---|---|---|---|---|
| toàn bộ |  | 1,674 | 0.057 [0.046, 0.068] | 1,674 | 0.117 [0.102, 0.133] |
| có từ trong lexicon độc hại | có | 33 | 0.242 [0.121, 0.394] | 51 | 0.294 [0.163, 0.431] |
| có từ trong lexicon độc hại | không | 1,641 | 0.053 [0.042, 0.064] | 1,623 | 0.112 [0.097, 0.126] |
| dài (> trung vị số từ) | có | 759 | 0.105 [0.086, 0.127] | 912 | 0.155 [0.132, 0.178] |
| dài (> trung vị số từ) | không | 915 | 0.016 [0.009, 0.025] | 762 | 0.072 [0.055, 0.090] |

## Head B — H1

OOF tfidf_lr: 26,719 mẫu, prevalence 0.173 ; τ_B theo FPR ≤ 5% trên calibration từng fold. FP = 1,064, FN = 1,960. CI 95% cluster bootstrap theo group (B = 1000).

| slice | giá trị | n lành | FPR (buộc tội oan) | n độc hại | recall |
|---|---|---|---|---|---|
| toàn bộ |  | 22,099 | 0.048 [0.045, 0.051] | 4,620 | 0.576 [0.562, 0.591] |
| có từ trong lexicon độc hại | có | 440 | 0.543 [0.492, 0.592] | 686 | 0.924 [0.900, 0.945] |
| có từ trong lexicon độc hại | không | 21,659 | 0.038 [0.036, 0.041] | 3,934 | 0.515 [0.499, 0.530] |
| dài (> trung vị số từ) | có | 9,285 | 0.078 [0.073, 0.084] | 2,809 | 0.609 [0.590, 0.626] |
| dài (> trung vị số từ) | không | 12,814 | 0.026 [0.023, 0.029] | 1,811 | 0.525 [0.500, 0.549] |
| có teencode / viết tắt | có | 3,404 | 0.110 [0.100, 0.121] | 1,083 | 0.709 [0.682, 0.737] |
| có teencode / viết tắt | không | 18,695 | 0.037 [0.034, 0.040] | 3,537 | 0.535 [0.518, 0.553] |
| không dấu tiếng Việt | có | 1,723 | 0.042 [0.032, 0.053] | 363 | 0.540 [0.482, 0.596] |
| không dấu tiếng Việt | không | 20,376 | 0.049 [0.046, 0.052] | 4,257 | 0.579 [0.564, 0.593] |

## Head B — H2

OOF tfidf_lr: 8,001 mẫu, prevalence 0.110 ; τ_B theo FPR ≤ 5% trên calibration từng fold. FP = 377, FN = 615. CI 95% cluster bootstrap theo group (B = 1000).

| slice | giá trị | n lành | FPR (buộc tội oan) | n độc hại | recall |
|---|---|---|---|---|---|
| toàn bộ |  | 7,119 | 0.053 [0.041, 0.066] | 882 | 0.303 [0.240, 0.369] |
| có từ trong lexicon độc hại | có | 6 | — | 1 | — |
| có từ trong lexicon độc hại | không | 7,113 | 0.052 [0.040, 0.066] | 881 | 0.303 [0.241, 0.370] |
| dài (> trung vị số từ) | có | 3,444 | 0.071 [0.052, 0.094] | 476 | 0.311 [0.225, 0.389] |
| dài (> trung vị số từ) | không | 3,675 | 0.036 [0.027, 0.044] | 406 | 0.293 [0.222, 0.366] |
| có teencode / viết tắt | có | 678 | 0.065 [0.042, 0.093] | 120 | 0.392 [0.286, 0.493] |
| có teencode / viết tắt | không | 6,441 | 0.052 [0.040, 0.066] | 762 | 0.289 [0.228, 0.353] |
| không dấu tiếng Việt | có | 24 | 0.000 [0.000, 0.000] | 0 | — |
| không dấu tiếng Việt | không | 7,095 | 0.053 [0.042, 0.067] | 882 | 0.303 [0.240, 0.369] |

