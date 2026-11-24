import {
  BadRequestException,
  Injectable,
  NotFoundException,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Block, Profile, QuestionAnswer, Swipe } from '../database/entities';
import { CampusMediaStorage } from '../campus/campus-media.storage';
import { QUESTION_BY_ID, questionMatch } from '../questions/questions.bank';
import {
  compatibilityScore,
  distanceKm,
  normaliseInterest,
} from '../common/matching';
import { isUuid } from '../common/anonymity';
import { ProfileView, toProfileView } from '../common/profile.view';
import {
  DiscoverQueryDto,
  NearbyQueryDto,
  UpdateProfileDto,
} from './profile.dto';

interface QuestionInfo {
  match: { percent: number; common: number } | null;
  answers: Array<{ question: string; answer: string }>;
}

@Injectable()
export class ProfilesService {
  constructor(
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(Swipe) private readonly swipes: Repository<Swipe>,
    @InjectRepository(Block) private readonly blocks: Repository<Block>,
    @Optional() @InjectRepository(QuestionAnswer)
    private readonly answers?: Repository<QuestionAnswer>,
    @Optional() private readonly media?: CampusMediaStorage,
  ) {}

  static readonly MIN_PHOTOS = 3;
  static readonly MAX_PHOTOS = 6;
  static readonly PHOTO_PREFIX = '/profiles/photos/';

  async addPhoto(userId: string, image: string): Promise<ProfileView> {
    if (!this.media) throw new BadRequestException('Photo storage unavailable');
    const profile = await this.ensureProfile(userId);
    if ((profile.photos ?? []).length >= ProfilesService.MAX_PHOTOS) {
      throw new BadRequestException(`At most ${ProfilesService.MAX_PHOTOS} photos`);
    }
    const stored = await this.media.saveDataUrl(image);
    profile.photos = [...(profile.photos ?? []), ProfilesService.PHOTO_PREFIX + stored.storageKey];
    profile.profileComplete = this.isComplete(profile);
    return toProfileView(await this.profiles.save(profile));
  }

  async removePhoto(userId: string, index: number): Promise<ProfileView> {
    const profile = await this.ensureProfile(userId);
    const photos = [...(profile.photos ?? [])];
    if (!Number.isInteger(index) || index < 0 || index >= photos.length) {
      throw new NotFoundException('Photo not found');
    }
    const [removed] = photos.splice(index, 1);
    profile.photos = photos;
    profile.profileComplete = this.isComplete(profile);
    const saved = await this.profiles.save(profile);
    if (removed.startsWith(ProfilesService.PHOTO_PREFIX)) {
      await this.media?.remove(removed.slice(ProfilesService.PHOTO_PREFIX.length)).catch(() => undefined);
    }
    return toProfileView(saved);
  }

  async setCover(userId: string, image: string | null): Promise<ProfileView> {
    const profile = await this.ensureProfile(userId);
    const previous = profile.coverPhoto;
    if (image === null) {
      profile.coverPhoto = null;
    } else {
      if (!this.media) throw new BadRequestException('Photo storage unavailable');
      profile.coverPhoto = ProfilesService.PHOTO_PREFIX + (await this.media.saveDataUrl(image)).storageKey;
    }
    const saved = await this.profiles.save(profile);
    if (previous?.startsWith(ProfilesService.PHOTO_PREFIX)) {
      await this.media?.remove(previous.slice(ProfilesService.PHOTO_PREFIX.length)).catch(() => undefined);
    }
    return toProfileView(saved);
  }

  /** Only files that are currently someone's profile photo are served without a signature. */
  async isPublicPhoto(file: string): Promise<boolean> {
    const path = ProfilesService.PHOTO_PREFIX + file;
    return (await this.profiles
      .createQueryBuilder('p')
      .where(':path = ANY(p.photos) OR p.cover_photo = :path', { path })
      .getCount()) > 0;
  }

  /** userId → % hợp gu + các câu người đó đã trả lời ; rỗng khi không có repo câu trả lời (unit test). */
  private async questionMatches(viewerId: string, userIds: string[]) {
    const result = new Map<string, QuestionInfo>();
    if (!this.answers || !userIds.length) return result;
    const rows = await this.answers.find({ where: { userId: In([viewerId, ...userIds]) } });
    const by = new Map<string, QuestionAnswer[]>();
    for (const row of rows) by.set(row.userId, [...(by.get(row.userId) ?? []), row]);
    const mine = by.get(viewerId) ?? [];
    for (const id of userIds) {
      const theirs = by.get(id) ?? [];
      result.set(id, {
        match: questionMatch(mine, theirs),
        answers: theirs.flatMap((a) => {
          const q = QUESTION_BY_ID.get(a.questionId);
          return q && q.options[a.answer] ? [{ question: q.text, answer: q.options[a.answer] }] : [];
        }),
      });
    }
    return result;
  }

