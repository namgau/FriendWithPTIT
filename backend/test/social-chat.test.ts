/** Finite real-AppModule HTTP + PostgreSQL tests, never targets the development DB. */
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { DataSource } from 'typeorm';
import { BadRequestException } from '@nestjs/common';
import { ChatService } from '../src/chat/chat.service';
import { decodeChatCursor, encodeChatCursor } from '../src/chat/chat.cursor';
import { SocialChatContracts1762600000000 } from '../src/database/migrations/1762600000000-SocialChatContracts';
import { Match, Message, Notification, Profile } from '../src/database/entities';

const ADMIN_URL = process.env.CAMPUS_TEST_ADMIN_URL;
const skip = ADMIN_URL ? false : 'set CAMPUS_TEST_ADMIN_URL to run isolated PostgreSQL/HTTP tests';
const database = `social_chat_it_${Date.now()}_${process.pid}`;
let admin: DataSource;
let ds: DataSource;
let app: any;
let uploadDir: string;
let base: string;
let chat: ChatService;
let jwt: any;

before(async () => {
  if (skip) return;
  const url = new URL(ADMIN_URL!);
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(url.hostname), 'refusing non-local DB');
  admin = await new DataSource({ type: 'postgres', url: ADMIN_URL }).initialize();
  await admin.query(`CREATE DATABASE ${database}`);
  url.pathname = `/${database}`;
  const boot = await new DataSource({ type: 'postgres', url: url.toString() }).initialize();
  try { await boot.query('CREATE EXTENSION IF NOT EXISTS postgis'); } finally { await boot.destroy(); }
  uploadDir = await fs.mkdtemp(path.join(os.tmpdir(), 'social-chat-it-'));
  Object.assign(process.env, {
    DATABASE_URL: url.toString(), DB_SYNCHRONIZE: 'true', DB_MIGRATIONS_RUN: 'false', SEED_DEMO: 'false',
    JWT_SECRET: 'social-chat-isolated-test-only', CAMPUS_MEDIA_SECRET: 'isolated-media-test-only',
    CAMPUS_UPLOAD_DIR: uploadDir, PORT: '0',
  });
  const { NestFactory } = require('@nestjs/core');
  const { JwtService } = require('@nestjs/jwt');
  const { AppModule } = require('../src/app.module');
  const { configureApp } = require('../src/app.setup');
  app = await NestFactory.create(AppModule, { logger: ['error'] });
  configureApp(app);
  await app.init();
  await app.listen(0, '127.0.0.1');
  base = await app.getUrl();
  ds = app.get(DataSource); chat = app.get(ChatService); jwt = app.get(JwtService);
});

after(async () => {
  if (app) await app.close();
  if (admin) {
    try { await admin.query(`DROP DATABASE IF EXISTS ${database} WITH (FORCE)`); }
    finally { await admin.destroy(); }
  }
  if (uploadDir) await fs.rm(uploadDir, { recursive: true, force: true });
});

async function account() {
  const id = randomUUID(); const email = `${id}@test.invalid`;
  await ds.query(`INSERT INTO users (id,email,password_hash) VALUES ($1,$2,'test')`, [id, email]);
  await ds.query(`INSERT INTO profiles (user_id,nickname,full_name,school,faculty,interests,profile_complete) VALUES ($1,'Person','Name','PTIT','IT',ARRAY['A','B','C'],true)`, [id]);
  return { id, token: await jwt.signAsync({ sub: id, email }) };
}
async function pair(anonymous = false) {
  const a = await account(); const b = await account();
  const [aId, bId] = [a.id, b.id].sort();
  const [match] = await ds.query(`INSERT INTO matches (user_a_id,user_b_id,anonymous,compatibility_score) VALUES ($1,$2,$3,90) RETURNING id`, [aId, bId, anonymous]);
  return { a, b, id: match.id as string };
}
async function http(method: string, route: string, token?: string, body?: unknown) {
  const response = await fetch(base + route, {
    method, headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10_000),
  });
  return { status: response.status, body: await response.json() as any };
}
const route = (id: string) => `/matches/${id}/messages`;

