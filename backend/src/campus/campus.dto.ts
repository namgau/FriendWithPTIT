import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsBoolean,
  IsIn,
  IsInt,
  IsOptional,
  IsString,
  Length,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator';

export const CAMPUS_CATEGORIES = [
  'GENERAL',
  'STUDY',
  'EVENT',
  'CONFESSION',
  'MARKET',
  'QUESTION',
] as const;

export const CAMPUS_ANONYMOUS_MODES = [
  'FULLY_ANONYMOUS',
  'DISCOVERABLE',
] as const;

export const CAMPUS_REPORT_REASONS = [
  'HARASSMENT',
  'HATE_SPEECH',
  'SPAM',
  'SEXUAL_CONTENT',
  'VIOLENCE',
  'PRIVACY',
  'OTHER',
] as const;

export class CampusImageDto {
  // 4 MiB of image bytes is about 5.6M base64 characters plus the header.
  @IsString()
  @MaxLength(6_000_000)
  dataUrl: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(20_000)
  width?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(20_000)
  height?: number;
}

export class CreateCampusPostDto {
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  content?: string;

  @IsIn(CAMPUS_CATEGORIES)
  category: (typeof CAMPUS_CATEGORIES)[number];

  @IsIn(CAMPUS_ANONYMOUS_MODES)
  anonymousMode: (typeof CAMPUS_ANONYMOUS_MODES)[number];

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(4)
  @ValidateNested({ each: true })
  @Type(() => CampusImageDto)
  images?: CampusImageDto[];
}

export class ListCampusQueryDto {
  @IsOptional()
  @IsIn(['for-you', 'saved'])
  tab?: 'for-you' | 'saved';

  @IsOptional()
  @IsIn(CAMPUS_CATEGORIES)
  category?: (typeof CAMPUS_CATEGORIES)[number];

  @IsOptional()
  @IsString()
  @MaxLength(100)
  campus?: string;

  @IsOptional()
  @IsString()
  @MaxLength(512)
  cursor?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(30)
  limit?: number;
}

export class CreateCampusCommentDto {
  @IsString()
  @Length(1, 600)
  content: string;
}

export class CampusReportDto {
  @IsIn(CAMPUS_REPORT_REASONS)
  reason: (typeof CAMPUS_REPORT_REASONS)[number];

  @IsOptional()
  @IsString()
  @MaxLength(500)
  details?: string;
}

export class CampusSwipeDto {
  @IsIn(['LIKE', 'PASS', 'SUPER_LIKE'])
  action: 'LIKE' | 'PASS' | 'SUPER_LIKE';
}

export class PageQueryDto {
  @IsOptional()
  @IsString()
  @MaxLength(512)
  cursor?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(30)
  limit?: number;
}

export class ModerationQueueQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number;
}

/** Only text and category are editable; privacy mode and images are not. */
export class UpdateCampusPostDto {
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  content?: string;

  @IsOptional()
  @IsIn(CAMPUS_CATEGORIES)
  category?: (typeof CAMPUS_CATEGORIES)[number];
}

export class UpdateCampusSettingsDto {
  @IsOptional()
  @IsBoolean()
  discoveryEnabled?: boolean;

  @IsOptional()
  @IsBoolean()
  showFaculty?: boolean;

  @IsOptional()
  @IsBoolean()
  showCampus?: boolean;

  @IsOptional()
  @IsBoolean()
  showInterests?: boolean;
}

export class ModerationDecisionDto {
  @IsIn(['RESTORE', 'REMOVE'])
  action: 'RESTORE' | 'REMOVE';
}
