import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { randomInt, randomUUID } from 'crypto';
import { In, Repository, SelectQueryBuilder } from 'typeorm';
import {
  Block,
  CampusAuthor,
  CampusCommentReport,
  CampusPost,
  CampusPostBookmark,
  CampusPostComment,
  CampusPostReport,
  Profile,
} from '../database/entities';
import { isUuid } from '../common/anonymity';
import { SocialService } from '../social/social.service';
import {
  CampusReportDto,
  CampusSwipeDto,
  CreateCampusCommentDto,
  CreateCampusPostDto,
  ListCampusQueryDto,
  ModerationDecisionDto,
  PageQueryDto,
  UpdateCampusPostDto,
  UpdateCampusSettingsDto,
} from './campus.dto';
import { CampusMediaStorage } from './campus-media.storage';
import { CampusStore, SettingsPatch } from './campus.store';
import {
  CampusCommentView,
  CampusPostView,
  isPostDiscoverable,
  toCampusAuthorProfile,
  toCampusCommentView,
  toCampusPostView,
} from './campus.view';

interface Persona {
  alias: string;
  avatarSeed: number;
}

interface CursorValue {
  createdAt: string;
  id: string;
}

const ALIASES = [
  'Sao Băng',
  'Mây Nhỏ',
  'Gió Thu',
  'Nắng Mai',
  'Mưa Nhẹ',
  'Đêm Sao',
  'Lá Phong',
  'Sương Sớm',
  'Mèo Mun',
  'Cà Phê',
];

const NOT_BLOCKED_SQL = (me: string, otherUserColumn: string) =>
  // blocks.* are varchar while user ids in Campus tables are uuid: compare as text.
  `NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = :${me} AND b.blocked_id = (${otherUserColumn})::text) OR (b.blocked_id = :${me} AND b.blocker_id = (${otherUserColumn})::text))`;

@Injectable()
export class CampusService {
  constructor(
    @InjectRepository(CampusAuthor)
    private readonly authors: Repository<CampusAuthor>,
    @InjectRepository(CampusPost)
    private readonly posts: Repository<CampusPost>,
    @InjectRepository(CampusPostComment)
    private readonly comments: Repository<CampusPostComment>,
    @InjectRepository(CampusPostBookmark)
    private readonly bookmarks: Repository<CampusPostBookmark>,
    @InjectRepository(CampusPostReport)
    private readonly campusReports: Repository<CampusPostReport>,
    @InjectRepository(CampusCommentReport)
    private readonly commentReports: Repository<CampusCommentReport>,
    @InjectRepository(Profile)
    private readonly profiles: Repository<Profile>,
    @InjectRepository(Block)
    private readonly blocks: Repository<Block>,
    private readonly social: SocialService,
    private readonly mediaStorage: CampusMediaStorage,
    private readonly store: CampusStore,
  ) {}

  // ------------------------------------------------------------------ feed

