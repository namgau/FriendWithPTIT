import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { NestExpressApplication } from '@nestjs/platform-express';

/**
 * HTTP-level configuration shared by the real server (main.ts) and the HTTP
 * integration tests, so tests exercise exactly what production runs.
 */
export function configureApp(app: NestExpressApplication): void {
  // Campus posts carry up to 4 base64 images, so the default 100kb JSON limit
  // rejects them. Keep an explicit ceiling instead of an unbounded parser.
  const bodyLimit = app.get(ConfigService).get<string>('REQUEST_BODY_LIMIT', '24mb');
  app.useBodyParser('json', { limit: bodyLimit });
  app.useBodyParser('urlencoded', { limit: '1mb', extended: false });
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      transform: true,
      transformOptions: { enableImplicitConversion: false },
    }),
  );
}
