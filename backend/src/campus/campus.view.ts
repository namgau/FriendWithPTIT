import { createHash } from 'crypto';
import {
  CampusAuthor,
  CampusPost,
  CampusPostComment,
  CampusPostMedia,
  Profile,
} from '../database/entities';

const FALLBACK_ALIAS = 'Ẩn danh';
/** Showing the whole interest list would let viewers fingerprint the author. */
const MAX_PUBLIC_INTERESTS = 2;

export interface CampusAuthorBadgeView {
  alias: string;
  avatarSeed: number;
  discoverable: boolean;
  authorToken?: string;
}

export interface CampusAuthorProfileView {
  token: string;
  alias: string;
  avatarSeed: number;
  bio: string;
  faculty?: string;
  campus?: string;
  interests?: string[];
  discoverable: true;
}

export interface CampusMediaView {
  id: string;
  url: string;
  mimeType: string;
  width: number | null;
  height: number | null;
}

export interface CampusPostView {
  id: string;
  content: string;
  category: string;
  anonymousMode: string;
  campus: string;
  createdAt: Date;
  editedAt: Date | null;
  author: CampusAuthorBadgeView;
  media: CampusMediaView[];
  counts: {
    likes: number;
    comments: number;
    bookmarks: number;
  };
  viewer: {
    liked: boolean;
    bookmarked: boolean;
    isAuthor: boolean;
  };
}

export interface CampusCommentView {
  id: string;
  content: string;
  createdAt: Date;
  author: {
    alias: string;
    avatarSeed: number;
  };
  isMine: boolean;
}

/** A post is discoverable only while its author has not switched discovery off. */
export function isPostDiscoverable(post: CampusPost): boolean {
  return (
    post.anonymousMode === 'DISCOVERABLE' &&
    post.author?.discoveryEnabled === true &&
    Boolean(post.discoverToken)
  );
}

export function toCampusAuthorBadge(post: CampusPost): CampusAuthorBadgeView {
  const discoverable = isPostDiscoverable(post);
  return {
    alias: post.authorAlias || FALLBACK_ALIAS,
    avatarSeed: post.authorAvatarSeed || 1,
    discoverable,
    ...(discoverable && post.discoverToken
      ? { authorToken: post.discoverToken }
      : {}),
  };
}

/** Deterministic per-token subset so repeated fetches cannot be unioned. */
export function pickPublicInterests(
  interests: string[],
  token: string,
): string[] {
  return Array.from(new Set(interests.map((item) => item.trim()).filter(Boolean)))
    .map((item) => ({
      item,
      rank: createHash('sha256').update(`${token}:${item}`).digest('hex'),
    }))
    .sort((a, b) => (a.rank < b.rank ? -1 : a.rank > b.rank ? 1 : 0))
    .slice(0, MAX_PUBLIC_INTERESTS)
    .map((entry) => entry.item);
}

/**
 * Limited card for a discoverable post. Alias and avatar come from the post's
 * random persona. Age, mode, bio, name, school and exact interests are never
 * exposed; faculty, campus and a small interest sample only appear when the
 * author explicitly enabled them.
 */
export function toCampusAuthorProfile(
  post: CampusPost,
  author: CampusAuthor,
  profile: Profile | null,
): CampusAuthorProfileView {
  const token = post.discoverToken ?? '';
  return {
    token,
    alias: post.authorAlias || FALLBACK_ALIAS,
    avatarSeed: post.authorAvatarSeed || 1,
    bio: '',
    ...(profile && author.showFaculty && profile.faculty
      ? { faculty: profile.faculty }
      : {}),
    ...(profile && author.showCampus && profile.campus
      ? { campus: profile.campus }
      : {}),
    ...(profile && author.showInterests && profile.interests?.length
      ? { interests: pickPublicInterests(profile.interests, token) }
      : {}),
    discoverable: true,
  };
}

export type MediaUrlFn = (storageKey: string) => string;

const plainMediaUrl: MediaUrlFn = (key) => `/campus/media/${key}`;

export function toCampusPostView(
  post: CampusPost,
  viewer: { liked: boolean; bookmarked: boolean; isAuthor: boolean },
  mediaUrl: MediaUrlFn = plainMediaUrl,
): CampusPostView {
  return {
    id: post.id,
    content: post.content,
    category: post.category,
    anonymousMode: post.anonymousMode,
    // Legacy rows may still hold a campus; only publish it with consent.
    campus: post.author?.showCampus ? post.campus : '',
    createdAt: post.createdAt,
    editedAt: post.editedAt,
    author: toCampusAuthorBadge(post),
    media: (post.media ?? [])
      .slice()
      .sort((a, b) => a.position - b.position)
      .map((item) => toCampusMediaView(item, mediaUrl)),
    counts: {
      likes: post.likeCount,
      comments: post.commentCount,
      bookmarks: post.bookmarkCount,
    },
    viewer,
  };
}

export function toCampusMediaView(
  media: CampusPostMedia,
  mediaUrl: MediaUrlFn = plainMediaUrl,
): CampusMediaView {
  return {
    id: media.id,
    url: mediaUrl(media.storageKey),
    mimeType: media.mimeType,
    width: media.width,
    height: media.height,
  };
}

/** Comments use the thread persona stored on the comment, never the account one. */
export function toCampusCommentView(
  comment: CampusPostComment,
  viewerId: string,
): CampusCommentView {
  return {
    id: comment.id,
    content: comment.content,
    createdAt: comment.createdAt,
    author: {
      alias: comment.authorAlias || FALLBACK_ALIAS,
      avatarSeed: comment.authorAvatarSeed || 1,
    },
    isMine: comment.authorUserId === viewerId,
  };
}
