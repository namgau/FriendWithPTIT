import {
  CanActivate,
  ExecutionContext,
  HttpException,
  HttpStatus,
  Injectable,
  SetMetadata,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';

export interface RateLimitRule {
  /** Bucket name, so different actions do not share a budget. */
  key: string;
  limit: number;
  windowMs: number;
}

export const RATE_LIMIT_KEY = 'fwp:rate-limit';
export const RateLimit = (rule: RateLimitRule) => SetMetadata(RATE_LIMIT_KEY, rule);

/**
 * Process-wide so every route and every guard instance shares one budget.
 * In-memory: with several API replicas the effective limit is per replica.
 */
const hits = new Map<string, number[]>();
let calls = 0;

export function resetRateLimits(): void {
  hits.clear();
  calls = 0;
}

function sweep(now: number): void {
  for (const [key, stamps] of hits) {
    if (!stamps.length || now - stamps[stamps.length - 1] > 3_600_000) hits.delete(key);
  }
}

@Injectable()
export class RateLimitGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const rule = this.reflector.get<RateLimitRule | undefined>(
      RATE_LIMIT_KEY,
      context.getHandler(),
    );
    if (!rule) return true;
    const http = context.switchToHttp();
    const request = http.getRequest<{ user?: { userId: string }; ip?: string }>();
    const response = http.getResponse<{ setHeader?: (n: string, v: string) => void }>();
    const subject = request.user?.userId ?? request.ip ?? 'anonymous';
    const bucket = `${rule.key}:${subject}`;
    const now = Date.now();

    calls += 1;
    if (calls % 500 === 0 || hits.size > 20_000) sweep(now);

    const recent = (hits.get(bucket) ?? []).filter((t) => now - t < rule.windowMs);
    if (recent.length >= rule.limit) {
      const retryAfter = Math.max(1, Math.ceil((recent[0] + rule.windowMs - now) / 1000));
      response.setHeader?.('Retry-After', String(retryAfter));
      hits.set(bucket, recent);
      throw new HttpException('Too many requests', HttpStatus.TOO_MANY_REQUESTS);
    }
    recent.push(now);
    hits.set(bucket, recent);
    return true;
  }
}
