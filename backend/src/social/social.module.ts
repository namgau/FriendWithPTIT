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
  User,
} from '../database/entities';
import { SocialController } from './social.controller';
import { SocialService } from './social.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      User,
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
