# Kết quả test (A8) — mở một lần

> Sinh bởi `python -m src.m3_health.run_test --open --datasets E1` lúc 2026-11-23T20:01:53, commit `5df9dc1`. Mô hình fit chỉ trên train (C2 fit toàn bộ H1 train), τ chọn trên train. CI 95% cluster bootstrap theo group, B = 2000 ; Δ = PR-AUC(chốt) − PR-AUC(mô hình), paired trên cùng lần lấy mẫu.

| Bộ | Mô hình | PR-AUC [CI 95%] | ROC-AUC | Brier | Precision / Recall / FPR @ τ | Δ chốt − mô hình [CI] |
|---|---|---|---|---|---|---|
| E1 | rule | 0.103 [0.102, 0.104] | 0.513 | 0.129 | — | +0.233 [+0.230, +0.237] |
| E1 | gap_last | 0.143 [0.141, 0.145] | 0.588 | — | — | +0.194 [+0.190, +0.197] |
| E1 | lgbm_T1T2 | 0.316 [0.312, 0.320] | 0.833 | 0.078 | — | +0.020 [+0.018, +0.022] |
| E1 | lgbm_T1-T4 | 0.337 [0.333, 0.341] | 0.843 | 0.077 | 0.364 / 0.379 / 0.075 @ 0.284 | (chốt) |
