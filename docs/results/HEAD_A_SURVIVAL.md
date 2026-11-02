# Head A — survival so với phân loại (A6, X3, X5)

> Sinh bởi `python -m src.m3_health.run_survival`. Chỉ tập train ; cùng hội thoại và fold forward chaining với `HEAD_A_CV.md`. P(tắt trong Δ) của survival = S(Δ | x) từ **một** mô hình ; LightGBM học lại cho từng Δ. T kiểm duyệt hành chính ở 7 ngày ; RSF fit trên mẫu con 20,000 dòng. C-index Harrell, IBS trên 5 phút–72h.

## E2

123,199 mẫu (97 mẫu censored ở 48h — mô hình phân loại phải bỏ, survival dùng được), 60,000 hội thoại.

| Mô hình | C-index | IBS | PR-AUC 1h | PR-AUC 24h | PR-AUC 48h | PR-AUC 72h | Brier 48h | Thời gian fit (s) |
|---|---|---|---|---|---|---|---|---|
| cox | 0.607 ± 0.003 | 0.118 ± 0.007 | 0.619 ± 0.025 | 0.479 ± 0.022 | 0.459 ± 0.024 | 0.449 ± 0.022 | 0.213 ± 0.008 | 7 |
| rsf | 0.631 ± 0.004 | 0.121 ± 0.008 | 0.653 ± 0.027 | 0.535 ± 0.024 | 0.520 ± 0.025 | 0.513 ± 0.024 | 0.203 ± 0.008 | 23 |
| lgbm_clf | — | — | 0.664 ± 0.030 | 0.547 ± 0.027 | 0.530 ± 0.029 | 0.524 ± 0.027 | 0.200 ± 0.006 | 12 |

## E1

338,242 mẫu (2 mẫu censored ở 48h — mô hình phân loại phải bỏ, survival dùng được), 60,000 hội thoại.

| Mô hình | C-index | IBS | PR-AUC 1h | PR-AUC 24h | PR-AUC 48h | PR-AUC 72h | Brier 48h | Thời gian fit (s) |
|---|---|---|---|---|---|---|---|---|
| cox | 0.602 ± 0.004 | 0.032 ± 0.001 | 0.197 ± 0.013 | 0.184 ± 0.010 | 0.180 ± 0.008 | 0.177 ± 0.007 | 0.092 ± 0.001 | 3 |
| rsf | 0.616 ± 0.005 | 0.019 ± 0.002 | 0.291 ± 0.013 | 0.283 ± 0.011 | 0.281 ± 0.010 | 0.280 ± 0.010 | 0.082 ± 0.001 | 19 |
| lgbm_clf | — | — | 0.334 ± 0.002 | 0.325 ± 0.004 | 0.320 ± 0.005 | 0.319 ± 0.005 | 0.079 ± 0.001 | 11 |
