/**
 * HTTP smoke tests: the REAL AppModule (all guards, pipes, controllers, TypeORM)
 * served on an ephemeral 127.0.0.1 port against a throw-away database.
 * Skipped unless CAMPUS_TEST_ADMIN_URL is set; refuses non-local databases.
 * The app and every connection are closed in after().
 *
 *   CAMPUS_TEST_ADMIN_URL=postgresql://mig:mig@127.0.0.1:5433/migtest
 */
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import sharp from 'sharp';
import { DataSource } from 'typeorm';
import { resetRateLimits } from '../src/common/rate-limit';

const ADMIN_URL = process.env.CAMPUS_TEST_ADMIN_URL;
const skip = ADMIN_URL ? false : 'set CAMPUS_TEST_ADMIN_URL to run HTTP integration tests';
const DB_NAME = `campus_http_${Date.now()}`;
const SECRET_IN_EXIF = 'SECRET-OWNER-NAME';

let app: any;
let admin: DataSource;
let uploadDir: string;
let base = '';
let jwt: any;
const MOD_ID = randomUUID();
let modToken = '';

interface Reply {
  status: number;
  json: any;
  text: string;
  headers: Headers;
}

async function call(
  method: string,
  url: string,
  opts: { token?: string; body?: unknown; rawBody?: string; headers?: Record<string, string> } = {},
): Promise<Reply> {
  const headers: Record<string, string> = { ...(opts.headers ?? {}) };
  if (opts.token) headers.authorization = `Bearer ${opts.token}`;
  let body: string | undefined;
  if (opts.rawBody !== undefined) {
    body = opts.rawBody;
    headers['content-type'] = headers['content-type'] ?? 'application/json';
  } else if (opts.body !== undefined) {
    body = JSON.stringify(opts.body);
    headers['content-type'] = 'application/json';
  }
  const res = await fetch(url.startsWith('http') ? url : base + url, { method, headers, body });
  const text = await res.text();
  let json: any = null;
  try {
    json = JSON.parse(text);
  } catch {
    /* non-JSON body (e.g. an image or an HTML error page) */
  }
  return { status: res.status, json, text, headers: res.headers };
}

interface Account {
  email: string;
  id: string;
  token: string;
}

async function register(prefix: string): Promise<Account> {
  const email = `${prefix}${Date.now()}${Math.floor(Math.random() * 1e6)}@t.vn`;
  const res = await call('POST', '/auth/register', { body: { email, password: 'Password123!' } });
  assert.equal(res.status, 201, res.text);
  return { email, id: res.json.user.id, token: res.json.accessToken };
}

const postBody = (extra: object = {}) => ({
  content: 'xin chao campus',
  category: 'GENERAL',
  anonymousMode: 'FULLY_ANONYMOUS',
  ...extra,
});
const discoverableBody = (extra: object = {}) => postBody({ anonymousMode: 'DISCOVERABLE', ...extra });

async function exifJpeg(): Promise<Buffer> {
  return sharp({ create: { width: 64, height: 32, channels: 3, background: '#2266cc' } })
    .jpeg()
    .withExif({ IFD0: { Copyright: SECRET_IN_EXIF } })
    .toBuffer();
}
const asDataUrl = (mime: string, bytes: Buffer) => `data:${mime};base64,${bytes.toString('base64')}`;

before(async () => {
  if (skip) return;
  const parsed = new URL(ADMIN_URL!);
  assert.ok(['127.0.0.1', 'localhost', '::1'].includes(parsed.hostname), 'refusing non-local database');

  admin = new DataSource({ type: 'postgres', url: ADMIN_URL });
  await admin.initialize();
  await admin.query(`CREATE DATABASE ${DB_NAME}`);
  const target = new URL(ADMIN_URL!);
  target.pathname = `/${DB_NAME}`;
  const boot = new DataSource({ type: 'postgres', url: target.toString() });
  await boot.initialize();
  await boot.query('CREATE EXTENSION IF NOT EXISTS postgis');
  await boot.destroy();

  uploadDir = await fs.mkdtemp(path.join(os.tmpdir(), 'campus-http-'));
  Object.assign(process.env, {
    DATABASE_URL: target.toString(),
    DB_SYNCHRONIZE: 'true',
    DB_MIGRATIONS_RUN: 'false',
    SEED_DEMO: 'false',
    JWT_SECRET: 'http-test-secret',
    CAMPUS_UPLOAD_DIR: uploadDir,
    CAMPUS_MEDIA_SECRET: 'http-media-secret',
    CAMPUS_MAX_IMAGE_BYTES: '100000',
    CAMPUS_REPORT_HIDE_THRESHOLD: '2',
    CAMPUS_MODERATOR_USER_IDS: MOD_ID,
    REQUEST_BODY_LIMIT: '1mb',
    PORT: '0',
  });

  const { NestFactory } = require('@nestjs/core');
  const { JwtService } = require('@nestjs/jwt');
  const { AppModule } = require('../src/app.module');
  const { configureApp } = require('../src/app.setup');
  app = await NestFactory.create(AppModule, { logger: ['error'] });
  configureApp(app);
  await app.init();
  await app.listen(0, '127.0.0.1');
  base = (await app.getUrl()).replace('[::1]', '127.0.0.1');
  assert.match(base, /^http:\/\/127\.0\.0\.1:\d+$/);
  jwt = app.get(JwtService);

  // The moderator is a plain user whose id is listed in CAMPUS_MODERATOR_USER_IDS.
  const ds = app.get(DataSource);
  await ds.query(`INSERT INTO users (id, email, password_hash) VALUES ($1, 'mod@t.vn', 'x')`, [MOD_ID]);
  modToken = await jwt.signAsync({ sub: MOD_ID, email: 'mod@t.vn' });
  resetRateLimits();
});

