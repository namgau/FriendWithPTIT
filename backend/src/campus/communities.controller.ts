import { Body, Controller, Delete, Get, HttpCode, Param, Post, UseGuards } from '@nestjs/common';
import { IsBoolean, IsOptional, IsString, MaxLength } from 'class-validator';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import { CommunitiesService } from './communities.service';

export class CreateCommunityDto {
  @IsString()
  @MaxLength(60)
  name: string;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  description?: string;

  @IsOptional()
  @IsBoolean()
  requiresApproval?: boolean;
}

export class DecideRequestDto {
  @IsBoolean()
  approve: boolean;
}

@Controller('campus/communities')
@UseGuards(JwtAuthGuard)
export class CommunitiesController {
  constructor(private readonly communities: CommunitiesService) {}

  @Get()
  list(@CurrentUser() user: RequestUser) {
    return this.communities.list(user.userId);
  }

  @Post()
  @HttpCode(200)
  create(@CurrentUser() user: RequestUser, @Body() dto: CreateCommunityDto) {
    return this.communities.create(user.userId, dto);
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

  @Get(':id/requests')
  requests(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.communities.requests(user.userId, id);
  }

  @Post(':id/requests/:userId')
  @HttpCode(200)
  decide(@CurrentUser() user: RequestUser, @Param('id') id: string, @Param('userId') memberId: string, @Body() dto: DecideRequestDto) {
    return this.communities.decide(user.userId, id, memberId, dto.approve);
  }
}
