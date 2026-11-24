export interface FunQuestion {
  id: string;
  text: string;
  options: string[];
}

/** Câu hỏi vui kiểu OkCupid ; id cố định vì câu trả lời lưu theo id. */
export const QUESTION_BANK: FunQuestion[] = [
  { id: 'sunday', text: 'Sáng chủ nhật lý tưởng của bạn là…', options: ['Ngủ tới trưa', 'Cà phê một mình', 'Đi chơi với bạn bè', 'Chạy bộ / tập thể thao'] },
  { id: 'milktea', text: 'Trà sữa của bạn?', options: ['Full topping', 'Ít topping', 'Không topping', 'Không uống trà sữa'] },
  { id: 'deadline', text: 'Deadline còn 1 ngày, bạn đang…', options: ['Xong từ tuần trước', 'Đang làm nốt', 'Mới bắt đầu', 'Deadline là gì?'] },
  { id: 'pet', text: 'Mèo hay chó?', options: ['Mèo', 'Chó', 'Cả hai', 'Không nuôi gì'] },
  { id: 'firstdate', text: 'Buổi gặp đầu tiên nên là…', options: ['Cà phê', 'Đi dạo', 'Xem phim', 'Học nhóm ở thư viện'] },
  { id: 'contact', text: 'Nhắn tin hay gọi điện?', options: ['Nhắn tin', 'Gọi điện', 'Voice message', 'Gặp luôn cho nhanh'] },
  { id: 'owl', text: 'Bạn là cú đêm hay chim sớm?', options: ['Cú đêm', 'Chim sớm', 'Tùy hôm'] },
  { id: 'travel', text: 'Đi du lịch kiểu nào?', options: ['Lên lịch từng giờ', 'Đi đâu tính đó', 'Ở nhà là nhất'] },
  { id: 'bill', text: 'Đi ăn cùng nhau thì trả tiền thế nào?', options: ['Chia đôi', 'Ai rủ người đó trả', 'Thay phiên nhau', 'Tùy hôm'] },
  { id: 'unread', text: 'Số tin nhắn chưa đọc trong máy bạn?', options: ['0', 'Dưới 10', 'Vài chục', 'Hàng trăm'] },
  { id: 'latefood', text: 'Đói lúc 11 giờ đêm, bạn chọn…', options: ['Bánh mì', 'Mì cay', 'Xôi', 'Nhịn cho đẹp dáng'] },
  { id: 'zodiac', text: 'Bạn có tin cung hoàng đạo không?', options: ['Tin', 'Đọc cho vui', 'Không tin'] },
];

export const QUESTION_BY_ID = new Map(QUESTION_BANK.map((q) => [q.id, q]));

/** Cần ít nhất ngần này câu chung mới hiện % hợp câu hỏi. */
export const MIN_COMMON_QUESTIONS = 3;

export interface AnswerLike {
  questionId: string;
  answer: number;
  acceptable: number[];
}

/**
 * Kiểu OkCupid (bỏ trọng số "mức quan trọng"): mỗi bên tính tỉ lệ câu chung mà
 * câu trả lời của người kia nằm trong tập mình chấp nhận ; % hợp = trung bình nhân hai tỉ lệ.
 */
export function questionMatch(
  mine: AnswerLike[],
  theirs: AnswerLike[],
): { percent: number; common: number } | null {
  const other = new Map(theirs.map((a) => [a.questionId, a]));
  const pairs = mine.flatMap((a) => (other.has(a.questionId) ? [[a, other.get(a.questionId)!] as const] : []));
  if (pairs.length < MIN_COMMON_QUESTIONS) return null;
  const okMine = pairs.filter(([m, t]) => m.acceptable.includes(t.answer)).length / pairs.length;
  const okTheirs = pairs.filter(([m, t]) => t.acceptable.includes(m.answer)).length / pairs.length;
  return { percent: Math.round(100 * Math.sqrt(okMine * okTheirs)), common: pairs.length };
}
