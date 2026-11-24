import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import {
  Match,
  Message,
  Notification,
  Profile,
  QuestionAnswer,
  Swipe,
  User,
} from '../database/entities';
import { SeedService } from './seed.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      User,
      Profile,
      Swipe,
      Match,
      Message,
      Notification,
      QuestionAnswer,
    ]),
  ],
  providers: [SeedService],
})
export class SeedModule {}