test('cursor codec is versioned, conversation-bound and rejects malformed input', () => {
  const match = randomUUID(); const id = randomUUID(); const time = '2026-11-01T12:00:00.123456Z';
  assert.deepEqual(decodeChatCursor(encodeChatCursor(match, time, id), match), { time, id });
  assert.deepEqual(decodeChatCursor('2026-11-01T12:00:00.123Z', match), { time: '2026-11-01T12:00:00.123Z' });
  for (const value of ['', 'garbage', '2026-02-30T12:00:00.000Z', 'v2.abc', 'v1.!!!',
    encodeChatCursor(randomUUID(), time, id), encodeChatCursor(match, time, 'not-uuid'),
    encodeChatCursor(match, 'invalid', id), 'v1.' + Buffer.from('{}').toString('base64url')]) {
    assert.throws(() => decodeChatCursor(value, match), BadRequestException);
  }
});

test('additive migration upgrades legacy messages without rewriting/deleting data and can re-run', { skip }, async () => {
  const p = await pair();
  const [legacy] = await ds.query(`INSERT INTO messages (match_id,sender_id,text) VALUES ($1,$2,'legacy') RETURNING *`, [p.id, p.a.id]);
  // Reproduce the pre-III.A schema only in this throw-away DB.
  await ds.query(`DROP INDEX "IDX_messages_client_send"`);
  await ds.query(`DROP INDEX "IDX_messages_history"`);
  await ds.query(`ALTER TABLE messages DROP COLUMN client_message_id`);
  const runner = ds.createQueryRunner();
  try {
    await runner.startTransaction();
    await new SocialChatContracts1762600000000().up(runner);
    await new SocialChatContracts1762600000000().up(runner);
    await runner.commitTransaction();
  } catch (error) { await runner.rollbackTransaction(); throw error; }
  finally { await runner.release(); }
  const [retained] = await ds.query(`SELECT * FROM messages WHERE id=$1`, [legacy.id]);
  assert.equal(retained.text, 'legacy'); assert.equal(retained.client_message_id, null);
  assert.deepEqual(retained.created_at, legacy.created_at);
  const key = randomUUID();
  await ds.query(`INSERT INTO messages (match_id,sender_id,text,client_message_id) VALUES ($1,$2,'one',$3)`, [p.id, p.a.id, key]);
  await assert.rejects(ds.query(`INSERT INTO messages (match_id,sender_id,text,client_message_id) VALUES ($1,$2,'two',$3)`, [p.id, p.a.id, key]), (e: any) => e.code === '23505');
});

test('HTTP identical retry returns original and creates only one notification', { skip }, async () => {
  const p = await pair(); const clientMessageId = randomUUID();
  const first = await http('POST', route(p.id), p.a.token, { text: ' hello ', clientMessageId, meta: { b: 2, a: 1 } });
  const retry = await http('POST', route(p.id), p.a.token, { text: 'hello', type: 'TEXT', clientMessageId: clientMessageId.toUpperCase(), meta: { a: 1, b: 2 } });
  assert.equal(first.status, 201); assert.deepEqual(retry, first);
  assert.equal(first.body.clientMessageId, clientMessageId);
  assert.equal(Number((await ds.query(`SELECT count(*) n FROM notifications WHERE user_id=$1`, [p.b.id]))[0].n), 1);
});

test('concurrent duplicate HTTP sends are serialized to the same original message', { skip }, async () => {
  const p = await pair(); const payload = { text: 'concurrent', clientMessageId: randomUUID() };
  const results = await Promise.all(Array.from({ length: 16 }, () => http('POST', route(p.id), p.a.token, payload)));
  assert.ok(results.every(r => r.status === 201));
  assert.equal(new Set(results.map(r => r.body.id)).size, 1);
  assert.equal(Number((await ds.query(`SELECT count(*) n FROM messages WHERE match_id=$1`, [p.id]))[0].n), 1);
  assert.equal(Number((await ds.query(`SELECT count(*) n FROM notifications WHERE user_id=$1`, [p.b.id]))[0].n), 1);
});

