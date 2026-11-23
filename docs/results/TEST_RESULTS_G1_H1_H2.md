# Kết quả test (A8) — mở một lần

> Sinh bởi `python -m src.m3_health.run_test --open --datasets G1 H1 H2` lúc 2026-11-23T20:57:00, commit `5df9dc1`. Mô hình fit chỉ trên train (C2 fit toàn bộ H1 train), τ chọn trên train. CI 95% cluster bootstrap theo group, B = 2000 ; Δ = PR-AUC(chốt) − PR-AUC(mô hình), paired trên cùng lần lấy mẫu.

| Bộ | Mô hình | PR-AUC [CI 95%] | ROC-AUC | Brier | Precision / Recall / FPR @ τ | Δ chốt − mô hình [CI] |
|---|---|---|---|---|---|---|
| G1 | lexicon | 0.500 [0.499, 0.502] | 0.499 | 0.501 | — | +0.116 [+0.085, +0.153] |
| G1 | tfidf_lr | 0.616 [0.585, 0.653] | 0.645 | 0.235 | 0.662 / 0.102 / 0.052 @ 0.689 | (chốt) |
| H1 | lexicon | 0.233 [0.215, 0.254] | 0.561 | 0.165 | — | +0.554 [+0.527, +0.579] |
| H1 | tfidf_lr | 0.707 [0.680, 0.736] | 0.901 | 0.083 | 0.704 / 0.593 / 0.052 @ 0.429 | +0.080 [+0.056, +0.102] |
| H1 | phobert_c2 | 0.787 [0.763, 0.810] | 0.933 | 0.075 | 0.664 / 0.769 / 0.081 @ 0.489 | (chốt) |
| H2 | lexicon | 0.110 [0.076, 0.148] | 0.500 | 0.110 | — | +0.291 [+0.181, +0.399] |
| H2 | tfidf_lr | 0.401 [0.268, 0.528] | 0.802 | 0.081 | 0.457 / 0.384 / 0.056 @ 0.253 | (chốt) |
| H2 | phobert_c2_x12 | 0.354 [0.212, 0.491] | 0.757 | 0.091 | 0.448 / 0.274 / 0.042 @ 0.489 | +0.047 [-0.022, +0.117] |
