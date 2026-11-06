import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  OneToMany,
  OneToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type ConnectionMode = 'DATING' | 'FRIENDS' | 'STUDY';
export type SwipeAction = 'LIKE' | 'PASS' | 'SUPER_LIKE';
export type MessageType = 'TEXT' | 'IMAGE' | 'SYSTEM';
export type HealthLevel = 'HEALTHY' | 'FADING' | 'AT_RISK';
export type CampusAnonymousMode = 'FULLY_ANONYMOUS' | 'DISCOVERABLE';
export type CampusPostStatus = 'ACTIVE' | 'UNDER_REVIEW' | 'REMOVED' | 'DELETED';
export type CampusCommentStatus = 'ACTIVE' | 'UNDER_REVIEW' | 'REMOVED';
export type CampusCategory =
  | 'GENERAL'
  | 'STUDY'
  | 'EVENT'
  | 'CONFESSION'
  | 'MARKET'
  | 'QUESTION';

@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ unique: true, length: 190 })
  email: string;

  @Column({ name: 'password_hash', length: 190 })
  passwordHash: string;

  @Column({ name: 'is_demo', default: false })
  isDemo: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('profiles')
export class Profile {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index({ unique: true })
  @Column({ name: 'user_id', unique: true })
  userId: string;

  @OneToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id' })
  user?: User;

  @Column({ length: 80, default: '' })
  nickname: string;

  @Column({ name: 'full_name', length: 140, default: '' })
  fullName: string;

  @Column({ type: 'int', default: 20 })
  age: number;

  @Column({ length: 16, default: 'OTHER' })
  gender: Gender;

  @Column({ length: 190, default: '' })
  school: string;

  @Column({ length: 100, default: '' })
  campus: string;

  @Column({ length: 140, default: '' })
  faculty: string;

  @Column({ length: 140, default: '' })
  major: string;

  @Column({ name: 'academic_year', length: 40, default: '' })
  academicYear: string;

  @Column({ default: false })
  alumni: boolean;

  @Column({ type: 'double precision', default: 0 })
  gpa: number;

  @Column({ name: 'gpa_tier', length: 24, default: 'GOOD' })
  gpaTier: string;

  @Column({ type: 'text', default: '' })
  bio: string;

  @Column({ type: 'text', array: true, default: () => "'{}'" })
  interests: string[];

  @Column({ name: 'avatar_seed', type: 'int', default: 1 })
  avatarSeed: number;

  @Column({ length: 16, default: 'FRIENDS' })
  mode: ConnectionMode;

  @Column({ type: 'double precision', default: 0 })
  lat: number;

  @Column({ type: 'double precision', default: 0 })
  lng: number;

  @Column({
    type: 'geometry',
    spatialFeatureType: 'Point',
    srid: 4326,
    nullable: true,
  })
  location: { type: 'Point'; coordinates: [number, number] } | null;

  @Column({ default: false })
  verified: boolean;

  @Column({ name: 'show_gpa_public', default: true })
  showGpaPublic: boolean;

  @Column({ name: 'show_location_public', default: true })
  showLocationPublic: boolean;

  @Column({ name: 'profile_complete', default: false })
  profileComplete: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}

@Entity('swipes')
@Index(['fromUserId', 'targetUserId'], { unique: true })
export class Swipe {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'from_user_id' })
  fromUserId: string;

  @Index()
  @Column({ name: 'target_user_id' })
  targetUserId: string;

  @Column({ length: 16 })
  action: SwipeAction;

  /** True when the swipe originated from Campus Feed author discovery. */
  @Column({ default: false })
  anonymous: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('matches')
@Index(['userAId', 'userBId'], { unique: true })
export class Match {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_a_id' })
  userAId: string;

  @Index()
  @Column({ name: 'user_b_id' })
  userBId: string;

  @Column({ name: 'compatibility_score', type: 'int', default: 50 })
  compatibilityScore: number;