after(async () => {
  resetRateLimits();
  if (app) await app.close();
  if (admin) {
    await admin.query(`DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE)`);
    await admin.destroy();
  }
  if (uploadDir) await fs.rm(uploadDir, { recursive: true, force: true });
});

// --------------------------------------------------------------- bootstrap

test('app boots, routes are registered and the database is reachable', { skip }, async () => {
  assert.equal((await call('GET', '/')).status, 200);
  const health = await call('GET', '/healthz');
  assert.equal(health.status, 200);
  assert.equal(health.json.database, 'up');
  assert.equal((await call('GET', '/definitely/not/a/route')).status, 404);
  const routes = app.getHttpServer()._events.request ? true : false;
  assert.equal(routes, true);
});

// ------------------------------------------------------------ authentication

test('every Campus route rejects missing, malformed, expired and forged tokens with 401', { skip }, async () => {
  const forged = await jwt.signAsync({ sub: randomUUID(), email: 'x@t.vn' }, { secret: 'wrong-secret' });
  const expired = await jwt.signAsync({ sub: randomUUID(), email: 'x@t.vn' }, { expiresIn: '-10s' });
  const postId = randomUUID();
  const routes: Array<[string, string]> = [
    ['GET', '/campus/posts'],
    ['GET', '/campus/posts/mine'],
    ['POST', '/campus/posts'],
    ['GET', `/campus/posts/${postId}`],
    ['PATCH', `/campus/posts/${postId}`],
    ['DELETE', `/campus/posts/${postId}`],
    ['POST', `/campus/posts/${postId}/like`],
    ['POST', `/campus/posts/${postId}/comments`],
    ['POST', `/campus/posts/${postId}/report`],
    ['GET', '/campus/settings'],
    ['PATCH', '/campus/settings'],
    ['GET', `/campus/authors/${postId}`],
    ['POST', `/campus/authors/${postId}/swipes`],
    ['GET', '/campus/moderation/queue'],
    ['POST', `/campus/moderation/posts/${postId}`],
    ['GET', '/matches'],
  ];
  for (const [method, url] of routes) {
    const headers: Array<Record<string, string> | undefined> = [
      undefined,
      { authorization: 'Basic abc' },
      { authorization: 'Bearer' },
      { authorization: 'Bearer not.a.jwt' },
      { authorization: `Bearer ${forged}` },
      { authorization: `Bearer ${expired}` },
    ];
    for (const h of headers) {
      const res = await call(method, url, { headers: h, body: method === 'GET' ? undefined : {} });
      assert.equal(res.status, 401, `${method} ${url} with ${JSON.stringify(h)}`);
    }
  }
});

test('registration validates input and rejects duplicates', { skip }, async () => {
  assert.equal((await call('POST', '/auth/register', { body: { email: 'nope', password: 'Password123!' } })).status, 400);
  assert.equal((await call('POST', '/auth/register', { body: { email: 'a@t.vn', password: 'short' } })).status, 400);
  assert.equal((await call('POST', '/auth/register', { rawBody: '{not json' })).status, 400);
  const user = await register('dup');
  const again = await call('POST', '/auth/register', { body: { email: user.email, password: 'Password123!' } });
  assert.equal(again.status, 409);
  const me = await call('GET', '/auth/me', { token: user.token });
  assert.equal(me.status, 200);
});

// --------------------------------------------------------------- posts

