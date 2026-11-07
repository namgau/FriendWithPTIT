import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ConflictException } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { after, before } from 'node:test';
import { DataSource } from 'typeorm';
import { SocialService } from '../src/social/social.service';

test('identified conversation cannot be reused as an anonymous Campus match', async () => {
  for (const origin of ['campus', 'reciprocal', 'existing'] as const) {
    let writes = 0;
    const swipes = {
      findOne: async ({ where }: any) =>
        (origin === 'existing' && where.fromUserId === 'viewer') ||
        (origin === 'reciprocal' && where.fromUserId === 'target')
          ? { anonymous: true, action: 'LIKE' } : null,
      save: async () => { writes++; },
    };
    const matches = { findOne: async () => ({ anonymous: false, unmatchedAt: new Date() }) };
    const profiles = { findOne: async ({ where }: any) => ({ userId: where.userId }) };
    const blocks = { findOne: async () => null };
    const service = new SocialService(swipes as any, matches as any, profiles as any,
      {} as any, {} as any, blocks as any, {} as any);
    await assert.rejects(
      service.swipe('viewer', { targetUserId: 'target', action: 'LIKE' }, { anonymous: origin === 'campus' }),
      ConflictException,
    );
    assert.equal(writes, 0, 'a rejected anonymous attempt must not modify swipes');
  }
});

const adminUrl = process.env.CAMPUS_TEST_ADMIN_URL;
const dbName = `social_rematch_it_${Date.now()}_${process.pid}`;
let admin: DataSource, app: any, ds: DataSource, base: string, jwt: any;
before(async () => {
  if (!adminUrl) return;
  const url = new URL(adminUrl);
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(url.hostname));
  admin = await new DataSource({ type: 'postgres', url: adminUrl }).initialize();
  await admin.query(`CREATE DATABASE ${dbName}`);
  url.pathname = `/${dbName}`;
  const boot = await new DataSource({ type: 'postgres', url: url.toString() }).initialize();
  try { await boot.query('CREATE EXTENSION IF NOT EXISTS postgis'); } finally { await boot.destroy(); }
  Object.assign(process.env, { DATABASE_URL: url.toString(), DB_SYNCHRONIZE: 'true', DB_MIGRATIONS_RUN: 'false', SEED_DEMO: 'false', JWT_SECRET: 'social-rematch-isolated-test' });
  const { NestFactory } = require('@nestjs/core');
  const { JwtService } = require('@nestjs/jwt');
  const { AppModule } = require('../src/app.module');
  const { configureApp } = require('../src/app.setup');
  app = await NestFactory.create(AppModule, { logger: ['error'] });
  configureApp(app); await app.listen(0, '127.0.0.1'); base = await app.getUrl();
  ds = app.get(DataSource); jwt = app.get(JwtService);
});
after(async () => {
  if (app) await app.close();
  if (admin) { try { await admin.query(`DROP DATABASE IF EXISTS ${dbName} WITH (FORCE)`); } finally { await admin.destroy(); } }
});
async function account() {
  const id = randomUUID(), email = `${id}@test.invalid`;
  await ds.query(`INSERT INTO users (id,email,password_hash) VALUES ($1,$2,'test')`, [id, email]);
  await ds.query(`INSERT INTO profiles (user_id,nickname,full_name,school,faculty,interests,profile_complete) VALUES ($1,'Person','Name','PTIT','IT',ARRAY['A','B','C'],true)`, [id]);
  return { id, token: await jwt.signAsync({ sub: id, email }) };
}
async function http(method: string, route: string, token: string, body?: unknown) {
  const r = await fetch(base + route, { method, headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000) });
  return { status: r.status, body: await r.json() as any };
}
async function campusAuthor(author: { token: string }) {
  const post = await http('POST', '/campus/posts', author.token, { content: 'Isolated anonymous rematch fixture', category: 'GENERAL', anonymousMode: 'DISCOVERABLE' });
  assert.equal(post.status, 201); return post.body;
}