  async own(userId: string): Promise<ProfileView> {
    const profile = await this.ensureProfile(userId);
    return toProfileView(profile);
  }

  async updateOwn(userId: string, dto: UpdateProfileDto): Promise<ProfileView> {
    const profile = await this.ensureProfile(userId);
    const allowed: Array<keyof UpdateProfileDto> = [
      'nickname',
      'fullName',
      'age',
      'gender',
      'school',
      'campus',
      'faculty',
      'major',
      'academicYear',
      'alumni',
      'gpa',
      'gpaTier',
      'bio',
      'interests',
      'avatarSeed',
      'mode',
      'showGpaPublic',
      'showLocationPublic',
    ];
    for (const key of allowed) {
      if (dto[key] !== undefined) {
        (profile as unknown as Record<string, unknown>)[key] = dto[key];
      }
    }
    if (dto.interests) {
      profile.interests = Array.from(
        new Set(dto.interests.map((item) => item.trim()).filter(Boolean)),
      );
    }
    if (dto.gpa !== undefined && dto.gpaTier === undefined) {
      profile.gpaTier = this.tierForGpa(dto.gpa);
    }
    if (dto.lat !== undefined || dto.lng !== undefined) {
      profile.lat = dto.lat ?? profile.lat;
      profile.lng = dto.lng ?? profile.lng;
      if (profile.lat || profile.lng) {
        profile.location = {
          type: 'Point',
          coordinates: [profile.lng, profile.lat],
        };
      }
    }
    profile.profileComplete = this.isComplete(profile);
    const saved = await this.profiles.save(profile);
    return toProfileView(saved);
  }

