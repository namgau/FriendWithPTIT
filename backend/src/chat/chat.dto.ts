import {
  IsIn,
  IsObject,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
} from 'class-validator';

export class SendMessageDto {
  /** Reuse the same UUID for every retry of one logical send. */
  @IsOptional()
  @IsUUID()
  clientMessageId?: string;

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