test('conflicting text, type and meta reuse returns 409; concurrent conflicting sends have one winner', { skip }, async () => {
  const p = await pair(); const clientMessageId = randomUUID();
  assert.equal((await http('POST', route(p.id), p.a.token, { text: 'one', clientMessageId })).status, 201);
  for (const payload of [{ text: 'two' }, { text: 'one', type: 'IMAGE' }, { text: 'one', meta: { changed: true } }]) {
    assert.equal((await http('POST', route(p.id), p.a.token, { ...payload, clientMessageId })).status, 409);
  }
  const key = randomUUID();
  const results = await Promise.all(['a', 'b'].map(text => http('POST', route(p.id), p.a.token, { text, clientMessageId: key })));
  assert.deepEqual(results.map(r => r.status).sort(), [201, 409]);
});

test('idempotency scope separates sender and conversation; missing keys preserve legacy sending', { skip }, async () => {
  const p = await pair(); const key = randomUUID();
  const c = await account(); const ids = [p.a.id, c.id].sort();
  const [other] = await ds.query(`INSERT INTO matches (user_a_id,user_b_id) VALUES ($1,$2) RETURNING id`, ids);
  const results = await Promise.all([
    http('POST', route(p.id), p.a.token, { text: 'same', clientMessageId: key }),
    http('POST', route(p.id), p.b.token, { text: 'same', clientMessageId: key }),
    http('POST', route(other.id), p.a.token, { text: 'same', clientMessageId: key }),
    http('POST', route(p.id), p.a.token, { text: 'legacy' }),
    http('POST', route(p.id), p.a.token, { text: 'legacy' }),
  ]);
  assert.ok(results.every(r => r.status === 201));
  assert.equal(new Set(results.map(r => r.body.id)).size, 5);
});

test('HTTP validation, authorization and unmatch still apply to retries', { skip }, async () => {
  const p = await pair(); const stranger = await account(); const payload = { text: 'hi', clientMessageId: randomUUID() };
  assert.equal((await http('POST', route(p.id), undefined, payload)).status, 401);
  assert.equal((await http('POST', route(p.id), stranger.token, payload)).status, 404);
  for (const clientMessageId of ['bad', '', 10]) assert.equal((await http('POST', route(p.id), p.a.token, { ...payload, clientMessageId })).status, 400);
  assert.equal((await http('POST', route(p.id), p.a.token, payload)).status, 201);
  assert.equal((await http('POST', `/matches/${p.id}/unmatch`, p.a.token)).status, 200);
  assert.equal((await http('POST', route(p.id), p.a.token, payload)).status, 404);
  assert.equal((await http('GET', route(p.id), p.a.token)).status, 404);
});

test('notification failure rolls back the message and retry can safely complete', { skip }, async () => {
  const p = await pair(); const payload = { text: 'atomic', clientMessageId: randomUUID() };
  await ds.query(`ALTER TABLE notifications ADD CONSTRAINT test_notification_failure CHECK (kind <> 'MESSAGE') NOT VALID`);
  try {
    await assert.rejects(chat.send(p.a.id, p.id, payload));
    assert.equal(Number((await ds.query(`SELECT count(*) n FROM messages WHERE match_id=$1`, [p.id]))[0].n), 0);
    assert.equal((await ds.query(`SELECT last_message_at FROM matches WHERE id=$1`, [p.id]))[0].last_message_at, null);
  } finally { await ds.query(`ALTER TABLE notifications DROP CONSTRAINT test_notification_failure`); }
  const sent = await chat.send(p.a.id, p.id, payload);
  assert.equal(sent.clientMessageId, payload.clientMessageId);
});