test('create, read, list and validate Campus posts over HTTP', { skip }, async () => {
  const u = await register('poster');
  const created = await call('POST', '/campus/posts', {
    token: u.token,
    body: postBody({ authorId: 'evil', status: 'REMOVED', likeCount: 999, authorAlias: 'Hacked #0000' }),
  });
  assert.equal(created.status, 201, created.text);
  const post = created.json;
  assert.equal(post.counts.likes, 0, 'client cannot set counters');
  assert.notEqual(post.author.alias, 'Hacked #0000', 'client cannot choose the persona');
  assert.equal(post.viewer.isAuthor, true);
  assert.equal(post.author.discoverable, false);
  assert.equal('authorToken' in post.author, false);
  for (const secret of [u.id, u.email, u.email.split('@')[0]]) {
    assert.equal(created.text.includes(secret), false, `response leaked ${secret}`);
  }

  const detail = await call('GET', `/campus/posts/${post.id}`, { token: u.token });
  assert.equal(detail.status, 200);
  assert.equal(detail.json.id, post.id);
  const other = await register('reader');
  const asOther = await call('GET', `/campus/posts/${post.id}`, { token: other.token });
  assert.equal(asOther.json.viewer.isAuthor, false);
  const feed = await call('GET', '/campus/posts?limit=30', { token: other.token });
  assert.equal(feed.status, 200);
  assert.ok(feed.json.items.some((p: any) => p.id === post.id));
  assert.equal(JSON.stringify(feed.json).includes(u.id), false);

  const bad: Array<[string, object]> = [
    ['empty post', { category: 'GENERAL', anonymousMode: 'FULLY_ANONYMOUS' }],
    ['unknown category', postBody({ category: 'NOPE' })],
    ['unknown mode', postBody({ anonymousMode: 'PUBLIC' })],
    ['content too long', postBody({ content: 'x'.repeat(2001) })],
    ['content not a string', postBody({ content: 12345 })],
    ['too many images', postBody({ images: Array.from({ length: 5 }, () => ({ dataUrl: 'data:image/png;base64,AAAA' })) })],
    ['image without dataUrl', postBody({ images: [{ width: 5 }] })],
    ['image not an object', postBody({ images: ['nope'] })],
  ];
  for (const [label, body] of bad) {
    const res = await call('POST', '/campus/posts', { token: u.token, body });
    assert.equal(res.status, 400, label);
  }
});

test('malformed ids, cursors and query parameters are 404/400, never 500', { skip }, async () => {
  const u = await register('malformed');
  for (const id of ['not-a-uuid', '1%27%20OR%20%271%27%3D%271', '%2e%2e%2f', '00000000-0000-0000-0000-000000000000']) {
    for (const [method, suffix] of [['GET', ''], ['PATCH', ''], ['DELETE', ''], ['POST', '/like'], ['DELETE', '/like'], ['POST', '/bookmark'], ['GET', '/comments'], ['POST', '/report']] as const) {
      const res = await call(method, `/campus/posts/${id}${suffix}`, { token: u.token, body: method === 'GET' || method === 'DELETE' ? undefined : { content: 'x', reason: 'SPAM' } });
      assert.equal(res.status, 404, `${method} /campus/posts/${id}${suffix} -> ${res.status}`);
    }
  }
  const encode = (v: unknown) => Buffer.from(JSON.stringify(v)).toString('base64url');
  for (const cursor of ['%25%25', 'abc', encode({ createdAt: 'x', id: randomUUID() }), encode({ createdAt: new Date().toISOString(), id: "1'; DROP TABLE x;--" })]) {
    for (const url of ['/campus/posts', '/campus/posts/mine']) {
      assert.equal((await call('GET', `${url}?cursor=${cursor}`, { token: u.token })).status, 400, `${url} ${cursor}`);
    }
  }
  for (const query of ['limit=0', 'limit=999', 'limit=abc', 'tab=bogus', 'category=NOPE']) {
    assert.equal((await call('GET', `/campus/posts?${query}`, { token: u.token })).status, 400, query);
  }
  assert.equal((await call('GET', '/campus/authors/not-a-uuid', { token: u.token })).status, 404);
  assert.equal((await call('GET', `/campus/authors/${randomUUID()}`, { token: u.token })).status, 404);
  assert.equal((await call('POST', `/campus/authors/${randomUUID()}/swipes`, { token: u.token, body: { action: 'LIKE' } })).status, 404);
  assert.equal((await call('POST', `/campus/authors/${randomUUID()}/swipes`, { token: u.token, body: { action: 'EXPLODE' } })).status, 400);
  assert.equal((await call('POST', '/campus/posts', { token: u.token, rawBody: '{broken' })).status, 400);
});

// ----------------------------------------------------------------- images

