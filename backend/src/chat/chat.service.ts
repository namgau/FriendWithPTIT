import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { isDeepStrictEqual } from 'node:util';
import {
  Match,
  Message,
  Notification,
  Profile,
} from '../database/entities';
import { isIdentityRevealed, maskedIdentityId } from '../common/anonymity';
import { SendMessageDto } from './chat.dto';
import { decodeChatCursor, encodeChatCursor } from './chat.cursor';

@Injectable()
export class ChatService {
  constructor(
    @InjectRepository(Message) private readonly messages: Repository<Message>,
    @InjectRepository(Match) private readonly matches: Repository<Match>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
  ) {}

  async list(userId: string, matchId: string, before?: string, limit = 50) {
    if (!Number.isInteger(limit) || limit < 1 || limit > 100) throw new BadRequestException('Message limit must be between 1 and 100');
    const cursor = before !== undefined ? decodeChatCursor(before, matchId) : null;
    const match = await this.loadMatch(userId, matchId);
    const masked = await this.isMasked(match);
    const query = this.messages.createQueryBuilder('message')
      .where('message.matchId = :matchId', { matchId: match.id })
      // Preserve PostgreSQL microseconds: JS Date alone loses cursor precision.
      .addSelect(`to_char(message.createdAt, 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')`, 'cursor_time')
      .orderBy('message.createdAt', 'DESC').addOrderBy('message.id', 'DESC').take(limit + 1);
    if (cursor) {
      query.andWhere(cursor.id
        ? '(message.createdAt, message.id) < (CAST(:time AS timestamp), CAST(:id AS uuid))'
        : 'message.createdAt < CAST(:time AS timestamp)', { time: cursor.time, id: cursor.id });
    }
    const { entities: descending, raw } = await query.getRawAndEntities();
    const hasMore = descending.length > limit;
    const page = descending.slice(0, limit).reverse();
    const nextBefore = hasMore && page.length
      ? encodeChatCursor(match.id, raw[limit - 1].cursor_time, page[0].id) : null;
    return {
      total: await this.messages.count({ where: { matchId: match.id } }),
      hasMore,
      nextBefore,
      items: page.map((message) => this.view(message, userId, masked)),
    };
  }

  async send(userId: string, matchId: string, dto: SendMessageDto) {
    // Serialize sends in a conversation and commit message + notification together.
    // The unique index is also enforced independently by PostgreSQL.
    return this.messages.manager.transaction(async (manager) => {
      const scoped = new ChatService(manager.getRepository(Message), manager.getRepository(Match),
        manager.getRepository(Profile), manager.getRepository(Notification));
      return scoped.sendInTransaction(userId, matchId, dto);
    });
  }

  private async sendInTransaction(userId: string, matchId: string, dto: SendMessageDto) {
    const match = await this.loadMatch(userId, matchId, true);
    const text = dto.text.trim();
    if (!text) {
      throw new BadRequestException('Message text is required');
    }
    const type = dto.type ?? 'TEXT';
    const meta = dto.meta ?? null;
    const clientMessageId = dto.clientMessageId?.toLowerCase() ?? null;
    if (clientMessageId) {
      const existing = await this.messages.findOne({ where: { matchId, senderId: userId, clientMessageId } });
      if (existing) {
        if (existing.text !== text || existing.type !== type || !isDeepStrictEqual(existing.meta, meta)) {
          throw new ConflictException('Message identifier already used with a different payload');
        }
        return this.view(existing, userId, await this.isMasked(match));
      }
    }
    const message = await this.messages.save(
      this.messages.create({
        matchId: match.id,
        senderId: userId,
        clientMessageId,
        text,
        type,
        meta,
      }),
    );
    match.lastMessageAt = message.createdAt;
    await this.matches.save(match);

    const otherUserId = match.userAId === userId ? match.userBId : match.userAId;
    const [sender, other] = await Promise.all([
      this.profiles.findOne({ where: { userId } }),
      this.profiles.findOne({ where: { userId: otherUserId } }),
    ]);
    const masked = await this.isMasked(match);
    if (other) {
      await this.notifications.save(
        this.notifications.create({
          userId: otherUserId,
          kind: 'MESSAGE',
          title: masked
            ? 'Tin nhắn từ người ẩn danh'
            : sender?.nickname || sender?.fullName || 'Tin nhắn mới',
          body: text.slice(0, 160),
          data: masked
            ? { matchId: match.id }
            : { matchId: match.id, profileId: sender?.id ?? null },
        }),
      );
    }
    return this.view(message, userId, masked);
  }

  /** Anonymous Campus matches stay masked until the reveal conditions are met. */
  private async isMasked(match: Match): Promise<boolean> {
    if (!match.anonymous) return false;
    const count = await this.messages.count({ where: { matchId: match.id } });
    return !isIdentityRevealed(match, count);
  }

  private async loadMatch(userId: string, matchId: string, lock = false): Promise<Match> {
    const match = await this.matches.findOne({ where: { id: matchId }, ...(lock ? { lock: { mode: 'pessimistic_write' as const } } : {}) });
    if (!match) throw new NotFoundException('Match not found');
    if (match.userAId !== userId && match.userBId !== userId) {
      throw new NotFoundException('Match not found');
    }
    if (match.unmatchedAt) throw new NotFoundException('Match is no longer active');
    return match;
  }

  private view(message: Message, viewerId: string, masked = false) {
    return {
      id: message.id,
      matchId: message.matchId,
      senderId:
        masked && message.senderId !== viewerId
          ? maskedIdentityId(message.matchId)
          : message.senderId,
      text: message.text,
      type: message.type,
      meta: message.meta,
      createdAt: message.createdAt,
      isMine: message.senderId === viewerId,
      ...(message.senderId === viewerId && message.clientMessageId ? { clientMessageId: message.clientMessageId } : {}),
    };
  }
}