test('stable tuple pagination traverses identical timestamps exactly once in chronological pages', { skip }, async () => {
  const p = await pair(); const time = '2026-11-01T12:00:00.123456Z';
  const ids = Array.from({ length: 11 }, () => randomUUID()).sort();
  for (const id of ids) await ds.query(`INSERT INTO messages (id,match_id,sender_id,text,created_at) VALUES ($1,$2,$3,'same time',$4)`, [id, p.id, p.a.id, time]);
  let before: string | null = null; const seen: string[] = []; let pages = 0;
  do {
    const response = await http('GET', route(p.id) + '?limit=3' + (before ? '&before=' + encodeURIComponent(before) : ''), p.a.token);
    assert.equal(response.status, 200);
    const pageIds = response.body.items.map((m: any) => m.id);
    assert.deepEqual(pageIds, [...pageIds].sort());
    seen.push(...pageIds); pages++;
    before = response.body.nextBefore;
    assert.equal(response.body.hasMore, before !== null);
    if (before) assert.equal(decodeChatCursor(before, p.id).time, time);
  } while (before && pages < 10);
  assert.equal(pages, 4); assert.equal(new Set(seen).size, 11);
  assert.deepEqual([...seen].sort(), ids);
});

test('pagination preserves microseconds across timestamps and is unaffected by new head messages', { skip }, async () => {
  const p = await pair(); const ids: string[] = [];
  for (const fraction of ['123001', '123002', '123003', '123004', '123005']) {
    const id = randomUUID(); ids.push(id);
    await ds.query(`INSERT INTO messages (id,match_id,sender_id,text,created_at) VALUES ($1,$2,$3,'precision',$4)`, [id, p.id, p.a.id, `2026-11-01T12:00:00.${fraction}Z`]);
  }
  const first = await chat.list(p.a.id, p.id, undefined, 2);
  assert.deepEqual(first.items.map(m => m.id), ids.slice(3));
  await chat.send(p.b.id, p.id, { text: 'new head', clientMessageId: randomUUID() });
  const second = await chat.list(p.a.id, p.id, first.nextBefore!, 2);
  assert.deepEqual(second.items.map(m => m.id), ids.slice(1, 3));
  const third = await chat.list(p.a.id, p.id, second.nextBefore!, 2);
  assert.deepEqual(third.items.map(m => m.id), ids.slice(0, 1));
  assert.equal(third.nextBefore, null);
  assert.equal((await chat.list(p.a.id, p.id, undefined, 1)).items[0].text, 'new head');
});

test('HTTP rejects malformed/cross-match cursors and invalid limits; accepts legacy ISO before', { skip }, async () => {
  const p = await pair();
  for (const before of ['', 'invalid', '2026-02-30T12:00:00.000Z', encodeChatCursor(randomUUID(), '2026-11-01T00:00:00.000Z', randomUUID())]) {
    assert.equal((await http('GET', route(p.id) + '?before=' + encodeURIComponent(before), p.a.token)).status, 400);
  }
  for (const limit of ['', '0', '-1', '101', '1.5', '2junk', 'NaN']) {
    assert.equal((await http('GET', route(p.id) + '?limit=' + limit, p.a.token)).status, 400);
  }
  assert.equal((await http('GET', route(p.id) + '?before=2026-11-01T00:00:00.000Z', p.a.token)).status, 200);
});