test('image upload: EXIF stripped, signed URL works, forged/expired/traversal links are 404', { skip }, async () => {
  const u = await register('imager');
  const original = await exifJpeg();
  assert.ok(original.includes(Buffer.from(SECRET_IN_EXIF)));
  const created = await call('POST', '/campus/posts', {
    token: u.token,
    body: postBody({ images: [{ dataUrl: asDataUrl('image/png', original), width: 9999, height: 9999 }] }),
  });
  assert.equal(created.status, 201, created.text);
  const media = created.json.media[0];
  assert.equal(media.mimeType, 'image/jpeg', 'real content wins over the claimed MIME');
  assert.equal(media.width, 64, 'dimensions come from the decoded image');
  assert.equal(media.height, 32);
  assert.match(media.url, /^\/campus\/media\/[a-f0-9-]{36}\.jpg\?exp=\d+&sig=[\w-]+$/);

  const image = await fetch(base + media.url);
  assert.equal(image.status, 200);
  assert.equal(image.headers.get('content-type'), 'image/jpeg');
  assert.equal(image.headers.get('x-content-type-options'), 'nosniff');
  assert.match(image.headers.get('cache-control') ?? '', /private/);
  const bytes = Buffer.from(await image.arrayBuffer());
  assert.equal(bytes.includes(Buffer.from(SECRET_IN_EXIF)), false, 'EXIF must not survive');
  assert.equal((await sharp(bytes).metadata()).exif, undefined);

  const [file, query] = media.url.split('/').pop()!.split('?');
  const params = new URLSearchParams(query);
  const exp = params.get('exp')!;
  const sig = params.get('sig')!;
  const reads: Array<[string, string]> = [
    ['no signature', `/campus/media/${file}`],
    ['empty signature', `/campus/media/${file}?exp=${exp}&sig=`],
    ['wrong signature', `/campus/media/${file}?exp=${exp}&sig=${sig.slice(0, -2)}AA`],
    ['exp changed', `/campus/media/${file}?exp=${Number(exp) + 100}&sig=${sig}`],
    ['exp in the past', `/campus/media/${file}?exp=1&sig=${sig}`],
    ['other file, same sig', `/campus/media/${randomUUID()}.jpg?exp=${exp}&sig=${sig}`],
    ['traversal', `/campus/media/%2e%2e%2fpackage.json?exp=${exp}&sig=${sig}`],
    ['traversal 2', `/campus/media/..%5c..%5cpackage.json?exp=${exp}&sig=${sig}`],
  ];
  for (const [label, url] of reads) {
    const res = await call('GET', url);
    assert.equal(res.status, 404, label);
  }

  const realNow = Date.now;
  try {
    Date.now = () => realNow() + 48 * 3600 * 1000;
    assert.equal((await call('GET', media.url)).status, 404, 'expired link');
  } finally {
    Date.now = realNow;
  }
  assert.equal((await fetch(base + media.url)).status, 200, 'still valid once the clock is back');

  const stored = await fs.readdir(uploadDir);
  assert.ok(stored.every((name) => /^[a-f0-9-]{36}\.(jpg|png|webp|gif)$/.test(name)));
});

