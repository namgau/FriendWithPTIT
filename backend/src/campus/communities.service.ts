import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { CampusCommunityMember } from '../database/entities';
import { COMMUNITIES, COMMUNITY_IDS } from './communities';

@Injectable()
export class CommunitiesService {
  constructor(
    @InjectRepository(CampusCommunityMember)
    private readonly members: Repository<CampusCommunityMember>,
  ) {}

  async list(userId: string) {
    const rows = await this.members.find();
    const mine = new Set(rows.filter((r) => r.userId === userId).map((r) => r.communityId));
    return {
      items: COMMUNITIES.map((c) => ({
        ...c,
        memberCount: rows.filter((r) => r.communityId === c.id).length,
        joined: mine.has(c.id),
      })),
    };
  }

  async setJoined(userId: string, communityId: string, joined: boolean) {
    if (!COMMUNITY_IDS.includes(communityId)) throw new NotFoundException('Community not found');
    const existing = await this.members.findOne({ where: { userId, communityId } });
    if (joined && !existing) await this.members.save(this.members.create({ userId, communityId }));
    if (!joined && existing) await this.members.remove(existing);
    return this.list(userId);
  }
}