test('both Campus-first and ordinary-first swipes create masked matches', { skip: !adminUrl }, async () => {
  for (const campusFirst of [true, false]) {
    const a = await account(), b = await account(), post = await campusAuthor(b);
    const feed = await http('GET', '/campus/posts', a.token);
    const token = feed.body.items.find((p: any) => p.id === post.id).author.authorToken;
    const campus = () => http('POST', `/campus/authors/${token}/swipes`, a.token, { action: 'LIKE' });
    const ordinary = () => http('POST', '/swipes', b.token, { targetUserId: a.id, action: 'LIKE' });
    const first = await (campusFirst ? campus() : ordinary());
    const second = await (campusFirst ? ordinary() : campus());
    assert.equal(first.status, 200); assert.equal(second.status, 200); assert.equal(second.body.matched, true);
    const id = campusFirst ? second.body.match.id : second.body.matchId;
    const match = await http('GET', `/matches/${id}`, a.token);
    assert.equal(match.status, 200); assert.equal(match.body.anonymous, true);
    assert.ok(!JSON.stringify(match.body).includes(b.id), 'masked response must not expose counterpart id');
  }
});

test('revealed anonymous history survives unmatch but cannot be reused without a new boundary', { skip: !adminUrl }, async () => {
  const a = await account(), b = await account(), ids = [a.id, b.id].sort();
  const [match] = await ds.query(`INSERT INTO matches (user_a_id,user_b_id,anonymous,compatibility_score,reveal_opt_in_a,reveal_opt_in_b) VALUES ($1,$2,true,90,true,true) RETURNING id`, ids);
  for (let i = 0; i < 8; i++) assert.equal((await http('POST', `/matches/${match.id}/messages`, a.token, { text: `history ${i}`, clientMessageId: randomUUID() })).status, 201);
  const revealed = await http('GET', `/matches/${match.id}`, a.token);
  assert.equal(revealed.status, 200); assert.equal(revealed.body.anonymous, false);
  assert.equal((await http('POST', `/matches/${match.id}/unmatch`, a.token)).status, 200);
  const post = await campusAuthor(b), feed = await http('GET', '/campus/posts', a.token);
  const token = feed.body.items.find((p: any) => p.id === post.id).author.authorToken;
  assert.equal((await http('POST', `/campus/authors/${token}/swipes`, a.token, { action: 'LIKE' })).status, 409);
  assert.equal(Number((await ds.query(`SELECT count(*) n FROM messages WHERE match_id=$1`, [match.id]))[0].n), 8);
  assert.ok((await ds.query(`SELECT unmatched_at FROM matches WHERE id=$1`, [match.id]))[0].unmatched_at);
});

test('an unmatched anonymous conversation with history cannot silently reuse that history', async () => {
  let writes = 0;
  const swipes = { findOne: async () => ({ anonymous: true, action: 'LIKE' }), save: async () => { writes++; } };
  const matches = { findOne: async () => ({ id: 'prior', anonymous: true, unmatchedAt: new Date(), revealOptInA: true, revealOptInB: true }) };
  const profiles = { findOne: async ({ where }: any) => ({ userId: where.userId }) };
  const messages = { count: async () => 8 };
  const service = new SocialService(swipes as any, matches as any, profiles as any,
    messages as any, {} as any, { findOne: async () => null } as any, {} as any);
  await assert.rejects(service.swipe('viewer', { targetUserId: 'target', action: 'LIKE' }, { anonymous: true }), ConflictException);
  assert.equal(writes, 0);
});

