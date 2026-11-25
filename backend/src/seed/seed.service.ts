import { Injectable, Logger, OnApplicationBootstrap } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { Repository } from 'typeorm';
import { DEMO_EMAIL, DEMO_PASSWORD } from '../auth/auth.service';
import {
  Match,
  Message,
  Notification,
  Profile,
  QuestionAnswer,
  Swipe,
  User,
} from '../database/entities';
import { QUESTION_BANK } from '../questions/questions.bank';

interface SeedProfile {
  key: string;
  nickname: string;
  fullName: string;
  age: number;
  gender: 'MALE' | 'FEMALE' | 'OTHER';
  faculty: string;
  major: string;
  year: string;
  gpa: number;
  bio: string;
  interests: string[];
  seed: number;
  mode: 'DATING' | 'FRIENDS' | 'STUDY';
  alumni?: boolean;
  campus?: string;
}

const PTIT = 'Học viện Công nghệ Bưu chính Viễn thông';
const CAMPUS_HN = 'Hà Nội — Hà Đông';
const CAMPUS_HCM = 'TP. Hồ Chí Minh';
const CENTER_LAT = 20.9818;
const CENTER_LNG = 105.7877;

/**
 * Ảnh mẫu: một chân dung từ pravatar.cc (bộ ảnh dựng sẵn cho dữ liệu giả, đã chọn tay:
 * không trẻ em, không người nổi tiếng) + 3 ảnh đời thường từ picsum.photos theo seed.
 */
/** "Thêm về tôi" mẫu, cố định theo seed. */
function seedAbout(seed: number, gender: string): Record<string, string> {
  const pick = <T>(xs: T[], k: number) => xs[(seed * 7 + k) % xs.length];
  return {
    smoking: pick(['Không hút thuốc', 'Không hút thuốc', 'Thỉnh thoảng'], 1),
    drinking: pick(['Không uống', 'Uống xã giao', 'Thỉnh thoảng'], 2),
    heightCm: String((gender === 'MALE' ? 165 : 152) + ((seed * 13) % 18)),
    pets: pick(['Nuôi mèo', 'Nuôi chó', 'Chưa nuôi', 'Muốn nuôi'], 3),
    religion: pick(['Không theo tôn giáo', 'Phật giáo', 'Công giáo', 'Không theo tôn giáo'], 4),
    exercise: pick(['Tập thường xuyên', 'Thỉnh thoảng', 'Hầu như không'], 5),
  };
}

const PORTRAIT: Record<string, number> = { p01: 5, p02: 14, p03: 39, p04: 55, p05: 24, p06: 6, p07: 25, p08: 59, p09: 31, p10: 60, p11: 32, p12: 30, p13: 57, p14: 22, p15: 53, p16: 9, p17: 54, p18: 28, p19: 12, p20: 45, demo: 8, mo: 52 };
export function seedPhotos(key: string, _gender: string, seed: number): string[] {
  return [
    `https://i.pravatar.cc/600?img=${PORTRAIT[key] ?? (seed % 40) + 5}`,
    ...[1, 2, 3].map((i) => `https://picsum.photos/seed/fwp-${key}-${i}/600/800`),
  ];
}