test('empty/exact page boundaries and non-UTC database timezone preserve the stored cursor tuple', { skip }, async () => {
  const p = await pair();
  const empty = await chat.list(p.a.id, p.id, undefined, 3);
  assert.deepEqual(empty.items, []); assert.equal(empty.hasMore, false); assert.equal(empty.nextBefore, null);
  const ids = Array.from({ length: 6 }, () => randomUUID()).sort();
  for (const id of ids) await ds.query(`INSERT INTO messages (id,match_id,sender_id,text,created_at) VALUES ($1,$2,$3,'boundary','2026-11-01T12:00:00.123456Z')`, [id, p.id, p.a.id]);
  await ds.transaction(async manager => {
    await manager.query(`SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh'`);
    const service = new ChatService(manager.getRepository(Message), manager.getRepository(Match), manager.getRepository(Profile), manager.getRepository(Notification));
    const first = await service.list(p.a.id, p.id, undefined, 3);
    assert.equal(decodeChatCursor(first.nextBefore!, p.id).time, '2026-11-01T12:00:00.123456Z');
    assert.deepEqual(first.items.map(m => m.id), ids.slice(3));
    const second = await service.list(p.a.id, p.id, first.nextBefore!, 3);
    assert.deepEqual(second.items.map(m => m.id), ids.slice(0, 3));
    assert.equal(second.hasMore, false); assert.equal(second.nextBefore, null);
  });
});

test('anonymous retries/history remain masked; reveal, report, block and membership semantics remain intact', { skip }, async () => {
  const p = await pair(true); const payload = { text: 'anonymous', clientMessageId: randomUUID() };
  await http('POST', route(p.id), p.a.token, payload);
  await http('POST', route(p.id), p.a.token, payload);
  const history = await http('GET', route(p.id), p.b.token);
  assert.equal(history.body.items.length, 1);
  assert.equal(history.body.items[0].senderId, `anon:${p.id}`);
  assert.equal(history.body.items[0].isMine, false);
  assert.equal('clientMessageId' in history.body.items[0], false);
  assert.equal(JSON.stringify(history.body).includes(p.a.id), false);
  const reveal = await http('POST', `/matches/${p.id}/reveal`, p.a.token, { enabled: true });
  assert.equal(reveal.body.anonymous, true); assert.equal(reveal.body.revealEligible, false);
  assert.equal((await http('POST', `/users/${encodeURIComponent('anon:' + p.id)}/block`, p.b.token)).status, 200);
  assert.equal((await http('POST', route(p.id), p.a.token, payload)).status, 404);
  const q = await pair();
  assert.equal((await http('POST', `/matches/${q.id}/report`, q.a.token, { reason: 'SPAM' })).status, 200);
  assert.equal((await http('GET', route(q.id), q.b.token)).status, 404);
});

test('mutual server reveal unmasks history only after eligibility and withdrawing consent masks it again', { skip }, async () => {
  const p = await pair(true);
  for (let i = 0; i < 30; i++) {
    await ds.query(`INSERT INTO messages (match_id,sender_id,text,type,created_at) VALUES ($1,$2,$3,'TEXT',$4)`,
      [p.id, i % 2 ? p.b.id : p.a.id, `message ${i}`, new Date(Date.UTC(2026, 10, 1 + i % 3))]);
  }
  await http('POST', `/matches/${p.id}/reveal`, p.a.token, { enabled: true });
  assert.equal((await http('GET', `/matches/${p.id}`, p.b.token)).body.anonymous, true);
  const mutual = await http('POST', `/matches/${p.id}/reveal`, p.b.token, { enabled: true });
  assert.equal(mutual.body.revealEligible, true); assert.equal(mutual.body.anonymous, false);
  assert.ok((await http('GET', route(p.id), p.b.token)).body.items.some((m: any) => m.senderId === p.a.id));
  await http('POST', `/matches/${p.id}/reveal`, p.a.token, { enabled: false });
  assert.ok((await http('GET', route(p.id), p.b.token)).body.items.some((m: any) => m.senderId === `anon:${p.id}`));
});

test('one sided or fewer than three UTC+7 days never unmasks anonymous identities', { skip }, async () => {
  const p = await pair(true);
  for (let i = 0; i < 40; i++) await ds.query(
    `INSERT INTO messages (match_id,sender_id,text,type,created_at) VALUES ($1,$2,'hello','TEXT',$3)`,
    [p.id, p.a.id, new Date(Date.UTC(2026, 10, 1 + i % 3))]);
  await http('POST', `/matches/${p.id}/reveal`, p.a.token, { enabled: true });
  const both = await http('POST', `/matches/${p.id}/reveal`, p.b.token, { enabled: true });
  assert.equal(both.body.revealEligible, false);
  assert.equal(both.body.anonymous, true);
  assert.equal((await http('GET', route(p.id), p.b.token)).body.items[0].senderId, `anon:${p.id}`);
});

