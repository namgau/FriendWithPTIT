# C1 — Embedding đa ngữ, T4 và PCA

## Quyết định đóng băng

- Encoder: `BAAI/bge-m3`.
- Revision: `5617a9f61b028005a4858fdac845db406aefb181`.
- Vector: 1.024 chiều, chuẩn hóa L2, tối đa 256 token; câu rỗng thành vector 0.
- Lý do chất lượng: trung bình VN-MTEB ghi trong config là BGE-M3 64,90 so với MiniLM-L12 46,05.
- Trạng thái: đóng băng; downstream phải đọc `configs/embedding.yaml`, không đổi model/revision/PCA riêng lẻ.

## Benchmark dữ liệu thật ngày 06/11/2026

Mẫu cố định seed 42: 1.000 tin E1 + 1.000 tin E2 cho tiếng Anh; 1.000 tin H1 + 1.000 tin H2 cho tiếng Việt. Mỗi cấu hình warmup 2 lần, đo 5 lần; máy đích RTX 3060. Report máy đọc nằm ở `reports/c1_embedding_benchmark.json` và `.csv` (không commit vì `reports/` bị ignore).

| Model | Thiết bị | Batch | Anh (tin/s) | Việt (tin/s) | Peak GPU lớn nhất |
|---|---:|---:|---:|---:|---:|
| BGE-M3 | CPU | 1 | 9,2 | 7,6 | N/A |
| BGE-M3 | CPU | 8 | 13,0 | 13,5 | N/A |
| BGE-M3 | GPU | 1 | 51,5 | 51,3 | 1.098 MiB |
| BGE-M3 | GPU | 32 | 686,7 | 630,8 | 1.270 MiB |
| BGE-M3 | GPU | 64 | 713,5 | 639,2 | 1.448 MiB |
| MiniLM | CPU | 1 | 66,1 | 63,3 | N/A |
| MiniLM | CPU | 8 | 133,4 | 133,1 | N/A |
| MiniLM | GPU | 1 | 91,4 | 90,9 | 237 MiB |
| MiniLM | GPU | 32 | 2.280,9 | 2.172,5 | 274 MiB |
| MiniLM | GPU | 64 | 3.510,1 | 3.295,7 | 311 MiB |

Footprint tham số đo lúc load FP32 là BGE-M3 2.165,8 MiB và MiniLM 448,8 MiB; khi load FP16 trên GPU tương ứng 1.082,9 MiB và 224,4 MiB. Đây là bộ nhớ tham số, không phải kích thước file checkpoint. BGE-M3 chậm hơn nhưng được chọn vì chất lượng tiếng Việt cao hơn rõ rệt và vẫn đạt 630–714 tin/s ở batch GPU lớn. Tỷ lệ vượt giới hạn token: BGE-M3 0% trên mẫu Anh và 0,55% trên mẫu Việt; MiniLM tương ứng 0% và 1,95%.

Audit provenance 06/11/2026: 20 dòng benchmark trong JSON/CSV đều có `n_texts=2000`; các tốc độ, peak GPU, footprint và tỷ lệ truncation trong bảng được làm tròn trực tiếp từ `reports/c1_embedding_benchmark.json`, không suy diễn từ checkpoint khác. JSON đã được bổ sung manifest của từng nguồn (path, SHA-256 file, số dòng, số dòng lấy mẫu và hash văn bản mẫu) cùng phần cứng thật (`NVIDIA GeForce RTX 3060`, 12.287 MiB, PyTorch/CUDA). Đây là bổ sung provenance hồi cứu, không chạy lại benchmark; cả bốn file nguồn có mtime trước report gốc và `n_texts` khớp manifest.

## T4 và PCA đóng băng

- T4 gồm reply cosine (last/mean/min/slope), topic drift, novelty, các chiều PCA và cờ thiếu/rỗng; chỉ đọc lượt hiện tại và quá khứ.
- PCA fit duy nhất trên 87.171 utterance của DailyDialog train, 1.024 → 32 chiều, `whiten=false`, seed 42.
- PCA giữ 0,420795 tổng explained variance trên DailyDialog train (đây là thuộc tính của checkpoint PCA). DailyDialog validation 8.069 utterance chỉ dùng đánh giá reconstruction RMSE = 0,017240; không fit lại và không dùng để tính explained variance ratio.
- Artifact: `models/m3/t4/pca.pkl`; checksum SHA-256 `9aacf6273fbb0c1a4b5d2bef72f1a31ca936d52d023205cdd593f4a83fbae9e8`.
- Metadata giữ riêng hash config lúc fit (`encoder.config_sha256`) và hash config bàn giao hiện tại (`handoff.config_sha256`); sáu trường định danh encoder đã đối chiếu khớp. Không ghi đè hash lịch sử để tránh tạo provenance giả.

## Bàn giao cho B8

B dùng nguyên `configs/embedding.yaml` và `src/m3_health/embeddings.py`; ưu tiên GPU batch 64, ghi cache theo shard/memmap để có thể kiểm tra từng phần. Không fit lại PCA trên E1/E2/G1/H1/H2.

Tổng cache BGE-M3 FP32 ước tính khoảng 44,16 GiB cho 11.575.218 tin; PCA32 FP32 khoảng 1,38 GiB. Đây là ước tính dung lượng để B chuẩn bị ổ đĩa, không phải kết quả cache B8.
