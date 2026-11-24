import { ArrayMaxSize, IsArray, IsInt, Max, Min } from 'class-validator';

export class AnswerQuestionDto {
  @IsInt()
  @Min(0)
  @Max(9)
  answer: number;

  @IsArray()
  @ArrayMaxSize(10)
  @IsInt({ each: true })
  @Min(0, { each: true })
  @Max(9, { each: true })
  acceptable: number[];
}