test('discover applies graduation and location filters and sorts by shared tags', { skip }, async () => {
  const viewer = await account();
  const one = await account(); const two = await account(); const hidden = await account();
  await ds.query(`UPDATE profiles SET age=20,verified=true,alumni=true,lat=21,lng=105,show_location_public=true WHERE user_id=ANY($1)`, [[viewer.id,one.id,two.id,hidden.id]]);
  await ds.query(`UPDATE profiles SET interests=ARRAY[' A ','b'] WHERE user_id=$1`, [viewer.id]);
  await ds.query(`UPDATE profiles SET interests=ARRAY['a'] WHERE user_id=$1`, [one.id]);
  await ds.query(`UPDATE profiles SET interests=ARRAY['a','B'] WHERE user_id=$1`, [two.id]);
  await ds.query(`UPDATE profiles SET show_location_public=false WHERE user_id=$1`, [hidden.id]);
  const res = await http('GET', '/profiles/discover?graduation=ALUMNI&maxDistanceKm=10', viewer.token);
  assert.equal(res.status, 200);
  const ids = res.body.items.map((item: any) => item.userId);
  assert.ok(!ids.includes(hidden.id));
  assert.ok(ids.indexOf(two.id) < ids.indexOf(one.id));
  assert.equal(res.body.items.find((item: any) => item.userId === two.id).compatibility, 100);
});

test('incoming likes excludes either block direction and preserves anonymous/PASS/already-swiped privacy', { skip }, async () => {
  const viewer = await account();
  const [normal, superLike, blockedByMe, blockedMe, anonymous, passed, already] = await Promise.all(Array.from({ length: 7 }, () => account()));
  for (const [sender, action, anon] of [[normal, 'LIKE', false], [superLike, 'SUPER_LIKE', false], [blockedByMe, 'LIKE', false], [blockedMe, 'LIKE', false], [anonymous, 'LIKE', true], [passed, 'PASS', false], [already, 'LIKE', false]] as const) {
    await ds.query(`INSERT INTO swipes (from_user_id,target_user_id,action,anonymous) VALUES ($1,$2,$3,$4)`, [sender.id, viewer.id, action, anon]);
  }
  await ds.query(`INSERT INTO swipes (from_user_id,target_user_id,action) VALUES ($1,$2,'PASS')`, [viewer.id, already.id]);
  await http('POST', `/users/${blockedByMe.id}/block`, viewer.token);
  await http('POST', `/users/${viewer.id}/block`, blockedMe.token);
  const response = await http('GET', '/profiles/incoming-likes', viewer.token);
  assert.equal(response.status, 200); assert.equal(response.body.total, 2);
  assert.deepEqual(response.body.items.map((p: any) => p.userId).sort(), [normal.id, superLike.id].sort());
  assert.equal(response.body.items.find((p: any) => p.userId === superLike.id).superLike, true);
  assert.ok(response.body.items.every((p: any) => p.likedAt));
});

test('ordinary mutual and duplicate swipes still yield one server match', { skip }, async () => {
  const a = await account(); const b = await account();
  const one = await http('POST', '/swipes', a.token, { targetUserId: b.id, action: 'LIKE' });
  assert.equal(one.status, 200); assert.equal(one.body.matched, false);
  const two = await http('POST', '/swipes', b.token, { targetUserId: a.id, action: 'LIKE' });
  assert.equal(two.body.matched, true);
  const duplicate = await http('POST', '/swipes', b.token, { targetUserId: a.id, action: 'LIKE' });
  assert.equal(duplicate.body.match.id, two.body.match.id);
  assert.equal((await http('GET', '/matches', a.token)).body.items.length, 1);
});