  async list(userId: string, query: ListCampusQueryDto) {
    const viewer = await this.ensureAuthor(userId);
    const limit = this.pageSize(query.limit);
    const saved = (query.tab ?? 'for-you') === 'saved';
    const cursor = this.decodeCursor(query.cursor);

    // Saved tab pages over bookmarks (unique id, own timestamp); the feed pages
    // over posts. Both order by (createdAt, id) which the cursor mirrors.
    const qb: SelectQueryBuilder<any> = saved
      ? this.bookmarks
          .createQueryBuilder('item')
          .innerJoinAndSelect('item.post', 'post')
          .where('item.userId = :me', { me: userId })
      : this.posts.createQueryBuilder('post').where('1 = 1');
    qb.innerJoinAndSelect('post.author', 'author')
      .leftJoinAndSelect('post.media', 'media')
      .andWhere(`post.status = 'ACTIVE'`)
      // Separate parameter names: blocks.* are varchar, reporter_id is uuid, and
      // PostgreSQL infers one type per bind parameter.
      .andWhere(NOT_BLOCKED_SQL('meBlock', 'author.userId'), { meBlock: userId })
      .andWhere(
        `NOT EXISTS (SELECT 1 FROM campus_post_reports r WHERE r.post_id = post.id AND r.reporter_id = :meReporter)`,
        { meReporter: userId },
      );
    if (query.category) {
      qb.andWhere('post.category = :category', { category: query.category });
    }
    if (query.campus?.trim()) {
      qb.andWhere('post.campus = :campus', { campus: query.campus.trim() });
    }
    const key = saved ? 'item' : 'post';
    this.applyCursor(qb, key, cursor);
    const rows = await qb
      .orderBy(`${key}.createdAt`, 'DESC')
      .addOrderBy(`${key}.id`, 'DESC')
      .take(limit + 1)
      .getMany();

    const page = this.paginate(rows, limit);
    const posts: CampusPost[] = saved
      ? page.items.map((row: CampusPostBookmark) => row.post as CampusPost)
      : page.items;
    return {
      items: await this.toPostViews(userId, viewer.id, posts),
      hasMore: page.hasMore,
      nextCursor: page.nextCursor,
    };
  }

  async listMine(userId: string, query: PageQueryDto) {
    const viewer = await this.ensureAuthor(userId);
    const limit = this.pageSize(query.limit);
    const cursor = this.decodeCursor(query.cursor);
    const qb = this.posts
      .createQueryBuilder('post')
      .innerJoinAndSelect('post.author', 'author')
      .leftJoinAndSelect('post.media', 'media')
      .where('post.authorId = :authorId', { authorId: viewer.id })
      .andWhere(`post.status IN ('ACTIVE', 'UNDER_REVIEW')`);
    this.applyCursor(qb, 'post', cursor);
    const rows = await qb
      .orderBy('post.createdAt', 'DESC')
      .addOrderBy('post.id', 'DESC')
      .take(limit + 1)
      .getMany();
    const page = this.paginate(rows, limit);
    return {
      items: await this.toPostViews(userId, viewer.id, page.items),
      hasMore: page.hasMore,
      nextCursor: page.nextCursor,
    };
  }

  async detail(userId: string, postId: string): Promise<CampusPostView> {
    const viewer = await this.ensureAuthor(userId);
    const post = await this.loadActivePost(userId, postId, {
      hideReported: true,
    });
    return (await this.toPostViews(userId, viewer.id, [post]))[0];
  }

  // ------------------------------------------------------------ post write

  async create(
    userId: string,
    dto: CreateCampusPostDto,
  ): Promise<CampusPostView> {
    const author = await this.ensureAuthor(userId);
    const content = (dto.content ?? '').trim();
    const images = dto.images ?? [];
    if (!content && !images.length) {
      throw new BadRequestException('Post needs text or at least one image');
    }
    const discoverable = dto.anonymousMode === 'DISCOVERABLE';
    if (discoverable && author.discoveryDisabledByUser) {
      // Never silently undo the author's explicit opt-out.
      throw new ConflictException('Author discovery is turned off in your settings');
    }
    const profile = await this.profiles.findOne({ where: { userId } });

    const stored: Array<{
      storageKey: string;
      mimeType: string;
      sizeBytes: number;
      width: number;
      height: number;
    }> = [];
    const cleanup = () =>
      Promise.all(stored.map((item) => this.mediaStorage.remove(item.storageKey)));
    try {
      for (const image of images) {
        stored.push(await this.mediaStorage.saveDataUrl(image.dataUrl));
      }
      if (discoverable && !author.discoveryEnabled) {
        await this.store.updateSettings(author.id, { discoveryEnabled: true }, null);
        author.discoveryEnabled = true;
      }
      // Persona is random per post and never derived from the real profile.
      const persona = this.newPersona(profile?.avatarSeed);
      const post = await this.store.createPost(
        {
          authorId: author.id,
          authorAlias: persona.alias,
          authorAvatarSeed: persona.avatarSeed,
          discoverToken: discoverable ? randomUUID() : null,
          content,
          category: dto.category,
          anonymousMode: dto.anonymousMode,
          // Campus is only public when the author explicitly allowed it.
          campus:
            author.showCampus && profile?.campus ? profile.campus.trim() : '',
          status: 'ACTIVE',
        },
        stored.map((item, index) => ({
          kind: 'IMAGE' as const,
          storageKey: item.storageKey,
          mimeType: item.mimeType,
          sizeBytes: item.sizeBytes,
          // Dimensions come from the decoded image, never from the client.
          width: item.width,
          height: item.height,
          position: index,
        })),
      );
      post.author = author;
      return toCampusPostView(
        post,
        { liked: false, bookmarked: false, isAuthor: true },
        (key) => this.mediaStorage.urlFor(key),
      );
    } catch (error) {
      await cleanup();
      throw error;
    }
  }

