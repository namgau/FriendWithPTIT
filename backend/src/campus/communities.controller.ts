import { Controller, Delete, Get, HttpCode, Param, Post, UseGuards } from '@nestjs/common';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import { CommunitiesService } from './communities.service';

@Controller('campus/communities')
@UseGuards(JwtAuthGuard)
export class CommunitiesController {
  constructor(private readonly communities: CommunitiesService) {}

  @Get()
  list(@CurrentUser() user: RequestUser) {
    return this.communities.list(user.userId);
  }

  @Post(':id/join')
  @HttpCode(200)
  join(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.communities.setJoined(user.userId, id, true);
  }

  @Delete(':id/join')
  leave(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.communities.setJoined(user.userId, id, false);
  }
}