const SEED_PROFILES: SeedProfile[] = [
  { key: 'p01', nickname: 'Lộ I I', fullName: 'Nguyễn Minh Anh', age: 20, gender: 'FEMALE', faculty: 'Công nghệ Thông tin', major: 'Kỹ thuật Phần mềm', year: '2024', gpa: 3.72, bio: 'Mình mê code, cà phê và những buổi chiều nhiều nắng. Đang học cách làm một app tử tế và nuôi thêm một chú mèo nữa.', interests: ['Cà phê', 'Lập trình', 'Indie', 'Mèo', 'Xem phim'], seed: 1, mode: 'DATING' },
  { key: 'p02', nickname: 'Bộ PC', fullName: 'Trần Gia Huy', age: 21, gender: 'MALE', faculty: 'An toàn Thông tin', major: 'An toàn Thông tin', year: '2023', gpa: 3.41, bio: 'Chơi bóng rổ mỗi tối, thích tìm hiểu bảo mật và đi cà phê với bạn bè. Người mới quen sẽ thấy mình ít nói, quen rồi thì rất nhiều chuyện.', interests: ['Bóng rổ', 'Cà phê', 'Bảo mật', 'Du lịch'], seed: 2, mode: 'FRIENDS' },
  { key: 'p03', nickname: 'Bộ Bikini', fullName: 'Lê Thảo Linh', age: 19, gender: 'FEMALE', faculty: 'Đa phương tiện', major: 'Thiết kế Đồ họa', year: '2025', gpa: 3.18, bio: 'Chụp ảnh, nghe nhạc và sưu tầm board game. Mình tin một cuộc trò chuyện hay bắt đầu từ một câu hỏi thật lòng.', interests: ['Chụp ảnh', 'Âm nhạc', 'Mèo', 'Board game'], seed: 3, mode: 'DATING' },
  { key: 'p04', nickname: 'Tộ Ti Ti', fullName: 'Phạm Đức Long', age: 23, gender: 'MALE', faculty: 'Viễn thông', major: 'Kỹ thuật Viễn thông', year: '2021', gpa: 3.55, bio: 'Đã tốt nghiệp, đang làm về hạ tầng mạng. Cuối tuần thường chạy bộ ở Hồ Tây rồi ôm guitar đến tối.', interests: ['Chạy bộ', 'Guitar', 'Cà phê', 'Khởi nghiệp'], seed: 4, mode: 'STUDY', alumni: true },
  { key: 'p05', nickname: 'Hộ Ly Ly', fullName: 'Hoàng Ngọc Mai', age: 20, gender: 'FEMALE', faculty: 'Kinh tế', major: 'Marketing', year: '2024', gpa: 3.66, bio: 'Đọc sách, tập yoga và hay ghi chú mọi thứ. Mình thích những người có gu riêng nhưng vẫn biết lắng nghe.', interests: ['Sách', 'Cà phê', 'Yoga', 'Nhiếp ảnh'], seed: 5, mode: 'DATING' },
  { key: 'p06', nickname: 'Cộ Bi Bi', fullName: 'Đỗ Hoàng Nam', age: 22, gender: 'MALE', faculty: 'Công nghệ Thông tin', major: 'Hệ thống Thông tin', year: '2022', gpa: 2.98, bio: 'Game thủ cuối tuần, fan bóng đá và phim khoa học viễn tưởng. Đang tìm người cùng leo rank hoặc cùng xem phim.', interests: ['Game', 'Bóng đá', 'Xem phim', 'Công nghệ'], seed: 6, mode: 'FRIENDS', campus: CAMPUS_HCM },
  { key: 'p07', nickname: 'Mộ Na Na', fullName: 'Nguyễn Khánh Vy', age: 21, gender: 'FEMALE', faculty: 'Đa phương tiện', major: 'Truyền thông Đa phương tiện', year: '2023', gpa: 3.83, bio: 'Vẽ linh tinh, nghe indie và đi triển lãm. Mình thích những buổi hẹn không cần kịch bản, chỉ cần thấy vui.', interests: ['Thiết kế', 'Vẽ', 'Indie', 'Mèo'], seed: 7, mode: 'DATING' },
  { key: 'p08', nickname: 'Gộ Pi Pi', fullName: 'Bùi Tuấn Kiệt', age: 20, gender: 'MALE', faculty: 'An toàn Thông tin', major: 'An toàn Thông tin', year: '2024', gpa: 3.29, bio: 'Mê CTF, thích đọc write-up và uống cà phê sữa đá. Có thể ngồi im cả buổi nếu đang debug.', interests: ['CTF', 'Lập trình', 'Cà phê', 'Board game'], seed: 8, mode: 'STUDY' },
  { key: 'p09', nickname: 'Xộ La La', fullName: 'Phan Lan Chi', age: 22, gender: 'FEMALE', faculty: 'Viễn thông', major: 'Truyền thông và Mạng máy tính', year: '2022', gpa: 3.05, bio: 'Thích đi đây đó, chụp ảnh và nấu ăn cho bạn bè. Mình tin mỗi người đều có một câu chuyện hay để kể.', interests: ['Du lịch', 'Chụp ảnh', 'Âm nhạc', 'Nấu ăn'], seed: 9, mode: 'DATING' },
  { key: 'p10', nickname: 'Rộ Tê Tê', fullName: 'Ngô Quang Dũng', age: 19, gender: 'MALE', faculty: 'Kinh tế', major: 'Thương mại Điện tử', year: '2025', gpa: 2.72, bio: 'Bóng rổ, game và đồ ăn ngon. Học không giỏi nhất nhưng luôn cố gắng. Rất vui được làm quen.', interests: ['Bóng rổ', 'Game', 'Cà phê', 'Xem phim'], seed: 10, mode: 'FRIENDS', campus: CAMPUS_HCM },
  { key: 'p11', nickname: 'Nộ Xi Xi', fullName: 'Trần Hà My', age: 20, gender: 'FEMALE', faculty: 'Công nghệ Thông tin', major: 'Khoa học Máy tính', year: '2024', gpa: 3.62, bio: 'Học AI, chạy bộ và đọc sách. Đang tìm một người bạn học nghiêm túc nhưng vẫn biết đùa.', interests: ['Lập trình', 'Sách', 'Cà phê', 'Chạy bộ'], seed: 11, mode: 'STUDY' },
  { key: 'p12', nickname: 'Sộ Mi Mi', fullName: 'Lý Bảo Trâm', age: 21, gender: 'FEMALE', faculty: 'Đa phương tiện', major: 'Thiết kế Đồ họa', year: '2023', gpa: 3.37, bio: 'Làm visual, vẽ minh họa và nuôi mèo. Thích board game và những cuộc trò chuyện dài không mục đích.', interests: ['Vẽ', 'Thiết kế', 'Board game', 'Mèo'], seed: 12, mode: 'DATING' },
  { key: 'p13', nickname: 'Vộ Lu Lu', fullName: 'Vũ Việt Anh', age: 23, gender: 'MALE', faculty: 'An toàn Thông tin', major: 'An toàn Thông tin', year: '2021', gpa: 3.91, bio: 'Alumni, làm bảo mật. Thích guitar và cà phê, sẵn sàng chia sẻ kinh nghiệm nghề cho các bạn sinh viên.', interests: ['Bảo mật', 'CTF', 'Guitar', 'Cà phê'], seed: 13, mode: 'STUDY', alumni: true },
  { key: 'p14', nickname: 'Tộ Ka Ka', fullName: 'Đỗ Thu Trang', age: 19, gender: 'FEMALE', faculty: 'Kinh tế', major: 'Kế toán', year: '2025', gpa: 2.45, bio: 'K-pop, nhảy và trà sữa. Học chưa giỏi nhưng đang cố mỗi ngày. Tìm bạn cùng đi nhảy hoặc đi ăn.', interests: ['K-pop', 'Nhảy', 'Chụp ảnh', 'Trà sữa'], seed: 14, mode: 'FRIENDS' },
  { key: 'p15', nickname: 'Hộ Bo Bo', fullName: 'Nguyễn Đức Anh', age: 22, gender: 'MALE', faculty: 'Viễn thông', major: 'Kỹ thuật Điện tử', year: '2022', gpa: 3.48, bio: 'Chạy bộ mỗi sáng, thích bóng đá và du lịch bụi. Đang học cách chụp ảnh đẹp hơn.', interests: ['Chạy bộ', 'Bóng đá', 'Du lịch', 'Nhiếp ảnh'], seed: 15, mode: 'DATING', campus: CAMPUS_HCM },
  { key: 'p16', nickname: 'Lộ Gu Gu', fullName: 'Lê Phương Thảo', age: 20, gender: 'FEMALE', faculty: 'Công nghệ Thông tin', major: 'Khoa học Máy tính', year: '2024', gpa: 3.78, bio: 'Làm AI, đọc sách và uống cà phê. Thích thảo luận về mô hình học máy và những ý tưởng kỳ lạ.', interests: ['AI', 'Lập trình', 'Sách', 'Cà phê'], seed: 16, mode: 'STUDY' },
  { key: 'p17', nickname: 'Bộ Su Su', fullName: 'Trần Quốc Bảo', age: 21, gender: 'MALE', faculty: 'Đa phương tiện', major: 'Truyền thông Đa phương tiện', year: '2023', gpa: 2.89, bio: 'Mê phim, nghe nhạc và làm thiết kế. Có một danh sách phim dài hơn cả học kỳ, cần người xem cùng.', interests: ['Xem phim', 'Âm nhạc', 'Thiết kế', 'Board game'], seed: 17, mode: 'FRIENDS' },
  { key: 'p18', nickname: 'Chộ Mô Mô', fullName: 'Phạm Diệu Linh', age: 22, gender: 'FEMALE', faculty: 'Kinh tế', major: 'Quản trị Kinh doanh', year: '2022', gpa: 3.24, bio: 'Yoga, nấu ăn và đi cà phê. Thích những buổi tối ấm áp với bạn bè và một nồi lẩu nhỏ.', interests: ['Yoga', 'Nấu ăn', 'Cà phê', 'Du lịch'], seed: 18, mode: 'DATING' },
  { key: 'p19', nickname: 'Gộ Lê Lê', fullName: 'Lâm Trung Hiếu', age: 20, gender: 'MALE', faculty: 'Công nghệ Thông tin', major: 'Kỹ thuật Phần mềm', year: '2024', gpa: 3.12, bio: 'Code, game và cà phê. Đang tập chạy bộ để bớt thức khuya. Rất thích làm quen với người mới.', interests: ['Lập trình', 'Game', 'Cà phê', 'Bóng rổ'], seed: 19, mode: 'FRIENDS' },
  { key: 'p20', nickname: 'Ngộ Kê Kê', fullName: 'Nguyễn Bích Ngọc', age: 21, gender: 'FEMALE', faculty: 'Viễn thông', major: 'Kỹ thuật Viễn thông', year: '2023', gpa: 3.57, bio: 'Guitar, sách và chạy bộ. Mình thích những người kiên nhẫn và biết giữ lời hứa.', interests: ['Âm nhạc', 'Guitar', 'Sách', 'Chạy bộ'], seed: 20, mode: 'STUDY' },
  { key: 'mo', nickname: 'Phùng Thanh Mộ', fullName: 'Phùng Thanh Mộ', age: 22, gender: 'MALE', faculty: 'Đa phương tiện', major: 'Công nghệ Đa phương tiện', year: '2022', gpa: 3.15, bio: 'Nhân vật demo, trả lời bằng AI (Gemini). Mê game, bóng đá và trà sữa — nhắn thử đi, mình rep liền 😄', interests: ['Game', 'Bóng đá', 'Trà sữa', 'Âm nhạc', 'Xem phim'], seed: 22, mode: 'DATING' },
];