  /** Text and category only. Privacy mode, persona and images never change. */
  async update(
    userId: string,
    postId: string,
    dto: UpdateCampusPostDto,
  ): Promise<CampusPostView> {
    if (dto.content === undefined && dto.category === undefined) {
      throw new BadRequestException('Nothing to update');
    }
    const post = await this.posts.findOne({
      where: { id: postId },
      relations: { author: true, media: true },
    });
    // Only live posts of the caller are editable: editing a post that is under
    // review would let its author dodge moderation.
    if (
      !post ||
      !post.author ||
      post.author.userId !== userId ||
      post.status !== 'ACTIVE'
    ) {
      throw new NotFoundException('Post not found');
    }
    const content = dto.content !== undefined ? dto.content.trim() : post.content;
    if (!content && !(post.media?.length ?? 0)) {
      throw new BadRequestException('Post needs text or at least one image');
    }
    const category = dto.category ?? post.category;
    const editedAt = new Date();
    const result = await this.posts.update(
      { id: post.id, status: 'ACTIVE' },
      { content, category, editedAt },
    );
    if (!result.affected) throw new NotFoundException('Post not found');
    post.content = content;
    post.category = category;
    post.editedAt = editedAt;
    return (await this.toPostViews(userId, post.author.id, [post]))[0];
  }

  async removePost(userId: string, postId: string) {
    const post = await this.posts.findOne({
      where: { id: postId },
      relations: { author: true, media: true },
    });
    if (
      !post ||
      post.status === 'DELETED' ||
      // A moderator removal is a record, not something the author can overwrite.
      post.status === 'REMOVED' ||
      !post.author ||
      post.author.userId !== userId
    ) {
      throw new NotFoundException('Post not found');
    }
    const media = post.media ?? [];
    post.status = 'DELETED';
    post.discoverToken = null;
    post.content = '';
    post.editedAt = new Date();
    await this.posts.save(post);
    if (media.length) {
      await Promise.all(media.map((item) => this.mediaStorage.remove(item.storageKey)));
      await this.store.deleteMedia(post.id);
    }
    return { deleted: true };
  }

  // ------------------------------------------------------------- reactions

  async like(userId: string, postId: string) {
    const post = await this.loadActivePost(userId, postId, { hideReported: true });
    if (post.author?.userId === userId) {
      throw new BadRequestException('You cannot like your own post');
    }
    const result = await this.store.setFlag('like', true, post.id, userId);
    return { liked: true, likeCount: result.count };
  }

  async unlike(userId: string, postId: string) {
    const post = await this.loadActivePost(userId, postId);
    const result = await this.store.setFlag('like', false, post.id, userId);
    return { liked: false, likeCount: result.count };
  }

  async bookmark(userId: string, postId: string) {
    const post = await this.loadActivePost(userId, postId, { hideReported: true });
    const result = await this.store.setFlag('bookmark', true, post.id, userId);
    return { bookmarked: true, bookmarkCount: result.count };
  }

