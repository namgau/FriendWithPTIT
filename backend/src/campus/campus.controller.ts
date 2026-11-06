import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
  Req,
  Res,
  UseGuards,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import { RateLimit, RateLimitGuard } from '../common/rate-limit';
import {
  CampusReportDto,
  CampusSwipeDto,
  CreateCampusCommentDto,
  CreateCampusPostDto,
  ListCampusQueryDto,
  ModerationDecisionDto,
  ModerationQueueQueryDto,
  PageQueryDto,
  UpdateCampusPostDto,
  UpdateCampusSettingsDto,
} from './campus.dto';
import { CampusMediaStorage } from './campus-media.storage';
import { CampusModeratorGuard } from './campus-moderator.guard';
import { CampusService } from './campus.service';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const uuid = () => new ParseUUIDPipe({ errorHttpStatusCode: 404 });
// Unknown ids are "not found" rather than "malformed" so ids cannot be probed.

@Controller('campus')
@UseGuards(JwtAuthGuard, RateLimitGuard)
export class CampusController {
  constructor(private readonly campus: CampusService) {}

  // ---------------------------------------------------------------- posts

  @Get('posts')
  @RateLimit({ key: 'campus-read', limit: 120, windowMs: MINUTE })
  list(@CurrentUser() user: RequestUser, @Query() query: ListCampusQueryDto) {
    return this.campus.list(user.userId, query);
  }

  @Get('posts/mine')
  @RateLimit({ key: 'campus-read', limit: 120, windowMs: MINUTE })
  mine(@CurrentUser() user: RequestUser, @Query() query: PageQueryDto) {
    return this.campus.listMine(user.userId, query);
  }

  @Post('posts')
  @HttpCode(201)
  @RateLimit({ key: 'campus-post', limit: 10, windowMs: HOUR })
  create(@CurrentUser() user: RequestUser, @Body() dto: CreateCampusPostDto) {
    return this.campus.create(user.userId, dto);
  }

  @Get('posts/:id')
  @RateLimit({ key: 'campus-read', limit: 120, windowMs: MINUTE })
  detail(@CurrentUser() user: RequestUser, @Param('id', uuid()) id: string) {
    return this.campus.detail(user.userId, id);
  }

  @Patch('posts/:id')
  @RateLimit({ key: 'campus-edit', limit: 30, windowMs: HOUR })
  update(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Body() dto: UpdateCampusPostDto,
  ) {
    return this.campus.update(user.userId, id, dto);
  }

  @Delete('posts/:id')
  @RateLimit({ key: 'campus-delete', limit: 30, windowMs: HOUR })
  removePost(@CurrentUser() user: RequestUser, @Param('id', uuid()) id: string) {
    return this.campus.removePost(user.userId, id);
  }

  // ------------------------------------------------------------ reactions

  @Post('posts/:id/like')
  @HttpCode(200)
  @RateLimit({ key: 'campus-react', limit: 120, windowMs: MINUTE })
  like(@CurrentUser() user: RequestUser, @Param('id', uuid()) id: string) {
    return this.campus.like(user.userId, id);
  }

  @Delete('posts/:id/like')
  @RateLimit({ key: 'campus-react', limit: 120, windowMs: MINUTE })
  unlike(@CurrentUser() user: RequestUser, @Param('id', uuid()) id: string) {
    return this.campus.unlike(user.userId, id);
  }

  @Post('posts/:id/bookmark')
  @HttpCode(200)
  @RateLimit({ key: 'campus-react', limit: 120, windowMs: MINUTE })
  bookmark(@CurrentUser() user: RequestUser, @Param('id', uuid()) id: string) {
    return this.campus.bookmark(user.userId, id);
  }

  @Delete('posts/:id/bookmark')
  @RateLimit({ key: 'campus-react', limit: 120, windowMs: MINUTE })
  unbookmark(@CurrentUser() user: RequestUser, @Param('id', uuid()) id: string) {
    return this.campus.unbookmark(user.userId, id);
  }

  // ------------------------------------------------------------- comments

  @Get('posts/:id/comments')
  @RateLimit({ key: 'campus-read', limit: 120, windowMs: MINUTE })
  listComments(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Query() query: PageQueryDto,
  ) {
    return this.campus.listComments(user.userId, id, query);
  }

  @Post('posts/:id/comments')
  @HttpCode(201)
  @RateLimit({ key: 'campus-comment', limit: 30, windowMs: 10 * MINUTE })
  addComment(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Body() dto: CreateCampusCommentDto,
  ) {
    return this.campus.addComment(user.userId, id, dto);
  }

