import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { randomUUID } from 'crypto';
import { Repository } from 'typeorm';
import { Profile, User } from '../database/entities';
import { LoginDto, OtpDto, RegisterDto } from './auth.dto';

export const DEMO_EMAIL = 'demo@ptit.edu.vn';
export const DEMO_OTP = '123456';
export const DEMO_PASSWORD = 'Fwp@123456';

@Injectable()
export class AuthService {
  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(Profile) private readonly profiles: Repository<Profile>,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {}

  async register(dto: RegisterDto) {
    const email = this.normaliseEmail(dto.email);
    if (await this.users.exist({ where: { email } })) {
      throw new ConflictException('Email already registered');
    }
    const user = await this.users.save(
      this.users.create({
        email,
        passwordHash: await bcrypt.hash(dto.password, 12),
        isDemo: false,
      }),
    );
    await this.createEmptyProfile(user);
    return this.session(user);
  }

  async login(dto: LoginDto) {
    const email = this.normaliseEmail(dto.email);
    const user = await this.users.findOne({ where: { email } });
    if (!user || !(await bcrypt.compare(dto.password, user.passwordHash))) {
      throw new UnauthorizedException('Invalid email or password');
    }
    return this.session(user);
  }

  async verifyOtp(dto: OtpDto) {
    const expected = this.config.get<string>('DEMO_OTP', DEMO_OTP);
    if (dto.code !== expected) {
      throw new UnauthorizedException('Invalid OTP');
    }
    const email = this.normaliseEmail(dto.email);
    let user = await this.users.findOne({ where: { email } });
    if (!user) {
      user = await this.users.save(
        this.users.create({
          email,
          passwordHash: await bcrypt.hash(randomUUID(), 12),
          isDemo: email === DEMO_EMAIL,
        }),
      );
      await this.createEmptyProfile(user);
    }
    return this.session(user);
  }

  async demo() {
    return this.verifyOtp({ email: DEMO_EMAIL, code: DEMO_OTP });
  }

  async me(userId: string) {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) throw new UnauthorizedException('User no longer exists');
    const profile = await this.profiles.findOne({ where: { userId } });
    return {
      user: this.sanitize(user),
      profileComplete: profile?.profileComplete ?? false,
      profile: profile ?? null,
    };
  }

  private async session(user: User) {
    const profile = await this.profiles.findOne({ where: { userId: user.id } });
    const expiresIn = this.config.get<string>('JWT_EXPIRES_IN', '30d');
    return {
      accessToken: await this.jwt.signAsync({ sub: user.id, email: user.email }),
      tokenType: 'Bearer',
      expiresIn,
      user: this.sanitize(user),
      profileComplete: profile?.profileComplete ?? false,
    };
  }

  private async createEmptyProfile(user: User) {
    const nickname = user.email.split('@')[0].slice(0, 40);
    await this.profiles.save(
      this.profiles.create({
        userId: user.id,
        nickname,
        fullName: '',
        interests: [],
        avatarSeed: Math.floor(Math.random() * 20) + 1,
        profileComplete: false,
      }),
    );
  }

  private sanitize(user: User) {
    return {
      id: user.id,
      email: user.email,
      isDemo: user.isDemo,
      createdAt: user.createdAt,
    };
  }

  private normaliseEmail(value: string): string {
    return value.trim().toLowerCase();
  }
}
