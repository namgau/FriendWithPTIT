import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  Block,
  Match,
  Message,
  Notification,
  Profile,
  Report,
  Swipe,
} from '../database/entities';
import { SocialController } from './social.controller';
import { SocialService } from './social.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      Swipe,
      Match,
      Profile,
      Message,
      Notification,
      Block,
      Report,
    ]),
  ],
  controllers: [SocialController],
  providers: [SocialService],
  exports: [SocialService],
})
export class SocialModule {}
