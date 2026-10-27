import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import {
  Block,
  Match,
  Message,
  Notification,
  Profile,
  Report,
  Swipe,
} from '../database/entities';
import { compatibilityScore } from '../common/matching';
import { toProfileView } from '../common/profile.view';
import { ReportDto, RevealDto, SwipeDto } from './social.dto';

const MATCH_MIN_MESSAGES = 8;
const MATCH_MIN_SCORE = 72;

@Injectable()
export class SocialService {
  constructor(
    @InjectRepository(Swipe) private readonly swipes: Repository<Swipe>,
    @InjectRepository(Match) private readonly matches: Repository<Match>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(Message) private readonly messages: Repository<Message>,
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
    @InjectRepository(Block) private readonly blocks: Repository<Block>,
    @InjectRepository(Report) private readonly reports: Repository<Report>,
  ) {}

  async swipe(userId: string, dto: SwipeDto) {
    if (userId === dto.targetUserId) {
      throw new BadRequestException('You cannot swipe yourself');
    }
    const [viewer, target] = await Promise.all([
      this.profiles.findOne({ where: { userId } }),
      this.profiles.findOne({ where: { userId: dto.targetUserId } }),
    ]);
    if (!viewer) throw new NotFoundException('Your profile does not exist');
    if (!target) throw new NotFoundException('Target profile not found');

    const existingSwipe = await this.swipes.findOne({
      where: { fromUserId: userId, targetUserId: dto.targetUserId },
    });
    if (existingSwipe) {
      existingSwipe.action = dto.action;
      await this.swipes.save(existingSwipe);
    } else {
      await this.swipes.save(
        this.swipes.create({
          fromUserId: userId,
          targetUserId: dto.targetUserId,
          action: dto.action,
        }),
      );
    }

    if (dto.action === 'PASS') {
      return { matched: false, match: null };
    }

    const reciprocal = await this.swipes.findOne({
      where: {
        fromUserId: dto.targetUserId,
        targetUserId: userId,
        action: 'LIKE',
      },
    });
    const reciprocalSuper = await this.swipes.findOne({
      where: {
        fromUserId: dto.targetUserId,
        targetUserId: userId,
        action: 'SUPER_LIKE',
      },
    });
    if (!reciprocal && !reciprocalSuper) {
      return { matched: false, match: null };
    }

    const [userAId, userBId] = [userId, dto.targetUserId].sort();
    let match = await this.matches.findOne({ where: { userAId, userBId } });
    if (match?.unmatchedAt) {
      match.unmatchedAt = null;
      await this.matches.save(match);
    }
    if (!match) {
      match = await this.matches.save(
        this.matches.create({
          userAId,
          userBId,
          compatibilityScore: compatibilityScore(viewer, target),
        }),
      );
      await Promise.all([
        this.notify(
          userId,
          'MATCH',
          'Bạn có match mới',
          `Bạn và ${target.nickname || target.fullName} đã match.`,
          { matchId: match.id, profileId: target.id },
        ),
        this.notify(
          target.userId,
          'MATCH',
          'Bạn có match mới',
          `Bạn và ${viewer.nickname || viewer.fullName} đã match.`,
          { matchId: match.id, profileId: viewer.id },
        ),
      ]);
    }
    const otherProfile = target.userId === userId ? viewer : target;
    return {
      matched: true,
      match: await this.summary(match, otherProfile, userId),
    };
  }

  async list(userId: string) {
    const matches = await this.matches.find({
      where: [
        { userAId: userId, unmatchedAt: IsNull() },
        { userBId: userId, unmatchedAt: IsNull() },
      ],
    });
    const profiles = await this.profiles.find();
    const byUser = new Map(profiles.map((profile) => [profile.userId, profile]));
    const items = await Promise.all(
      matches.map((match) => {
        const otherUserId = match.userAId === userId ? match.userBId : match.userAId;
        const other = byUser.get(otherUserId);
        if (!other) return null;
        return this.summary(match, other, userId);
      }),
    );
    return {
      total: items.filter(Boolean).length,
      items: items
        .filter((item): item is NonNullable<typeof item> => item !== null)
        .sort((a, b) => {
          const aTime = a.lastMessageAt?.getTime() ?? a.createdAt.getTime();
          const bTime = b.lastMessageAt?.getTime() ?? b.createdAt.getTime();
          return bTime - aTime;
        }),
    };
  }

