"""Đăng ~10 bài mẫu lên Sân trường (Campus) qua API thật, bằng các tài khoản seed.

Chạy (backend đang chạy ở localhost:3000):  python backend/scripts/seed_campus_posts.py [base_url]
Bỏ qua bài đã có cùng nội dung, nên chạy lại không bị trùng. Ảnh lấy từ picsum.photos.
"""
import base64
import json
import sys
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:3000").rstrip("/")
PASSWORD = "Fwp@123456"

# (tài khoản seed, cộng đồng, chuyên mục, nội dung, seed ảnh hoặc None)
POSTS = [
    ("p01", "code", "GENERAL", "Ai còn thức chạy code đồ án không? Mình vừa sửa xong cái bug từ chiều, giờ mới dám đi ngủ 😴", None),
    ("p03", "photo", "GENERAL", "Hoàng hôn ở Hà Đông chiều nay, chụp vội bằng điện thoại thôi mà đẹp quá", "sunset-hadong"),
    ("p05", "study", "STUDY", "Nhóm mình ôn Xác suất thống kê sáng thứ 7, 8h ở thư viện tầng 3. Ai muốn vào học cùng cứ nhắn nhé!", None),
    ("p06", "game", "GENERAL", "Tìm 2 đồng đội leo rank tối nay, chơi vui là chính, không toxic 🙏", None),
    ("p07", "cats", "CONFESSION", "Bé mèo hay ngồi trước cổng KTX mấy hôm nay không thấy đâu, ai gặp thì báo mình với 🥺", "cat-dorm"),
    ("p09", "food", "QUESTION", "Quán bún chả gần cổng sau vừa ngon vừa rẻ. Ai biết quán nào ngon hơn chỉ mình với!", "bun-cha"),
    ("p10", "sport", "EVENT", "Chủ nhật này đá giao hữu CNTT vs Viễn thông lúc 7h sáng ở sân trường, đang thiếu 1 thủ môn!", None),
    ("p12", "music", "EVENT", "Tối thứ 6 có buổi acoustic nhỏ ở sảnh A2, ai rảnh qua nghe nhé 🎸", "acoustic-a2"),
    ("p14", None, "MARKET", "Pass lại giáo trình Giải tích 1 và 2, còn mới 90%, giá sinh viên 🫡", "textbooks"),
    ("p16", "code", "QUESTION", "Có ai làm đồ án dùng PhoBERT chưa? Train trên CPU mất bao lâu vậy mọi người 😭", None),
    ("p18", "food", "GENERAL", "Nấu lẩu cho cả phòng tối nay, ai cần công thức nước dùng thì mình gửi", "hotpot-room"),
    ("demo", None, "CONFESSION", "Cần tìm đội uống bia tối nay 🍺", None),
]


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method,
                                 data=json.dumps(body).encode("utf-8") if body is not None else None,
                                 headers={"Content-Type": "application/json; charset=utf-8",
                                          **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(req, timeout=60) as res:
        raw = res.read()
        return json.loads(raw) if raw else None


def image(seed):
    with urllib.request.urlopen(f"https://picsum.photos/seed/fwp-campus-{seed}/800/600", timeout=60) as res:
        return {"dataUrl": "data:image/jpeg;base64," + base64.b64encode(res.read()).decode("ascii"), "width": 800, "height": 600}


def main():
    existing = set()
    first = call("POST", "/auth/login", {"email": "p01@demo.fwp.local", "password": PASSWORD})["accessToken"]
    feed = call("GET", "/campus/posts?tab=for-you&limit=30", token=first)
    existing.update(p["content"] for p in feed["items"])
    for key, community, category, content, img in POSTS:
        if content in existing:
            print("bỏ qua (đã có):", content[:40])
            continue
        email = "demo@ptit.edu.vn" if key == "demo" else f"{key}@demo.fwp.local"
        token = call("POST", "/auth/login", {"email": email, "password": PASSWORD})["accessToken"]
        if community:
            call("POST", f"/campus/communities/{community}/join", token=token)
        body = {"content": content, "category": category, "anonymousMode": "FULLY_ANONYMOUS"}
        if community:
            body["community"] = community
        if img:
            body["images"] = [image(img)]
        call("POST", "/campus/posts", body, token=token)
        print("đã đăng:", content[:40])


if __name__ == "__main__":
    main()