test('concurrent reciprocal Campus and ordinary likes preserve a single masked match', { skip: !adminUrl }, async () => {
  for (let iteration = 0; iteration < 20; iteration++) {
    const a = await account(), b = await account(), post = await campusAuthor(b);
    const feed = await http('GET', '/campus/posts', a.token);
    const token = feed.body.items.find((p: any) => p.id === post.id).author.authorToken;
    const responses = await Promise.all([
      http('POST', `/campus/authors/${token}/swipes`, a.token, { action: 'LIKE' }),
      http('POST', '/swipes', b.token, { targetUserId: a.id, action: 'LIKE' }),
    ]);
    assert.ok(responses.every(r => r.status === 200), JSON.stringify(responses));
    const [row] = await ds.query(`SELECT count(*)::int n, bool_and(anonymous) masked FROM matches WHERE user_a_id=$1 AND user_b_id=$2`, [a.id, b.id].sort());
    if (row.n === 0) {
      console.log('RACE_RECIPROCAL_LOST_MATCH', JSON.stringify(responses));
    }
    assert.equal(row.n, 1, 'reciprocal likes must create exactly one match');
    assert.equal(row.masked, true, 'Campus-origin match must be anonymous');
    const lists = await Promise.all([http('GET', '/matches', a.token), http('GET', '/matches', b.token)]);
    assert.ok(lists.every(r => r.body.items.length === 1 && r.body.items[0].anonymous === true));
  }
});

test('concurrent anonymous rematch attempts against identified history all return 409 without changes', { skip: !adminUrl }, async () => {
  const a = await account(), b = await account(), ids = [a.id, b.id].sort();
  const [match] = await ds.query(`INSERT INTO matches (user_a_id,user_b_id,anonymous,unmatched_at) VALUES ($1,$2,false,now()) RETURNING id`, ids);
  await ds.query(`INSERT INTO messages (match_id,sender_id,text) VALUES ($1,$2,'identified-history')`, [match.id, a.id]);
  const post = await campusAuthor(b), feed = await http('GET', '/campus/posts', a.token);
  const token = feed.body.items.find((p: any) => p.id === post.id).author.authorToken;
  const results = await Promise.all(Array.from({ length: 8 }, () => http('POST', `/campus/authors/${token}/swipes`, a.token, { action: 'LIKE' })));
  assert.ok(results.every(r => r.status === 409), JSON.stringify(results));
  const [state] = await ds.query(`SELECT unmatched_at,anonymous FROM matches WHERE id=$1`, [match.id]);
  const [count] = await ds.query(`SELECT count(*)::int n FROM messages WHERE match_id=$1`, [match.id]);
  assert.ok(state.unmatched_at); assert.equal(state.anonymous, false); assert.equal(count.n, 1);
  assert.equal(Number((await ds.query(`SELECT count(*) n FROM swipes WHERE from_user_id=$1 AND target_user_id=$2`, [a.id, b.id]))[0].n), 0);
});

test('simultaneous ordinary and Campus likes for the same pair never produce a falsely anonymous match', { skip: !adminUrl }, async () => {
  for (let iteration = 0; iteration < 20; iteration++) {
    const a = await account(), b = await account(), post = await campusAuthor(b);
    const feed = await http('GET', '/campus/posts', a.token);
    const token = feed.body.items.find((p: any) => p.id === post.id).author.authorToken;
    const replies = await Promise.all([
      http('POST', '/swipes', a.token, { targetUserId: b.id, action: 'LIKE' }),
      http('POST', `/campus/authors/${token}/swipes`, a.token, { action: 'LIKE' }),
      http('POST', '/swipes', b.token, { targetUserId: a.id, action: 'LIKE' }),
    ]);
    assert.ok(replies.every(r => [200, 409].includes(r.status)), JSON.stringify(replies));
    const [row] = await ds.query(`SELECT count(*)::int n, bool_and(anonymous) masked FROM matches WHERE user_a_id=$1 AND user_b_id=$2`, [a.id, b.id].sort());
    assert.ok(row.n <= 1);
    const campus = replies[1];
    if (campus.status === 200 && row.n === 1) assert.equal(row.masked, true, JSON.stringify(replies));
    const [swipe] = await ds.query(`SELECT anonymous FROM swipes WHERE from_user_id=$1 AND target_user_id=$2`, [a.id, b.id]);
    if (campus.status === 200) assert.equal(swipe.anonymous, true, 'Campus origin must remain sticky');
  }
});
