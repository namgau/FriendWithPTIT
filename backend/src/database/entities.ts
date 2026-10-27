import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  OneToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type ConnectionMode = 'DATING' | 'FRIENDS' | 'STUDY';
export type SwipeAction = 'LIKE' | 'PASS' | 'SUPER_LIKE';
export type MessageType = 'TEXT' | 'IMAGE' | 'SYSTEM';
export type HealthLevel = 'HEALTHY' | 'FADING' | 'AT_RISK';

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

  @Column({ name: 'match_id', nullable: true })
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

export const ENTITIES = [
  User,
  Profile,
  Swipe,
  Match,
  Message,
  Notification,
  Block,
  Report,
];
