import {
  IsEmail,
  IsString,
  Matches,
  MaxLength,
  MinLength,
} from 'class-validator';

export class RegisterDto {
  @IsEmail()
  @MaxLength(190)
  email: string;

  @IsString()
  @MinLength(8)
  @MaxLength(128)
  password: string;
}

export class LoginDto {
  @IsEmail()
  @MaxLength(190)
  email: string;

  @IsString()
  @MinLength(1)
  @MaxLength(128)
  password: string;
}

export class OtpDto {
  @IsEmail()
  @MaxLength(190)
  email: string;

  @IsString()
  @Matches(/^\d{6}$/, { message: 'OTP must contain 6 digits' })
  code: string;
}
