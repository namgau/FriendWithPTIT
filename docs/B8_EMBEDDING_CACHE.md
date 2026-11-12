# B8 — cache embedding BGE-M3

## Đầu vào và phạm vi

`python -m src.m3_health.embed_ids G1 H1 H2` sinh danh sách hội thoại không nhãn từ
`data/processed/<ID>/messages.parquet`. G1/H1/H2 dùng **toàn bộ hội thoại**;
pipeline kiểm tra danh sách bao phủ toàn bộ tin. E1/E2 chỉ chạy sau khi có dữ liệu
thật, nhãn train và danh sách được `embed_ids` chọn theo quy tắc Head A. Không đọc
`data/locked/`. Dữ liệu không nằm trong Git: cần tự chuyển các file đã xử lý sang
môi trường GPU. Không sửa thứ tự/dữ liệu hoặc danh sách ID giữa các lần resume.

Encoder C1 trong `embeddings.py` dùng revision được pin trong
`configs/embedding.yaml`, BGE-M3 dense 1024 chiều, `max_length=256`, chuẩn hóa L2,
cache float16; tin rỗng được mã hóa bằng vector 0. Mỗi dòng trong
`emb_bge-m3_index.parquet` (`conversation_id`, `turn_idx`) tương ứng cùng hàng
trong `emb_bge-m3.npy`. Có thể đọc bằng `src.m3_health.embedding_cache.load_cache`.
Pipeline tạo encoder bằng `make_encoder()` và dùng `encode_to_memmap()` của C1
cho từng chunk riêng; sau đó ghép chunk vào file cache tạm và checkpoint. File
chunk là tạm thời, nên chế độ `w+` của C1 không ghi đè các hàng đã checkpoint.

## Chạy trên Google Colab hoặc Kaggle

1. Bật GPU trong Colab (**Runtime → Change runtime type → GPU**) hoặc Kaggle
   (**Settings → Accelerator → GPU**). Dùng Python 3.10–3.12 và CUDA PyTorch phù hợp.
2. Đưa **phiên bản code có `src/m3_health/cache_embeddings_b8.py`** cùng
   `configs/embedding.yaml` lên môi trường. Clone repository nếu phiên bản đó đã
   được push; nếu chưa, upload thêm file mới này vào đúng đường dẫn sau khi clone.
   Trong Colab có thể mount Google Drive; trong Kaggle input `/kaggle/input` chỉ đọc,
   nên copy sang `/kaggle/working` hoặc một thư mục lưu bền vững trước khi chạy.
3. Copy `messages.parquet` thực của G1, H1, H2 vào đúng các thư mục
   `data/processed/G1`, `H1`, `H2` trong bản code. Cần bộ nhớ lưu trữ bền vững
   cho cả checkpoint và kết quả; Colab `/content` và Kaggle `/kaggle/working`
   có thể mất khi session bị thu hồi. Mount Drive hoặc lưu/tải lại toàn bộ thư mục
   đang chạy sau mỗi lần ngắt. Không chạy hai tiến trình cùng một dataset.
4. Tại thư mục gốc repo, chạy trong cell shell hoặc terminal (trong notebook thêm
   `!` đầu mỗi lệnh):

```bash
python -m pip install 'numpy>=1.26' 'pandas>=2.1' 'pyarrow>=15,<27' 'PyYAML>=6' 'scipy>=1.11' 'scikit-learn>=1.4' 'sentence-transformers>=3,<4' 'transformers>=4.41,<5'
python -c "import torch; print(torch.__version__, torch.cuda.is_available(), torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'NO GPU')"
python -m src.m3_health.embed_ids G1 H1 H2
python -m src.m3_health.cache_embeddings_b8 G1 H1 H2 --batch-size 32 --chunk-size 2048
```

Colab/Kaggle thường có sẵn PyTorch CUDA; nếu thiếu thì cài wheel CUDA tương ứng
trước. Lần đầu cần Internet để tải revision model từ Hugging Face. `--batch-size`
là batch inference trên GPU; giảm xuống 8 hoặc 16 nếu hết VRAM. `--chunk-size`
là số dòng đọc/ghi giữa các checkpoint, không phải batch GPU. Cấu hình mặc định
không cho âm thầm chạy CPU; `--allow-cpu` chỉ dành cho thử nghiệm nhỏ có chủ đích.

