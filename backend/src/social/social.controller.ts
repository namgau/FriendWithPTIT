import {
  Body,
  Controller,
  Get,
  HttpCode,
  Param,
  Post,
  UseGuards,
} from '@nestjs/common';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import { ReportDto, RevealDto, SwipeDto } from './social.dto';
import { SocialService } from './social.service';

@Controller()
@UseGuards(JwtAuthGuard)
export class SocialController {
  constructor(private readonly social: SocialService) {}

  @Post('swipes')
  @HttpCode(200)
  swipe(@CurrentUser() user: RequestUser, @Body() dto: SwipeDto) {
    return this.social.swipe(user.userId, dto);
  }

  @Get('matches')
  matches(@CurrentUser() user: RequestUser) {
    return this.social.list(user.userId);
  }

  @Get('matches/:id')
  match(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.social.detail(user.userId, id);
  }

  @Post('matches/:id/reveal')
  @HttpCode(200)
  reveal(
    @CurrentUser() user: RequestUser,
    @Param('id') id: string,
    @Body() dto: RevealDto,
  ) {
    return this.social.reveal(user.userId, id, dto);
  }

  @Post('matches/:id/unmatch')
  @HttpCode(200)
  unmatch(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.social.unmatch(user.userId, id);
  }

  @Post('matches/:id/report')
  @HttpCode(200)
  report(
    @CurrentUser() user: RequestUser,
    @Param('id') id: string,
    @Body() dto: ReportDto,
  ) {
    return this.social.report(user.userId, id, dto);
  }

  @Post('users/:id/block')
  @HttpCode(200)
  block(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.social.block(user.userId, id);
  }
}