  async detail(userId: string, matchId: string) {
    const { match } = await this.loadMatch(matchId, userId);
    const otherUserId = match.userAId === userId ? match.userBId : match.userAId;
    const other = await this.profiles.findOne({ where: { userId: otherUserId } });
    if (!other) throw new NotFoundException('Profile not found');
    return this.summary(match, other, userId);
  }

  async reveal(userId: string, matchId: string, dto: RevealDto) {
    const { match } = await this.loadMatch(matchId, userId);
    if (match.userAId === userId) {
      match.revealOptInA = dto.enabled;
    } else {
      match.revealOptInB = dto.enabled;
    }
    const saved = await this.matches.save(match);
    const otherUserId = saved.userAId === userId ? saved.userBId : saved.userAId;
    const other = await this.profiles.findOne({ where: { userId: otherUserId } });
    if (!other) throw new NotFoundException('Profile not found');
    return this.summary(saved, other, userId);
  }

  async unmatch(userId: string, matchId: string) {
    const { match } = await this.loadMatch(matchId, userId);
    match.unmatchedAt = new Date();
    await this.matches.save(match);
    return { unmatched: true, matchId };
  }

  async report(userId: string, matchId: string, dto: ReportDto) {
    const { match } = await this.loadMatch(matchId, userId);
    const targetUserId = match.userAId === userId ? match.userBId : match.userAId;
    await this.reports.save(
      this.reports.create({
        reporterId: userId,
        targetUserId,
        matchId,
        reason: dto.reason,
        details: dto.details ?? '',
      }),
    );
    match.unmatchedAt = new Date();
    await this.matches.save(match);
    return { reported: true, matchId };
  }

  async block(userId: string, targetUserId: string) {
    if (userId === targetUserId) {
      throw new BadRequestException('You cannot block yourself');
    }
    const target = await this.profiles.findOne({ where: { userId: targetUserId } });
    if (!target) throw new NotFoundException('Profile not found');
    const existing = await this.blocks.findOne({
      where: { blockerId: userId, blockedId: targetUserId },
    });
    if (!existing) {
      await this.blocks.save(
        this.blocks.create({ blockerId: userId, blockedId: targetUserId }),
      );
    }
    const [userAId, userBId] = [userId, targetUserId].sort();
    const match = await this.matches.findOne({ where: { userAId, userBId } });
    if (match && !match.unmatchedAt) {
      match.unmatchedAt = new Date();
      await this.matches.save(match);
    }
    return { blocked: true, userId: targetUserId };
  }

  async summary(match: Match, other: Profile, viewerId: string) {
    const isA = match.userAId === viewerId;
    const lastMessage = await this.messages.findOne({
      where: { matchId: match.id },
      order: { createdAt: 'DESC' },
    });
    const messageCount = await this.messages.count({
      where: { matchId: match.id },
    });
    const revealOptInMe = isA ? match.revealOptInA : match.revealOptInB;
    const revealOptInThem = isA ? match.revealOptInB : match.revealOptInA;
    const revealEligible =
      revealOptInMe &&
      revealOptInThem &&
      messageCount >= MATCH_MIN_MESSAGES &&
      match.compatibilityScore >= MATCH_MIN_SCORE;
    return {
      id: match.id,
      other: toProfileView(other),
      compatibilityScore: match.compatibilityScore,
      healthLevel: match.healthLevel,
      revealOptInMe,
      revealOptInThem,
      revealEligible,
      messageCount,
      lastMessage: lastMessage
        ? {
            id: lastMessage.id,
            text: lastMessage.text,
            senderId: lastMessage.senderId,
            type: lastMessage.type,
            createdAt: lastMessage.createdAt,
          }
        : null,
      lastMessageAt: match.lastMessageAt,
      createdAt: match.createdAt,
    };
  }

  private async loadMatch(matchId: string, userId: string): Promise<{ match: Match }> {
    const match = await this.matches.findOne({ where: { id: matchId } });
    if (!match) throw new NotFoundException('Match not found');
    if (match.userAId !== userId && match.userBId !== userId) {
      throw new NotFoundException('Match not found');
    }
    if (match.unmatchedAt) throw new NotFoundException('Match is no longer active');
    return { match };
  }

  private notify(
    userId: string,
    kind: string,
    title: string,
    body: string,
    data: Record<string, unknown>,
  ) {
    return this.notifications.save(
      this.notifications.create({ userId, kind, title, body, data }),
    );
  }
}
