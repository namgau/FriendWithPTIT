# Head B — kết quả CV trên tập train

> Sinh bởi `python -m src.m3_health.run_head_b`. Tập test **chưa mở** (khóa tới 20/11).
> Outer: group K-fold K = 5 theo `group_id`; calibrator platt fit trên 20% group của outer-train; τ_B chọn theo FPR ≤ 5.0%.
> G1 dùng tiền tố trước lượt công kích; H1/H2 dùng từng bình luận. Mean ± SD qua các fold.

## G1

N = 3,348 mẫu, 3,348 hội thoại, 1,446 group, prevalence 0.500.

| Mô hình | PR-AUC | ROC-AUC | Brier | ECE | τ_B | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|---|---|---|---|
| majority | 0.500 ± 0.000 | 0.500 ± 0.000 | — | — | — | — | — | — | — |
| lexicon | 0.501 ± 0.001 | 0.501 ± 0.001 | 0.499 ± 0.001 | 0.498 ± 0.002 | 1.000 ± 0.000 | 0.600 ± 0.435 | 0.003 ± 0.002 | 0.006 ± 0.004 | 0.002 ± 0.003 |
| tfidf_lr | 0.630 ± 0.031 | 0.654 ± 0.017 | 0.233 ± 0.005 | 0.048 ± 0.013 | 0.688 ± 0.011 | 0.672 ± 0.071 | 0.117 ± 0.030 | 0.198 ± 0.046 | 0.057 ± 0.017 |

**τ_B toàn cục trên out-of-fold `tfidf_lr`:** 0.698 (FPR 0.050, precision 0.668, recall 0.100, F1 0.174).

## H1

N = 26,719 mẫu, 26,719 hội thoại, 24,452 group, prevalence 0.173.

| Mô hình | PR-AUC | ROC-AUC | Brier | ECE | τ_B | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|---|---|---|---|
| majority | 0.173 ± 0.000 | 0.500 ± 0.000 | — | — | — | — | — | — | — |
| lexicon | 0.238 ± 0.013 | 0.564 ± 0.009 | 0.164 ± 0.004 | 0.131 ± 0.002 | 0.668 ± 0.404 | 0.608 ± 0.039 | 0.148 ± 0.018 | 0.239 ± 0.026 | 0.020 ± 0.001 |
| tfidf_lr | 0.708 ± 0.013 | 0.904 ± 0.004 | 0.084 ± 0.001 | 0.019 ± 0.003 | 0.414 ± 0.006 | 0.715 ± 0.014 | 0.576 ± 0.026 | 0.637 ± 0.013 | 0.048 ± 0.005 |

**τ_B toàn cục trên out-of-fold `tfidf_lr`:** 0.406 (FPR 0.050, precision 0.710, recall 0.583, F1 0.641).

## H2

N = 8,001 mẫu, 8,001 hội thoại, 660 group, prevalence 0.110.

| Mô hình | PR-AUC | ROC-AUC | Brier | ECE | τ_B | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|---|---|---|---|
| majority | 0.110 ± 0.000 | 0.500 ± 0.000 | — | — | — | — | — | — | — |
| lexicon | 0.111 ± 0.001 | 0.500 ± 0.001 | 0.111 ± 0.001 | 0.109 ± 0.001 | 1.000 ± 0.000 | 0.000 ± 0.000 | 0.000 ± 0.000 | 0.000 ± 0.000 | 0.000 ± 0.001 |
| tfidf_lr | 0.349 ± 0.044 | 0.779 ± 0.034 | 0.085 ± 0.003 | 0.017 ± 0.002 | 0.264 ± 0.016 | 0.419 ± 0.049 | 0.303 ± 0.104 | 0.343 ± 0.066 | 0.053 ± 0.020 |

**τ_B toàn cục trên out-of-fold `tfidf_lr`:** 0.267 (FPR 0.050, precision 0.425, recall 0.297, F1 0.350).