  async unbookmark(userId: string, postId: string) {
    const post = await this.loadActivePost(userId, postId);
    const result = await this.store.setFlag('bookmark', false, post.id, userId);
    return { bookmarked: false, bookmarkCount: result.count };
  }

  // -------------------------------------------------------------- comments

  async listComments(userId: string, postId: string, query: PageQueryDto) {
    const post = await this.loadActivePost(userId, postId);
    const limit = this.pageSize(query.limit, 30);
    const cursor = this.decodeCursor(query.cursor);
    const qb = this.comments
      .createQueryBuilder('comment')
      .where('comment.postId = :postId', { postId: post.id })
      .andWhere(`comment.status = 'ACTIVE'`)
      .andWhere(NOT_BLOCKED_SQL('meBlock', 'comment.authorUserId'), { meBlock: userId });
    this.applyCursor(qb, 'comment', cursor);
    const rows = await qb
      .orderBy('comment.createdAt', 'DESC')
      .addOrderBy('comment.id', 'DESC')
      .take(limit + 1)
      .getMany();
    const page = this.paginate(rows, limit);
    const items: CampusCommentView[] = page.items.map((comment) =>
      toCampusCommentView(comment, userId),
    );
    return { items, hasMore: page.hasMore, nextCursor: page.nextCursor };
  }

  async addComment(userId: string, postId: string, dto: CreateCampusCommentDto) {
    const post = await this.loadActivePost(userId, postId, { hideReported: true });
    const author = await this.ensureAuthor(userId);
    const content = dto.content.trim();
    if (!content) throw new BadRequestException('Comment is required');

    // Thread-scoped persona: the post owner appears as the post persona (OP);
    // a returning commenter keeps one persona inside this thread only.
    let persona: Persona;
    if (post.authorId === author.id) {
      persona = { alias: post.authorAlias, avatarSeed: post.authorAvatarSeed };
    } else {
      const previous = await this.comments.findOne({
        where: { postId: post.id, authorUserId: userId },
      });
      persona = previous?.authorAlias
        ? { alias: previous.authorAlias, avatarSeed: previous.authorAvatarSeed }
        : this.newPersona();
    }
    const id = await this.store.addComment({
      postId: post.id,
      authorId: author.id,
      authorUserId: userId,
      authorAlias: persona.alias,
      authorAvatarSeed: persona.avatarSeed,
      content,
    });
    if (!id) throw new NotFoundException('Post not found');
    const comment = await this.comments.findOne({ where: { id } });
    if (!comment) throw new NotFoundException('Comment not found');
    return toCampusCommentView(comment, userId);
  }

  async removeComment(userId: string, postId: string, commentId: string) {
    await this.loadActivePost(userId, postId);
    const deleted = await this.store.deleteComment(commentId, postId, userId);
    if (!deleted) throw new NotFoundException('Comment not found');
    return { deleted: true };
  }

  // --------------------------------------------------------------- reports

  async report(userId: string, postId: string, dto: CampusReportDto) {
    const post = await this.loadActivePost(userId, postId);
    const authorUserId = post.author?.userId;
    if (!authorUserId) throw new NotFoundException('Post not found');
    if (authorUserId === userId) {
      throw new BadRequestException('You cannot report your own post');
    }
    const result = await this.store.reportPost(
      post.id,
      userId,
      authorUserId,
      dto.reason,
      (dto.details ?? '').trim(),
      this.reportThreshold(),
    );
    // Reporting twice is idempotent and looks identical to the first report.
    return { reported: true, ...(result.created ? {} : { duplicate: true }) };
  }

