import {
  IsBoolean,
  IsIn,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator';

export class SwipeDto {
  @IsString()
  targetUserId: string;

  @IsIn(['LIKE', 'PASS', 'SUPER_LIKE'])
  action: 'LIKE' | 'PASS' | 'SUPER_LIKE';
}

export class RevealDto {
  @IsBoolean()
  enabled: boolean;
}

export class ReportDto {
  @IsString()
  @MaxLength(64)
  reason: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  details?: string;
}
