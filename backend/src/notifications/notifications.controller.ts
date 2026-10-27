import {
  Body,
  Controller,
  Get,
  HttpCode,
  Post,
  UseGuards,
} from '@nestjs/common';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import { MarkReadDto } from './notifications.dto';
import { NotificationsService } from './notifications.service';

@Controller('notifications')
@UseGuards(JwtAuthGuard)
export class NotificationsController {
  constructor(private readonly notifications: NotificationsService) {}

  @Get()
  list(@CurrentUser() user: RequestUser) {
    return this.notifications.list(user.userId);
  }

  @Get('unread-count')
  unread(@CurrentUser() user: RequestUser) {
    return this.notifications.unreadCount(user.userId);
  }

  @Post('read')
  @HttpCode(200)
  markRead(@CurrentUser() user: RequestUser, @Body() dto: MarkReadDto) {
    return this.notifications.markRead(user.userId, dto.ids);
  }
}