  @Column({ name: 'reveal_opt_in_a', default: false })
  revealOptInA: boolean;

  @Column({ name: 'reveal_opt_in_b', default: false })
  revealOptInB: boolean;

  @Column({ name: 'health_level', length: 16, default: 'FADING' })
  healthLevel: HealthLevel;

  @Column({ name: 'last_message_at', type: 'timestamptz', nullable: true })
  lastMessageAt: Date | null;

  @Column({ name: 'unmatched_at', type: 'timestamptz', nullable: true })
  unmatchedAt: Date | null;

  /**
   * True when the match came from anonymous Campus Feed discovery. Counterpart
   * identity stays masked until both sides opt in and reveal is eligible.
   */
  @Column({ default: false })
  anonymous: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('messages')
@Index(['matchId', 'createdAt'])
export class Message {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'match_id' })
  matchId: string;

  @Column({ name: 'sender_id' })
  senderId: string;

  @Column({ type: 'text' })
  text: string;

  @Column({ length: 16, default: 'TEXT' })
  type: MessageType;

  @Column({ type: 'jsonb', nullable: true })
  meta: Record<string, unknown> | null;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('notifications')
export class Notification {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id' })
  userId: string;

  @Column({ length: 32 })
  kind: string;

  @Column({ length: 190 })
  title: string;

  @Column({ type: 'text' })
  body: string;

  @Column({ default: false })
  read: boolean;

  @Column({ type: 'jsonb', nullable: true })
  data: Record<string, unknown> | null;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('blocks')
@Index(['blockerId', 'blockedId'], { unique: true })
export class Block {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'blocker_id' })
  blockerId: string;

  @Index()
  @Column({ name: 'blocked_id' })
  blockedId: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('reports')
export class Report {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'reporter_id' })
  reporterId: string;

  @Index()
  @Column({ name: 'target_user_id' })
  targetUserId: string;

  @Column({ name: 'match_id', type: 'uuid', nullable: true })
  matchId: string | null;

  @Column({ length: 64 })
  reason: string;

  @Column({ type: 'text', default: '' })
  details: string;

  @Column({ length: 16, default: 'OPEN' })
  status: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('campus_authors')
export class CampusAuthor {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('IDX_campus_authors_user_id', { unique: true })
  @Column({ name: 'user_id', type: 'uuid', unique: true })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id', foreignKeyConstraintName: 'FK_campus_authors_user' })
  user?: User;

  @Column({ length: 48 })
  alias: string;

  @Column({ name: 'avatar_seed', type: 'int', default: 1 })
  avatarSeed: number;

  @Column({ type: 'text', default: '' })
  bio: string;

  @Column({ name: 'show_faculty', default: false })
  showFaculty: boolean;

  @Column({ name: 'show_campus', default: false })
  showCampus: boolean;

  @Column({ name: 'show_interests', default: false })
  showInterests: boolean;

  @Column({ name: 'discovery_enabled', default: false })
  discoveryEnabled: boolean;

  /** True when the author switched discovery off; posting must not re-enable it. */
  @Column({ name: 'discovery_disabled_by_user', default: false })
  discoveryDisabledByUser: boolean;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;
}

@Entity('campus_posts')
@Index('IDX_campus_posts_status_created_at', ['status', 'createdAt'])
@Index('IDX_campus_posts_author_created_at', ['authorId', 'createdAt'])
@Index('IDX_campus_posts_discover_token', ['discoverToken'], { unique: true })
export class CampusPost {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('IDX_campus_posts_author_id')
  @Column({ name: 'author_id', type: 'uuid' })
  authorId: string;

