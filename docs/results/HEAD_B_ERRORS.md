# Head B — phân tích lỗi và horizon trên tập train

> Sinh bởi `python -m src.m3_health.analyze_head_b`. Chỉ dùng out-of-fold của tập train; test **chưa mở**.
> Ngưỡng mô tả: τ_B toàn cục trên OOF theo FPR ≤ 5%. Đây là phân tích chẩn đoán, không dùng để chọn mô hình.

## Tổng quan lỗi

| Tập | N | Prevalence | τ_B | PR-AUC | Precision | Recall | F1 | FPR | FP | FN |
|---|---|---|---|---|---|---|---|---|---|---|
| G1 | 3,348 | 0.500 | 0.698 | 0.626 | 0.668 | 0.100 | 0.174 | 0.050 | 83 | 1507 |
| H1 | 26,719 | 0.173 | 0.406 | 0.706 | 0.710 | 0.583 | 0.641 | 0.050 | 1100 | 1925 |
| H2 | 8,001 | 0.110 | 0.267 | 0.342 | 0.425 | 0.297 | 0.350 | 0.050 | 355 | 620 |

## Ví dụ lỗi

### G1

**Báo nhầm (FP) — top 5 theo xác suất**

| p | n_messages | Nhãn | Nội dung rút gọn |
|---|---|---|---|
| 0.945 | 16 | 0 | Reverting all my edits tonight is wiki-stalking. There is no consensus to warrent a mass-revert of all my work. I urge you to undo your reverts, or i will have to take this higher… |
| 0.906 | 10 | 0 | I'd like to point you to wiki_link where it says "''Participants, including participating administrators, should not reopen non-admin closures.''" You opined in that discussion an… |
| 0.893 | 10 | 0 | Well then its the same about deleting whatever I'm adding until i discuss it here to add ) so it goes against WP:BOLD also . for sure . — Preceding unsigned comment added by No it… |
| 0.863 | 4 | 0 | Below, you the researcher of Surrealism will find one of the many, many, many reference sources on Surrealism and Revolution. Here is one(at least I do the work and try to help!):… |
| 0.861 | 10 | 0 | Have a question for you? what do you think of this editions?? and ..this has been added to projects all over...is this clear to you what is being said and asked?? Should we reword… |

**Bỏ sót (FN) — top 5 theo xác suất**

| p | n_messages | Nhãn | Nội dung rút gọn |
|---|---|---|---|
| 0.126 | 4 | 1 | Soran in Northern Hama seized by Government troops per SOHR. <URL> — Preceding unsigned comment added by In other news, visual evidence that another one of Ced_Lab's maps is wrong… |
| 0.181 | 9 | 1 | The article already briefly mention the reason, why "fiscal austerity" was a necessary evil to implement in the midst of the Greek government-debt crisis: The initial position of … |
| 0.201 | 2 | 1 | The current article would be more accurately called "Islamic Charities who have been smeared as having terrorist links mainly by US government organizations". Which is a long titl… |
| 0.203 | 4 | 1 | OK, REFER TO THE "WU-TANG FOREVER" ALBUM BOOKLET, IN ONE OF THE LAST PAGES THEY HAVE A PAGE FOR FANS TO ORDER A "WU-TANG ID CARD". AS AN EXAMPLE OF WHAT IT WILL LOOK LIKE, THEY HA… |
| 0.210 | 3 | 1 | When Amy Adams engaged her new boyfriend, Jonas Andromeda on January 14, 2011, her daughter Aviana Olea Le Gallo might be renamed to Aviana Olea Andromeda. Where's your source? - … |

### H1

**Báo nhầm (FP) — top 5 theo xác suất**

| p | n_messages | Nhãn | Nội dung rút gọn |
|---|---|---|---|
| 1.000 | 1 | 0 | 📌<URL> AE CHƠI CÁ ĐỘ BANH BÓNG CẦN VỀ BỜ LIÊN HỆ @<user> Thế Xuân . KÈO LÊN FREE TRONG NHÓM KHÔNG THU PHÍ , KO LỢI DỤNG AE . RẤT CÓ TÂM VÀ NHIỆT TÌNH . RẤT NHIỀU AE ĐÃ VỀ BỜ . AE … |
| 1.000 | 1 | 0 | Trong thới giới thứ 3 những người đáng được tôn trọng và đáng được tuyên vương rất nhiều nhưng ko thấy ai biet đến bởi vì họ nghèo, còn nhung thứ đang sống trong cái kiếp giàu san… |
| 0.997 | 1 | 0 | đm mấy cái đồng bạc thay lốp vá xăm mà chúng nó coi rẻ mạng người vl :( |
| 0.995 | 1 | 0 | Lol :))) |
| 0.994 | 1 | 0 | Dm đéo hiểu sao giờ vẫn hỏi bác Thăng :))) báo thì 1 năm trc rồi! câu like vl ad ơi. |

