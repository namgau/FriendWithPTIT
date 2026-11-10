import { Match, Message } from '../database/entities';

export const MATCH_MIN_MESSAGES = 30;
export const MATCH_MIN_DAYS = 3;

export function revealProgress(match: Pick<Match, 'userAId' | 'userBId'>,
  messages: Pick<Message, 'senderId' | 'createdAt' | 'type'>[]) {
  const chat = messages.filter((m) => m.type !== 'SYSTEM' && (m.senderId === match.userAId || m.senderId === match.userBId));
  const a = chat.filter((m) => m.senderId === match.userAId).length;
  const b = chat.filter((m) => m.senderId === match.userBId).length;
  const days = new Set(chat.map((m) => m.createdAt?.getTime()).filter((t): t is number => Number.isFinite(t) && t! > 0)
    .map((t) => Math.floor((t + 7 * 3_600_000) / 86_400_000))).size;
  return { effectiveMessages: 2 * Math.min(a, b), days };
}

/**
 * Matches that originate from anonymous Campus Feed discovery keep the
 * counterpart masked until both sides opted in to reveal and the existing
 * reveal thresholds are met. Ordinary matches are never masked.
 */
export function isIdentityRevealed(
  match: Pick<
    Match,
    'anonymous' | 'revealOptInA' | 'revealOptInB'
  >,
  progress: { effectiveMessages: number; days: number },
): boolean {
  if (!match.anonymous) return true;
  return (
    match.revealOptInA === true &&
    match.revealOptInB === true &&
    progress.effectiveMessages >= MATCH_MIN_MESSAGES &&
    progress.days >= MATCH_MIN_DAYS
  );
}

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_RE.test(value);
}

export const MASKED_ID_PREFIX = 'anon:';

/** Opaque stand-in for the counterpart's user/profile id in masked matches. */
export function maskedIdentityId(matchId: string): string {
  return `${MASKED_ID_PREFIX}${matchId}`;
}

/** Stable, non-identifying avatar seed (1..20) derived from the match id. */
export function maskedAvatarSeed(matchId: string): number {
  let sum = 0;
  for (let i = 0; i < matchId.length; i += 1) sum += matchId.charCodeAt(i);
  return (sum % 20) + 1;
}
