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
  Swipe,
  User,
} from '../database/entities';

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

const SEED_PROFILES: SeedProfile[] = [
  { key: 'p01', nickname: 'Mèo Lười', fullName: 'Nguyễn Minh Anh', age: 20, gender: 'FEMALE', faculty: 'Công nghệ Thông tin', major: 'Kỹ thuật Phần mềm', year: 'K66', gpa: 3.72, bio: 'Mình mê code, cà phê và những buổi chiều nhiều nắng. Đang học cách làm một app tử tế và nuôi thêm một chú mèo nữa.', interests: ['Cà phê', 'Lập trình', 'Indie', 'Mèo', 'Xem phim'], seed: 1, mode: 'DATING' },
  { key: 'p02', nickname: 'Huy Bóng Rổ', fullName: 'Trần Gia Huy', age: 21, gender: 'MALE', faculty: 'An toàn Thông tin', major: 'An toàn Thông tin', year: 'K65', gpa: 3.41, bio: 'Chơi bóng rổ mỗi tối, thích tìm hiểu bảo mật và đi cà phê với bạn bè. Người mới quen sẽ thấy mình ít nói, quen rồi thì rất nhiều chuyện.', interests: ['Bóng rổ', 'Cà phê', 'Bảo mật', 'Du lịch'], seed: 2, mode: 'FRIENDS' },
  { key: 'p03', nickname: 'Nắng Sớm', fullName: 'Lê Thảo Linh', age: 19, gender: 'FEMALE', faculty: 'Đa phương tiện', major: 'Thiết kế Đồ họa', year: 'K67', gpa: 3.18, bio: 'Chụp ảnh, nghe nhạc và sưu tầm board game. Mình tin một cuộc trò chuyện hay bắt đầu từ một câu hỏi thật lòng.', interests: ['Chụp ảnh', 'Âm nhạc', 'Mèo', 'Board game'], seed: 3, mode: 'DATING' },
  { key: 'p04', nickname: 'Long Guitar', fullName: 'Phạm Đức Long', age: 23, gender: 'MALE', faculty: 'Viễn thông', major: 'Kỹ thuật Viễn thông', year: 'K63', gpa: 3.55, bio: 'Đã tốt nghiệp, đang làm về hạ tầng mạng. Cuối tuần thường chạy bộ ở Hồ Tây rồi ôm guitar đến tối.', interests: ['Chạy bộ', 'Guitar', 'Cà phê', 'Khởi nghiệp'], seed: 4, mode: 'STUDY', alumni: true },
  { key: 'p05', nickname: 'Mai Sách', fullName: 'Hoàng Ngọc Mai', age: 20, gender: 'FEMALE', faculty: 'Kinh tế', major: 'Marketing', year: 'K66', gpa: 3.66, bio: 'Đọc sách, tập yoga và hay ghi chú mọi thứ. Mình thích những người có gu riêng nhưng vẫn biết lắng nghe.', interests: ['Sách', 'Cà phê', 'Yoga', 'Nhiếp ảnh'], seed: 5, mode: 'DATING' },
  { key: 'p06', nickname: 'Nam Game', fullName: 'Đỗ Hoàng Nam', age: 22, gender: 'MALE', faculty: 'Công nghệ Thông tin', major: 'Hệ thống Thông tin', year: 'K64', gpa: 2.98, bio: 'Game thủ cuối tuần, fan bóng đá và phim khoa học viễn tưởng. Đang tìm người cùng leo rank hoặc cùng xem phim.', interests: ['Game', 'Bóng đá', 'Xem phim', 'Công nghệ'], seed: 6, mode: 'FRIENDS', campus: CAMPUS_HCM },
  { key: 'p07', nickname: 'Vy Vẽ', fullName: 'Nguyễn Khánh Vy', age: 21, gender: 'FEMALE', faculty: 'Đa phương tiện', major: 'Truyền thông Đa phương tiện', year: 'K65', gpa: 3.83, bio: 'Vẽ linh tinh, nghe indie và đi triển lãm. Mình thích những buổi hẹn không cần kịch bản, chỉ cần thấy vui.', interests: ['Thiết kế', 'Vẽ', 'Indie', 'Mèo'], seed: 7, mode: 'DATING' },
  { key: 'p08', nickname: 'Kiệt CTF', fullName: 'Bùi Tuấn Kiệt', age: 20, gender: 'MALE', faculty: 'An toàn Thông tin', major: 'An toàn Thông tin', year: 'K66', gpa: 3.29, bio: 'Mê CTF, thích đọc write-up và uống cà phê sữa đá. Có thể ngồi im cả buổi nếu đang debug.', interests: ['CTF', 'Lập trình', 'Cà phê', 'Board game'], seed: 8, mode: 'STUDY' },
  { key: 'p09', nickname: 'Chi Lang Thang', fullName: 'Phan Lan Chi', age: 22, gender: 'FEMALE', faculty: 'Viễn thông', major: 'Truyền thông và Mạng máy tính', year: 'K64', gpa: 3.05, bio: 'Thích đi đây đó, chụp ảnh và nấu ăn cho bạn bè. Mình tin mỗi người đều có một câu chuyện hay để kể.', interests: ['Du lịch', 'Chụp ảnh', 'Âm nhạc', 'Nấu ăn'], seed: 9, mode: 'DATING' },
  { key: 'p10', nickname: 'Dũng Bóng Rổ', fullName: 'Ngô Quang Dũng', age: 19, gender: 'MALE', faculty: 'Kinh tế', major: 'Thương mại Điện tử', year: 'K67', gpa: 2.72, bio: 'Bóng rổ, game và đồ ăn ngon. Học không giỏi nhất nhưng luôn cố gắng. Rất vui được làm quen.', interests: ['Bóng rổ', 'Game', 'Cà phê', 'Xem phim'], seed: 10, mode: 'FRIENDS', campus: CAMPUS_HCM },
  { key: 'p11', nickname: 'My Code', fullName: 'Trần Hà My', age: 20, gender: 'FEMALE', faculty: 'Công nghệ Thông tin', major: 'Khoa học Máy tính', year: 'K66', gpa: 3.62, bio: 'Học AI, chạy bộ và đọc sách. Đang tìm một người bạn học nghiêm túc nhưng vẫn biết đùa.', interests: ['Lập trình', 'Sách', 'Cà phê', 'Chạy bộ'], seed: 11, mode: 'STUDY' },
  { key: 'p12', nickname: 'Trâm Màu', fullName: 'Lý Bảo Trâm', age: 21, gender: 'FEMALE', faculty: 'Đa phương tiện', major: 'Thiết kế Đồ họa', year: 'K65', gpa: 3.37, bio: 'Làm visual, vẽ minh họa và nuôi mèo. Thích board game và những cuộc trò chuyện dài không mục đích.', interests: ['Vẽ', 'Thiết kế', 'Board game', 'Mèo'], seed: 12, mode: 'DATING' },
  { key: 'p13', nickname: 'Anh Bảo Mật', fullName: 'Vũ Việt Anh', age: 23, gender: 'MALE', faculty: 'An toàn Thông tin', major: 'An toàn Thông tin', year: 'K63', gpa: 3.91, bio: 'Alumni, làm bảo mật. Thích guitar và cà phê, sẵn sàng chia sẻ kinh nghiệm nghề cho các bạn sinh viên.', interests: ['Bảo mật', 'CTF', 'Guitar', 'Cà phê'], seed: 13, mode: 'STUDY', alumni: true },
  { key: 'p14', nickname: 'Trang Kpop', fullName: 'Đỗ Thu Trang', age: 19, gender: 'FEMALE', faculty: 'Kinh tế', major: 'Kế toán', year: 'K67', gpa: 2.45, bio: 'K-pop, nhảy và trà sữa. Học chưa giỏi nhưng đang cố mỗi ngày. Tìm bạn cùng đi nhảy hoặc đi ăn.', interests: ['K-pop', 'Nhảy', 'Chụp ảnh', 'Trà sữa'], seed: 14, mode: 'FRIENDS' },
  { key: 'p15', nickname: 'Anh Chạy Bộ', fullName: 'Nguyễn Đức Anh', age: 22, gender: 'MALE', faculty: 'Viễn thông', major: 'Kỹ thuật Điện tử', year: 'K64', gpa: 3.48, bio: 'Chạy bộ mỗi sáng, thích bóng đá và du lịch bụi. Đang học cách chụp ảnh đẹp hơn.', interests: ['Chạy bộ', 'Bóng đá', 'Du lịch', 'Nhiếp ảnh'], seed: 15, mode: 'DATING', campus: CAMPUS_HCM },
  { key: 'p16', nickname: 'Thảo AI', fullName: 'Lê Phương Thảo', age: 20, gender: 'FEMALE', faculty: 'Công nghệ Thông tin', major: 'Khoa học Máy tính', year: 'K66', gpa: 3.78, bio: 'Làm AI, đọc sách và uống cà phê. Thích thảo luận về mô hình học máy và những ý tưởng kỳ lạ.', interests: ['AI', 'Lập trình', 'Sách', 'Cà phê'], seed: 16, mode: 'STUDY' },
  { key: 'p17', nickname: 'Bảo Phim', fullName: 'Trần Quốc Bảo', age: 21, gender: 'MALE', faculty: 'Đa phương tiện', major: 'Truyền thông Đa phương tiện', year: 'K65', gpa: 2.89, bio: 'Mê phim, nghe nhạc và làm thiết kế. Có một danh sách phim dài hơn cả học kỳ, cần người xem cùng.', interests: ['Xem phim', 'Âm nhạc', 'Thiết kế', 'Board game'], seed: 17, mode: 'FRIENDS' },
  { key: 'p18', nickname: 'Linh Nấu Ăn', fullName: 'Phạm Diệu Linh', age: 22, gender: 'FEMALE', faculty: 'Kinh tế', major: 'Quản trị Kinh doanh', year: 'K64', gpa: 3.24, bio: 'Yoga, nấu ăn và đi cà phê. Thích những buổi tối ấm áp với bạn bè và một nồi lẩu nhỏ.', interests: ['Yoga', 'Nấu ăn', 'Cà phê', 'Du lịch'], seed: 18, mode: 'DATING' },
  { key: 'p19', nickname: 'Hiếu Code', fullName: 'Lâm Trung Hiếu', age: 20, gender: 'MALE', faculty: 'Công nghệ Thông tin', major: 'Kỹ thuật Phần mềm', year: 'K66', gpa: 3.12, bio: 'Code, game và cà phê. Đang tập chạy bộ để bớt thức khuya. Rất thích làm quen với người mới.', interests: ['Lập trình', 'Game', 'Cà phê', 'Bóng rổ'], seed: 19, mode: 'FRIENDS' },
  { key: 'p20', nickname: 'Ngọc Guitar', fullName: 'Nguyễn Bích Ngọc', age: 21, gender: 'FEMALE', faculty: 'Viễn thông', major: 'Kỹ thuật Viễn thông', year: 'K65', gpa: 3.57, bio: 'Guitar, sách và chạy bộ. Mình thích những người kiên nhẫn và biết giữ lời hứa.', interests: ['Âm nhạc', 'Guitar', 'Sách', 'Chạy bộ'], seed: 20, mode: 'STUDY' },
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
    }

    for (const key of ['p01', 'p03', 'p05']) {
      const profile = profiles.get(key);
      if (!profile) continue;
      await this.ensureSwipe(demoUser.id, profile.userId, 'LIKE');
      await this.ensureSwipe(profile.userId, demoUser.id, 'LIKE');
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
          [demoUser.id, 'Chào Nắng Sớm, ảnh cậu chụp đẹp thật.', 4 * 1440],
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
    if (existing?.profileComplete) return existing;
    const profile =
      existing ??
      this.profiles.create({
        userId: user.id,
        interests: [],
      });
    Object.assign(profile, {
      nickname: 'Bạn',
      fullName: 'Sinh viên PTIT',
      age: 20,
      gender: 'OTHER',
      school: PTIT,
      campus: CAMPUS_HN,
      faculty: 'Công nghệ Thông tin',
      major: 'Kỹ thuật Phần mềm',
      academicYear: 'K66',
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
    const existing = await this.profiles.findOne({ where: { userId: user.id } });
    if (existing?.profileComplete) return existing;
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
        this.swipes.create({ fromUserId, targetUserId, action }),
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
        body: 'Mèo Lười đã thích bạn. Ảnh vẫn được ẩn danh nhé!',
        read: false,
        createdAt: new Date(now - 2 * 86_400_000),
      }),
      this.notifications.create({
        userId: demo.id,
        kind: 'MESSAGE',
        title: 'Tin nhắn mới từ Mèo Lười',
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

  private tierForGpa(gpa: number): string {
    if (gpa >= 3.6) return 'EXCELLENT';
    if (gpa >= 3.2) return 'GOOD';
    if (gpa >= 2.5) return 'FAIR';
    return 'AVERAGE';
  }
}
