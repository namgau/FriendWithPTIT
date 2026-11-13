# B9 — TabPFN v2 so với LightGBM (X7)

Runner: `python -m src.m3_health.run_tabpfn_b9`. Đây là **so sánh trên train/CV**, không mở test khóa, không tự tải dữ liệu hay chạy GPU từ xa.

## Giao thức

- Đọc `labels_headA_train.parquet` và `messages.parquet` qua `run_head_a.load` của A, cùng giới hạn hội thoại, 21 đặc trưng T1/T2 và outer forward-chaining K=5. Đơn vị split/lấy mẫu là `group_id`, không chia các mẫu cùng group; E2 group có thể gồm nhiều hội thoại.
- Mỗi fold lấy ngẫu nhiên có seed các group trong outer-train, theo các mức lồng nhau 100/300/1.000 group. Cả hai mô hình dùng **đúng cùng mẫu train và validation**. Ghi cả số group và số dòng. Không cắt bớt group; dừng nếu vượt 10.000 dòng hoặc có lớp duy nhất. Các mức là lựa chọn ban đầu của B để tái lập X7, cần A review; không phải mức A đã chốt.
- LightGBM dùng `MODELS['lgbm']` của A, không xử lý imbalance. TabPFN dùng package `tabpfn==2.0.9`, checkpoint v2 classifier tường minh, cấu hình mặc định khác, không tuning. PR-AUC, ROC-AUC và Brier là **xác suất thô, chưa calibration**; khác protocol calibration trong `HEAD_A_CV.md`, nên không coi kết quả này là thay thế trực tiếp bảng đó.
- Chia prediction theo batch để hạn chế RAM; TabPFN dự đoán theo batch có thể thay đổi hiệu năng và thời gian; batch size được ghi lại. Ngẫu nhiên seed = `test_seed + fold`.

## Chạy

Tạo môi trường Python riêng có PyTorch/GPU phù hợp, LightGBM, các phụ thuộc repo và `tabpfn==2.0.9`. Lấy checkpoint TabPFN v2 classifier từ `Prior-Labs/TabPFN-v2-clf`, tuân thủ giấy phép và điều kiện truy cập; runner ghi SHA-256 checkpoint. Không commit model, dữ liệu hoặc checkpoint.

```powershell
python -m src.m3_health.run_tabpfn_b9 --dataset E1 --checkpoint <duong-dan-tabpfn-v2-classifier.ckpt> --output reports/b9_E1.csv --device cuda
python -m src.m3_health.run_tabpfn_b9 --dataset E2 --checkpoint <duong-dan-tabpfn-v2-classifier.ckpt> --output reports/b9_E2.csv --device cuda
```

Đường dẫn output phải chưa tồn tại. CSV ghi từng fold/mô hình/mức; JSON cạnh CSV ghi config, hash input và model, phiên bản, GPU, commit và cờ dirty. Runner không tự động cập nhật báo cáo hay ledger. Nếu runner chạy từ working tree có thay đổi, `git_dirty=true`; cần commit code hoặc lưu provenance chính xác trước lần chạy báo cáo.

## Giới hạn hiện tại

Máy local không có PyTorch/TabPFN/GPU. Sau khi người dùng cho phép, nhãn train E1/E2 được chuyển riêng lên volume Modal dưới `/b9/labels/`; không chuyển hay mở `data/locked/`. Kết quả chạy T4 12/11, learning curve, paired bootstrap CI từ lần chạy lại và giải thích ledger nằm ở `docs/results/B9_TABPFN_CV.md`. Cần A review protocol và mức group. Commit cơ sở không chứa runner, được ghi rõ cùng hash runner trong ledger.
