import { BadRequestException } from '@nestjs/common';
import { isUuid } from '../common/anonymity';

export interface ChatCursor { time: string; id?: string }

function validTime(time: unknown): boolean {
  if (typeof time !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}(\d{3})?Z$/.test(time)) return false;
  const date = new Date(time);
  return Number.isFinite(date.getTime()) && date.toISOString() === time.replace(/(\.\d{3})\d{3}Z$/, '$1Z');
}

export function encodeChatCursor(matchId: string, time: string, id: string): string {
  return 'v1.' + Buffer.from(JSON.stringify([matchId, time, id])).toString('base64url');
}

export function decodeChatCursor(value: string, matchId: string): ChatCursor {
  // Legacy timestamp callers remain accepted. New clients must reuse the opaque nextBefore.
  if (validTime(value)) return { time: value };
  try {
    if (value.length > 512 || !/^v1\.[A-Za-z0-9_-]+$/.test(value)) throw new Error();
    const encoded = value.slice(3);
    const bytes = Buffer.from(encoded, 'base64url');
    if (bytes.toString('base64url') !== encoded) throw new Error();
    const data = JSON.parse(bytes.toString('utf8'));
    if (!Array.isArray(data) || data.length !== 3 || data[0] !== matchId || !validTime(data[1]) || typeof data[2] !== 'string' || !isUuid(data[2])) throw new Error();
    return { time: data[1], id: data[2] };
  } catch {
    throw new BadRequestException('Invalid message cursor');
  }
}
