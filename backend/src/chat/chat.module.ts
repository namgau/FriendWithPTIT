import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Match, Message, Notification, Profile } from '../database/entities';
import { ChatController } from './chat.controller';
import { ChatService } from './chat.service';

@Module({
  imports: [TypeOrmModule.forFeature([Message, Match, Profile, Notification])],
  controllers: [ChatController],
  providers: [ChatService],
})
export class ChatModule {}
