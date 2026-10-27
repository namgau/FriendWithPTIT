import 'reflect-metadata';
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, { cors: true });
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      transformOptions: { enableImplicitConversion: false },
    }),
  );
  app.enableShutdownHooks();
  const config = app.get(ConfigService);
  const port = Number.parseInt(config.get<string>('PORT', '3000'), 10);
  await app.listen(port, '0.0.0.0');
  console.log(`FwP backend listening on http://localhost:${port}`);
}

void bootstrap();