test('hostile or oversized uploads are rejected with 4xx and leave no file behind', { skip }, async () => {
  const u = await register('hostile');
  const before = (await fs.readdir(uploadDir)).length;
  const noise = await sharp(Buffer.from(Array.from({ length: 200 * 200 * 3 }, () => Math.floor(Math.random() * 256))), {
    raw: { width: 200, height: 200, channels: 3 },
  })
    .png()
    .toBuffer();
  assert.ok(noise.length > 100_000, 'fixture must exceed CAMPUS_MAX_IMAGE_BYTES');
  const cases: Array<[string, string]> = [
    ['svg', 'data:image/svg+xml;base64,' + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>').toString('base64')],
    ['html', 'data:text/html;base64,' + Buffer.from('<script>alert(1)</script>').toString('base64')],
    ['text posing as png', asDataUrl('image/png', Buffer.from('definitely not an image'))],
    ['truncated jpeg', asDataUrl('image/jpeg', (await exifJpeg()).subarray(0, 40))],
    ['not base64', 'data:image/png;base64,%%%'],
    ['over the size limit', asDataUrl('image/png', noise)],
  ];
  for (const [label, dataUrl] of cases) {
    const res = await call('POST', '/campus/posts', { token: u.token, body: postBody({ images: [{ dataUrl }] }) });
    assert.equal(res.status, 400, `${label}: ${res.text.slice(0, 120)}`);
  }
  assert.equal((await fs.readdir(uploadDir)).length, before);

  const tooBig = await call('POST', '/campus/posts', { token: u.token, body: postBody({ content: 'x'.repeat(2_000_000) }) });
  assert.equal(tooBig.status, 413, 'body limit is enforced by the HTTP layer');
});

// -------------------------------------------------------------- reactions

test('like/bookmark/comment over HTTP: idempotent, ownership enforced, comments paged', { skip }, async () => {
  const [owner, fan] = [await register('owner'), await register('fan')];
  const { json: post } = await call('POST', '/campus/posts', { token: owner.token, body: postBody() });

  assert.equal((await call('POST', `/campus/posts/${post.id}/like`, { token: owner.token })).status, 400);
  const likes = await Promise.all(Array.from({ length: 8 }, () => call('POST', `/campus/posts/${post.id}/like`, { token: fan.token })));
  assert.ok(likes.every((r) => r.status === 200 && r.json.likeCount === 1));
  assert.equal((await call('GET', `/campus/posts/${post.id}`, { token: fan.token })).json.viewer.liked, true);
  assert.equal((await call('DELETE', `/campus/posts/${post.id}/like`, { token: fan.token })).json.likeCount, 0);

  await call('POST', `/campus/posts/${post.id}/bookmark`, { token: fan.token });
  const saved = await call('GET', '/campus/posts?tab=saved', { token: fan.token });
  assert.deepEqual(saved.json.items.map((p: any) => p.id), [post.id]);

  assert.equal((await call('POST', `/campus/posts/${post.id}/comments`, { token: fan.token, body: { content: '' } })).status, 400);
  assert.equal((await call('POST', `/campus/posts/${post.id}/comments`, { token: fan.token, body: { content: 'x'.repeat(601) } })).status, 400);
  const comment = await call('POST', `/campus/posts/${post.id}/comments`, { token: fan.token, body: { content: 'hay qua' } });
  assert.equal(comment.status, 201);
  assert.equal(comment.json.isMine, true);
  assert.equal(comment.text.includes(fan.id), false);
  const listed = await call('GET', `/campus/posts/${post.id}/comments`, { token: owner.token });
  assert.equal(listed.json.items[0].isMine, false);
  assert.equal(listed.text.includes(fan.id), false);
  assert.equal((await call('DELETE', `/campus/posts/${post.id}/comments/${comment.json.id}`, { token: owner.token })).status, 404);
  assert.equal((await call('DELETE', `/campus/posts/${post.id}/comments/${comment.json.id}`, { token: fan.token })).status, 200);
  assert.equal((await call('GET', `/campus/posts/${post.id}`, { token: fan.token })).json.counts.comments, 0);
});

test('edit, my posts and delete over HTTP', { skip }, async () => {
  const [owner, other] = [await register('editor'), await register('bystander')];
  const { json: post } = await call('POST', '/campus/posts', { token: owner.token, body: postBody({ content: 'v1' }) });
  const edited = await call('PATCH', `/campus/posts/${post.id}`, { token: owner.token, body: { content: 'v2', category: 'STUDY', anonymousMode: 'DISCOVERABLE' } });
  assert.equal(edited.status, 200);
  assert.equal(edited.json.content, 'v2');
  assert.equal(edited.json.anonymousMode, 'FULLY_ANONYMOUS', 'privacy mode cannot be edited');
  assert.ok(edited.json.editedAt);
  assert.equal((await call('PATCH', `/campus/posts/${post.id}`, { token: other.token, body: { content: 'hack' } })).status, 404);
  assert.equal((await call('PATCH', `/campus/posts/${post.id}`, { token: owner.token, body: {} })).status, 400);
  assert.equal((await call('PATCH', `/campus/posts/${post.id}`, { token: owner.token, body: { category: 'NOPE' } })).status, 400);
  const mine = await call('GET', '/campus/posts/mine', { token: owner.token });
  assert.deepEqual(mine.json.items.map((p: any) => p.id), [post.id]);
  assert.deepEqual((await call('GET', '/campus/posts/mine', { token: other.token })).json.items, []);
  assert.equal((await call('DELETE', `/campus/posts/${post.id}`, { token: other.token })).status, 404);
  assert.equal((await call('DELETE', `/campus/posts/${post.id}`, { token: owner.token })).status, 200);
  assert.equal((await call('GET', `/campus/posts/${post.id}`, { token: owner.token })).status, 404);
});

// ------------------------------------------------ discover author + match

test('anonymous discovery -> swipe -> match -> chat never reveals the author', { skip }, async () => {
  const alice = await register('alicezzq');
  const bob = await register('bobzzq');
  const aliceTag = alice.email.split('@')[0];
  const bobTag = bob.email.split('@')[0];

  const made = await call('POST', '/campus/posts', { token: alice.token, body: discoverableBody() });
  assert.equal(made.status, 201);
  const token = made.json.author.authorToken;
  assert.match(token, /^[0-9a-f-]{36}$/);
  assert.notEqual(token, alice.id);
  const hidden = await call('POST', '/campus/posts', { token: alice.token, body: postBody() });
  assert.equal('authorToken' in hidden.json.author, false, 'fully anonymous posts have no token');

  // Bob only ever sees the persona.
  const feed = await call('GET', '/campus/posts?limit=30', { token: bob.token });
  assert.ok(feed.json.items.find((p: any) => p.author.authorToken === token));
  const card = await call('GET', `/campus/authors/${token}`, { token: bob.token });
  assert.equal(card.status, 200);
  for (const key of ['age', 'mode', 'compatibility', 'nickname', 'fullName', 'school', 'userId']) {
    assert.equal(key in card.json, false, key);
  }
  for (const secret of [alice.id, alice.email, aliceTag]) {
    assert.equal(card.text.includes(secret), false, `card leaked ${secret}`);
    assert.equal(feed.text.includes(secret), false, `feed leaked ${secret}`);
  }

  // Self-swipe and bad actions.
  assert.equal((await call('POST', `/campus/authors/${token}/swipes`, { token: alice.token, body: { action: 'LIKE' } })).status, 400);
  assert.equal((await call('POST', `/campus/authors/${token}/swipes`, { token: bob.token, body: {} })).status, 400);

  // Bob likes anonymously, repeatedly. Alice has not liked back yet.
  const first = await call('POST', `/campus/authors/${token}/swipes`, { token: bob.token, body: { action: 'LIKE' } });
  assert.deepEqual(first.json, { anonymous: true, matched: false, matchId: null });
  const dup = await call('POST', `/campus/authors/${token}/swipes`, { token: bob.token, body: { action: 'LIKE' } });
  assert.deepEqual(dup.json, first.json);
  assert.deepEqual((await call('GET', '/profiles/incoming-likes', { token: alice.token })).json.items, [], 'anonymous likes are not listed');

  // Alice likes Bob back through ordinary discovery: the match must stay anonymous.
  const back = await call('POST', '/swipes', { token: alice.token, body: { targetUserId: bob.id, action: 'LIKE' } });
  assert.equal(back.status, 200);
  assert.equal(back.json.matched, true);
  assert.equal(back.json.match.anonymous, true);
  assert.equal(back.json.match.other.anonymous, true);
  for (const secret of [bob.id, bob.email, bobTag]) assert.equal(back.text.includes(secret), false, `swipe response leaked ${secret}`);
  const matchId = back.json.match.id;

  const listA = await call('GET', '/matches', { token: alice.token });
  const listB = await call('GET', '/matches', { token: bob.token });
  assert.equal(listA.status, 200);
  assert.equal(listA.json.items[0].other.anonymous, true);
  assert.equal(listB.json.items[0].other.anonymous, true);
  assert.equal(listA.text.includes(bob.id) || listA.text.includes(bobTag), false);
  assert.equal(listB.text.includes(alice.id) || listB.text.includes(aliceTag), false);
  assert.equal(listB.json.items[0].other.userId, `anon:${matchId}`);

  const notesA = await call('GET', '/notifications', { token: alice.token });
  const notesB = await call('GET', '/notifications', { token: bob.token });
  for (const [notes, secrets] of [[notesA, [bob.id, bobTag]], [notesB, [alice.id, aliceTag]]] as const) {
    const match = notes.json.items.find((n: any) => n.kind === 'MATCH');
    assert.equal(match.title, 'Bạn có match ẩn danh');
    assert.equal('profileId' in match.data, false);
    for (const secret of secrets) assert.equal(notes.text.includes(secret), false, `notification leaked ${secret}`);
  }

  // Chat keeps both sides masked.
  const sent = await call('POST', `/matches/${matchId}/messages`, { token: bob.token, body: { text: 'xin chao' } });
  assert.equal(sent.status, 201);
  const read = await call('GET', `/matches/${matchId}/messages`, { token: alice.token });
  assert.equal(read.json.items[0].senderId, `anon:${matchId}`);
  assert.equal(read.json.items[0].isMine, false);
  assert.equal(read.text.includes(bob.id), false);
  const msgNote = (await call('GET', '/notifications', { token: alice.token })).json.items.find((n: any) => n.kind === 'MESSAGE');
  assert.equal(msgNote.title, 'Tin nhắn từ người ẩn danh');
  assert.equal('profileId' in msgNote.data, false);
  assert.equal(JSON.stringify(msgNote).includes(bobTag), false);

  // The masked id never resolves to a profile, and cannot be used by outsiders.
  assert.equal((await call('GET', `/profiles/anon:${matchId}`, { token: alice.token })).status, 404);
  const stranger = await register('stranger');
  assert.equal((await call('GET', `/matches/${matchId}`, { token: stranger.token })).status, 404);
  assert.equal((await call('POST', `/users/anon:${matchId}/block`, { token: stranger.token })).status, 404);

  // Reveal stays locked until both opted in and the thresholds are met.
  const reveal = await call('POST', `/matches/${matchId}/reveal`, { token: alice.token, body: { enabled: true } });
  assert.equal(reveal.status, 200);
  assert.equal(reveal.json.other.anonymous, true);
  assert.equal(reveal.json.revealEligible, false);

  // Bob can block the anonymous author without ever learning the real id.
  const blocked = await call('POST', `/users/anon:${matchId}/block`, { token: bob.token });
  assert.equal(blocked.status, 200);
  assert.equal(blocked.json.userId, `anon:${matchId}`);
  assert.equal((await call('GET', `/campus/authors/${token}`, { token: bob.token })).status, 404, 'blocked author is undiscoverable');
  assert.equal((await call('POST', `/campus/authors/${token}/swipes`, { token: bob.token, body: { action: 'LIKE' } })).status, 404);
  assert.equal((await call('GET', `/campus/posts/${made.json.id}`, { token: bob.token })).status, 404);
  assert.equal((await call('GET', `/matches/${matchId}`, { token: bob.token })).status, 404, 'match is closed');
});

test('author can disable discovery (tokens revoked), and posting cannot silently re-enable it', { skip }, async () => {
  const [author, viewer] = [await register('opt'), await register('viewer')];
  const p1 = await call('POST', '/campus/posts', { token: author.token, body: discoverableBody() });
  const p2 = await call('POST', '/campus/posts', { token: author.token, body: discoverableBody() });
  const [t1, t2] = [p1.json.author.authorToken, p2.json.author.authorToken];
  assert.notEqual(t1, t2, 'one token per post');
  assert.equal((await call('GET', `/campus/authors/${t1}`, { token: viewer.token })).status, 200);

  assert.equal((await call('PATCH', '/campus/settings', { token: author.token, body: { discoveryEnabled: 'yes' } })).status, 400);
  assert.equal((await call('PATCH', '/campus/settings', { token: author.token, body: { showCampus: 1 } })).status, 400);
  const off = await call('PATCH', '/campus/settings', { token: author.token, body: { discoveryEnabled: false } });
  assert.equal(off.status, 200);
  assert.equal(off.json.discoveryEnabled, false);
  assert.equal(off.json.revokedTokens, 2);
  for (const t of [t1, t2]) {
    assert.equal((await call('GET', `/campus/authors/${t}`, { token: viewer.token })).status, 404);
    assert.equal((await call('POST', `/campus/authors/${t}/swipes`, { token: viewer.token, body: { action: 'LIKE' } })).status, 404);
  }
  const seen = await call('GET', `/campus/posts/${p1.json.id}`, { token: viewer.token });
  assert.equal(seen.json.author.discoverable, false);
  assert.equal('authorToken' in seen.json.author, false);

  assert.equal((await call('POST', '/campus/posts', { token: author.token, body: discoverableBody() })).status, 409);
  assert.equal((await call('POST', '/campus/posts', { token: author.token, body: postBody() })).status, 201);

  const on = await call('PATCH', '/campus/settings', { token: author.token, body: { discoveryEnabled: true } });
  assert.equal(on.json.issuedTokens, 2);
  const fresh = await call('GET', `/campus/posts/${p1.json.id}`, { token: viewer.token });
  assert.ok(fresh.json.author.authorToken);
  assert.notEqual(fresh.json.author.authorToken, t1, 'old links never come back');
  assert.equal((await call('GET', `/campus/authors/${t1}`, { token: viewer.token })).status, 404);

  const settings = await call('GET', '/campus/settings', { token: author.token });
  assert.deepEqual(Object.keys(settings.json).sort(), ['discoverablePosts', 'discoveryEnabled', 'showCampus', 'showFaculty', 'showInterests']);
});

// ----------------------------------------------- reports and moderation

test('reports, moderator authorisation (401/403) and decisions over HTTP', { skip }, async () => {
  const [owner, r1, r2, bystander] = [await register('target'), await register('rep'), await register('rep'), await register('by')];
  const post = (await call('POST', '/campus/posts', { token: owner.token, body: postBody() })).json;
  const comment = (await call('POST', `/campus/posts/${post.id}/comments`, { token: bystander.token, body: { content: 'meh' } })).json;

  assert.equal((await call('POST', `/campus/posts/${post.id}/report`, { token: owner.token, body: { reason: 'SPAM' } })).status, 400);
  assert.equal((await call('POST', `/campus/posts/${post.id}/report`, { token: r1.token, body: { reason: 'BOGUS' } })).status, 400);
  assert.equal((await call('POST', `/campus/posts/${post.id}/report`, { token: r1.token, body: {} })).status, 400);
  assert.deepEqual((await call('POST', `/campus/posts/${post.id}/report`, { token: r1.token, body: { reason: 'SPAM' } })).json, { reported: true });
  assert.deepEqual((await call('POST', `/campus/posts/${post.id}/report`, { token: r1.token, body: { reason: 'SPAM' } })).json, { reported: true, duplicate: true });
  assert.equal((await call('GET', `/campus/posts/${post.id}`, { token: r1.token })).status, 404, 'reporter no longer sees it');
  // Comment reports happen while the post is still ACTIVE (the second post
  // report below pushes it over the threshold of 2 into review).
  const cUrl = `/campus/posts/${post.id}/comments/${comment.id}/report`;
  assert.equal((await call('POST', cUrl, { token: bystander.token, body: { reason: 'SPAM' } })).status, 400, 'own comment');
  assert.equal((await call('POST', `/campus/posts/${post.id}/comments/not-a-uuid/report`, { token: owner.token, body: { reason: 'SPAM' } })).status, 404);
  assert.equal((await call('POST', cUrl, { token: owner.token, body: { reason: 'SPAM' } })).status, 200);

  await call('POST', `/campus/posts/${post.id}/report`, { token: r2.token, body: { reason: 'HARASSMENT', details: 'x'.repeat(10) } });

  // Under review: hidden from everyone, locked for its author.
  const viewer = await register('late');
  assert.equal((await call('GET', `/campus/posts/${post.id}`, { token: viewer.token })).status, 404);
  assert.equal((await call('PATCH', `/campus/posts/${post.id}`, { token: owner.token, body: { content: 'sneaky edit' } })).status, 404);
  assert.equal((await call('POST', `/campus/posts/${post.id}/report`, { token: viewer.token, body: { reason: 'SPAM' } })).status, 404);
  assert.ok((await call('GET', '/campus/posts/mine', { token: owner.token })).json.items.some((p: any) => p.id === post.id), 'owner still sees it as theirs');

  // Authorisation: 401 without token, 403 for ordinary users, 200 for the moderator.
  assert.equal((await call('GET', '/campus/moderation/queue')).status, 401);
  for (const u of [owner, r1, bystander]) {
    assert.equal((await call('GET', '/campus/moderation/queue', { token: u.token })).status, 403);
    assert.equal((await call('POST', `/campus/moderation/posts/${post.id}`, { token: u.token, body: { action: 'REMOVE' } })).status, 403);
    assert.equal((await call('POST', `/campus/moderation/comments/${comment.id}`, { token: u.token, body: { action: 'REMOVE' } })).status, 403);
  }
  const queue = await call('GET', '/campus/moderation/queue', { token: modToken });
  assert.equal(queue.status, 200);
  assert.ok(queue.json.posts.some((p: any) => p.id === post.id && p.reasons.length === 2));
  assert.equal(queue.text.includes(owner.id) || queue.text.includes(owner.email), false, 'queue carries no author identity');

  assert.equal((await call('POST', `/campus/moderation/posts/${post.id}`, { token: modToken, body: { action: 'DELETE_EVERYTHING' } })).status, 400);
  assert.equal((await call('POST', '/campus/moderation/posts/not-a-uuid', { token: modToken, body: { action: 'REMOVE' } })).status, 404);
  assert.equal((await call('POST', `/campus/moderation/posts/${randomUUID()}`, { token: modToken, body: { action: 'REMOVE' } })).status, 404);

  // Author cannot delete a comment under review, the moderator decides.
  assert.equal((await call('DELETE', `/campus/posts/${post.id}/comments/${comment.id}`, { token: bystander.token })).status, 404);

  const restored = await call('POST', `/campus/moderation/posts/${post.id}`, { token: modToken, body: { action: 'RESTORE' } });
  assert.deepEqual(restored.json, { postId: post.id, status: 'ACTIVE' });
  assert.equal((await call('GET', `/campus/posts/${post.id}`, { token: viewer.token })).status, 200);
  assert.equal((await call('POST', `/campus/moderation/posts/${post.id}`, { token: modToken, body: { action: 'RESTORE' } })).status, 404, 'already decided');
});

test('moderator removal is final for the author', { skip }, async () => {
  const [owner, a, b] = [await register('gone'), await register('flag'), await register('flag')];
  const post = (await call('POST', '/campus/posts', { token: owner.token, body: discoverableBody() })).json;
  const token = post.author.authorToken;
  for (const r of [a, b]) await call('POST', `/campus/posts/${post.id}/report`, { token: r.token, body: { reason: 'SPAM' } });
  const removed = await call('POST', `/campus/moderation/posts/${post.id}`, { token: modToken, body: { action: 'REMOVE' } });
  assert.deepEqual(removed.json, { postId: post.id, status: 'REMOVED' });
  assert.equal((await call('DELETE', `/campus/posts/${post.id}`, { token: owner.token })).status, 404);
  assert.equal((await call('PATCH', `/campus/posts/${post.id}`, { token: owner.token, body: { content: 'x' } })).status, 404);
  assert.equal((await call('GET', `/campus/authors/${token}`, { token: a.token })).status, 404, 'token revoked on removal');
});

// ------------------------------------------------------------- rate limits

test('rate limits answer 429 with Retry-After, per user and per action', { skip }, async () => {
  resetRateLimits();
  const [spammer, calm] = [await register('spam'), await register('calm')];
  for (let i = 0; i < 10; i += 1) {
    const res = await call('POST', '/campus/posts', { token: spammer.token, body: postBody({ content: `p${i}` }) });
    assert.equal(res.status, 201, `post ${i}`);
  }
  const limited = await call('POST', '/campus/posts', { token: spammer.token, body: postBody() });
  assert.equal(limited.status, 429);
  assert.ok(Number(limited.headers.get('retry-after')) >= 1);
  assert.equal((await call('POST', '/campus/posts', { token: calm.token, body: postBody() })).status, 201, 'other users are unaffected');
  assert.equal((await call('GET', '/campus/posts', { token: spammer.token })).status, 200, 'other actions are unaffected');

  const post = (await call('POST', '/campus/posts', { token: calm.token, body: postBody() })).json;
  const reporters = await Promise.all(Array.from({ length: 1 }, () => register('lim')));
  let last = 0;
  for (let i = 0; i < 21; i += 1) {
    last = (await call('POST', `/campus/posts/${post.id}/report`, { token: reporters[0].token, body: { reason: 'SPAM' } })).status;
  }
  assert.equal(last, 429, 'report budget (20/hour) is enforced even for duplicates');
  resetRateLimits();
});
