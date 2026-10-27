import { IsArray, IsOptional, IsString } from 'class-validator';

export class MarkReadDto {
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  ids?: string[];
}
