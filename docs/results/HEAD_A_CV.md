# Head A — kết quả CV trên tập train (A5, X2)

> Sinh bởi `python -m src.m3_health.run_head_a`. Tập test **chưa mở** (khóa tới 20/11).
> Outer: forward chaining K = 5 theo thời điểm bắt đầu group ; calibration trên 20% group muộn nhất của mỗi outer-train ; Δ = 48h ; đặc trưng T1 + T2 tối thiểu (`src/m3_health/features.py`). Mean ± SD qua 5 fold.

## E1

N = 338,240 mẫu, 60,000 hội thoại (lấy ngẫu nhiên 60000 hội thoại vì giới hạn tài nguyên), prevalence 0.104.

| Mô hình | Imbalance | PR-AUC | ROC-AUC | Brier (isotonic) | ECE thô | ECE isotonic |
|---|---|---|---|---|---|---|
| majority | none | 0.103 ± 0.002 | 0.500 ± 0.000 | — | — | — |
| rule | none | 0.105 ± 0.002 | 0.515 ± 0.004 | 0.092 ± 0.001 | — | 0.003 ± 0.003 |
| gap_last | none | 0.147 ± 0.004 | 0.583 ± 0.005 | 0.091 ± 0.001 | — | 0.003 ± 0.002 |
| lr | none | 0.197 ± 0.007 | 0.692 ± 0.009 | 0.088 ± 0.001 | 0.012 ± 0.002 | 0.004 ± 0.001 |
| lr | class_weight | 0.194 ± 0.007 | 0.692 ± 0.008 | 0.088 ± 0.001 | 0.015 ± 0.002 | 0.005 ± 0.001 |
| lr | undersample | 0.194 ± 0.007 | 0.692 ± 0.008 | 0.088 ± 0.001 | 0.015 ± 0.002 | 0.004 ± 0.002 |
| lr | oversample | 0.194 ± 0.007 | 0.692 ± 0.008 | 0.088 ± 0.001 | 0.015 ± 0.002 | 0.004 ± 0.001 |
| lr | smote | 0.195 ± 0.008 | 0.690 ± 0.008 | 0.088 ± 0.001 | 0.012 ± 0.001 | 0.004 ± 0.001 |
| lgbm | none | 0.317 ± 0.004 | 0.827 ± 0.003 | 0.079 ± 0.001 | 0.006 ± 0.002 | 0.004 ± 0.001 |
| lgbm | class_weight | 0.318 ± 0.004 | 0.827 ± 0.003 | 0.079 ± 0.002 | 0.009 ± 0.005 | 0.004 ± 0.001 |
| lgbm | undersample | 0.303 ± 0.008 | 0.823 ± 0.004 | 0.080 ± 0.002 | 0.009 ± 0.003 | 0.004 ± 0.002 |
| lgbm | oversample | 0.318 ± 0.002 | 0.827 ± 0.003 | 0.079 ± 0.001 | 0.010 ± 0.005 | 0.004 ± 0.002 |
| lgbm | smote | 0.317 ± 0.002 | 0.826 ± 0.002 | 0.079 ± 0.001 | 0.084 ± 0.002 | 0.004 ± 0.002 |

**Mô hình tốt nhất theo PR-AUC:** `lgbm` + `class_weight`. Ngưỡng chọn trên dự đoán out-of-fold đã hiệu chỉnh (C_FN : C_FP = 1 : 2, recall mức hời hợt ≥ 0.8): **τ1 = 0.141, τ2 = 1.000** ; tỉ lệ 3 mức khỏe / hời hợt / nguy cơ = 0.65 / 0.35 / 0.00 ; tại τ2: precision 0.455, recall 0.001.

## E2

N = 123,102 mẫu, 60,000 hội thoại (lấy ngẫu nhiên 60000 hội thoại vì giới hạn tài nguyên), prevalence 0.334.

| Mô hình | Imbalance | PR-AUC | ROC-AUC | Brier (isotonic) | ECE thô | ECE isotonic |
|---|---|---|---|---|---|---|
| majority | none | 0.341 ± 0.026 | 0.500 ± 0.000 | — | — | — |
| rule | none | 0.351 ± 0.026 | 0.515 ± 0.005 | 0.224 ± 0.008 | — | 0.018 ± 0.011 |
| gap_last | none | 0.367 ± 0.026 | 0.557 ± 0.002 | 0.222 ± 0.008 | — | 0.015 ± 0.006 |
| lr | none | 0.484 ± 0.025 | 0.659 ± 0.005 | 0.209 ± 0.007 | 0.028 ± 0.015 | 0.019 ± 0.006 |
| lr | class_weight | 0.482 ± 0.026 | 0.658 ± 0.005 | 0.209 ± 0.007 | 0.028 ± 0.015 | 0.020 ± 0.006 |
| lr | undersample | 0.482 ± 0.025 | 0.658 ± 0.005 | 0.209 ± 0.007 | 0.028 ± 0.015 | 0.019 ± 0.006 |
| lr | oversample | 0.482 ± 0.026 | 0.658 ± 0.005 | 0.209 ± 0.007 | 0.028 ± 0.015 | 0.020 ± 0.006 |
| lr | smote | 0.484 ± 0.025 | 0.659 ± 0.005 | 0.209 ± 0.007 | 0.028 ± 0.015 | 0.021 ± 0.006 |
| lgbm | none | 0.527 ± 0.030 | 0.699 ± 0.005 | 0.200 ± 0.006 | 0.021 ± 0.010 | 0.015 ± 0.003 |
| lgbm | class_weight | 0.528 ± 0.029 | 0.699 ± 0.005 | 0.200 ± 0.006 | 0.028 ± 0.010 | 0.013 ± 0.004 |
| lgbm | undersample | 0.519 ± 0.033 | 0.694 ± 0.005 | 0.202 ± 0.006 | 0.021 ± 0.011 | 0.015 ± 0.004 |
| lgbm | oversample | 0.526 ± 0.032 | 0.697 ± 0.005 | 0.201 ± 0.006 | 0.032 ± 0.011 | 0.015 ± 0.004 |
| lgbm | smote | 0.529 ± 0.029 | 0.699 ± 0.005 | 0.200 ± 0.006 | 0.128 ± 0.015 | 0.016 ± 0.004 |

**Mô hình tốt nhất theo PR-AUC:** `lgbm` + `smote`. Ngưỡng chọn trên dự đoán out-of-fold đã hiệu chỉnh (C_FN : C_FP = 1 : 2, recall mức hời hợt ≥ 0.8): **τ1 = 0.266, τ2 = 0.667** ; tỉ lệ 3 mức khỏe / hời hợt / nguy cơ = 0.38 / 0.60 / 0.02 ; tại τ2: precision 0.698, recall 0.050.

