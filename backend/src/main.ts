import 'reflect-metadata';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { configureApp } from './app.setup';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    cors: true,
  });
  configureApp(app);
  app.enableShutdownHooks();
  const config = app.get(ConfigService);
  const port = Number.parseInt(config.get<string>('PORT', '3000'), 10);
  await app.listen(port, '0.0.0.0');
  console.log(`FwP backend listening on http://localhost:${port}`);
}

void bootstrap();
