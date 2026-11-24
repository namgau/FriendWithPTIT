import { CommunitiesController } from './communities.controller';
import { CommunitiesService } from './communities.service';
import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  Block,
  CampusAuthor,
  CampusCommentReport,
  CampusCommunityMember,
  CampusPost,
  CampusPostBookmark,
  CampusPostComment,
  CampusPostLike,
  CampusPostMedia,
  CampusPostReport,
  Profile,
} from '../database/entities';
import { RateLimitGuard } from '../common/rate-limit';
import { SocialModule } from '../social/social.module';
import {
  CampusController,
  CampusMediaController,
  CampusModerationController,
} from './campus.controller';
import { CampusMediaStorage } from './campus-media.storage';
import { CampusModeratorGuard } from './campus-moderator.guard';
import { CampusService } from './campus.service';
import { CampusStore } from './campus.store';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      CampusAuthor,
      CampusPost,
      CampusPostMedia,
      CampusPostLike,
      CampusPostComment,
      CampusPostBookmark,
      CampusPostReport,
      CampusCommentReport,
      Profile,
      Block,
      CampusCommunityMember,
    ]),
    SocialModule,
  ],
  controllers: [CampusController, CampusModerationController, CampusMediaController, CommunitiesController],
  providers: [
    CommunitiesService,
    CampusService,
    CampusStore,
    CampusMediaStorage,
    CampusModeratorGuard,
    RateLimitGuard,
  ],
  exports: [CampusService],
})
export class CampusModule {}