  async reportComment(
    userId: string,
    postId: string,
    commentId: string,
    dto: CampusReportDto,
  ) {
    const post = await this.loadActivePost(userId, postId);
    const comment = await this.comments.findOne({
      where: { id: commentId, postId: post.id },
    });
    if (!comment || comment.status !== 'ACTIVE') {
      throw new NotFoundException('Comment not found');
    }
    await this.assertNotBlocked(userId, comment.authorUserId);
    if (comment.authorUserId === userId) {
      throw new BadRequestException('You cannot report your own comment');
    }
    const result = await this.store.reportComment(
      comment.id,
      post.id,
      userId,
      dto.reason,
      (dto.details ?? '').trim(),
      this.reportThreshold(),
    );
    return { reported: true, ...(result.created ? {} : { duplicate: true }) };
  }

  // ------------------------------------------------------------ moderation

  /** Queue for moderators. Author identity is never part of the payload. */
  async moderationQueue(limit = 20) {
    const posts = await this.posts.find({
      where: { status: 'UNDER_REVIEW' },
      relations: { author: true, media: true },
      order: { updatedAt: 'ASC' },
      take: Math.min(Math.max(limit, 1), 50),
    });
    const reports = posts.length
      ? await this.campusReports.find({
          where: { postId: In(posts.map((post) => post.id)), status: 'OPEN' },
        })
      : [];
    const comments = await this.comments.find({
      where: { status: 'UNDER_REVIEW' },
      order: { createdAt: 'ASC' },
      take: Math.min(Math.max(limit, 1), 50),
    });
    return {
      posts: posts.map((post) => ({
        ...toCampusPostView(
          post,
          { liked: false, bookmarked: false, isAuthor: false },
          (key) => this.mediaStorage.urlFor(key),
        ),
        reportCount: post.reportCount,
        reasons: reports
          .filter((report) => report.postId === post.id)
          .map((report) => report.reason),
      })),
      comments: comments.map((comment) => ({
        id: comment.id,
        postId: comment.postId,
        content: comment.content,
        reportCount: comment.reportCount,
        createdAt: comment.createdAt,
      })),
    };
  }

  async decidePost(moderatorId: string, postId: string, dto: ModerationDecisionDto) {
    this.assertUuid(postId, 'Post not found');
    const result = await this.store.decidePost(postId, dto.action, moderatorId);
    if (!result) throw new NotFoundException('Post is not under review');
    await Promise.all(result.storageKeys.map((key) => this.mediaStorage.remove(key)));
    return { postId, status: result.status };
  }

  async decideComment(commentId: string, dto: ModerationDecisionDto) {
    this.assertUuid(commentId, 'Comment not found');
    const status = await this.store.decideComment(commentId, dto.action);
    if (!status) throw new NotFoundException('Comment is not under review');
    return { commentId, status };
  }

  // -------------------------------------------------------------- settings

  async getSettings(userId: string) {
    const author = await this.ensureAuthor(userId);
    const discoverablePosts = await this.posts.count({
      where: {
        authorId: author.id,
        status: 'ACTIVE',
        anonymousMode: 'DISCOVERABLE',
      },
    });
    return this.settingsView(author, discoverablePosts);
  }

  async updateSettings(userId: string, dto: UpdateCampusSettingsDto) {
    const author = await this.ensureAuthor(userId);
    const patch: SettingsPatch = {};
    if (dto.showFaculty !== undefined) patch.showFaculty = dto.showFaculty;
    if (dto.showInterests !== undefined) patch.showInterests = dto.showInterests;
    if (dto.showCampus !== undefined) patch.showCampus = dto.showCampus;
    if (dto.discoveryEnabled !== undefined) {
      patch.discoveryEnabled = dto.discoveryEnabled;
      // Remember the explicit opt-out so posting cannot undo it.
      patch.discoveryDisabledByUser = !dto.discoveryEnabled;
    }
    let campusForPosts: string | null = null;
    if (dto.showCampus !== undefined && dto.showCampus !== author.showCampus) {
      const profile = await this.profiles.findOne({ where: { userId } });
      campusForPosts = dto.showCampus ? profile?.campus?.trim() ?? '' : '';
    }
    const result = await this.store.updateSettings(author.id, patch, campusForPosts);
    const fresh = await this.authors.findOne({ where: { id: author.id } });
    const discoverablePosts = await this.posts.count({
      where: {
        authorId: author.id,
        status: 'ACTIVE',
        anonymousMode: 'DISCOVERABLE',
      },
    });
    return {
      ...this.settingsView(fresh ?? author, discoverablePosts),
      revokedTokens: result.revoked,
      issuedTokens: result.issued,
    };
  }

