"""Sinh docs/data_cards/DATA_CARD_<ID>.md từ data/processed/<ID>/stats.json.

Chạy sau src.data.prepare:  python -m src.data.cards
Phần mô tả (nguồn, license, xử lý) viết tay ở META ; mọi con số lấy từ stats.json.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

META = {
    "E1": {
        "name": "Ubuntu Dialogue Corpus",
        "head": "Head A (hội thoại sắp tắt) — bộ tiếng Anh chính",
        "source": "Kaggle `rtatman/ubuntu-dialogue-corpus`, file `dialogueText.csv` + `dialogueText_196.csv` "
                  "(không dùng `dialogueText_301.csv` 1.8 GB). Gốc: Lowe et al. 2015, log IRC #ubuntu.",
        "license": "Kaggle ghi \"other\" ; log IRC công khai, dùng cho nghiên cứu.",
        "processing": "Giữ hội thoại có đúng 2 người gửi ; sắp theo thời gian rồi theo thứ tự trong file. "
                      "Timestamp gốc làm tròn tới phút.",
        "unit": "hội thoại (`group_id` = hội thoại). Một người có thể ở nhiều hội thoại → còn phụ thuộc "
                "giữa hội thoại, nêu ở Practical limits.",
        "caveats": "Hỗ trợ kỹ thuật, không phải chat làm quen ; khoảng cách tin rất ngắn (phiên IRC) → "
                   "Δ = 48h gần như chỉ đánh dấu tin cuối, A cần cân nhắc Δ theo miền (X5).",
    },
    "E2": {
        "name": "Customer Support on Twitter",
        "head": "Head A — bộ thứ 2 (cross-domain E1 ↔ E2)",
        "source": "Kaggle `thoughtvector/customer-support-on-twitter`, file `twcs/twcs.csv`.",
        "license": "CC BY-NC-SA 4.0 (phi thương mại).",
        "processing": "Nối tweet thành cây theo `in_response_to_tweet_id` ; giữ cây có đúng 1 khách hàng + "
                      "1 tài khoản CSKH và ≥ 2 tin. Thứ tự theo `created_at` rồi `tweet_id`.",
        "unit": "khách hàng (`group_id` = khách hàng): mọi hội thoại của một khách nằm cùng phía split.",
        "caveats": "Hội thoại thường \"tắt\" vì đã giải quyết xong, không phải vì mất hứng thú ; "
                   "phần lớn hội thoại rất ngắn (xem số hội thoại ≥ 6 tin).",
    },
    "G1": {
        "name": "Conversations Gone Awry — Wikipedia",
        "head": "Head B (hội thoại sắp trở nên công kích)",
        "source": "ConvoKit `conversations-gone-awry-corpus` "
                  "(zissou.infosci.cornell.edu/convokit/datasets/). Zhang et al. 2018 ; Chang & "
                  "Danescu-Niculescu-Mizil 2019.",
        "license": "Nội dung trang thảo luận Wikipedia (CC BY-SA) ; nhãn của tác giả corpus, dùng cho nghiên cứu.",
        "processing": "Bỏ dòng tiêu đề mục (`is_section_header`) như CRAFT ; sắp theo timestamp. Cột "
                      "`is_attack` đánh dấu lượt công kích (A dùng để cắt tiền tố, A2c).",
        "unit": "trang Wikipedia (`group_id` = trang): giữ nguyên cặp ghép hỏng/lành cùng trang.",
        "caveats": "Thiết kế ghép cặp → cân bằng 50/50, ngoài đời tỉ lệ thấp hơn nhiều (prior shift).",
    },
    "H1": {
        "name": "ViHSD",
        "head": "Head B — bộ phân loại độc hại từng tin tiếng Việt",
        "source": "HuggingFace `htdung167/ViHSD` — bản sao trùng kích thước từng file với `uitnlp/vihsd` "
                  "(bản gốc bị gated). Luu et al. 2021.",
        "license": "HuggingFace `uitnlp/vihsd` ghi MIT ; chỉ dùng cho nghiên cứu.",
        "processing": "Gộp train/dev/test gốc (giữ `orig_split`) ; `toxic` = OFFENSIVE hoặc HATE.",
        "unit": "nội dung (`group_id` = băm văn bản đã chuẩn hóa): bình luận trùng nhau cùng phía split.",
        "caveats": "Bình luận mạng xã hội công khai, không phải chat riêng ; có teencode, tiếng lóng.",
    },
    "H2": {
        "name": "UIT-ViCTSD",
        "head": "Head B — bộ tiếng Việt thứ 2 ; bài thực hành imbalance/SMOTE",
        "source": "HuggingFace `tarudesu/ViCTSD`. Nguyen et al. 2021.",
        "license": "Không ghi trên HuggingFace ; dùng cho nghiên cứu (cần kiểm tra lại với bài báo gốc).",
        "processing": "Gộp train/valid/test gốc (giữ `orig_split`) ; `toxic` = cột Toxicity.",
        "unit": "bài báo (`group_id` = băm tiêu đề bài): bình luận cùng bài phụ thuộc nhau.",
        "caveats": "Rất mất cân bằng ; bình luận báo điện tử, văn phong khác chat.",
    },
}


def render(d: str, meta: dict, stats: dict) -> str:
    rows = "\n".join(f"| `{k}` | {json.dumps(v, ensure_ascii=False)} |" for k, v in stats.items())
    return f"""# Data Card {d} — {meta['name']}

> Sinh tự động bởi `python -m src.data.cards` từ `data/processed/{d}/stats.json`. Không sửa số bằng tay.

- **Dùng cho:** {meta['head']}
- **Nguồn:** {meta['source']}
- **License:** {meta['license']}
- **Checksum file gốc:** `data/checksums.txt`
- **Tiền xử lý** (`src/data/prepare.py`): {meta['processing']}
- **Đơn vị độc lập:** {meta['unit']}
- **Lưu ý / hạn chế:** {meta['caveats']}
- **Riêng tư:** tên người dùng được băm (sha1, 12 ký tự) ; không commit dữ liệu.

## Số liệu (in từ code)

| Chỉ số | Giá trị |
|---|---|
{rows}
"""


def main() -> None:
    out = ROOT / "docs" / "data_cards"
    for d, meta in META.items():
        f = ROOT / "data" / "processed" / d / "stats.json"
        if f.exists():
            (out / f"DATA_CARD_{d}.md").write_text(render(d, meta, json.loads(f.read_text(encoding="utf-8"))),
                                                   encoding="utf-8", newline="\n")
            print("wrote", d)


if __name__ == "__main__":
    main()