**Bỏ sót (FN) — top 5 theo xác suất**

| p | n_messages | Nhãn | Nội dung rút gọn |
|---|---|---|---|
| 0.003 | 1 | 1 | 12:03 Toang cmmr |
| 0.003 | 1 | 1 | Anh em yếu sly ib |
| 0.006 | 1 | 1 | Các bác sống nhanh vaicutt :((( |
| 0.009 | 1 | 1 | anh trung cô hồn |
| 0.009 | 1 | 1 | 20/10 em tăng abe một bó nhan nhé! |

### H2

**Báo nhầm (FP) — top 5 theo xác suất**

| p | n_messages | Nhãn | Nội dung rút gọn |
|---|---|---|---|
| 0.875 | 1 | 0 | Người lớn đánh người lớn đã là tội hình sự rồi, người lớn đánh trẻ em tội nặng thêm...Phải xử tên côn đồ này cho thật nghiêm.Cô giáo thì chỉ có thể phân xử cho 2 đứa con nít chứ s… |
| 0.850 | 1 | 0 | Nếu có trường hợp ntn.- Bố Mẹ chỉ có 1 người con trai, người con lập gia đình sinh ra đứa cháu đích tôn. Chẳng may hai vợ chồng người con mất. Cháu đích tôn ở cùng Ông Bà Nội. Sau… |
| 0.830 | 1 | 0 | Không hiểu nổi rằng chỉ tên các nhân vật quan trọng và hiển thị như vậy lại bị đặt tên sai? Do đâu? Sở giao thôg công chánh làm sai? Hay do nhà in thi công bảng hiệu sai? Việc này… |
| 0.819 | 1 | 0 | Đào tạo quan trong là chất lượng chứ không phải mấy tờ giấy xin phép . Thu hồi bằng chỉ vì thủ tục hành chánh vậy ai phải là người chịu trách nhiệm? Học viên có tội tình gì , khi … |
| 0.788 | 1 | 0 | Bạn lớn nhà mình năm nay lớp 4, trc khi vào lớp 1 cháu chỉ biết các chữ cái đơn, nhưng mình rất nhà khi kèm con học. Bé em năm nay vào lớp 1, thấy thật sự vất vả, đi học cả ngày t… |

**Bỏ sót (FN) — top 5 theo xác suất**

| p | n_messages | Nhãn | Nội dung rút gọn |
|---|---|---|---|
| 0.000 | 1 | 1 | Tinh thần thể thao thì có, mà ý thức tham gia giao thông an toàn thì không có. Các anh nghĩ gì nếu lỡ ma men đang lái xe hơi, hoặc một anh tài xế mệt mỏi và… |
| 0.002 | 1 | 1 | LIONEL MESSI: 6 QBV, 6 GIÀY VÀNG, 34 DANH HIỆUCRISTIANO RONALDO : 5 QBV, 4 GIÀY VÀNG, 30 DANH HIỆUKL. LIONEL MESSI > CRISTIANO RONALDO |
| 0.010 | 1 | 1 | Tôi chưa bao giờ có khai niệm thích hàng trung quốc |
| 0.012 | 1 | 1 | Kế hoạch lãi 10tr đồng nhưng khi thực hiện thành 140 tỷ |
| 0.013 | 1 | 1 | 67 nhìn mặt như 97 và sức thì hơn 27 |

## Metric theo slice

| Tập | Slice | Giá trị | N | Dương tính | PR-AUC | Precision | Recall | FPR |
|---|---|---|---|---|---|---|---|---|
| G1 | length_bucket | ≤50 | 5 | 1 | 0.333 | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | 51–100 | 2 | 0 | — | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | 101–200 | 43 | 16 | 0.418 | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | 201–400 | 172 | 72 | 0.599 | 0.000 | 0.000 | 0.000 |
| G1 | length_bucket | >400 | 3,126 | 1,585 | 0.629 | 0.668 | 0.105 | 0.054 |
| G1 | lexicon_bucket | có từ độc hại | 84 | 51 | 0.655 | 0.650 | 0.255 | 0.212 |
| G1 | lexicon_bucket | không | 3,264 | 1,623 | 0.625 | 0.670 | 0.095 | 0.046 |
| G1 | prefix_bucket | 2 | 315 | 106 | 0.491 | 1.000 | 0.009 | 0.000 |
| G1 | prefix_bucket | 3–4 | 1,043 | 480 | 0.585 | 0.786 | 0.023 | 0.005 |
| G1 | prefix_bucket | 5–9 | 1,929 | 1,069 | 0.677 | 0.706 | 0.133 | 0.069 |
| G1 | prefix_bucket | 10+ | 61 | 19 | 0.549 | 0.382 | 0.684 | 0.500 |
| H1 | length_bucket | ≤50 | 18,976 | 2,544 | 0.687 | 0.734 | 0.526 | 0.030 |
| H1 | length_bucket | 51–100 | 5,326 | 1,145 | 0.711 | 0.710 | 0.559 | 0.063 |
| H1 | length_bucket | 101–200 | 1,837 | 663 | 0.781 | 0.699 | 0.706 | 0.172 |
| H1 | length_bucket | 201–400 | 558 | 256 | 0.820 | 0.626 | 0.930 | 0.470 |
| H1 | length_bucket | >400 | 22 | 12 | 0.573 | 0.571 | 1.000 | 0.900 |
| H1 | lexicon_bucket | có từ độc hại | 1,126 | 686 | 0.880 | 0.721 | 0.926 | 0.559 |
| H1 | lexicon_bucket | không | 25,593 | 3,934 | 0.667 | 0.707 | 0.524 | 0.039 |
| H2 | length_bucket | ≤50 | 2,144 | 219 | 0.356 | 0.500 | 0.274 | 0.031 |
| H2 | length_bucket | 51–100 | 2,396 | 237 | 0.351 | 0.433 | 0.287 | 0.041 |
| H2 | length_bucket | 101–200 | 2,115 | 235 | 0.350 | 0.443 | 0.281 | 0.044 |
| H2 | length_bucket | 201–400 | 1,046 | 138 | 0.295 | 0.328 | 0.275 | 0.086 |
| H2 | length_bucket | >400 | 300 | 53 | 0.467 | 0.400 | 0.566 | 0.182 |
| H2 | lexicon_bucket | có từ độc hại | 7 | 1 | 0.167 | 0.000 | 0.000 | 0.833 |
| H2 | lexicon_bucket | không | 7,994 | 881 | 0.344 | 0.428 | 0.297 | 0.049 |

## Horizon/lead-time trên G1

Chỉ tính trên hội thoại hỏng. `lead = attack_turn_idx - eval_len`; `eval_len = prefix_len` là cảnh báo ngay trước lượt công kích (lead 0). Cảnh báo sớm được chọn theo lần đầu tiên `p ≥ τ_B` trên các tiền tố ngắn hơn.

| Fold | N | Phát hiện sớm | Tỉ lệ | Median lead | TP tại prefix | Median lead TP | τ_B |
|---|---|---|---|---|---|---|---|
| 0 | 333 | 57 | 0.171 | 0.0 | 45 | 0.0 | 0.695 |
| 1 | 333 | 47 | 0.141 | 1.0 | 34 | 0.5 | 0.690 |
| 2 | 335 | 32 | 0.096 | 1.0 | 24 | 1.0 | 0.690 |
| 3 | 334 | 65 | 0.195 | 2.0 | 48 | 1.0 | 0.669 |
| 4 | 339 | 45 | 0.133 | 0.0 | 45 | 0.0 | 0.696 |

- Tổng số hội thoại hỏng: **1674**; cảnh báo sớm ở bất kỳ tiền tố nào: **246** (14.7%).
- Tại tiền tố triển khai (đúng giao thức hiện tại): **196 TP (11.7%)**. Trong số TP này, lead trung vị: **0.0 lượt**; IQR: **0.0–2.0**.
- Trong số TP tại prefix, phát hiện trước ≥1 lượt: **48.0%**; ≥2 lượt: **30.1%**; ≥3 lượt: **16.3%**.
- Trên toàn bộ hội thoại hỏng, cảnh báo trước ≥1 lượt: **8.6%**; ≥2 lượt: **5.4%**; ≥3 lượt: **3.0%**.

> Lưu ý: mô hình được huấn luyện trên tiền tố ngay trước lượt công kích; điểm ở các tiền tố ngắn hơn là ngoài phân phối huấn luyện. Ngưỡng giữ nguyên theo calibration, nên horizon chỉ là phân tích khảo sát, không phải metric chọn mô hình.