  @ManyToOne(() => CampusAuthor, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'author_id', foreignKeyConstraintName: 'FK_campus_posts_author' })
  author?: CampusAuthor;

  /** Per-post random persona. Never derived from the real profile. */
  @Column({ name: 'author_alias', length: 48, default: '' })
  authorAlias: string;

  @Column({ name: 'author_avatar_seed', type: 'int', default: 1 })
  authorAvatarSeed: number;

  /** Opaque per-post token, only set for DISCOVERABLE posts. */
  @Column({ name: 'discover_token', type: 'uuid', nullable: true })
  discoverToken: string | null;

  @Column({ type: 'text' })
  content: string;

  @Column({ length: 24, default: 'GENERAL' })
  category: CampusCategory;

  @Column({ name: 'anonymous_mode', length: 24, default: 'FULLY_ANONYMOUS' })
  anonymousMode: CampusAnonymousMode;

  @Column({ length: 100, default: '' })
  campus: string;

  @Index('IDX_campus_posts_status')
  @Column({ length: 16, default: 'ACTIVE' })
  status: CampusPostStatus;

  @Column({ name: 'like_count', type: 'int', default: 0 })
  likeCount: number;

  @Column({ name: 'comment_count', type: 'int', default: 0 })
  commentCount: number;

  @Column({ name: 'bookmark_count', type: 'int', default: 0 })
  bookmarkCount: number;

  @Column({ name: 'report_count', type: 'int', default: 0 })
  reportCount: number;

  @Column({ name: 'edited_at', type: 'timestamptz', nullable: true })
  editedAt: Date | null;

  /** Moderator audit trail. Never exposed through the public views. */
  @Column({ name: 'moderated_by', type: 'uuid', nullable: true })
  moderatedBy: string | null;

  @Column({ name: 'moderated_at', type: 'timestamptz', nullable: true })
  moderatedAt: Date | null;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at' })
  updatedAt: Date;

  @OneToMany(() => CampusPostMedia, (media) => media.post)
  media?: CampusPostMedia[];
}

@Entity('campus_post_media')
@Index('IDX_campus_post_media_post_position', ['postId', 'position'])
export class CampusPostMedia {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('IDX_campus_post_media_post_id')
  @Column({ name: 'post_id', type: 'uuid' })
  postId: string;

  @ManyToOne(() => CampusPost, (post) => post.media, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'post_id', foreignKeyConstraintName: 'FK_campus_post_media_post' })
  post?: CampusPost;

  @Column({ length: 16, default: 'IMAGE' })
  kind: 'IMAGE';

  @Column({ name: 'storage_key', length: 120 })
  storageKey: string;

  @Column({ name: 'mime_type', length: 64 })
  mimeType: string;

  @Column({ name: 'size_bytes', type: 'int', default: 0 })
  sizeBytes: number;

  @Column({ type: 'int', nullable: true })
  width: number | null;

  @Column({ type: 'int', nullable: true })
  height: number | null;

  @Column({ type: 'int', default: 0 })
  position: number;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('campus_post_likes')
@Index('IDX_campus_post_likes_post_user', ['postId', 'userId'], { unique: true })
@Index('IDX_campus_post_likes_user_created', ['userId', 'createdAt'])
export class CampusPostLike {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'post_id', type: 'uuid' })
  postId: string;

  @ManyToOne(() => CampusPost, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'post_id', foreignKeyConstraintName: 'FK_campus_post_likes_post' })
  post?: CampusPost;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id', foreignKeyConstraintName: 'FK_campus_post_likes_user' })
  user?: User;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('campus_post_comments')