Nếu bị ngắt trong một dataset, chạy tiếp từng dataset đã bắt đầu bằng `--resume`;
dataset chưa bắt đầu chạy bằng lệnh bình thường. Ví dụ H1 bị ngắt sau khi G1 xong:

```bash
python -m src.m3_health.cache_embeddings_b8 H1 --batch-size 16 --chunk-size 2048 --resume
python -m src.m3_health.cache_embeddings_b8 H2 --batch-size 16 --chunk-size 2048
```

Có thể thay batch/chunk size khi resume; input parquet, danh sách ID và config
phải giữ nguyên. Pipeline kiểm tra SHA-256 của input, ID, index và từng chunk đã
checkpoint, cùng cấu hình, shape và dtype. Checkpoint phiên bản cũ (trước khi dùng
`encode_to_memmap` theo chunk) không tương thích: sao lưu và chạy lại dataset đó.
Không có khóa độc quyền: chỉ chạy một tiến trình trên mỗi dataset.
Sau khi toàn bộ các khối được ghi, index và `.npy` mới xuất hiện dưới tên cuối;
`*.partial.*` và `*.checkpoint.json` là trạng thái phục hồi, không phải kết quả.
Trong trường hợp ngắt lúc khởi tạo trước khi có checkpoint, hoặc muốn làm lại từ
đầu, sao lưu rồi xóa các file `emb_bge-m3*` của **dataset đó** trước khi chạy lại.
Nếu dừng trong lúc công bố file, chạy `--resume` để hoàn tất. Không xóa checkpoint
trong lúc đang chạy; không commit dữ liệu/cache.

## Kiểm tra và ghi ledger sau khi chạy thật

```bash
python -c "from src.m3_health.embedding_cache import load_cache; import numpy as np; [(lambda x: print(d, x[0].shape, x[0].dtype, len(x[1])))(load_cache(d)) for d in ['G1','H1','H2']]"
```

Kiểm tra thêm `data/processed/<ID>/emb_bge-m3.metadata.json`: `completed == rows`,
`status == complete`, `devices` và `active_seconds` (tổng thời gian encode/ghi các
khối đã hoàn tất, bao gồm tải model nếu được tải trong khối đầu; không tính thời
gian chuẩn bị index hoặc chờ giữa các session). File này cũng ghi SHA-256 input và hash config. Ghi **một dòng cho mỗi
dataset** vào `experiments/ledger.csv`, dùng đúng header hiện có: `module=m3_b8`,
`owner=B`, `config_hash=fingerprint.config_hash`, `data_hash=fingerprint.source_sha256`,
`git_commit` của phiên bản đã chạy, `seeds=42`, `model=<ID>:BAAI/bge-m3`,
`metric_name=messages`, `metric_mean=rows`, để trống `metric_sd`; trong `notes`
ghi GPU, `active_seconds`, batch size, các lần resume và revision. Điền `run_id`
và `date` theo thời điểm chạy thật; không tự ghi kết quả khi chưa chạy.

## E1/E2

Các data card là tài liệu, không phải dữ liệu. Đặt file thực vào
`data/processed/E1` và `E2` theo `docs/data_cards/DATA_CARD_E1.md`,
`DATA_CARD_E2.md` và pipeline `src.data.prepare`; cần cả
`labels_headA_train.parquet` để `embed_ids` chọn đúng. Sau đó:

```bash
python -m src.m3_health.embed_ids E1 E2
python -m src.m3_health.cache_embeddings_b8 E1 E2 --batch-size 32 --chunk-size 2048
```

E1/E2 lớn hơn nhiều; kiểm tra dung lượng trước khi chạy (1024 × 2 byte mỗi tin,
chưa tính input, index và dung lượng trống dự phòng). Không tự tạo dữ liệu thay thế.

## Chạy từng dataset trên Modal

