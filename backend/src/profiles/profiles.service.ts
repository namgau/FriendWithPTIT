import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Block, Profile, Swipe } from '../database/entities';
import {
  compatibilityScore,
  distanceKm,
  normaliseInterest,
} from '../common/matching';
import { ProfileView, toProfileView } from '../common/profile.view';
import {
  DiscoverQueryDto,
  NearbyQueryDto,
  UpdateProfileDto,
} from './profile.dto';

@Injectable()
export class ProfilesService {
  constructor(
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(Swipe) private readonly swipes: Repository<Swipe>,
    @InjectRepository(Block) private readonly blocks: Repository<Block>,
  ) {}

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
      if (query.minAge && candidate.age < query.minAge) return false;
      if (query.maxAge && candidate.age > query.maxAge) return false;
      if (query.onlyVerified && !candidate.verified) return false;
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
      const compatibility = compatibilityScore(viewer, candidate);
      const distance = distanceKm(viewer.lat, viewer.lng, candidate.lat, candidate.lng);
      return { candidate, compatibility, distance };
    });
    if (query.maxDistanceKm !== undefined) {
      scored = scored.filter((item) => item.distance <= query.maxDistanceKm!);
    }

    const sort = query.sort ?? 'compatibility';
    scored.sort((a, b) => {
      if (sort === 'distance') return a.distance - b.distance;
      if (sort === 'newest') {
        return b.candidate.createdAt.getTime() - a.candidate.createdAt.getTime();
      }
      return b.compatibility - a.compatibility;
    });

    return {
      total: scored.length,
      items: scored
        .slice(offset, offset + limit)
        .map((item) =>
          toProfileView(item.candidate, viewer, item.compatibility),
        ),
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
    const incoming = (
      await this.swipes.find({ where: { targetUserId: userId } })
    ).filter(
      (item) =>
        (item.action === 'LIKE' || item.action === 'SUPER_LIKE') &&
        !alreadySwiped.has(item.fromUserId),
    );
    if (!incoming.length) return { total: 0, items: [] };
    const profiles = await this.profiles.find();
    const byUser = new Map(profiles.map((profile) => [profile.userId, profile]));
    return {
      total: incoming.length,
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
    return {
      ...view,
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
    const result = new Set(swipes.map((item) => item.targetUserId));
    for (const block of blocks) {
      result.add(block.blockerId === userId ? block.blockedId : block.blockerId);
    }
    return result;
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
        interests.length >= 3,
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