@Index('IDX_campus_post_comments_post_created', ['postId', 'createdAt'])
export class CampusPostComment {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'post_id', type: 'uuid' })
  postId: string;

  @ManyToOne(() => CampusPost, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'post_id', foreignKeyConstraintName: 'FK_campus_post_comments_post' })
  post?: CampusPost;

  @Index('IDX_campus_post_comments_author_id')
  @Column({ name: 'author_id', type: 'uuid' })
  authorId: string;

  @ManyToOne(() => CampusAuthor, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'author_id', foreignKeyConstraintName: 'FK_campus_post_comments_author' })
  author?: CampusAuthor;

  @Column({ name: 'author_user_id', type: 'uuid' })
  authorUserId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'author_user_id', foreignKeyConstraintName: 'FK_campus_post_comments_user' })
  authorUser?: User;

  /** Persona scoped to this post thread, unlinkable across posts. */
  @Column({ name: 'author_alias', length: 48, default: '' })
  authorAlias: string;

  @Column({ name: 'author_avatar_seed', type: 'int', default: 1 })
  authorAvatarSeed: number;

  @Column({ type: 'text' })
  content: string;

  @Column({ name: 'report_count', type: 'int', default: 0 })
  reportCount: number;

  @Column({ length: 16, default: 'ACTIVE' })
  status: CampusCommentStatus;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('campus_post_bookmarks')
@Index('IDX_campus_post_bookmarks_post_user', ['postId', 'userId'], { unique: true })
@Index('IDX_campus_post_bookmarks_user_created', ['userId', 'createdAt'])
export class CampusPostBookmark {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'post_id', type: 'uuid' })
  postId: string;

  @ManyToOne(() => CampusPost, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'post_id', foreignKeyConstraintName: 'FK_campus_post_bookmarks_post' })
  post?: CampusPost;

  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'user_id', foreignKeyConstraintName: 'FK_campus_post_bookmarks_user' })
  user?: User;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('campus_post_reports')
@Index('IDX_campus_post_reports_post_reporter', ['postId', 'reporterId'], { unique: true })
export class CampusPostReport {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('IDX_campus_post_reports_post_id')
  @Column({ name: 'post_id', type: 'uuid' })
  postId: string;

  @ManyToOne(() => CampusPost, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'post_id', foreignKeyConstraintName: 'FK_campus_post_reports_post' })
  post?: CampusPost;

  @Index('IDX_campus_post_reports_reporter_id')
  @Column({ name: 'reporter_id', type: 'uuid' })
  reporterId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'reporter_id', foreignKeyConstraintName: 'FK_campus_post_reports_reporter' })
  reporter?: User;

  @Column({ name: 'target_author_user_id', type: 'uuid' })
  targetAuthorUserId: string;

  @Column({ length: 64 })
  reason: string;

  @Column({ type: 'text', default: '' })
  details: string;

  @Column({ length: 16, default: 'OPEN' })
  status: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

@Entity('campus_comment_reports')
@Index('IDX_campus_comment_reports_comment_reporter', ['commentId', 'reporterId'], {
  unique: true,
})
export class CampusCommentReport {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('IDX_campus_comment_reports_comment_id')
  @Column({ name: 'comment_id', type: 'uuid' })
  commentId: string;

  @ManyToOne(() => CampusPostComment, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'comment_id', foreignKeyConstraintName: 'FK_campus_comment_reports_comment' })
  comment?: CampusPostComment;

  @Index('IDX_campus_comment_reports_reporter_id')
  @Column({ name: 'reporter_id', type: 'uuid' })
  reporterId: string;

  @ManyToOne(() => User, { onDelete: 'CASCADE' })
  @JoinColumn({ name: 'reporter_id', foreignKeyConstraintName: 'FK_campus_comment_reports_reporter' })
  reporter?: User;

  @Column({ name: 'target_author_user_id', type: 'uuid' })
  targetAuthorUserId: string;

  @Column({ length: 64 })
  reason: string;

  @Column({ type: 'text', default: '' })
  details: string;

  @Column({ length: 16, default: 'OPEN' })
  status: string;

  @CreateDateColumn({ name: 'created_at' })
  createdAt: Date;
}

export const ENTITIES = [
  User,
  Profile,
  Swipe,
  Match,
  Message,
  Notification,
  Block,
  Report,
  CampusAuthor,
  CampusPost,
  CampusPostMedia,
  CampusPostLike,
  CampusPostComment,
  CampusPostBookmark,
  CampusPostReport,
  CampusCommentReport,
];
