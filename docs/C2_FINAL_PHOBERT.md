# C2 — PhoBERT cuối cho test ngày 20/11

Trên GPU RTX 5070 ngày 17/11, `python -m src.m3_health.final_phobert_c2` đã fit một model H1 80/20 group để lấy Platt và τ_B, sau đó fit **model cuối trên toàn bộ 26.719 dòng H1 train** (24.452 group). Không đọc `data/locked/` trong lúc fit. Dùng đúng tiền xử lý underthesea và weighted BCE của C2.

Artifact tại `models/m3/c2/final/`:

- `model/model.safetensors`: trọng số 540.020.308 byte; SHA-256 `4b035a25dcd09989704c26642a99ab8ab4fdca6b1e4e8c36e1fced220add1605`.
- `model/`: config và tokenizer để `from_pretrained` nạp được hoàn toàn từ local.
- `manifest.json`: hash dữ liệu train, siêu tham số, Platt coefficient `0.7561979218744912`, intercept `-1.1912812270462965`, τ_B `0.4889881181701468`, phần cứng và thời gian chạy.

Trọng số 540 MB bị `.gitignore` loại theo quy tắc repo; **phải giữ file này ở máy chạy test hoặc chuyển nguyên thư mục artifact sang máy đó**. CPU smoke test đã nạp model local và trả điểm cho hai câu mẫu. Chỉ các file nhỏ và manifest được commit.

Ngày **20/11**, mở test đúng một lần theo quy tắc A8. Nếu chỉ chạy C2 trên H1/H2 bằng CPU từ workspace có dữ liệu khóa và artifact:

```powershell
python -m src.m3_health.run_test --open --datasets H1 H2
```

Lệnh này ghi dấu mở test vào `experiments/test_opened.json`, xuất `docs/results/TEST_RESULTS_H1_H2.md`, điểm từng mẫu test vào `reports/`, và ledger. Nếu lệnh toàn bộ test đã chạy trước đó, chỉ dùng `--again` để tái lập đúng cùng cấu hình. Không chạy lệnh test trước ngày 20/11.

PhoBERT H1 là model chốt của C2. H2 dùng cùng trọng số, Platt và τ_B của H1 để đo zero-shot; không fit lại hay chọn ngưỡng trên H2 test. Vì Platt/τ_B được học từ model fit 80% và chuyển sang model refit 100%, FPR 5% trên phần calibration **không bảo đảm** FPR 5% trên test. Báo cả FPR thực tế và PR-AUC test; không chỉnh model theo test.
