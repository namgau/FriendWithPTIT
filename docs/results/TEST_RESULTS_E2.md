# Kết quả test (A8) — mở một lần

> Sinh bởi `python -m src.m3_health.run_test --open --datasets E2` lúc 2026-11-23T20:50:50, commit `5df9dc1`. Mô hình fit chỉ trên train (C2 fit toàn bộ H1 train), τ chọn trên train. CI 95% cluster bootstrap theo group, B = 2000 ; Δ = PR-AUC(chốt) − PR-AUC(mô hình), paired trên cùng lần lấy mẫu.

| Bộ | Mô hình | PR-AUC [CI 95%] | ROC-AUC | Brier | Precision / Recall / FPR @ τ | Δ chốt − mô hình [CI] |
|---|---|---|---|---|---|---|
| E2 | rule | 0.339 [0.331, 0.347] | 0.512 | 0.261 | — | +0.254 [+0.244, +0.264] |
| E2 | gap_last | 0.355 [0.347, 0.365] | 0.553 | — | — | +0.238 [+0.226, +0.249] |
| E2 | lgbm_T1T2 | 0.518 [0.506, 0.530] | 0.705 | 0.198 | — | +0.075 [+0.067, +0.082] |
| E2 | lgbm_T1-T4 | 0.593 [0.581, 0.604] | 0.745 | 0.185 | 0.738 / 0.225 / 0.040 @ 0.640 | (chốt) |
