# Head A — conformal cho thời gian tới tin kế tiếp (A6, X8)

> Sinh bởi `python -m src.m3_health.run_conformal_a`. Chỉ tập train ; cùng hội thoại và fold forward chaining K = 5 với `HEAD_A_SURVIVAL.md`. Mục tiêu 1 − α = **90%** cho từng cận một phía ; T̃ = min(T, 7 ngày). Mean ± SD qua 5 fold.

- **L(x)** — cận dưới: "cặp này còn im ít nhất L" với xác suất ≥ 90% → không nhắc trước L.
- **U(x)** — cận trên: "có tin kế tiếp trong U (hoặc im > 7 ngày)" với xác suất ≥ 90%.
- `marginal` = cận hằng (không dùng x, chỉ conformal trên T̃) — mốc so độ chặt ; `qr_raw` = hồi quy phân vị LightGBM **không** conformal ; `cqr` = CQR (Romano et al. 2019).
- Censoring thật xử lý bảo thủ (xem docstring) ; số mẫu censored rất nhỏ: E1: 2, E2: 651.

## E1 — trong miền

| Phương pháp | Coverage L | L trung vị (giờ) | Tỉ lệ L ≥ 1h | Coverage U | U trung vị (giờ) | Tỉ lệ U < 7 ngày |
|---|---|---|---|---|---|---|
| marginal | 1.000 ± 0.000 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.920 ± 0.045 | 142.17 ± 34.99 | 1.000 ± 0.000 |
| qr_raw | 1.000 ± 0.000 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.898 ± 0.006 | 1.07 ± 0.25 | 0.868 ± 0.024 |
| cqr | 1.000 ± 0.000 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.901 ± 0.002 | 1.08 ± 0.26 | 0.860 ± 0.013 |

## E2 — trong miền

| Phương pháp | Coverage L | L trung vị (giờ) | Tỉ lệ L ≥ 1h | Coverage U | U trung vị (giờ) | Tỉ lệ U < 7 ngày |
|---|---|---|---|---|---|---|
| marginal | 0.911 ± 0.015 | 0.01 ± 0.00 | 0.000 ± 0.000 | 0.994 ± 0.004 | 168.00 ± 0.00 | 1.000 ± 0.000 |
| qr_raw | 0.890 ± 0.016 | 0.02 ± 0.00 | 0.000 ± 0.000 | 0.737 ± 0.068 | 168.00 ± 0.00 | 0.810 ± 0.131 |
| cqr | 0.903 ± 0.010 | 0.02 ± 0.00 | 0.000 ± 0.000 | 0.935 ± 0.031 | 168.00 ± 0.00 | 0.695 ± 0.263 |

## Chuyển miền E1 ↔ E2 (RQ2)

Mô hình phân vị + calibration học trên nguồn (80/20 theo thời gian), đánh giá trên fold đích. `+ trọng số` = weighted conformal, w(x) từ bộ phân loại miền (LightGBM) học trên calibration nguồn và X **không nhãn** của outer-train đích ; ESS = cỡ mẫu hiệu dụng của trọng số calibration. `calib lại` dùng nhãn của 20% group muộn nhất trong outer-train đích (giữ nguyên mô hình nguồn).

### Đích E2

| Phương pháp | Coverage L | L trung vị (giờ) | Tỉ lệ L ≥ 1h | Coverage U | U trung vị (giờ) | Tỉ lệ U < 7 ngày | ESS |
|---|---|---|---|---|---|---|---|
| E1 CQR | 1.000 ± 0.000 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.564 ± 0.027 | 0.70 ± 0.04 | 0.863 ± 0.004 | — |
| E1 CQR + trọng số | 1.000 ± 0.000 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.967 ± 0.009 | 168.00 ± 0.00 | 0.065 ± 0.011 | 145.82 ± 94.14 |
| E1 mô hình, calib lại trên E2 | 0.911 ± 0.015 | 0.01 ± 0.00 | 0.000 ± 0.000 | 0.897 ± 0.009 | 168.00 ± 0.00 | 0.303 ± 0.021 | — |

### Đích E1

| Phương pháp | Coverage L | L trung vị (giờ) | Tỉ lệ L ≥ 1h | Coverage U | U trung vị (giờ) | Tỉ lệ U < 7 ngày | ESS |
|---|---|---|---|---|---|---|---|
| E2 CQR | 0.591 ± 0.012 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.927 ± 0.001 | 74.47 ± 3.20 | 0.866 ± 0.004 | — |
| E2 CQR + trọng số | 0.988 ± 0.021 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.998 ± 0.005 | 168.00 ± 0.00 | 0.023 ± 0.044 | 280.30 ± 30.08 |
| E2 mô hình, calib lại trên E1 | 0.898 ± 0.003 | 0.00 ± 0.00 | 0.000 ± 0.000 | 0.902 ± 0.004 | 61.84 ± 10.61 | 0.962 ± 0.032 | — |

