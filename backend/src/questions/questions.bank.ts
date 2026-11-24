export interface FunQuestion {
  id: string;
  text: string;
  options: string[];
}

/**
 * Câu hỏi ghép đôi dịch từ các câu hỏi công khai nổi tiếng của OkCupid (OkTrends, "The best
 * questions for a first date", 2011, và các câu hỏi match phổ biến). id cố định vì câu trả lời lưu theo id.
 */
export const QUESTION_BANK: FunQuestion[] = [
  { id: 'horror', text: 'Bạn có thích phim kinh dị không?', options: ['Có', 'Không'] },
  { id: 'travel_alone', text: 'Bạn đã từng đi du lịch một mình ở nước khác chưa?', options: ['Rồi', 'Chưa'] },
  { id: 'sailboat', text: 'Bỏ hết mọi thứ để ra sống trên một chiếc thuyền buồm, nghe có vui không?', options: ['Vui chứ', 'Không đâu'] },
  { id: 'sun_earth', text: 'Cái nào lớn hơn: Mặt Trời hay Trái Đất?', options: ['Mặt Trời', 'Trái Đất'] },
  { id: 'grammar', text: 'Lỗi chính tả, ngữ pháp có làm bạn khó chịu không?', options: ['Rất khó chịu', 'Hơi hơi', 'Không để ý'] },
  { id: 'messy', text: 'Bạn bừa bộn cỡ nào?', options: ['Rất gọn gàng', 'Bình thường', 'Khá bừa', 'Bãi chiến trường'] },
  { id: 'promises', text: 'Bạn giữ lời hứa thường xuyên tới đâu?', options: ['Luôn luôn', 'Phần lớn', 'Thỉnh thoảng', 'Hiếm khi'] },
  { id: 'astrology', text: 'Cung hoàng đạo có quan trọng khi chọn người yêu không?', options: ['Có', 'Đọc cho vui', 'Không'] },
  { id: 'jealousy', text: 'Ghen một chút có lành mạnh trong tình yêu không?', options: ['Có', 'Không'] },
  { id: 'weird', text: 'Bạn muốn mình bình thường hay kỳ quặc?', options: ['Bình thường', 'Kỳ quặc'] },
  { id: 'politics', text: 'Bạn có thích bàn chuyện thời sự, chính trị không?', options: ['Rất thích', 'Thỉnh thoảng', 'Tránh xa'] },
  { id: 'smoke', text: 'Bạn có hút thuốc không?', options: ['Không', 'Thỉnh thoảng', 'Có'] },
  { id: 'children', text: 'Sau này bạn có muốn có con không?', options: ['Có', 'Chưa chắc', 'Không'] },
  { id: 'first_date_bill', text: 'Buổi hẹn đầu, ai nên trả tiền?', options: ['Người rủ', 'Chia đôi', 'Ai cũng được'] },
  { id: 'proud', text: 'Bạn tự hào gì về bản thân nhất?', options: ['Sự tử tế', 'Sự chăm chỉ', 'Khiếu hài hước', 'Biết lắng nghe'] },
  { id: 'reply_time', text: 'Người ấy trả lời tin nhắn sau bao lâu thì bạn thấy ổn?', options: ['Vài phút', 'Vài giờ', 'Trong ngày', 'Lúc nào cũng được'] },
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
  // Chỉ tính câu còn trong bộ hiện tại (câu trả lời cho câu đã bỏ vẫn nằm trong DB).
  const other = new Map(theirs.filter((a) => QUESTION_BY_ID.has(a.questionId) && a.answer >= 0).map((a) => [a.questionId, a]));
  // Câu tự viết (answer = -1) không so được nên bỏ qua khi tính % hợp gu.
  const pairs = mine.filter((a) => QUESTION_BY_ID.has(a.questionId) && a.answer >= 0).flatMap((a) => (other.has(a.questionId) ? [[a, other.get(a.questionId)!] as const] : []));
  if (pairs.length < MIN_COMMON_QUESTIONS) return null;
  const okMine = pairs.filter(([m, t]) => m.acceptable.includes(t.answer)).length / pairs.length;
  const okTheirs = pairs.filter(([m, t]) => t.acceptable.includes(m.answer)).length / pairs.length;
  return { percent: Math.round(100 * Math.sqrt(okMine * okTheirs)), common: pairs.length };
}