  async discover(userId: string, query: DiscoverQueryDto) {
    const viewer = await this.ensureProfile(userId);
    const excluded = await this.excludedUserIds(userId);
    const limit = query.limit ?? 20;
    const offset = query.offset ?? 0;
    const modes = this.csv(query.modes).map((item) => item.toUpperCase());
    const gpaTiers = this.csv(query.gpaTiers).map((item) => item.toUpperCase());
    const q = (query.q ?? '').trim().toLocaleLowerCase('vi');

    const candidates = (await this.profiles.find()).filter((candidate) => {
      if (candidate.userId === userId || excluded.has(candidate.userId)) return false;
      if (!candidate.profileComplete) return false;
      if (modes.length && !modes.includes(candidate.mode)) return false;
      if (gpaTiers.length && !gpaTiers.includes(candidate.gpaTier)) return false;
      if (query.school && candidate.school !== query.school) return false;
      if (query.campus && candidate.campus !== query.campus) return false;
      if (query.faculty && candidate.faculty !== query.faculty) return false;
      if (candidate.age < (query.minAge ?? 18)) return false;
      if (candidate.age > (query.maxAge ?? 30)) return false;
      if ((query.onlyVerified ?? true) && !candidate.verified) return false;
      if (query.graduation === 'ALUMNI' && !candidate.alumni) return false;
      if (query.graduation === 'STUDYING' && candidate.alumni) return false;
      if (q) {
        const haystack = [
          candidate.nickname,
          candidate.fullName,
          candidate.school,
          candidate.campus,
          candidate.faculty,
          candidate.major,
          candidate.bio,
          ...(candidate.interests ?? []),
        ]
          .join(' ')
          .toLocaleLowerCase('vi');
        if (!haystack.includes(q)) return false;
      }
      return true;
    });

    let scored = candidates.map((candidate) => {
      const norm = (s: string) => s.trim().toLowerCase().replace(/ß/g, 'ss').replace(/ς/g, 'σ').replace(/\s+/g, ' ');
      const own = new Set((viewer.interests ?? []).map(norm).filter(Boolean));
      const shared = new Set((candidate.interests ?? []).map(norm).filter((s) => s && own.has(s))).size;
      const compatibility = Math.min(100, Math.round(100 * shared / Math.max(1, own.size)));
      const distance = distanceKm(viewer.lat, viewer.lng, candidate.lat, candidate.lng);
      return { candidate, compatibility, distance, shared };
    });
    const hasViewerLocation = !!viewer.lat && !!viewer.lng;
    const hasLocation = (p: typeof viewer) => p.showLocationPublic && !!p.lat && !!p.lng;
    if (query.maxDistanceKm !== undefined && query.maxDistanceKm < 50) {
      scored = scored.filter((item) => hasViewerLocation && hasLocation(item.candidate) && item.distance <= query.maxDistanceKm!);
    }

    const sort = query.sort ?? 'compatibility';
    scored.sort((a, b) => {
      if (sort === 'distance') return a.distance - b.distance;
      if (sort === 'newest') {
        return b.candidate.createdAt.getTime() - a.candidate.createdAt.getTime();
      }
      const normal = (s: string) => s.trim().toLowerCase().replace(/ß/g, 'ss').replace(/ς/g, 'σ').replace(/\s+/g, ' ');
      const own = new Set((viewer.interests ?? []).map(normal).filter(Boolean));
      const shared = (p: typeof viewer) => new Set((p.interests ?? []).map(normal).filter((s) => s && own.has(s))).size;
      const tiers = ['EXCELLENT', 'GOOD', 'FAIR', 'AVERAGE'];
      const rank = (p: typeof viewer) => [shared(p), Number(p.mode === viewer.mode),
        viewer.campus && p.campus.toLowerCase() === viewer.campus.toLowerCase() ? 2 : viewer.school && p.school.toLowerCase() === viewer.school.toLowerCase() ? 1 : 0,
        Number(!!viewer.faculty && p.faculty.toLowerCase() === viewer.faculty.toLowerCase()),
        -Math.abs(tiers.indexOf(p.gpaTier) - tiers.indexOf(viewer.gpaTier)),
        hasViewerLocation && hasLocation(p) ? -distanceKm(viewer.lat, viewer.lng, p.lat, p.lng) : -Infinity,
        -Math.abs(viewer.age - p.age)];
      const ar = rank(a.candidate), br = rank(b.candidate);
      for (let i = 0; i < ar.length; i++) if (ar[i] !== br[i]) return br[i] - ar[i];
      return a.candidate.id < b.candidate.id ? -1 : a.candidate.id > b.candidate.id ? 1 : 0;
    });

    const page = scored.slice(offset, offset + limit);
    const qm = await this.questionMatches(userId, page.map((item) => item.candidate.userId));
    return {
      total: scored.length,
      items: page
        .map((item) => ({
          ...toProfileView(item.candidate, viewer, item.compatibility),
          ...this.questionFields(qm, item.candidate.userId),
          // Only expose public, truthful factors of the default ranking; never infer
          // hidden GPA/location from the tie breakers.
          rankingReasons: sort === 'compatibility' ? [
            item.shared ? `${item.shared} sở thích chung` : 'Chưa có sở thích chung',
            ...(item.candidate.mode === viewer.mode ? ['Cùng chế độ kết nối'] : []),
          ] : [],
        })),
      filters: {
        modes,
        gpaTiers,
        minAge: query.minAge ?? 18,
        maxAge: query.maxAge ?? 80,
      },
    };
  }

  async incomingLikes(userId: string) {
    const viewer = await this.ensureProfile(userId);
    const mine = await this.swipes.find({ where: { fromUserId: userId } });
    const alreadySwiped = new Set(mine.map((item) => item.targetUserId));
    const blockedPairs = await this.blocks.find({
      where: [{ blockerId: userId }, { blockedId: userId }],
    });
    const blocked = new Set(blockedPairs.map((item) => item.blockerId === userId ? item.blockedId : item.blockerId));
    const incoming = (
      await this.swipes.find({ where: { targetUserId: userId } })
    ).filter(
      (item) =>
        (item.action === 'LIKE' || item.action === 'SUPER_LIKE') &&
        // Likes sent through anonymous Campus discovery must not reveal the
        // sender here; they only surface as an anonymous match.
        !item.anonymous &&
        !blocked.has(item.fromUserId) &&
        !alreadySwiped.has(item.fromUserId),
    );
    if (!incoming.length) return { total: 0, items: [] };
    const profiles = await this.profiles.find();
    const byUser = new Map(profiles.map((profile) => [profile.userId, profile]));
    return {
      total: incoming.filter((swipe) => byUser.has(swipe.fromUserId)).length,
      items: incoming.flatMap((swipe) => {
        const profile = byUser.get(swipe.fromUserId);
        if (!profile) return [];
        return [
          {
            ...toProfileView(
              profile,
              viewer,
              compatibilityScore(viewer, profile),
            ),
            likedAt: swipe.createdAt,
            superLike: swipe.action === 'SUPER_LIKE',
          },
        ];
      }),
    };
  }

