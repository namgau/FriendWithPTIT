import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { LessThan, Repository } from 'typeorm';
import {
  Match,
  Message,
  Notification,
  Profile,
} from '../database/entities';
import { SendMessageDto } from './chat.dto';

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
    const match = await this.loadMatch(userId, matchId);
    const capped = Math.min(Math.max(limit, 1), 100);
    const beforeDate = before ? new Date(before) : null;
    const where = beforeDate
      ? { matchId: match.id, createdAt: LessThan(beforeDate) }
      : { matchId: match.id };
    const descending = await this.messages.find({
      where,
      order: { createdAt: 'DESC' },
      take: capped + 1,
    });
    const hasMore = descending.length > capped;
    const page = descending.slice(0, capped).reverse();
    return {
      total: await this.messages.count({ where: { matchId: match.id } }),
      hasMore,
      nextBefore: hasMore && page.length ? page[0].createdAt : null,
      items: page.map((message) => this.view(message, userId)),
    };
  }

  async send(userId: string, matchId: string, dto: SendMessageDto) {
    const match = await this.loadMatch(userId, matchId);
    const text = dto.text.trim();
    if (!text) {
      throw new BadRequestException('Message text is required');
    }
    const message = await this.messages.save(
      this.messages.create({
        matchId: match.id,
        senderId: userId,
        text,
        type: dto.type ?? 'TEXT',
        meta: dto.meta ?? null,
      }),
    );
    match.lastMessageAt = message.createdAt;
    await this.matches.save(match);

    const otherUserId = match.userAId === userId ? match.userBId : match.userAId;
    const [sender, other] = await Promise.all([
      this.profiles.findOne({ where: { userId } }),
      this.profiles.findOne({ where: { userId: otherUserId } }),
    ]);
    if (other) {
      await this.notifications.save(
        this.notifications.create({
          userId: otherUserId,
          kind: 'MESSAGE',
          title: sender?.nickname || sender?.fullName || 'Tin nhắn mới',
          body: text.slice(0, 160),
          data: { matchId: match.id, profileId: sender?.id ?? null },
        }),
      );
    }
    return this.view(message, userId);
  }

  private async loadMatch(userId: string, matchId: string): Promise<Match> {
    const match = await this.matches.findOne({ where: { id: matchId } });
    if (!match) throw new NotFoundException('Match not found');
    if (match.userAId !== userId && match.userBId !== userId) {
      throw new NotFoundException('Match not found');
    }
    if (match.unmatchedAt) throw new NotFoundException('Match is no longer active');
    return match;
  }

  private view(message: Message, viewerId: string) {
    return {
      id: message.id,
      matchId: message.matchId,
      senderId: message.senderId,
      text: message.text,
      type: message.type,
      meta: message.meta,
      createdAt: message.createdAt,
      isMine: message.senderId === viewerId,
    };
  }
}
