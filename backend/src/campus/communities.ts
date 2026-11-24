/** Cộng đồng trong Sân trường: danh sách cố định, người dùng tham gia/rời và đăng bài vào cộng đồng. */
export interface Community {
  id: string;
  name: string;
  description: string;
}

export const COMMUNITIES: Community[] = [
  { id: 'code', name: 'CLB Lập trình', description: 'Hỏi bài, khoe project, rủ nhau đi hackathon.' },
  { id: 'study', name: 'Ôn thi cuối kỳ', description: 'Chia sẻ đề cũ, lập nhóm học, nhắc deadline.' },
  { id: 'music', name: 'Hội mê nhạc', description: 'Indie, rap, nhạc phim, rủ đi show.' },
  { id: 'cats', name: 'Hội con sen', description: 'Ảnh mèo chó, tìm nhà cho thú cưng.' },
  { id: 'food', name: 'Ăn gì quanh trường', description: 'Review quán, rủ nhau đi ăn đêm.' },
  { id: 'sport', name: 'Thể thao PTIT', description: 'Bóng đá, bóng rổ, chạy bộ buổi sáng.' },
  { id: 'game', name: 'Game thủ', description: 'Tìm đồng đội leo rank, board game cuối tuần.' },
  { id: 'photo', name: 'Nhiếp ảnh', description: 'Khoe ảnh đẹp, rủ nhau đi chụp.' },
];

export const COMMUNITY_IDS = COMMUNITIES.map((c) => c.id);