@Injectable()
export class SeedService implements OnApplicationBootstrap {
  private readonly logger = new Logger(SeedService.name);

  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(Swipe) private readonly swipes: Repository<Swipe>,
    @InjectRepository(Match) private readonly matches: Repository<Match>,
    @InjectRepository(Message) private readonly messages: Repository<Message>,
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
    @InjectRepository(QuestionAnswer)
    private readonly answers: Repository<QuestionAnswer>,
    private readonly config: ConfigService,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    if (this.config.get<string>('SEED_DEMO', 'true') !== 'true') return;
    try {
      await this.seed();
    } catch (error) {
      this.logger.error('Demo seed failed', error instanceof Error ? error.stack : undefined);
    }
  }

  private async seed(): Promise<void> {
    const passwordHash = await bcrypt.hash(DEMO_PASSWORD, 10);
    const demoUser = await this.ensureUser(DEMO_EMAIL, passwordHash, true);
    await this.ensureDemoProfile(demoUser);

    const profiles = new Map<string, Profile>();
    for (const spec of SEED_PROFILES) {
      const user = await this.ensureUser(
        `${spec.key}@demo.fwp.local`,
        passwordHash,
        true,
      );
      const profile = await this.ensureSeedProfile(user, spec);
      profiles.set(spec.key, profile);
      await this.ensureAnswers(user.id, spec.seed, true);
    }
    await this.ensureAnswers(demoUser.id, 21);

    for (const key of ['p01', 'p03', 'p05']) {
      const profile = profiles.get(key);
      if (!profile) continue;
      await this.ensureSwipe(demoUser.id, profile.userId, 'LIKE');
      await this.ensureSwipe(profile.userId, demoUser.id, 'LIKE');
    }
    // Nhân vật demo trả lời bằng Gemini: match sẵn với tài khoản demo và chào trước.
    const mo = profiles.get('mo');
    if (mo) {
      await this.ensureSwipe(demoUser.id, mo.userId, 'LIKE');
      await this.ensureSwipe(mo.userId, demoUser.id, 'LIKE');
      await this.seedConversation(demoUser, mo, 80, 'HEALTHY', [
        [mo.userId, 'Ê match rồi nè! Mình là Mộ, dân Đa phương tiện. Cậu đang làm gì đó? 😄', 3],
      ]);
    }
    for (const key of ['p07', 'p11', 'p16']) {
      const profile = profiles.get(key);
      if (!profile) continue;
      await this.ensureSwipe(profile.userId, demoUser.id, 'LIKE');
    }

    const minhAnh = profiles.get('p01');
    const thaoLinh = profiles.get('p03');
    const ngocMai = profiles.get('p05');
    if (minhAnh) {
      await this.seedConversation(
        demoUser,
        minhAnh,
        68,
        'FADING',
        [
          [demoUser.id, 'Chào cậu, thấy cậu cũng thích indie. Cậu hay nghe ai?', 2 * 1440],
          [minhAnh.userId, 'Chào cậu! Mình nghe Vũ., Chillies với cả Cá Hồi Hoang nữa.', 2 * 1440 - 45],
          [demoUser.id, 'Ồ, gu giống mình thật. Cậu có đi show nào gần đây không?', 2 * 1440 - 60],
          [minhAnh.userId, 'Có, mình mới đi một show nhỏ ở Hà Nội. Không khí dễ chịu lắm.', 2 * 1440 - 70],
          [demoUser.id, 'Nghe hay đấy. Mình thì hay ngồi cà phê nghe nhạc thôi.', 2 * 1440 - 85],
          [minhAnh.userId, 'Cà phê cũng là một loại show mà, miễn là có tai nghe tốt.', 2 * 1440 - 90],
          [demoUser.id, 'Chuẩn. Cậu học KTPM đúng không? Có môn nào vui không?', 1440],
          [minhAnh.userId, 'Có môn thiết kế hệ thống, vừa khó vừa vui. Cậu bên ATTT à?', 1440 - 10],
          [demoUser.id, 'Không, mình cũng CNTT. Đang tập làm app và học thêm về AI.', 1440 - 20],
          [minhAnh.userId, 'Vậy là cùng ngành rồi. Cậu có đang làm project gì không?', 1440 - 30],
          [demoUser.id, 'Có, một app kết nối sinh viên. Nghe hơi tham vọng nhưng vui.', 1440 - 45],
          [minhAnh.userId, 'Cậu cũng thích indie à? Có nghe Vũ. chưa?', 5],
        ],
      );
    }
    if (thaoLinh) {
      await this.seedConversation(
        demoUser,
        thaoLinh,
        42,
        'AT_RISK',
        [
          [demoUser.id, 'Chào Bộ Bikini, ảnh cậu chụp đẹp thật.', 4 * 1440],
          [thaoLinh.userId, 'Cảm ơn cậu, mình chụp lúc đi cà phê.', 4 * 1440 - 30],
          [demoUser.id, 'Cậu thích chụp thể loại gì?', 4 * 1440 - 45],
          [thaoLinh.userId, 'Mình thích chân dung với cảnh đời thường.', 4 * 1440 - 60],
          [demoUser.id, 'Nghe thú vị. Hôm nào cà phê không?', 30 * 60],
          [thaoLinh.userId, 'Ừm, cũng được...', 26 * 60],
        ],
      );
    }
    if (ngocMai) {
      await this.seedConversation(
        demoUser,
        ngocMai,
        31,
        'AT_RISK',
        [
          [demoUser.id, 'Chào Mai, cậu có tài liệu môn Marketing không?', 6 * 1440],
          [ngocMai.userId, 'Có, mình gửi sau nhé.', 6 * 1440 - 20],
          [demoUser.id, 'Cảm ơn cậu nhiều.', 6 * 1440 - 30],
          [ngocMai.userId, 'Không có gì đâu.', 3 * 1440],
          [ngocMai.userId, 'Mai gửi tài liệu sau nhé', 3 * 1440 - 1],
        ],
      );
    }

    await this.seedNotifications(demoUser);
    this.logger.log(`Demo seed ready: ${profiles.size} profiles + demo account ${DEMO_EMAIL}`);
  }

  private async ensureUser(
    email: string,
    passwordHash: string,
    isDemo: boolean,
  ): Promise<User> {
    const existing = await this.users.findOne({ where: { email } });
    if (existing) return existing;
    return this.users.save(
      this.users.create({ email, passwordHash, isDemo }),
    );
  }

  private async ensureDemoProfile(user: User): Promise<Profile> {
    const existing = await this.profiles.findOne({ where: { userId: user.id } });
    if (existing?.profileComplete) {
      // Khóa cũ dạng K66/D24 → năm nhập học ; ảnh mẫu cũ (randomuser 128px) → bộ mới.
      const cohort = /^[DK](\d\d)$/.exec(existing.academicYear);
      if (cohort) existing.academicYear = existing.academicYear.startsWith('K') ? '2024' : `20${cohort[1]}`;
      const oldSeedPhotos = (existing.photos ?? []).some((p) => p.includes('randomuser.me'));
      if ((existing.photos ?? []).length && !oldSeedPhotos && !cohort && existing.coverPhoto) return existing;
      if (!(existing.photos ?? []).length || oldSeedPhotos) existing.photos = seedPhotos('demo', 'OTHER', 21);
      existing.coverPhoto ??= 'https://picsum.photos/seed/fwp-demo-cover/1200/500';
      return this.profiles.save(existing);
    }
    const profile =
      existing ??
      this.profiles.create({
        userId: user.id,
        interests: [],
      });
    Object.assign(profile, {
      photos: seedPhotos('demo', 'OTHER', 21),
      coverPhoto: 'https://picsum.photos/seed/fwp-demo-cover/1200/500',
      nickname: 'Bạn',
      fullName: 'Sinh viên PTIT',
      age: 20,
      gender: 'OTHER',
      school: PTIT,
      campus: CAMPUS_HN,
      faculty: 'Công nghệ Thông tin',
      major: 'Kỹ thuật Phần mềm',
      academicYear: '2024',
      alumni: false,
      gpa: 3.42,
      gpaTier: 'GOOD',
      bio: 'Mình đang học CNTT và thích xây dựng những sản phẩm nhỏ nhưng tử tế. Rất vui được làm quen!',
      interests: ['Cà phê', 'Lập trình', 'Âm nhạc', 'Du lịch'],
      avatarSeed: 21,
      mode: 'DATING',
      lat: CENTER_LAT,
      lng: CENTER_LNG,
      location: { type: 'Point', coordinates: [CENTER_LNG, CENTER_LAT] },
      verified: true,
      showGpaPublic: true,
      showLocationPublic: true,
      profileComplete: true,
    });
    return this.profiles.save(profile);
  }

  private async ensureSeedProfile(user: User, spec: SeedProfile): Promise<Profile> {
    // Seed-owned accounts: re-sync every boot so seed edits (ảnh, khóa) reach old databases.
    const existing = await this.profiles.findOne({ where: { userId: user.id } });
    const lat = CENTER_LAT + ((spec.seed % 7) - 3) * 0.004;
    const lng = CENTER_LNG + ((spec.seed % 5) - 2) * 0.0045;
    const profile =
      existing ??
      this.profiles.create({
        userId: user.id,
        interests: [],
      });
    Object.assign(profile, {
      nickname: spec.nickname,
      fullName: spec.fullName,
      age: spec.age,
      gender: spec.gender,
      school: PTIT,
      campus: spec.campus ?? CAMPUS_HN,
      faculty: spec.faculty,
      major: spec.major,
      academicYear: spec.year,
      alumni: spec.alumni ?? false,
      gpa: spec.gpa,
      gpaTier: this.tierForGpa(spec.gpa),
      bio: spec.bio,
      interests: spec.interests,
      avatarSeed: spec.seed,
      photos: seedPhotos(spec.key, spec.gender, spec.seed),
      about: seedAbout(spec.seed, spec.gender),
      languages: ['Tiếng Việt', ...(spec.seed % 2 ? ['Tiếng Anh'] : []), ...(spec.seed % 5 === 0 ? ['Tiếng Nhật'] : []), ...(spec.seed % 7 === 0 ? ['Tiếng Hàn'] : [])],
      coverPhoto: `https://picsum.photos/seed/fwp-${spec.key}-cover/1200/500`,
      mode: spec.mode,
      lat,
      lng,
      location: { type: 'Point', coordinates: [lng, lat] },
      verified: true,
      showGpaPublic: true,
      showLocationPublic: true,
      profileComplete: true,
    });
    return this.profiles.save(profile);
  }

  private async ensureSwipe(
    fromUserId: string,
    targetUserId: string,
    action: 'LIKE' | 'PASS' | 'SUPER_LIKE',
  ): Promise<void> {
    const existing = await this.swipes.findOne({
      where: { fromUserId, targetUserId },
    });
    if (!existing) {
      await this.swipes.save(
        this.swipes.create({ fromUserId, targetUserId, action, createdAt: new Date(Date.now() - 7 * 86_400_000) }),
      );
    }
  }

  private async seedConversation(
    demo: User,
    partner: Profile,
    compatibility: number,
    healthLevel: 'HEALTHY' | 'FADING' | 'AT_RISK',
    lines: Array<[string, string, number]>,
  ): Promise<void> {
    const [userAId, userBId] = [demo.id, partner.userId].sort();
    let match = await this.matches.findOne({ where: { userAId, userBId } });
    if (!match) {
      match = await this.matches.save(
        this.matches.create({
          userAId,
          userBId,
          compatibilityScore: compatibility,
          healthLevel,
          revealOptInA: false,
          revealOptInB: partner.userId === userBId ? true : false,
        }),
      );
    }
    const count = await this.messages.count({ where: { matchId: match.id } });
    if (count > 0) return;
    const now = Date.now();
    const saved = await this.messages.save(
      lines.map(([senderId, text, minutesAgo]) =>
        this.messages.create({
          matchId: match.id,
          senderId,
          text,
          type: 'TEXT',
          createdAt: new Date(now - minutesAgo * 60_000),
        }),
      ),
    );
    const last = saved[saved.length - 1];
    match.lastMessageAt = last?.createdAt ?? new Date();
    match.healthLevel = healthLevel;
    await this.matches.save(match);
  }

  private async seedNotifications(demo: User): Promise<void> {
    const count = await this.notifications.count({ where: { userId: demo.id } });
    if (count > 0) return;
    const now = Date.now();
    await this.notifications.save([
      this.notifications.create({
        userId: demo.id,
        kind: 'MATCH',
        title: 'Bạn có match mới',
        body: 'Lộ I I đã thích bạn. Ảnh vẫn được ẩn danh nhé!',
        read: false,
        createdAt: new Date(now - 2 * 86_400_000),
      }),
      this.notifications.create({
        userId: demo.id,
        kind: 'MESSAGE',
        title: 'Tin nhắn mới từ Lộ I I',
        body: 'Cậu cũng thích indie à? Có nghe Vũ. chưa?',
        read: false,
        createdAt: new Date(now - 5 * 60_000),
      }),
      this.notifications.create({
        userId: demo.id,
        kind: 'SAFETY',
        title: 'FwP bảo vệ bạn',
        body: 'Chỉ bạn mới thấy số điện thoại và vị trí chính xác. Hãy báo cáo nếu có dấu hiệu không an toàn.',
        read: true,
        createdAt: new Date(now - 36 * 3_600_000),
      }),
      this.notifications.create({
        userId: demo.id,
        kind: 'ACHIEVEMENT',
        title: 'Huy hiệu mới: Người mở lời',
        body: 'Bạn đã gửi tin nhắn đầu tiên trong một cuộc trò chuyện. Tiếp tục giữ nhịp nhé!',
        read: true,
        createdAt: new Date(now - 48 * 3_600_000),
      }),
    ]);
  }

  /**
   * Câu trả lời mẫu cố định theo seed (không ngẫu nhiên giữa các lần khởi động) ; mỗi câu
   * chấp nhận mọi đáp án trừ một, cho % hợp gu trải khoảng 60–100. `force` ghi đè (tài khoản seed).
   */
  private async ensureAnswers(userId: string, seed: number, force = false): Promise<void> {
    const stored = await this.answers.find({ where: { userId } });
    if (stored.length) {
      const current = stored.some((a) => QUESTION_BANK.some((q) => q.id === a.questionId));
      if (current && !force) return;
      await this.answers.delete({ userId });
    }
    const rows = QUESTION_BANK.flatMap((q, i) => {
      if ((seed + i) % 5 === 0) return [];
      const n = q.options.length;
      const answer = (seed * 7 + i * 3 + seed * i) % n;
      const rejected = (answer + 1 + ((seed + i) % (n - 1))) % n;
      const acceptable = q.options.map((_, k) => k).filter((k) => k !== rejected);
      return [this.answers.create({ userId, questionId: q.id, answer, acceptable })];
    });
    await this.answers.save(rows);
  }

  private tierForGpa(gpa: number): string {
    if (gpa >= 3.6) return 'EXCELLENT';
    if (gpa >= 3.2) return 'GOOD';
    if (gpa >= 2.5) return 'FAIR';
    return 'AVERAGE';
  }
}