  @Delete('posts/:id/comments/:commentId')
  @RateLimit({ key: 'campus-comment-delete', limit: 60, windowMs: 10 * MINUTE })
  removeComment(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Param('commentId', uuid()) commentId: string,
  ) {
    return this.campus.removeComment(user.userId, id, commentId);
  }

  @Post('posts/:id/comments/:commentId/report')
  @HttpCode(200)
  @RateLimit({ key: 'campus-report', limit: 20, windowMs: HOUR })
  reportComment(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Param('commentId', uuid()) commentId: string,
    @Body() dto: CampusReportDto,
  ) {
    return this.campus.reportComment(user.userId, id, commentId, dto);
  }

  @Post('posts/:id/report')
  @HttpCode(200)
  @RateLimit({ key: 'campus-report', limit: 20, windowMs: HOUR })
  report(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Body() dto: CampusReportDto,
  ) {
    return this.campus.report(user.userId, id, dto);
  }

  // ------------------------------------------------------------- settings

  @Get('settings')
  @RateLimit({ key: 'campus-read', limit: 120, windowMs: MINUTE })
  settings(@CurrentUser() user: RequestUser) {
    return this.campus.getSettings(user.userId);
  }

  @Patch('settings')
  @RateLimit({ key: 'campus-settings', limit: 30, windowMs: HOUR })
  updateSettings(
    @CurrentUser() user: RequestUser,
    @Body() dto: UpdateCampusSettingsDto,
  ) {
    return this.campus.updateSettings(user.userId, dto);
  }

  // ------------------------------------------------------ discover author

  @Get('authors/:token')
  @RateLimit({ key: 'campus-author', limit: 60, windowMs: MINUTE })
  author(@CurrentUser() user: RequestUser, @Param('token') token: string) {
    return this.campus.authorDetail(user.userId, token);
  }

  @Post('authors/:token/swipes')
  @HttpCode(200)
  @RateLimit({ key: 'campus-swipe', limit: 60, windowMs: 10 * MINUTE })
  swipe(
    @CurrentUser() user: RequestUser,
    @Param('token') token: string,
    @Body() dto: CampusSwipeDto,
  ) {
    return this.campus.swipeAuthor(user.userId, token, dto);
  }
}

/** Reachable only with CAMPUS_MODERATOR_USER_IDS; closed by default. */
@Controller('campus/moderation')
@UseGuards(JwtAuthGuard, CampusModeratorGuard, RateLimitGuard)
export class CampusModerationController {
  constructor(private readonly campus: CampusService) {}

  @Get('queue')
  @RateLimit({ key: 'campus-mod', limit: 120, windowMs: MINUTE })
  queue(@Query() query: ModerationQueueQueryDto) {
    return this.campus.moderationQueue(query.limit);
  }

  @Post('posts/:id')
  @HttpCode(200)
  @RateLimit({ key: 'campus-mod', limit: 120, windowMs: MINUTE })
  decidePost(
    @CurrentUser() user: RequestUser,
    @Param('id', uuid()) id: string,
    @Body() dto: ModerationDecisionDto,
  ) {
    return this.campus.decidePost(user.userId, id, dto);
  }

  @Post('comments/:id')
  @HttpCode(200)
  @RateLimit({ key: 'campus-mod', limit: 120, windowMs: MINUTE })
  decideComment(
    @Param('id', uuid()) id: string,
    @Body() dto: ModerationDecisionDto,
  ) {
    return this.campus.decideComment(id, dto);
  }
}

/**
 * Image links are signed and expire (see CampusMediaStorage.urlFor). Image
 * loaders such as Coil cannot attach a bearer token, so the signature is the
 * access grant. Unsigned, forged or expired links are indistinguishable 404s.
 */
@Controller('campus/media')
@UseGuards(RateLimitGuard)
export class CampusMediaController {
  constructor(private readonly storage: CampusMediaStorage) {}

  @Get(':file')
  @RateLimit({ key: 'campus-media', limit: 600, windowMs: MINUTE })
  serve(
    @Param('file') file: string,
    @Query('exp') exp: string,
    @Query('sig') sig: string,
    @Req() _request: Request,
    @Res() response: Response,
  ) {
    if (!this.storage.verifySignature(file, exp, sig)) {
      return response.status(404).json({ message: 'Media not found' });
    }
    const fullPath = this.storage.resolvePublicFile(file);
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('Content-Security-Policy', "default-src 'none'; sandbox");
    response.sendFile(
      fullPath,
      {
        headers: {
          // The URL itself is a bearer credential: keep it out of shared caches.
          'Cache-Control': 'private, max-age=3600',
        },
      },
      (error) => {
        if (error && !response.headersSent) {
          response.status(404).json({ message: 'Media not found' });
        }
      },
    );
  }
}
