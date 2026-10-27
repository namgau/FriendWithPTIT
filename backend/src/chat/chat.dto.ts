import {
  IsIn,
  IsObject,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator';

export class SendMessageDto {
  @IsString()
  @MaxLength(2000)
  text: string;

  @IsOptional()
  @IsIn(['TEXT', 'IMAGE'])
  type?: 'TEXT' | 'IMAGE';

  @IsOptional()
  @IsObject()
  meta?: Record<string, unknown>;
}
