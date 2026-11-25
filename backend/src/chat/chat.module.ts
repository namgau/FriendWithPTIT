import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Match, Message, Notification, Profile, User } from '../database/entities';
import { ChatController } from './chat.controller';
import { BotService } from './bot.service';
import { ChatService } from './chat.service';

@Module({
  imports: [TypeOrmModule.forFeature([Message, Match, Profile, Notification, User])],
  controllers: [ChatController],
  providers: [ChatService, BotService],
})
export class ChatModule {}