  private settingsView(author: CampusAuthor, discoverablePosts: number) {
    return {
      discoveryEnabled: author.discoveryEnabled,
      showFaculty: author.showFaculty,
      showCampus: author.showCampus,
      showInterests: author.showInterests,
      discoverablePosts,
    };
  }

  // ------------------------------------------------------ discover author

  async authorDetail(userId: string, token: string) {
    const { post, author } = await this.loadDiscoverablePost(token);
    await this.assertNotBlocked(userId, author.userId);
    // Compatibility is omitted: it is computed from the real profile and would
    // let a viewer probe hidden fields by editing their own profile.
    const profile = await this.profiles.findOne({ where: { userId: author.userId } });
    return toCampusAuthorProfile(post, author, profile);
  }

  async swipeAuthor(userId: string, token: string, dto: CampusSwipeDto) {
    const { author } = await this.loadDiscoverablePost(token);
    if (author.userId === userId) {
      throw new BadRequestException('You cannot swipe yourself');
    }
    await this.assertNotBlocked(userId, author.userId);
    const result = await this.social.swipe(
      userId,
      { targetUserId: author.userId, action: dto.action },
      { anonymous: true },
    );
    return {
      anonymous: true,
      matched: result.matched,
      matchId: result.match?.id ?? null,
    };
  }

  /**
   * Resolves a per-post discover token. Unknown, malformed, deleted, under
   * review, fully anonymous or opted-out cases all return the same 404.
   */
  private async loadDiscoverablePost(
    token: string,
  ): Promise<{ post: CampusPost; author: CampusAuthor }> {
    if (!isUuid(token)) throw new NotFoundException('Author not found');
    const post = await this.posts.findOne({
      where: { discoverToken: token },
      relations: { author: true },
    });
    if (
      !post ||
      post.status !== 'ACTIVE' ||
      post.anonymousMode !== 'DISCOVERABLE' ||
      !post.author ||
      !isPostDiscoverable(post)
    ) {
      throw new NotFoundException('Author not found');
    }
    return { post, author: post.author };
  }

  // --------------------------------------------------------------- helpers

  private async loadActivePost(
    userId: string,
    postId: string,
    options: { hideReported?: boolean } = {},
  ): Promise<CampusPost> {
    this.assertUuid(postId, 'Post not found');
    const post = await this.posts.findOne({
      where: { id: postId },
      relations: { author: true, media: true },
    });
    if (!post || post.status !== 'ACTIVE' || !post.author) {
      throw new NotFoundException('Post not found');
    }
    await this.assertNotBlocked(userId, post.author.userId);
    if (options.hideReported) {
      const reported = await this.campusReports.findOne({
        where: { postId: post.id, reporterId: userId },
      });
      if (reported) throw new NotFoundException('Post not found');
    }
    return post;
  }

  private async toPostViews(
    userId: string,
    authorId: string,
    rows: CampusPost[],
  ): Promise<CampusPostView[]> {
    if (!rows.length) return [];
    const ids = rows.map((post) => post.id);
    const [liked, saved] = await Promise.all([
      this.store.flaggedPostIds('like', ids, userId),
      this.store.flaggedPostIds('bookmark', ids, userId),
    ]);
    return rows.map((post) => {
      if (!post.author) throw new NotFoundException('Post author not found');
      return toCampusPostView(
        post,
        {
          liked: liked.has(post.id),
          bookmarked: saved.has(post.id),
          isAuthor: post.authorId === authorId,
        },
        (key) => this.mediaStorage.urlFor(key),
      );
    });
  }

