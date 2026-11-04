# Head B — E5: nhiễu nhãn và sửa lỗi buộc tội oan (H1, H2)

> Sinh bởi `python -m src.m3_health.run_head_b_e5`. Chỉ tập train, cùng fold / calibration / τ_B (FPR ≤ 5%) với `HEAD_B_CV.md`. **Mọi biến thể đánh giá trên nhãn gốc.** Mean ± SD qua 5 fold ; Δ = chênh lệch theo cặp fold so với base.

## 1. Kiểm tra nhiễu nhãn (tự động, trên OOF của base)

| | H1 | H2 |
|---|---|---|
| số mẫu | 26,719 | 8,001 |
| văn bản trùng có nhãn mâu thuẫn | 162 | 1 |
| số dòng thuộc các văn bản đó | 390 | 2 |
| dòng chỉ có URL | 0 | 0 |
| tỉ lệ nghi nhiễu (confident learning) | 0.045 | 0.098 |
| … trong nhãn 0 (sạch) | 0.032 | 0.078 |
| … trong nhãn 1 (độc hại) | 0.106 | 0.255 |
| tỉ lệ FP thuộc diện nghi nhiễu | 0.666 | 1.000 |
| PR-AUC base | 0.706 | 0.342 |
| PR-AUC base, bỏ group trùng mâu thuẫn | 0.717 | 0.343 |
| FPR base | 0.048 | 0.053 |
| FPR base, bỏ group trùng mâu thuẫn | 0.046 | 0.053 |

Confident learning chỉ cho biết mẫu **nghi** nhiễu (mô hình rất tự tin ngược nhãn) — gồm cả nhãn sai thật lẫn lỗi thật của mô hình ; tỉ lệ nhãn sai thật xem mục đọc tay.

## 2. Biến thể (cộng dồn)

### H1

| Biến thể | Bỏ khỏi fit | PR-AUC | FPR | Recall | FPR có từ chửi | Recall có từ chửi | FPR không từ chửi | FPR teencode |
|---|---|---|---|---|---|---|---|---|
| base | 0 | 0.708 ± 0.013 | 0.048 ± 0.005 | 0.576 ± 0.026 | 0.503 ± 0.031 | 0.862 ± 0.011 | 0.034 ± 0.005 | 0.110 ± 0.014 |
| + url token | 0 | 0.708 ± 0.013 (Δ +0.000) | 0.048 ± 0.005 (Δ +0.000) | 0.576 ± 0.026 (Δ -0.000) | 0.506 ± 0.032 (Δ +0.003) | 0.863 ± 0.012 (Δ +0.001) | 0.034 ± 0.005 (Δ +0.000) | 0.111 ± 0.014 (Δ +0.001) |
| + url + chửi có/không đối tượng | 0 | 0.716 ± 0.013 (Δ +0.008) | 0.049 ± 0.005 (Δ +0.001) | 0.582 ± 0.030 (Δ +0.007) | 0.706 ± 0.046 (Δ +0.203) | 0.969 ± 0.012 (Δ +0.107) | 0.029 ± 0.005 (Δ -0.005) | 0.118 ± 0.019 (Δ +0.007) |
| + url + chửi + lọc nghi nhiễu | 851 | 0.677 ± 0.012 (Δ -0.031) | 0.048 ± 0.004 (Δ +0.000) | 0.532 ± 0.023 (Δ -0.043) | 0.820 ± 0.022 (Δ +0.317) | 0.985 ± 0.007 (Δ +0.123) | 0.024 ± 0.003 (Δ -0.010) | 0.143 ± 0.012 (Δ +0.033) |

### H2

| Biến thể | Bỏ khỏi fit | PR-AUC | FPR | Recall | FPR có từ chửi | Recall có từ chửi | FPR không từ chửi | FPR teencode |
|---|---|---|---|---|---|---|---|---|
| base | 0 | 0.349 ± 0.044 | 0.053 ± 0.020 | 0.303 ± 0.104 | 0.174 ± 0.143 | nan ± nan | 0.052 ± 0.020 | 0.066 ± 0.024 |
| + url token | 0 | 0.349 ± 0.044 (Δ -0.000) | 0.053 ± 0.020 (Δ +0.000) | 0.303 ± 0.104 (Δ +0.000) | 0.174 ± 0.143 (Δ +0.000) | nan ± nan (Δ +0.000) | 0.052 ± 0.020 (Δ +0.000) | 0.066 ± 0.024 (Δ +0.000) |
| + url + chửi có/không đối tượng | 0 | 0.338 ± 0.047 (Δ -0.011) | 0.055 ± 0.020 (Δ +0.002) | 0.293 ± 0.104 (Δ -0.010) | 0.407 ± 0.205 (Δ +0.233) | nan ± nan (Δ -0.045) | 0.051 ± 0.020 (Δ -0.001) | 0.067 ± 0.023 (Δ +0.001) |
| + url + chửi + lọc nghi nhiễu | 734 | 0.269 ± 0.049 (Δ -0.079) | 0.049 ± 0.013 (Δ -0.004) | 0.211 ± 0.079 (Δ -0.092) | 0.407 ± 0.328 (Δ +0.233) | nan ± nan (Δ -0.224) | 0.046 ± 0.013 (Δ -0.007) | 0.062 ± 0.014 (Δ -0.004) |

Đặc trưng chửi: `swear_directed` = từ chửi cách từ chỉ đối tượng (mày, thằng, con, bọn, ad…) ≤ 3 token ; `swear_exclaim` = có từ chửi nhưng không có đối tượng gần (đm, vl dùng như từ cảm thán).

