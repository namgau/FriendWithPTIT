import { Body, Controller, Get, Param, Put, UseGuards } from '@nestjs/common';
import { CurrentUser, RequestUser } from '../common/request-user';
import { JwtAuthGuard } from '../common/jwt-auth.guard';
import { AnswerQuestionDto } from './questions.dto';
import { QuestionsService } from './questions.service';

@Controller('questions')
@UseGuards(JwtAuthGuard)
export class QuestionsController {
  constructor(private readonly questions: QuestionsService) {}

  @Get()
  list(@CurrentUser() user: RequestUser) {
    return this.questions.list(user.userId);
  }

  @Put(':id')
  answer(@CurrentUser() user: RequestUser, @Param('id') id: string, @Body() dto: AnswerQuestionDto) {
    return this.questions.answer(user.userId, id, dto);
  }
}