  private async ensureAuthor(userId: string): Promise<CampusAuthor> {
    const existing = await this.authors.findOne({ where: { userId } });
    if (existing) return existing;
    // Account-level record only. alias/avatar here are never published; public
    // personas live on each post and comment. The real bio is not copied.
    const persona = this.newPersona();
    try {
      return await this.authors.save(
        this.authors.create({
          userId,
          alias: persona.alias,
          avatarSeed: persona.avatarSeed,
          bio: '',
        }),
      );
    } catch (error) {
      // Two first requests raced; the unique user_id index let one win.
      const winner = await this.authors.findOne({ where: { userId } });
      if (winner) return winner;
      throw error;
    }
  }

  /** Random persona independent of the real profile (avatar never equals it). */
  private newPersona(realAvatarSeed?: number): Persona {
    let avatarSeed = randomInt(1, 21);
    if (realAvatarSeed && avatarSeed === realAvatarSeed) {
      avatarSeed = (avatarSeed % 20) + 1;
    }
    return {
      alias: `${ALIASES[randomInt(ALIASES.length)]} #${randomInt(1000, 10000)}`,
      avatarSeed,
    };
  }

  private async assertNotBlocked(userId: string, otherUserId: string) {
    if (userId === otherUserId) return;
    const blocked = await this.blocks.findOne({
      where: [
        { blockerId: userId, blockedId: otherUserId },
        { blockerId: otherUserId, blockedId: userId },
      ],
    });
    if (blocked) throw new NotFoundException('Post not found');
  }

  private assertUuid(value: string, message: string): void {
    if (!isUuid(value)) throw new NotFoundException(message);
  }

  private pageSize(value: number | undefined, fallback = 10): number {
    return Math.min(Math.max(value ?? fallback, 1), 30);
  }

  private reportThreshold(): number {
    const value = Number.parseInt(process.env.CAMPUS_REPORT_HIDE_THRESHOLD ?? '5', 10);
    return Number.isFinite(value) && value > 0 ? value : 5;
  }

  // Compared at millisecond precision because the cursor travels through a JS
  // Date, which cannot represent PostgreSQL's microseconds.
  private applyCursor(
    qb: SelectQueryBuilder<any>,
    key: string,
    cursor: CursorValue | null,
  ): void {
    if (!cursor) return;
    qb.andWhere(
      `(date_trunc('milliseconds', ${key}.createdAt) < :cursorDate OR (date_trunc('milliseconds', ${key}.createdAt) = :cursorDate AND ${key}.id < :cursorId))`,
      { cursorDate: new Date(cursor.createdAt), cursorId: cursor.id },
    );
  }

  private paginate<T extends { createdAt: Date; id: string }>(
    rows: T[],
    limit: number,
  ): { items: T[]; hasMore: boolean; nextCursor: string | null } {
    const hasMore = rows.length > limit;
    const items = rows.slice(0, limit);
    const last = items[items.length - 1];
    return {
      items,
      hasMore,
      nextCursor: hasMore && last ? this.encodeCursor(last.createdAt, last.id) : null,
    };
  }

  private encodeCursor(createdAt: Date, id: string): string {
    return Buffer.from(
      JSON.stringify({ createdAt: createdAt.toISOString(), id }),
      'utf8',
    ).toString('base64url');
  }

  /** A bad cursor is the caller's mistake: always a 400, never a 500. */
  private decodeCursor(cursor?: string): CursorValue | null {
    if (!cursor) return null;
    let value: Partial<CursorValue>;
    try {
      value = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'));
    } catch {
      throw new BadRequestException('Invalid cursor');
    }
    if (
      !value ||
      typeof value.createdAt !== 'string' ||
      typeof value.id !== 'string' ||
      !isUuid(value.id) ||
      Number.isNaN(new Date(value.createdAt).getTime())
    ) {
      throw new BadRequestException('Invalid cursor');
    }
    return { createdAt: value.createdAt, id: value.id };
  }
}
