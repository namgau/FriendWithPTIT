import { Match } from '../database/entities';

export const MATCH_MIN_MESSAGES = 8;
export const MATCH_MIN_SCORE = 72;

/**
 * Matches that originate from anonymous Campus Feed discovery keep the
 * counterpart masked until both sides opted in to reveal and the existing
 * reveal thresholds are met. Ordinary matches are never masked.
 */
export function isIdentityRevealed(
  match: Pick<
    Match,
    'anonymous' | 'revealOptInA' | 'revealOptInB' | 'compatibilityScore'
  >,
  messageCount: number,
): boolean {
  if (!match.anonymous) return true;
  return (
    match.revealOptInA === true &&
    match.revealOptInB === true &&
    messageCount >= MATCH_MIN_MESSAGES &&
    match.compatibilityScore >= MATCH_MIN_SCORE
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
