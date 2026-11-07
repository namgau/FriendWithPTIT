import assert from 'node:assert/strict';
import { test } from 'node:test';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { BadRequestException, ExecutionContext, HttpException } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import sharp from 'sharp';
import { detectImageKind, sanitizeImage } from '../src/campus/campus-image';
import { CampusMediaStorage } from '../src/campus/campus-media.storage';
import { RateLimit, RateLimitGuard, resetRateLimits } from '../src/common/rate-limit';

const FILE = /^[a-f0-9-]{36}\.(jpg|png|webp|gif)$/;

async function photo(width = 40, height = 20) {
  return sharp({ create: { width, height, channels: 3, background: '#3366cc' } });
}

function dataUrl(mime: string, bytes: Buffer) {
  return `data:${mime};base64,${bytes.toString('base64')}`;
}

function storage(dir: string, env: Record<string, string> = {}) {
  const values: Record<string, string> = {
    CAMPUS_UPLOAD_DIR: dir,
    CAMPUS_MAX_IMAGE_BYTES: '200000',
    CAMPUS_MEDIA_SECRET: 'test-secret',
    ...env,
  };
  return new CampusMediaStorage({
    get: (key: string, fallback?: string) => values[key] ?? fallback,
  } as any);
}

async function tempDir() {
  return fs.mkdtemp(path.join(os.tmpdir(), 'campus-media-'));
}

// ------------------------------------------------------------ detection

