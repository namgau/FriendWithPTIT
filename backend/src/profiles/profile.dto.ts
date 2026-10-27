import { Transform, Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsBoolean,
  IsIn,
  IsInt,
  IsNumber,
  IsOptional,
  IsString,
  Length,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

const MODES = ['DATING', 'FRIENDS', 'STUDY'];
const GPA_TIERS = ['EXCELLENT', 'GOOD', 'FAIR', 'AVERAGE'];

export class UpdateProfileDto {
  @IsOptional()
  @IsString()
  @Length(1, 80)
  nickname?: string;

  @IsOptional()
  @IsString()
  @Length(1, 140)
  fullName?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(18)
  @Max(80)
  age?: number;

  @IsOptional()
  @IsIn(['MALE', 'FEMALE', 'OTHER'])
  gender?: string;

  @IsOptional()
  @IsString()
  @MaxLength(190)
  school?: string;

  @IsOptional()
  @IsString()
  @MaxLength(100)
  campus?: string;

  @IsOptional()
  @IsString()
  @MaxLength(140)
  faculty?: string;

  @IsOptional()
  @IsString()
  @MaxLength(140)
  major?: string;

  @IsOptional()
  @IsString()
  @MaxLength(40)
  academicYear?: string;

  @IsOptional()
  @IsBoolean()
  alumni?: boolean;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(0)
  @Max(4)
  gpa?: number;

  @IsOptional()
  @IsIn(GPA_TIERS)
  gpaTier?: string;

  @IsOptional()
  @IsString()
  @MaxLength(1000)
  bio?: string;

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(30)
  @IsString({ each: true })
  @MaxLength(48, { each: true })
  interests?: string[];

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  avatarSeed?: number;

  @IsOptional()
  @IsIn(MODES)
  mode?: string;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  lat?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  lng?: number;

  @IsOptional()
  @IsBoolean()
  showGpaPublic?: boolean;

  @IsOptional()
  @IsBoolean()
  showLocationPublic?: boolean;
}

export class DiscoverQueryDto {
  @IsOptional()
  @IsString()
  q?: string;

  @IsOptional()
  @IsString()
  modes?: string;

  @IsOptional()
  @IsString()
  gpaTiers?: string;

  @IsOptional()
  @IsString()
  school?: string;

  @IsOptional()
  @IsString()
  campus?: string;

  @IsOptional()
  @IsString()
  faculty?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(18)
  minAge?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Max(80)
  maxAge?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  maxDistanceKm?: number;

  @IsOptional()
  @Transform(({ value }) => value === true || value === 'true' || value === '1')
  @IsBoolean()
  onlyVerified?: boolean;

  @IsOptional()
  @IsIn(['compatibility', 'distance', 'newest'])
  sort?: 'compatibility' | 'distance' | 'newest';

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  limit?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  offset?: number;
}

export class NearbyQueryDto {
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(-90)
  @Max(90)
  lat?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(-180)
  @Max(180)
  lng?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  @Max(100)
  radiusKm?: number;
}