  async nearby(userId: string, query: NearbyQueryDto) {
    const viewer = await this.ensureProfile(userId);
    const lat = query.lat ?? viewer.lat;
    const lng = query.lng ?? viewer.lng;
    if (!lat || !lng) {
      throw new BadRequestException('Location is required to find nearby profiles');
    }
    const radiusKm = query.radiusKm ?? 15;
    const excluded = await this.excludedUserIds(userId);
    const items = (await this.profiles.find())
      .filter(
        (profile) =>
          profile.userId !== userId &&
          profile.profileComplete &&
          !excluded.has(profile.userId),
      )
      .map((profile) => ({
        profile,
        distance: distanceKm(lat, lng, profile.lat, profile.lng),
      }))
      .filter((item) => item.distance <= radiusKm)
      .sort((a, b) => a.distance - b.distance)
      .map(({ profile, distance }) => ({
        ...toProfileView(profile, viewer, compatibilityScore(viewer, profile)),
        distanceKm: distance,
        lat: profile.showLocationPublic ? profile.lat : undefined,
        lng: profile.showLocationPublic ? profile.lng : undefined,
      }));
    return { center: { lat, lng }, radiusKm, total: items.length, items };
  }

  async detail(userId: string, profileId: string) {
    const viewer = await this.ensureProfile(userId);
    // Masked anonymous-match ids (`anon:<matchId>`) never resolve to a profile.
    if (!isUuid(profileId)) throw new NotFoundException('Profile not found');
    const profile = await this.profiles.findOne({
      where: [{ id: profileId }, { userId: profileId }],
    });
    if (!profile) throw new NotFoundException('Profile not found');
    const excluded = await this.excludedUserIds(userId);
    if (
      profile.userId !== userId &&
      excluded.has(profile.userId)
    ) {
      throw new NotFoundException('Profile not found');
    }
    const view = toProfileView(
      profile,
      viewer,
      profile.userId === userId ? undefined : compatibilityScore(viewer, profile),
    );
    const qm = profile.userId === userId ? new Map<string, QuestionInfo>() : await this.questionMatches(userId, [profile.userId]);
    return {
      ...view,
      ...this.questionFields(qm, profile.userId),
      lat: profile.showLocationPublic ? profile.lat : undefined,
      lng: profile.showLocationPublic ? profile.lng : undefined,
    };
  }

  async ensureProfile(userId: string): Promise<Profile> {
    const existing = await this.profiles.findOne({ where: { userId } });
    if (existing) return existing;
    return this.profiles.save(
      this.profiles.create({
        userId,
        nickname: '',
        fullName: '',
        interests: [],
        avatarSeed: 1,
        profileComplete: false,
      }),
    );
  }

  private async excludedUserIds(userId: string): Promise<Set<string>> {
    const [swipes, blocks] = await Promise.all([
      this.swipes.find({ where: { fromUserId: userId } }),
      this.blocks.find({
        where: [{ blockerId: userId }, { blockedId: userId }],
      }),
    ]);
    // Anonymous Campus swipes are not excluded: otherwise the author would
    // vanish from ordinary Discover right after the swipe, letting the viewer
    // identify them by diffing the list before and after.
    const result = new Set(
      swipes.filter((item) => !item.anonymous).map((item) => item.targetUserId),
    );
    for (const block of blocks) {
      result.add(block.blockerId === userId ? block.blockedId : block.blockerId);
    }
    return result;
  }

  private questionFields(qm: Map<string, QuestionInfo>, userId: string) {
    const info = qm.get(userId);
    if (!info) return {};
    return { questionMatch: info.match?.percent ?? null, commonQuestions: info.match?.common ?? 0, answers: info.answers };
  }

  private isComplete(profile: Profile): boolean {
    const interests = (profile.interests ?? []).filter(
      (item) => normaliseInterest(item).length > 0,
    );
    return Boolean(
      profile.nickname?.trim() &&
        profile.fullName?.trim() &&
        profile.age >= 18 &&
        profile.school?.trim() &&
        profile.faculty?.trim() &&
        profile.mode &&
        interests.length >= 3 &&
        (profile.photos ?? []).length >= ProfilesService.MIN_PHOTOS,
    );
  }

  private tierForGpa(gpa: number): string {
    if (gpa >= 3.6) return 'EXCELLENT';
    if (gpa >= 3.2) return 'GOOD';
    if (gpa >= 2.5) return 'FAIR';
    return 'AVERAGE';
  }

  private csv(value?: string): string[] {
    if (!value) return [];
    return value
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  }
}
