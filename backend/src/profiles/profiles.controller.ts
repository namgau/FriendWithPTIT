import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  ParseIntPipe,
  Post,
  Put,
  Query,
  Res,
  UseGuards,
} from '@nestjs/common';
import type { Response } from 'express';
import { CampusMediaStorage } from '../campus/campus-media.storage';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import {
  AddPhotoDto,
  DiscoverQueryDto,
  NearbyQueryDto,
  UpdateProfileDto,
} from './profile.dto';
import { ProfilesService } from './profiles.service';

@Controller('profiles')
@UseGuards(JwtAuthGuard)
export class ProfilesController {
  constructor(private readonly profiles: ProfilesService) {}

  @Get('me')
  own(@CurrentUser() user: RequestUser) {
    return this.profiles.own(user.userId);
  }

  @Put('me')
  update(
    @CurrentUser() user: RequestUser,
    @Body() dto: UpdateProfileDto,
  ) {
    return this.profiles.updateOwn(user.userId, dto);
  }

  @Post('me/photos')
  addPhoto(@CurrentUser() user: RequestUser, @Body() dto: AddPhotoDto) {
    return this.profiles.addPhoto(user.userId, dto.image);
  }

  @Delete('me/photos/:index')
  removePhoto(@CurrentUser() user: RequestUser, @Param('index', ParseIntPipe) index: number) {
    return this.profiles.removePhoto(user.userId, index);
  }

  @Get('discover')
  discover(
    @CurrentUser() user: RequestUser,
    @Query() query: DiscoverQueryDto,
  ) {
    return this.profiles.discover(user.userId, query);
  }

  @Get('incoming-likes')
  incomingLikes(@CurrentUser() user: RequestUser) {
    return this.profiles.incomingLikes(user.userId);
  }

  @Get('nearby')
  nearby(
    @CurrentUser() user: RequestUser,
    @Query() query: NearbyQueryDto,
  ) {
    return this.profiles.nearby(user.userId, query);
  }

  @Get(':id')
  detail(@CurrentUser() user: RequestUser, @Param('id') id: string) {
    return this.profiles.detail(user.userId, id);
  }
}

/**
 * Ảnh hồ sơ: Coil không gửi được bearer token, nên phục vụ không cần ký nhưng chỉ
 * với file đang nằm trong danh sách ảnh của một hồ sơ (tên file là UUID ngẫu nhiên).
 * shortcut: ai có link vẫn xem được ảnh gốc dù app làm mờ ; cần URL ký như Campus nếu phát hành thật.
 */
@Controller('profiles/photos')
export class ProfilePhotosController {
  constructor(
    private readonly profiles: ProfilesService,
    private readonly storage: CampusMediaStorage,
  ) {}

  @Get(':file')
  async serve(@Param('file') file: string, @Res() response: Response) {
    if (!(await this.profiles.isPublicPhoto(file))) {
      return response.status(404).json({ message: 'Photo not found' });
    }
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.sendFile(this.storage.resolvePublicFile(file), { headers: { 'Cache-Control': 'private, max-age=3600' } });
  }
}
