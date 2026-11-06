import {
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { RequestUser } from '../common/request-user';

/**
 * Moderation endpoints are closed unless CAMPUS_MODERATOR_USER_IDS (comma
 * separated user ids) is configured. Runs after JwtAuthGuard.
 */
@Injectable()
export class CampusModeratorGuard implements CanActivate {
  private readonly allowed: Set<string>;

  constructor(config: ConfigService) {
    this.allowed = new Set(
      (config.get<string>('CAMPUS_MODERATOR_USER_IDS') ?? '')
        .split(',')
        .map((item) => item.trim())
        .filter(Boolean),
    );
  }

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<{ user?: RequestUser }>();
    const userId = request.user?.userId;
    if (!userId || !this.allowed.has(userId)) {
      throw new ForbiddenException('Moderator access required');
    }
    return true;
  }
}
