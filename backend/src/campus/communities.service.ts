import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
  OnModuleInit,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { createHash, randomUUID } from 'crypto';
import { Repository } from 'typeorm';
import { CampusCommunity, CampusCommunityMember } from '../database/entities';
import { ANIMAL_ALIASES } from './campus.service';
import { COMMUNITIES } from './communities';

export interface CreateCommunityInput {
  name: string;
  description?: string;
  requiresApproval?: boolean;
  hidden?: boolean;
}

/** Mã mời = 8 ký tự đầu của id (in hoa). */
export const inviteCode = (id: string) => id.replace(/-/g, '').slice(0, 8).toUpperCase();

@Injectable()
export class CommunitiesService implements OnModuleInit {
  constructor(
    @InjectRepository(CampusCommunity)
    private readonly communities: Repository<CampusCommunity>,
    @InjectRepository(CampusCommunityMember)
    private readonly members: Repository<CampusCommunityMember>,
  ) {}

  /** Cộng đồng có sẵn (không chủ, ai cũng vào được) được tạo một lần. */
  async onModuleInit(): Promise<void> {
    try {
      for (const c of COMMUNITIES) {
        if (!(await this.communities.findOne({ where: { id: c.id } }))) {
          await this.communities.save(this.communities.create({ ...c, ownerUserId: null, requiresApproval: false }));
        }
      }
    } catch {
      // Bảng chưa có (migration chưa chạy) — bỏ qua, lần khởi động sau sẽ tạo.
    }
  }

  async list(userId: string) {
    const [all, rows] = await Promise.all([
      this.communities.find({ order: { createdAt: 'ASC' } }),
      this.members.find(),
    ]);
    return {
      // Cộng đồng ẩn chỉ hiện với chủ, thành viên và người đang chờ duyệt.
      items: all.filter((c) => !c.hidden || c.ownerUserId === userId || rows.some((r) => r.communityId === c.id && r.userId === userId)).map((c) => {
        const mine = rows.find((r) => r.communityId === c.id && r.userId === userId);
        const isOwner = c.ownerUserId === userId;
        return {
          hidden: c.hidden,
          inviteCode: c.hidden && (isOwner || mine?.status === 'MEMBER') ? inviteCode(c.id) : null,
          id: c.id,
          name: c.name,
          description: c.description,
          requiresApproval: c.requiresApproval,
          isOwner,
          memberCount: rows.filter((r) => r.communityId === c.id && r.status === 'MEMBER').length,
          joined: mine?.status === 'MEMBER',
          pending: mine?.status === 'PENDING',
          pendingCount: isOwner ? rows.filter((r) => r.communityId === c.id && r.status === 'PENDING').length : 0,
        };
      }),
    };
  }

  async create(userId: string, input: CreateCommunityInput) {
    const name = input.name.trim();
    if (name.length < 3) throw new BadRequestException('Tên cộng đồng cần ít nhất 3 ký tự');
    if (await this.communities.findOne({ where: { name } })) throw new BadRequestException('Tên cộng đồng đã tồn tại');
    const community = await this.communities.save(this.communities.create({
      id: randomUUID(),
      name,
      description: (input.description ?? '').trim(),
      ownerUserId: userId,
      requiresApproval: input.requiresApproval ?? false,
      hidden: input.hidden ?? false,
    }));
    await this.members.save(this.members.create({ userId, communityId: community.id, status: 'MEMBER' }));
    return this.list(userId);
  }

  async joinByCode(userId: string, code: string) {
    const wanted = code.trim().toUpperCase();
    const community = (await this.communities.find()).find((c) => inviteCode(c.id) === wanted);
    if (!community) throw new NotFoundException('Mã mời không đúng');
    return this.setJoined(userId, community.id, true, true);
  }

  async setJoined(userId: string, communityId: string, joined: boolean, viaCode = false) {
    const community = await this.communities.findOne({ where: { id: communityId } });
    if (!community) throw new NotFoundException('Community not found');
    const member = await this.members.findOne({ where: { userId, communityId } });
    // Cộng đồng ẩn: người ngoài chỉ vào được bằng mã mời (giống như không tồn tại).
    if (joined && community.hidden && !viaCode && !member && community.ownerUserId !== userId) {
      throw new NotFoundException('Community not found');
    }
    const existing = await this.members.findOne({ where: { userId, communityId } });
    if (joined && !existing) {
      const status = community.requiresApproval && community.ownerUserId !== userId ? 'PENDING' : 'MEMBER';
      await this.members.save(this.members.create({ userId, communityId, status }));
    }
    if (!joined && existing) {
      if (community.ownerUserId === userId) throw new BadRequestException('Chủ cộng đồng không thể rời cộng đồng của mình');
      await this.members.remove(existing);
    }
    return this.list(userId);
  }

  /** Yêu cầu tham gia đang chờ ; chỉ chủ cộng đồng xem được. */
  async requests(userId: string, communityId: string) {
    await this.requireOwner(userId, communityId);
    const pending = await this.members.find({ where: { communityId, status: 'PENDING' } });
    // Trang chủ ẩn danh: chủ cộng đồng chỉ thấy danh tính con vật, cố định theo (người, cộng đồng).
    return {
      items: pending.map((p) => {
        const h = createHash('sha256').update(`${communityId}:${p.userId}`).digest();
        const seed = (h[0] % ANIMAL_ALIASES.length) + 1;
        return { userId: p.userId, nickname: `${ANIMAL_ALIASES[seed - 1]} #${1000 + (h.readUInt16BE(1) % 9000)}`, avatarSeed: seed, requestedAt: p.createdAt };
      }),
    };
  }

  async decide(userId: string, communityId: string, memberUserId: string, approve: boolean) {
    await this.requireOwner(userId, communityId);
    const row = await this.members.findOne({ where: { communityId, userId: memberUserId, status: 'PENDING' } });
    if (!row) throw new NotFoundException('Request not found');
    if (approve) {
      row.status = 'MEMBER';
      await this.members.save(row);
    } else {
      await this.members.remove(row);
    }
    return this.requests(userId, communityId);
  }

  private async requireOwner(userId: string, communityId: string) {
    const community = await this.communities.findOne({ where: { id: communityId } });
    if (!community) throw new NotFoundException('Community not found');
    if (community.ownerUserId !== userId) throw new ForbiddenException('Chỉ chủ cộng đồng được duyệt');
    return community;
  }
}
