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
import {
  MASKED_ID_PREFIX,
  isIdentityRevealed,
  isUuid,
  maskedIdentityId,
} from '../common/anonymity';
import { compatibilityScore } from '../common/matching';
import { toAnonymousProfileView, toProfileView } from '../common/profile.view';
import { ReportDto, RevealDto, SwipeDto } from './social.dto';

export interface SwipeOptions {
  anonymous?: boolean;
}

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

  async swipe(userId: string, dto: SwipeDto, options: SwipeOptions = {}) {
    if (userId === dto.targetUserId) {
      throw new BadRequestException('You cannot swipe yourself');
    }
    const [viewer, target] = await Promise.all([
      this.profiles.findOne({ where: { userId } }),
      this.profiles.findOne({ where: { userId: dto.targetUserId } }),
    ]);
    if (!viewer) throw new NotFoundException('Your profile does not exist');
    if (!target) throw new NotFoundException('Target profile not found');
    if (await this.isBlockedPair(userId, dto.targetUserId)) {
      // Same response as a missing profile so a block is not observable.
      throw new NotFoundException('Target profile not found');
    }

    const viaCampus = options.anonymous === true;
    const existingSwipe = await this.swipes.findOne({
      where: { fromUserId: userId, targetUserId: dto.targetUserId },
    });
    if (existingSwipe) {
      // Duplicate swipes are idempotent. The anonymous origin is sticky: a
      // later ordinary swipe must not downgrade an anonymous one.
      const anonymous = existingSwipe.anonymous === true || viaCampus;
      if (
        existingSwipe.action !== dto.action ||
        existingSwipe.anonymous !== anonymous
      ) {
        existingSwipe.action = dto.action;
        existingSwipe.anonymous = anonymous;
        await this.swipes.save(existingSwipe);
      }
    } else {
      try {
        await this.swipes.save(
          this.swipes.create({
            fromUserId: userId,
            targetUserId: dto.targetUserId,
            action: dto.action,
            anonymous: viaCampus,
          }),
        );
      } catch (error) {
        // Concurrent duplicate: the unique (from, target) index rejected the
        // insert, so fall back to updating the row that won the race.
        const raced = await this.swipes.findOne({
          where: { fromUserId: userId, targetUserId: dto.targetUserId },
        });
        if (!raced) throw error;
        raced.action = dto.action;
        raced.anonymous = raced.anonymous === true || viaCampus;
        await this.swipes.save(raced);
      }
    }

    if (dto.action === 'PASS') {
      return { matched: false, match: null };
    }

    const reciprocal =
      (await this.swipes.findOne({
        where: {
          fromUserId: dto.targetUserId,
          targetUserId: userId,
          action: 'LIKE',
        },
      })) ??
      (await this.swipes.findOne({
        where: {
          fromUserId: dto.targetUserId,
          targetUserId: userId,
          action: 'SUPER_LIKE',
        },
      }));
    if (!reciprocal) {
      return { matched: false, match: null };
    }

    // The match is anonymous if EITHER side's swipe came from Campus Feed.
    // This covers the reverse order: the author liking back through ordinary
    // discovery must not turn an anonymous like into an identified match.
    const matchAnonymous = viaCampus || reciprocal.anonymous === true;

    const [userAId, userBId] = [userId, dto.targetUserId].sort();
    let match = await this.matches.findOne({ where: { userAId, userBId } });
    if (match?.unmatchedAt) {
      match.unmatchedAt = null;
      if (match.anonymous) {
        match.revealOptInA = false;
        match.revealOptInB = false;
      }
      await this.matches.save(match);
    }
    if (!match) {
      try {
        match = await this.matches.save(
          this.matches.create({
            userAId,
            userBId,
            compatibilityScore: compatibilityScore(viewer, target),
            anonymous: matchAnonymous,
          }),
        );
      } catch (error) {
        // Concurrent mutual likes: the unique pair index makes the loser retry.
        match = await this.matches.findOne({ where: { userAId, userBId } });
        if (!match) throw error;
        return {
          matched: true,
          match: await this.summary(
            match,
            target.userId === userId ? viewer : target,
            userId,
          ),
        };
      }
      const anonymous = match.anonymous === true;
      const title = anonymous ? 'Bạn có match ẩn danh' : 'Bạn có match mới';
      const data = (profileId: string) => ({
        matchId: match!.id,
        ...(anonymous ? {} : { profileId }),
      });
      await Promise.all([
        this.notify(
          userId,
          'MATCH',
          title,
          anonymous
            ? 'Một người ẩn danh từ Campus Feed đã match với bạn.'
            : `Bạn và ${target.nickname || target.fullName} đã match.`,
          data(target.id),
        ),
        this.notify(
          target.userId,
          'MATCH',
          title,
          anonymous
            ? 'Một người ẩn danh từ Campus Feed đã match với bạn.'
            : `Bạn và ${viewer.nickname || viewer.fullName} đã match.`,
          data(viewer.id),
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

  async block(userId: string, requestedId: string) {
    // Counterparts of masked anonymous matches are addressed by `anon:<matchId>`
    // so a user can block them without ever learning the real user id.
    let targetUserId = requestedId;
    if (requestedId.startsWith(MASKED_ID_PREFIX)) {
      const matchId = requestedId.slice(MASKED_ID_PREFIX.length);
      const match = isUuid(matchId)
        ? await this.matches.findOne({ where: { id: matchId } })
        : null;
      if (
        !match ||
        !match.anonymous ||
        (match.userAId !== userId && match.userBId !== userId)
      ) {
        throw new NotFoundException('Profile not found');
      }
      targetUserId = match.userAId === userId ? match.userBId : match.userAId;
    }
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
    // Echo the id the caller used, never the resolved real user id.
    return { blocked: true, userId: requestedId };
  }

  private async isBlockedPair(a: string, b: string): Promise<boolean> {
    const row = await this.blocks.findOne({
      where: [
        { blockerId: a, blockedId: b },
        { blockerId: b, blockedId: a },
      ],
    });
    return Boolean(row);
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
    // Same thresholds as before; for anonymous matches this is also the exact
    // condition under which the counterpart's identity is unmasked.
    const revealEligible = isIdentityRevealed(
      { ...match, anonymous: true },
      messageCount,
    );
    const masked = match.anonymous === true && !revealEligible;
    return {
      id: match.id,
      anonymous: masked,
      other: masked ? toAnonymousProfileView(match.id) : toProfileView(other),
      // Score is derived from both real profiles; hide it while masked so it
      // cannot be used to probe the counterpart's hidden fields.
      compatibilityScore: masked ? 0 : match.compatibilityScore,
      healthLevel: match.healthLevel,
      revealOptInMe,
      revealOptInThem,
      revealEligible,
      messageCount,
      lastMessage: lastMessage
        ? {
            id: lastMessage.id,
            text: lastMessage.text,
            senderId:
              match.anonymous && lastMessage.senderId !== viewerId
                ? maskedIdentityId(match.id)
                : lastMessage.senderId,
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
