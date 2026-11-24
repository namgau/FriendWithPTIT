import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { CampusMediaStorage } from '../campus/campus-media.storage';
import { Block, Profile, QuestionAnswer, Swipe } from '../database/entities';
import { ProfilePhotosController, ProfilesController } from './profiles.controller';
import { ProfilesService } from './profiles.service';

@Module({
  imports: [TypeOrmModule.forFeature([Profile, Swipe, Block, QuestionAnswer])],
  // Photos route first so `profiles/photos/:file` is not captured by `profiles/:id`.
  controllers: [ProfilePhotosController, ProfilesController],
  providers: [ProfilesService, CampusMediaStorage],
  exports: [ProfilesService],
})
export class ProfilesModule {}
