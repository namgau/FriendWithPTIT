import {
  Body,
  Controller,
  Get,
  Param,
  Put,
  Query,
  UseGuards,
} from '@nestjs/common';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import {
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
