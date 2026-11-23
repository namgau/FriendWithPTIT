# C7 — Duyệt chéo phần B đang có trong báo cáo

Ngày đối chiếu: **23/11/2026**, nền `main` tại `5df9dc1`.
Phạm vi: `08_BAO_CAO_M3.md` §3.1, mô tả T3/B8, B6, B10 và phần Head B liên quan.
Đây là duyệt bản hiện có; chưa có thông báo bàn giao bản B7 cuối của B.
Không xác nhận thay review của A, không chạy lại huấn luyện hoặc mở test khóa.

## Kết quả đối chiếu

| Nội dung của B | Nguồn kiểm | Kết luận / xử lý trong C7 |
|---|---|---|
| N mẫu, N group | 5 Data Card trong `data_cards/`; bảng CV Head A/B | Số tổng nguồn khớp phép cộng train/test trong báo cáo đối với G1/H1/H2; E1/E2 phân biệt corpus gốc với mẫu đủ điều kiện. Không tính lại số test từ dữ liệu khóa. |
| Nhãn H1/H2 | `src/data/prepare.py::load_h1/load_h2` | H1 gộp OFFENSIVE + HATE; H2 dùng Toxicity, không dùng Constructiveness. Đã bổ sung rõ ở §3.1. |
| Group độc lập | Data Card và code prepare | G1 theo trang/giữ cặp; H1 hash nội dung chuẩn hóa; H2 hash tiêu đề bài; E2 theo khách hàng. Group không chứng minh mọi phụ thuộc đã hết. |
| Hai bộ cho cùng bài toán | H1/H2 và R9 | H1/H2 cùng phân loại độc hại bình luận tiếng Việt. G1 dự báo công kích tương lai là bài toán khác; không gộp số như cùng một target. |
| Nguồn/license | Data Card E1/H1/H2 | Giữ nguồn đã ghi; H2 chưa có license xác nhận ở HF, E1 ghi “other”. Đã nói rõ “nghiên cứu” không phải giấy phép; cần A/B hoàn tất xác nhận nguồn trước khi phát hành dữ liệu. |
| T3 của B5 | `B5_T3_FEATURES.md`, `features_t3.py`, R2 | 10 cột, cửa sổ 10 tin ≤ t, LSM đúng 2 sender, Jaccard mirroring, NaN/cờ thiếu; lexicon Việt tự xây/tách âm tiết chưa được đánh giá. Đã sửa mô tả để không ngầm coi là LIWC chuẩn. |
| Cache B8 | `B8_EMBEDDING_CACHE.md`, `configs/embedding.yaml`, ledger module `m3_b8` | Encoder/revision đóng băng; có thể cache tin thuộc cả nguồn train/test nhưng downstream chỉ lấy tiền tố và không fit từ nhãn test. C7 không đọc cache/test khóa. |
| B6 DailyDialog | `results/B6_DAILYDIALOG.md`, evaluator, ledger `86c12ed` | Validation 1.000 hội thoại/8.069 lượt; câu hỏi precision 0.7453, recall 0.9831; 148 lượt bị đánh dấu tin cụt chỉ là thống kê, không có ground truth tin cụt. R7 giữ đúng giới hạn; không suy ra hiệu quả tiếng Việt. |
| B10 imbalance H2 | `results/B10_PHOBERT_H2.md`, CSV fold, ledger `f4b6c4c` | 5 fold × 3 seed, mean ± SD trên 15 điểm phụ thuộc; ghép cặp bằng mean seed theo fold. Weighted 0.5086, none 0.4821, over 0.4708; không kết luận ý nghĩa thống kê. |
| B10 so A5b/X12 | B10 ghi sklearn 1.6.1 so với A5b 1.9.1 | Fold thực tế khác; so TF-IDF chỉ tham khảo, X12 cũng khác thiết kế. R9 đã giữ rõ; không dùng “cao hơn 1 SD” làm bằng chứng thắng hay tương đương. |

## Checklist `01_CHUAN_BAO_CAO_AUDITABLE.md` §5

| # | Kết quả ở bản đang có | Vị trí / việc còn lại |
|---|---|---|
| 1 | Đạt | Deployment target §3.1 tách hai loại target Head B. |
| 2 | Đạt ở artifact | N dòng/N đơn vị §3.1 và Data Card; chưa tái tính dữ liệu khóa. |
| 3 | Chưa đủ toàn báo cáo | Có prevalence train/test; B/A cần bổ sung prevalence từng fit/cal/validation nếu tiêu chuẩn nộp yêu cầu đầy đủ. |
| 4 | Đạt mô tả | §3.2, group/time constraints và lý do. |
| 5 | Đạt có giới hạn | K/R/seed §3.2; R=1 cho LR/LightGBM/C4 là khác mặc định R=3. B10 có 3 seed, không coi 15 điểm độc lập. |
| 6 | Đạt khi đọc code liên quan | Fit mô hình/scaler/SMOTE riêng fit; calibrator riêng cal. PCA từ DailyDialog train độc lập là ngoại lệ được công khai. |
| 7 | Đạt mô tả | §3.3/config C4/C2/B10; cùng cal cho chọn cấu hình/Platt/ngưỡng C4 là hạn chế, không gọi nested CV đầy đủ. |
| 8 | Chờ A8 | CV có mean ± SD; R4 còn trống. Không xác nhận đã mở test chỉ từ ngày hiện tại. |
| 9 | Đạt phần C4/X14 | Cluster/paired bootstrap theo trang, B=2000; CI OOF phân biệt mean fold. CI test chờ A8. |
| 10 | Chưa đủ mọi candidate | Có baseline; C4/X14 CI chứa 0. PhoBERT chưa có paired CI với TF-IDF trong artifact C2, đã hạ mức kết luận. |
| 11 | Đạt mô tả | §3.5 đủ resources/dependence/representativeness/shift; thêm giới hạn C-app2 chỉ là giảm phiền ở giao diện. |
| 12 | Chưa xác nhận tái lập số | Đã đối chiếu config/code/ledger và chạy unit test; không chạy lại GPU hay mở test. A8/B chịu trách nhiệm tái lập thực nghiệm cuối. |
| 13 | Chờ A duyệt | §3.4b có các phần lỗi; ví dụ/văn bản và `h1_fp_audit.csv` chưa được A duyệt cuối trong checkout. |
| 14 | Đạt mô tả có giới hạn | T3/T4/C4/X14 có đối chứng; bỏ khẳng định độ trễ C4 <2ms/RAM <1.5GB do thiếu benchmark end-to-end. |
| 15 | Đạt phạm vi | H1/H2 cùng bài toán bình luận; E1/E2 cùng nhãn Head A; G1 dự báo tương lai được phân biệt. |

## Việc bàn giao còn mở

- **B7:** B gửi bản Objective and data cuối, nhất là nguồn/license và prevalence theo fold; C duyệt phần thay đổi sau đó.
- **A8/A9/A10:** kết quả test/paired CI, duyệt ví dụ lỗi, bảng/hình và tổng hợp cuối. C7 không tự điền các ô thiếu bằng kết quả train.
- **C-app2:** đã có code/test và bản build; thử menu/hộp thoại trên thiết bị và Android–backend HTTP thật theo `C_APP2_C7_VERIFICATION.md`.

Không tạo dòng metric mới trong ledger: lượt này chỉnh báo cáo và code giao diện,
không chạy thí nghiệm ML mới. Các số sử dụng đều là artifact đã có trong repo.
