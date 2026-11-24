import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { QuestionAnswer } from '../database/entities';
import { AnswerQuestionDto } from './questions.dto';
import { QUESTION_BANK, QUESTION_BY_ID } from './questions.bank';

@Injectable()
export class QuestionsService {
  constructor(
    @InjectRepository(QuestionAnswer) private readonly answers: Repository<QuestionAnswer>,
  ) {}

  async list(userId: string) {
    const mine = new Map((await this.answers.find({ where: { userId } })).map((a) => [a.questionId, a]));
    return {
      answered: mine.size,
      items: QUESTION_BANK.map((q) => ({
        ...q,
        myAnswer: mine.get(q.id)?.answer ?? null,
        myAcceptable: mine.get(q.id)?.acceptable ?? [],
      })),
    };
  }

  async answer(userId: string, questionId: string, dto: AnswerQuestionDto) {
    const question = QUESTION_BY_ID.get(questionId);
    if (!question) throw new NotFoundException('Question not found');
    const n = question.options.length;
    if (dto.answer >= n || dto.acceptable.some((i) => i >= n)) {
      throw new BadRequestException('Answer out of range');
    }
    // Your own answer is always acceptable to you; empty set means "any answer".
    const acceptable = dto.acceptable.length
      ? Array.from(new Set([...dto.acceptable, dto.answer])).sort()
      : question.options.map((_, i) => i);
    const row = (await this.answers.findOne({ where: { userId, questionId } }))
      ?? this.answers.create({ userId, questionId });
    row.answer = dto.answer;
    row.acceptable = acceptable;
    await this.answers.save(row);
    return this.list(userId);
  }
}
