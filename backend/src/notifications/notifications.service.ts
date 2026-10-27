import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Notification } from '../database/entities';

@Injectable()
export class NotificationsService {
  constructor(
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
  ) {}

  async list(userId: string) {
    const [items, unread] = await Promise.all([
      this.notifications.find({
        where: { userId },
        order: { createdAt: 'DESC' },
        take: 50,
      }),
      this.notifications.count({ where: { userId, read: false } }),
    ]);
    return {
      unread,
      items: items.map((item) => ({
        ...item,
        time: item.createdAt,
      })),
    };
  }

  async markRead(userId: string, ids?: string[]) {
    if (ids?.length) {
      await this.notifications.update(
        { userId, id: In(ids), read: false },
        { read: true },
      );
    } else {
      await this.notifications.update({ userId, read: false }, { read: true });
    }
    return { updated: true, unread: await this.notifications.count({
      where: { userId, read: false },
    }) };
  }

  async unreadCount(userId: string) {
    return { unread: await this.notifications.count({ where: { userId, read: false } }) };
  }
}
