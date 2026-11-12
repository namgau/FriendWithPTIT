# B5 — đặc trưng T3 phong cách tương tác

`src.m3_health.features_t3.extract_t3_features(messages, samples, language='en', window=10)` nhận bảng tin và mẫu `(conversation_id, turn_idx)`, trả đặc trưng theo đúng thứ tự và index mẫu. E1/E2 dùng `en`; `vi` là từ điển tự xây để dùng thử, không phải lexicon chuẩn hoặc bộ phân đoạn từ tiếng Việt đã được đánh giá. Trích xuất là phép xác định, không fit tham số hay đọc nhãn.

## Định nghĩa

Chỉ xét tối đa `window` tin kết thúc tại t, sắp theo `turn_idx`; không yêu cầu chỉ số lượt liên tiếp. Văn bản được NFC, casefold và tách bằng biểu thức từ Unicode; tiếng Việt được tính theo **âm tiết**, không phải từ ghép. Các nhóm từ chức năng được liệt kê công khai trong `_LEXICONS`: đại từ, mạo từ, giới từ, liên từ, trợ động từ, phủ định (tiếng Anh); đại từ, trợ từ, liên từ, giới từ, phủ định (tiếng Việt). Đây là danh sách nhỏ tự xây, chưa kiểm định từ điển hay độ bao phủ, không tương đương LIWC.

- **LSM**: với đúng hai sender đã biết và mỗi bên có ít nhất một token, tính tỉ lệ token của từng nhóm trên tổng token của từng bên. Với các nhóm xuất hiện ở ít nhất một bên, điểm nhóm là `1 - |pA-pB|/(pA+pB+0.0001)`; LSM là trung bình điểm nhóm. Không tính các nhóm cả hai bằng 0 để tránh tạo tương đồng giả.
- **LSM slope**: hồi quy tuyến tính LSM theo vị trí tin trên các tiền tố liên tiếp trong cửa sổ hiện tại mà LSM xác định. Cần ít nhất hai điểm. Đây là xu hướng trong cửa sổ, không phải chuỗi LSM của các cửa sổ trượt trước đó.
- **Lexical mirroring**: Jaccard trên tập token của hai tin liền kề thuộc hai sender khác nhau và đều có token. `mean` là trung bình các cặp hợp lệ; `slope` là độ dốc theo vị trí tin trả lời thực tế.
- **Turn-taking**: một lượt là chuỗi tin liền nhau cùng sender đã biết; độ dài là số tin/lượt. Tỉ lệ đổi lượt = số cặp liền kề đổi sender / số cặp liền kề có cả hai sender đã biết. Sender thiếu phá vỡ lượt và không tham gia cặp.
- Các giá trị không xác định là `NaN`; cờ `*_missing` phân biệt thiếu dữ liệu với tương đồng/đổi lượt bằng 0. Slope cần ít nhất hai điểm. Khi có hơn hai sender, LSM không xác định và `t3_multi_sender=1`; mirroring/turn-taking vẫn tính cho các cặp kề hợp lệ. Từ điển có thể bỏ sót từ; không dùng thiếu phủ từ điển làm bằng chứng hai bên giống nhau.

`run_ablation_a` bổ sung T3 vào X1 dưới tên `T1+T2+T3`; nếu có T4, tập tích lũy là `T1+T2+T3+T4`. Baseline cũ và X4 không thay đổi. LightGBM xử lý NaN; nếu dùng estimator khác phải fit imputer chỉ trên train fold.

## Kiểm thử

`python -m pytest tests/test_features_t3.py tests/test_ablation_t3.py -q`

Không mở `data/locked/` hoặc dùng test khóa khi kiểm thử hay chạy ablation. Chưa có kết luận về giá trị dự báo của T3 cho đến khi A chạy X1 và đánh giá trên các fold quy định.