`src/m3_health/modal_b8_runner.py` chạy pipeline B8 hiện có trên một T4, 2 CPU,
8 GiB RAM, timeout tối đa 3 giờ cho một lần gọi. Chạy tuần tự, một writer cho
mỗi dataset; không cần mở endpoint. Các lệnh dưới đây là PowerShell, chạy tại
gốc repo. Chỉ thực hiện sau khi được phép dùng GPU và chuyển dữ liệu lên Modal.

Tạo Volume riêng với tên duy nhất do bạn chọn, không trùng Volume hiện có:

```powershell
$env:PYTHONUTF8='1'
$env:B8_MODAL_VOLUME='TEN-VOLUME-B8-DUY-NHAT'
python -m modal volume create --version 2 --profile frejalinz --env main $env:B8_MODAL_VOLUME
```

Chỉ chuyển input cần thiết, không tải `data/locked/`, nhãn, ZIP gốc hoặc token:

```powershell
foreach ($d in @('E1','E2','G1','H1','H2')) {
  python -m modal volume put --profile frejalinz --env main $env:B8_MODAL_VOLUME "data/processed/$d/messages.parquet" "/data/processed/$d/messages.parquet"
  if ($LASTEXITCODE -ne 0) { throw "Upload messages failed: $d" }
  python -m modal volume put --profile frejalinz --env main $env:B8_MODAL_VOLUME "data/processed/$d/embed_conversation_ids.txt" "/data/processed/$d/embed_conversation_ids.txt"
  if ($LASTEXITCODE -ne 0) { throw "Upload IDs failed: $d" }
}
```

Chạy một dataset tại một thời điểm; không tự động chạy tiếp nếu gặp lỗi:

```powershell
python -m modal run --profile frejalinz --env main src/m3_health/modal_b8_runner.py --dataset E1 --batch-size 32 --chunk-size 2048
```

Nếu đã có checkpoint của lần chạy trước, dùng `--resume` cho đúng dataset; nếu
chưa có checkpoint, không dùng `--resume`. Sau khi E1 hoàn thành, thay `E1` lần
lượt bằng `E2`, `G1`, `H1`, `H2`:

```powershell
python -m modal run --profile frejalinz --env main src/m3_health/modal_b8_runner.py --dataset E1 --batch-size 32 --chunk-size 2048 --resume
```

Tải các output hoàn tất về đúng dataset, không dùng `--force` để tránh ghi đè:

```powershell
$d='E1'
foreach ($f in @('emb_bge-m3.npy','emb_bge-m3_index.parquet','emb_bge-m3.metadata.json')) {
  python -m modal volume get --profile frejalinz --env main $env:B8_MODAL_VOLUME "/data/processed/$d/$f" "data/processed/$d/$f"
  if ($LASTEXITCODE -ne 0) { throw "Download failed: $d/$f" }
}
```

Sau khi tải, kiểm tra cache và metadata như mục trên rồi ghi ledger. Volume còn
tồn tại sau job và có thể tính phí lưu trữ; không xóa nếu chưa xác nhận. Model
được cache trong Volume dưới `/model-cache`. Nếu container bị preempt hoặc mất
đột ngột, background commit không đảm bảo checkpoint transaction; `--resume`
kiểm tra hash, nếu lỗi thì giữ nguyên file để điều tra thay vì ghi đè.

## Lấy cache đã tính (không cần chạy lại GPU)

Bản cache (zip 6,2 GB chia 7 phần Git LFS) **không nằm ở main** để clone không phải tải 6,2 GB ; nó chỉ có trong commit `37bb135` (thư mục `b8-cache-lfs/`):

```bash
git lfs fetch origin 37bb135
git show 37bb135:b8-cache-lfs/README.md      # script ghép + kiểm hash theo manifest
git show 37bb135:b8-cache-lfs/manifest.json
```

Mỗi phần nằm ở `.git/lfs/objects/<sha[:2]>/<sha[2:4]>/<sha>` (sha theo manifest). Ghép xong giải nén vào `data/processed/<ID>/` rồi kiểm bằng `embedding_cache.load_cache(<ID>)`. Có thể xóa object LFS sau đó bằng `git lfs prune`.
