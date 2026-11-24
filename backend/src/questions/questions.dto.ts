import { ArrayMaxSize, IsArray, IsInt, IsOptional, IsString, Max, MaxLength, Min } from 'class-validator';

export class AnswerQuestionDto {
  /** Chỉ số đáp án ; -1 khi tự viết (kèm text). */
  @IsInt()
  @Min(-1)
  @Max(9)
  answer: number;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  text?: string;

  @IsArray()
  @ArrayMaxSize(10)
  @IsInt({ each: true })
  @Min(0, { each: true })
  @Max(9, { each: true })
  acceptable: number[];
}