test('format is detected from the bytes, not from a claimed type', async () => {
  assert.equal(detectImageKind(await (await photo()).jpeg().toBuffer()), 'jpeg');
  assert.equal(detectImageKind(await (await photo()).png().toBuffer()), 'png');
  assert.equal(detectImageKind(await (await photo()).webp().toBuffer()), 'webp');
  assert.equal(detectImageKind(await (await photo()).gif().toBuffer()), 'gif');
  assert.equal(detectImageKind(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>')), null);
  assert.equal(detectImageKind(Buffer.from('<html><script>alert(1)</script></html>')), null);
  assert.equal(detectImageKind(Buffer.from('MZ\x90\x00 fake exe')), null);
  assert.equal(detectImageKind(Buffer.alloc(0)), null);
});

// ------------------------------------------------------------ sanitising

test('EXIF/GPS metadata is stripped and nothing identifying survives', async () => {
  const withExif = await (await photo())
    .jpeg()
    .withExif({ IFD0: { Copyright: 'SECRET-OWNER-NAME', Artist: 'Nguyen Van That' } })
    .toBuffer();
  assert.ok(withExif.includes(Buffer.from('SECRET-OWNER-NAME')), 'fixture must carry EXIF');

  const out = await sanitizeImage(withExif);
  assert.equal(out.kind, 'jpeg');
  assert.equal(out.mimeType, 'image/jpeg');
  assert.equal(out.data.includes(Buffer.from('SECRET-OWNER-NAME')), false);
  assert.equal(out.data.includes(Buffer.from('Nguyen Van That')), false);
  const meta = await sharp(out.data).metadata();
  assert.equal(meta.exif, undefined);
  assert.equal(meta.xmp, undefined);
  assert.equal(meta.iptc, undefined);
});

test('EXIF orientation is applied to the pixels, then the tag is gone', async () => {
  const rotated = await (await photo(40, 20)).jpeg().withMetadata({ orientation: 6 }).toBuffer();
  const out = await sanitizeImage(rotated);
  assert.equal(out.width, 20);
  assert.equal(out.height, 40);
  assert.equal((await sharp(out.data).metadata()).orientation, undefined);
});

test('dimensions come from the decoded image and large images are scaled down', async () => {
  const big = await (await photo(3000, 1000)).png().toBuffer();
  const out = await sanitizeImage(big);
  assert.equal(out.width, 2048);
  assert.ok(out.height > 600 && out.height < 700);
});

test('data appended after the image is dropped', async () => {
  const png = await (await photo()).png().toBuffer();
  const out = await sanitizeImage(Buffer.concat([png, Buffer.from('<?php system($_GET[0]); ?>SECRETTAIL')]));
  assert.equal(out.data.includes(Buffer.from('SECRETTAIL')), false);
  assert.equal(out.data.includes(Buffer.from('<?php')), false);
});

test('animated GIFs stay animated', async () => {
  const frames = await Promise.all(
    ['#ff0000', '#00ff00', '#0000ff'].map((background) =>
      sharp({ create: { width: 10, height: 10, channels: 3, background } }).raw().toBuffer(),
    ),
  );
  const animated = await sharp(Buffer.concat(frames), {
    raw: { width: 10, height: 30, channels: 3, pageHeight: 10 },
  })
    .gif({ loop: 0, delay: [100, 100, 100] })
    .toBuffer();
  const out = await sanitizeImage(animated);
  assert.equal(out.kind, 'gif');
  assert.equal((await sharp(out.data, { animated: true }).metadata()).pages, 3);
});

test('non-images and corrupted files are a 400', async () => {
  const jpeg = await (await photo()).jpeg().toBuffer();
  const truncated = jpeg.subarray(0, Math.floor(jpeg.length / 3));
  const cases: Buffer[] = [
    Buffer.from('plain text pretending to be a picture'),
    Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'),
    Buffer.concat([Buffer.from([0xff, 0xd8, 0xff]), Buffer.from('not really a jpeg')]),
    truncated,
  ];
  for (const bytes of cases) {
    await assert.rejects(sanitizeImage(bytes), BadRequestException);
  }
});

test('decompression bombs are rejected without being decoded', async () => {
  const bomb = await sharp({
    create: { width: 9000, height: 9000, channels: 3, background: '#000' },
  })
    .png({ compressionLevel: 9 })
    .toBuffer();
  assert.ok(bomb.length < 1_000_000, 'compresses to a small file');
  await assert.rejects(sanitizeImage(bomb), BadRequestException);
});

// --------------------------------------------------------------- storage

test('a stored upload is a re-encoded file with a random name', async () => {
  const dir = await tempDir();
  try {
    const store = storage(dir);
    const input = await (await photo()).jpeg().withExif({ IFD0: { Copyright: 'SECRET' } }).toBuffer();
    const saved = await store.saveDataUrl(dataUrl('image/jpeg', input));
    assert.match(saved.storageKey, FILE);
    assert.equal(saved.mimeType, 'image/jpeg');
    assert.equal(saved.width, 40);
    const onDisk = await fs.readFile(path.join(dir, saved.storageKey));
    assert.equal(onDisk.includes(Buffer.from('SECRET')), false);
    assert.equal(onDisk.length, saved.sizeBytes);
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
});

test('the claimed MIME type is ignored: the real content decides', async () => {
  const dir = await tempDir();
  try {
    const png = await (await photo()).png().toBuffer();
    const saved = await storage(dir).saveDataUrl(dataUrl('image/jpeg', png));
    assert.equal(saved.mimeType, 'image/png');
    assert.ok(saved.storageKey.endsWith('.png'));
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
});

test('hostile or malformed data URLs are 400 and write nothing', async () => {
  const dir = await tempDir();
  try {
    const store = storage(dir);
    const png = await (await photo()).png().toBuffer();
    const huge = Buffer.alloc(300_000, 1);
    const bad = [
      'not a data url',
      'data:text/html;base64,PGgxPmhpPC9oMT4=',
      'data:image/svg+xml;base64,PHN2Zy8+',
      'data:image/png;charset=utf-8,raw',
      'data:image/png;base64,',
      'data:image/png;base64,%%%not-base64%%%',
      dataUrl('image/png', Buffer.from('this is text')),
      dataUrl('image/png', huge),
      `data:image/png;base64,${'A'.repeat(1_000_000)}`,
      dataUrl('image/png', png.subarray(0, 20)),
    ];
    for (const value of bad) {
      await assert.rejects(store.saveDataUrl(value), BadRequestException, value.slice(0, 40));
    }
    assert.deepEqual(await fs.readdir(dir), []);
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
});

test('removal only ever touches safe file names inside the upload directory', async () => {
  const dir = await tempDir();
  const outside = path.join(path.dirname(dir), `outside-${path.basename(dir)}.txt`);
  try {
    await fs.writeFile(outside, 'keep me');
    const store = storage(dir);
    for (const key of ['../' + path.basename(outside), '..\\x.png', '/etc/passwd', 'a.png', '']) {
      await store.remove(key);
    }
    assert.equal(await fs.readFile(outside, 'utf8'), 'keep me');
    const saved = await store.saveDataUrl(dataUrl('image/png', await (await photo()).png().toBuffer()));
    await store.remove(saved.storageKey);
    assert.deepEqual(await fs.readdir(dir), []);
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
    await fs.rm(outside, { force: true });
  }
});

test('path traversal in a requested file name never resolves', async () => {
  const dir = await tempDir();
  try {
    const store = storage(dir);
    for (const name of ['../secret.png', '..%2fsecret.png', 'x/../../y.png', 'a.svg', 'a.png.html', '.env', '']) {
      assert.throws(() => store.resolvePublicFile(name), /not found/i, name);
    }
    const ok = '0a1b2c3d-0000-4000-8000-1234567890ab.png';
    assert.ok(store.resolvePublicFile(ok).startsWith(dir + path.sep));
  } finally {
    await fs.rm(dir, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------- signing

function query(url: string) {
  const parsed = new URL(url, 'http://localhost');
  return { file: path.basename(parsed.pathname), exp: parsed.searchParams.get('exp'), sig: parsed.searchParams.get('sig') };
}

test('signed media links verify, and only for the file they were made for', () => {
  const store = storage('x');
  const key = '0a1b2c3d-0000-4000-8000-1234567890ab.png';
  const other = '0a1b2c3d-0000-4000-8000-1234567890ac.png';
  const link = store.urlFor(key);
  assert.match(link, /^\/campus\/media\/[a-f0-9-]{36}\.png\?exp=\d+&sig=[A-Za-z0-9_-]+$/);
  const q = query(link);
  assert.equal(store.verifySignature(q.file, q.exp, q.sig), true);
  assert.equal(store.verifySignature(other, q.exp, q.sig), false, 'signature is bound to the file');
  assert.equal(store.verifySignature(q.file, String(Number(q.exp) + 1), q.sig), false, 'exp is covered');
  assert.equal(store.verifySignature(q.file, q.exp, q.sig!.slice(0, -2) + 'AA'), false);
  assert.equal(store.verifySignature(q.file, q.exp, 'x'), false);
  assert.equal(store.verifySignature(q.file, undefined, q.sig), false);
  assert.equal(store.verifySignature(q.file, q.exp, undefined), false);
  assert.equal(store.verifySignature('../x.png', q.exp, q.sig), false);
  assert.equal(storage('x', { CAMPUS_MEDIA_SECRET: 'other-secret' }).verifySignature(q.file, q.exp, q.sig), false);
});

test('links expire, and the URL is stable within an hour so image caches still hit', () => {
  const key = '0a1b2c3d-0000-4000-8000-1234567890ab.png';
  const store = storage('x', { CAMPUS_MEDIA_URL_TTL_SECONDS: '7200' });
  assert.equal(store.urlFor(key), store.urlFor(key));
  const realNow = Date.now;
  try {
    const q = query(store.urlFor(key));
    Date.now = () => realNow() + 3 * 3600 * 1000;
    assert.equal(store.verifySignature(q.file, q.exp, q.sig), false, 'expired');
    Date.now = () => realNow();
    assert.equal(store.verifySignature(q.file, q.exp, q.sig), true);
    // An exp far in the future cannot be forged by a long-lived signature either.
    const far = String(Math.floor(realNow() / 1000) + 10 * 365 * 24 * 3600);
    assert.equal(store.verifySignature(q.file, far, q.sig), false);
  } finally {
    Date.now = realNow;
  }
});

// ------------------------------------------------------------ rate limit

class Sample {
  @RateLimit({ key: 'unit-a', limit: 3, windowMs: 1000 })
  a() {}

  @RateLimit({ key: 'unit-b', limit: 1, windowMs: 1000 })
  b() {}

  plain() {}
}

function ctx(method: keyof Sample, userId?: string, headers: Record<string, string> = {}) {
  return {
    getHandler: () => Sample.prototype[method],
    switchToHttp: () => ({
      getRequest: () => ({ user: userId ? { userId } : undefined, ip: '203.0.113.9' }),
      getResponse: () => ({ setHeader: (k: string, v: string) => (headers[k] = v) }),
    }),
  } as unknown as ExecutionContext;
}

test('rate limit: budget per user and per action, 429 with Retry-After, recovers', async () => {
  resetRateLimits();
  const guard = new RateLimitGuard(new Reflector());
  const headers: Record<string, string> = {};
  for (let i = 0; i < 3; i += 1) assert.equal(guard.canActivate(ctx('a', 'u1', headers)), true);
  assert.throws(
    () => guard.canActivate(ctx('a', 'u1', headers)),
    (error: unknown) => error instanceof HttpException && error.getStatus() === 429,
  );
  assert.ok(Number(headers['Retry-After']) >= 1);
  // Other users, other actions and unlimited routes are unaffected.
  assert.equal(guard.canActivate(ctx('a', 'u2')), true);
  assert.equal(guard.canActivate(ctx('b', 'u1')), true);
  assert.equal(guard.canActivate(ctx('plain', 'u1')), true);
  // Budget returns once the window has passed.
  await new Promise((resolve) => setTimeout(resolve, 1050));
  assert.equal(guard.canActivate(ctx('a', 'u1')), true);
  resetRateLimits();
});

test('rate limit is shared by every guard instance in the process', () => {
  resetRateLimits();
  const first = new RateLimitGuard(new Reflector());
  const second = new RateLimitGuard(new Reflector());
  assert.equal(first.canActivate(ctx('b', 'u9')), true);
  assert.throws(() => second.canActivate(ctx('b', 'u9')), HttpException);
  resetRateLimits();
});
