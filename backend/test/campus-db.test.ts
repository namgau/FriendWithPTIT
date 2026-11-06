/**
 * Integration tests against a REAL PostgreSQL. Skipped unless
 * CAMPUS_TEST_ADMIN_URL is set. Safety: refuses any host except localhost and
 * always works in a freshly created, throw-away database named campus_it_*.
 *
 *   CAMPUS_TEST_ADMIN_URL=postgresql://mig:mig@127.0.0.1:5433/migtest
 */
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { randomUUID } from 'node:crypto';
import { BadRequestException, ConflictException, NotFoundException } from '@nestjs/common';
import { DataSource } from 'typeorm';
import {
  Block,
  CampusAuthor,
  CampusCommentReport,
  CampusPost,
  CampusPostBookmark,
  CampusPostComment,
  CampusPostReport,
  ENTITIES,
  Match,
  Message,
  Notification,
  Profile,
  Report,
  Swipe,
} from '../src/database/entities';
import { CampusService } from '../src/campus/campus.service';
import { CampusStore } from '../src/campus/campus.store';
import { SocialService } from '../src/social/social.service';

const ADMIN_URL = process.env.CAMPUS_TEST_ADMIN_URL;
const skip = ADMIN_URL ? false : 'set CAMPUS_TEST_ADMIN_URL to run DB integration tests';
const DB_NAME = `campus_it_${Date.now()}`;

let admin: DataSource;
let ds: DataSource;
let service: CampusService;
let store: CampusStore;
const removedFiles: string[] = [];

function assertSafeTarget(url: string) {
  const parsed = new URL(url);
  assert.ok(['127.0.0.1', 'localhost', '::1'].includes(parsed.hostname), 'refusing non-local database');
}

before(async () => {
  if (skip) return;
  assertSafeTarget(ADMIN_URL!);
  process.env.CAMPUS_REPORT_HIDE_THRESHOLD = '3';
  admin = new DataSource({ type: 'postgres', url: ADMIN_URL });
  await admin.initialize();
  await admin.query(`CREATE DATABASE ${DB_NAME}`);
  const target = new URL(ADMIN_URL!);
  target.pathname = `/${DB_NAME}`;
  const boot = new DataSource({ type: 'postgres', url: target.toString() });
  await boot.initialize();
  await boot.query('CREATE EXTENSION IF NOT EXISTS postgis');
  await boot.destroy();
  ds = new DataSource({ type: 'postgres', url: target.toString(), entities: ENTITIES, synchronize: true });
  await ds.initialize();
  store = new CampusStore(ds);
  const repo = <T extends object>(e: new () => T) => ds.getRepository(e);
  const social = new SocialService(
    repo(Swipe), repo(Match), repo(Profile), repo(Message), repo(Notification), repo(Block), repo(Report),
  );
  service = new CampusService(
    repo(CampusAuthor), repo(CampusPost), repo(CampusPostComment), repo(CampusPostBookmark),
    repo(CampusPostReport), repo(CampusCommentReport), repo(Profile), repo(Block),
    social,
    {
      remove: async (key: string) => void removedFiles.push(key),
      urlFor: (key: string) => `/campus/media/${key}?exp=1&sig=x`,
    } as any,
    store,
  );
});

after(async () => {
  if (skip) return;
  await ds?.destroy();
  await admin?.query(`DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE)`);
  await admin?.destroy();
});

async function user(): Promise<string> {
  const id = randomUUID();
  await ds.query(`INSERT INTO users (id, email, password_hash) VALUES ($1, $2, 'x')`, [id, `${id}@t.vn`]);
  return id;
}

const anon = (extra: object = {}) =>
  ({ content: 'hello', category: 'GENERAL', anonymousMode: 'FULLY_ANONYMOUS', images: [], ...extra }) as any;
const discoverable = () => anon({ anonymousMode: 'DISCOVERABLE' });
const row = async (sql: string, params: unknown[] = []) => (await ds.query(sql, params))[0];
const counts = (postId: string) =>
  row(`SELECT like_count l, bookmark_count b, comment_count c, report_count r, status FROM campus_posts WHERE id=$1`, [postId]);

// --------------------------------------------------------------- reactions

test('duplicate and concurrent likes count once and never error', { skip }, async () => {
  const [owner, fan] = [await user(), await user()];
  const { id } = await service.create(owner, anon());
  const results = await Promise.all(Array.from({ length: 20 }, () => service.like(fan, id)));
  assert.ok(results.every((r) => r.liked === true));
  assert.equal((await counts(id)).l, 1);
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_post_likes WHERE post_id=$1`, [id])).n), 1);

  const others = await Promise.all(Array.from({ length: 8 }, () => user()));
  await Promise.all(others.map((u) => service.like(u, id)));
  assert.equal((await counts(id)).l, 9);

  await Promise.all(Array.from({ length: 10 }, () => service.unlike(fan, id)));
  assert.equal((await counts(id)).l, 8);
  assert.deepEqual(await service.unlike(fan, id), { liked: false, likeCount: 8 });
  await assert.rejects(service.like(owner, id), BadRequestException);
});

test('bookmarks are idempotent under concurrency and the counter matches the rows', { skip }, async () => {
  const [owner, reader] = [await user(), await user()];
  const { id } = await service.create(owner, anon());
  await Promise.all(Array.from({ length: 15 }, () => service.bookmark(reader, id)));
  assert.equal((await counts(id)).b, 1);
  await Promise.all(Array.from({ length: 15 }, () => service.unbookmark(reader, id)));
  const after = await counts(id);
  assert.equal(after.b, 0);
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_post_bookmarks WHERE post_id=$1`, [id])).n), 0);
});

test('reacting to a missing, deleted or hidden post is a clean 404', { skip }, async () => {
  const [owner, reader] = [await user(), await user()];
  const { id } = await service.create(owner, anon());
  await service.removePost(owner, id);
  for (const op of [() => service.like(reader, id), () => service.bookmark(reader, id), () => service.detail(reader, id)]) {
    await assert.rejects(op(), NotFoundException);
  }
  await assert.rejects(service.like(reader, randomUUID()), NotFoundException);
});

// --------------------------------------------------------------- comments

test('comment counter equals the number of visible comments under concurrency', { skip }, async () => {
  const owner = await user();
  const { id } = await service.create(owner, anon());
  const writers = await Promise.all(Array.from({ length: 12 }, () => user()));
  const created = await Promise.all(writers.map((w) => service.addComment(w, id, { content: 'hi' })));
  assert.equal((await counts(id)).c, 12);
  await Promise.all(created.slice(0, 5).map((c, i) => service.removeComment(writers[i], id, c.id)));
  assert.equal((await counts(id)).c, 7);
  const listed = await service.listComments(owner, id, {});
  assert.equal(listed.items.length, 7);
  await assert.rejects(service.removeComment(writers[0], id, created[0].id), NotFoundException);
  await assert.rejects(service.removeComment(writers[6], id, created[7].id), NotFoundException);
  assert.equal((await counts(id)).c, 7);
});

test('blocked users disappear from comments both ways', { skip }, async () => {
  const [owner, a, b] = [await user(), await user(), await user()];
  const { id } = await service.create(owner, anon());
  await service.addComment(a, id, { content: 'from a' });
  await service.addComment(b, id, { content: 'from b' });
  await ds.query(`INSERT INTO blocks (blocker_id, blocked_id) VALUES ($1, $2)`, [a, b]);
  assert.deepEqual((await service.listComments(a, id, {})).items.map((c) => c.content), ['from a']);
  assert.deepEqual((await service.listComments(b, id, {})).items.map((c) => c.content), ['from b']);
  assert.equal((await service.listComments(owner, id, {})).items.length, 2);
});

// ------------------------------------------------------------------ reports

test('post reports: threshold hides the post, duplicates and races do not double count', { skip }, async () => {
  const owner = await user();
  const { id } = await service.create(owner, anon());
  const [r1, r2, r3, r4] = await Promise.all([user(), user(), user(), user()]);

  await Promise.all(Array.from({ length: 10 }, () => service.report(r1, id, { reason: 'SPAM' })));
  assert.deepEqual(await counts(id), { l: 0, b: 0, c: 0, r: 1, status: 'ACTIVE' });
  assert.deepEqual(await service.report(r1, id, { reason: 'SPAM' }), { reported: true, duplicate: true });

  await Promise.all([service.report(r2, id, { reason: 'SPAM' }), service.report(r3, id, { reason: 'HARASSMENT' })]);
  assert.deepEqual(await counts(id), { l: 0, b: 0, c: 0, r: 3, status: 'UNDER_REVIEW' });

  const viewer = await user();
  assert.equal((await service.list(viewer, { limit: 30 } as any)).items.some((p) => p.id === id), false);
  await assert.rejects(service.detail(viewer, id), NotFoundException);
  await assert.rejects(service.report(r4, id, { reason: 'SPAM' }), NotFoundException);
  await assert.rejects(service.report(owner, id, { reason: 'SPAM' }), NotFoundException);
});

test('a reporter no longer sees the post they reported', { skip }, async () => {
  const [owner, reporter] = [await user(), await user()];
  const { id } = await service.create(owner, anon());
  await service.report(reporter, id, { reason: 'OTHER' });
  assert.equal((await service.list(reporter, { limit: 30 } as any)).items.some((p) => p.id === id), false);
  await assert.rejects(service.detail(reporter, id), NotFoundException);
});

test('comment reports: threshold hides the comment and fixes the counter; moderator can restore', { skip }, async () => {
  const [owner, writer] = [await user(), await user()];
  const { id } = await service.create(owner, anon());
  const comment = await service.addComment(writer, id, { content: 'rude' });
  assert.equal((await counts(id)).c, 1);
  const reporters = await Promise.all([user(), user(), user()]);

  await Promise.all(reporters.slice(0, 2).map((r) => service.reportComment(r, id, comment.id, { reason: 'HARASSMENT' })));
  assert.equal((await service.listComments(owner, id, {})).items.length, 1);
  assert.deepEqual(
    await service.reportComment(reporters[0], id, comment.id, { reason: 'HARASSMENT' }),
    { reported: true, duplicate: true },
  );
  await service.reportComment(reporters[2], id, comment.id, { reason: 'SPAM' });

  assert.equal((await service.listComments(owner, id, {})).items.length, 0);
  assert.equal((await counts(id)).c, 0);
  await assert.rejects(service.reportComment(await user(), id, comment.id, { reason: 'SPAM' }), NotFoundException);

  // A comment under review is moderation evidence: its author cannot delete it,
  // and the failed attempt must not touch the counter or the evidence.
  const second = await service.addComment(writer, id, { content: 'fine' });
  await assert.rejects(service.removeComment(writer, id, comment.id), NotFoundException);
  assert.equal((await counts(id)).c, 1);
  assert.equal((await row(`SELECT status s, report_count r FROM campus_post_comments WHERE id=$1`, [comment.id])).s, 'UNDER_REVIEW');
  await service.removeComment(writer, id, second.id);
  assert.equal((await counts(id)).c, 0);

  // Restore a different hidden comment: counter is given back.
  const third = await service.addComment(writer, id, { content: 'maybe' });
  await Promise.all((await Promise.all([user(), user(), user()])).map((r) => service.reportComment(r, id, third.id, { reason: 'SPAM' })));
  assert.equal((await counts(id)).c, 0);
  const queue = await service.moderationQueue();
  assert.ok(queue.comments.some((c) => c.id === third.id));
  assert.equal(JSON.stringify(queue).includes(writer), false, 'queue never carries author identity');
  assert.equal((await service.decideComment(third.id, { action: 'RESTORE' })).status, 'ACTIVE');
  assert.equal((await counts(id)).c, 1);
  await assert.rejects(service.decideComment(third.id, { action: 'RESTORE' }), NotFoundException);
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_comment_reports WHERE comment_id=$1 AND status='DISMISSED'`, [third.id])).n), 3);
});

// ------------------------------------------- regression: comment moderation

async function hiddenComment(reportersCount = 3) {
  const [owner, writer] = [await user(), await user()];
  const { id } = await service.create(owner, anon());
  const comment = await service.addComment(writer, id, { content: 'contested' });
  const reporters = await Promise.all(Array.from({ length: reportersCount }, () => user()));
  for (const r of reporters) await service.reportComment(r, id, comment.id, { reason: 'SPAM' });
  return { owner, writer, postId: id, commentId: comment.id };
}
const commentRow = (id: string) =>
  row(`SELECT status, report_count r FROM campus_post_comments WHERE id=$1`, [id]);

test('REMOVE keeps the real report count as the record of the decision', { skip }, async () => {
  const { postId, commentId } = await hiddenComment(3);
  assert.deepEqual(await commentRow(commentId), { status: 'UNDER_REVIEW', r: 3 });
  assert.equal((await service.decideComment(commentId, { action: 'REMOVE' })).status, 'REMOVED');
  assert.deepEqual(await commentRow(commentId), { status: 'REMOVED', r: 3 });
  // The comment was already taken out of the counter when it was hidden.
  assert.equal((await counts(postId)).c, 0);
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_comment_reports WHERE comment_id=$1 AND status='UPHELD'`, [commentId])).n), 3);
});

test('RESTORE resets the report count because the reports were dismissed', { skip }, async () => {
  const { postId, commentId } = await hiddenComment(3);
  await service.decideComment(commentId, { action: 'RESTORE' });
  assert.deepEqual(await commentRow(commentId), { status: 'ACTIVE', r: 0 });
  assert.equal((await counts(postId)).c, 1);
});

test('an author cannot delete their own comment while it is under review or removed', { skip }, async () => {
  const { writer, postId, commentId } = await hiddenComment(3);
  await assert.rejects(service.removeComment(writer, postId, commentId), NotFoundException);
  assert.deepEqual(await commentRow(commentId), { status: 'UNDER_REVIEW', r: 3 });
  assert.equal((await counts(postId)).c, 0);

  await service.decideComment(commentId, { action: 'REMOVE' });
  await assert.rejects(service.removeComment(writer, postId, commentId), NotFoundException);
  assert.deepEqual(await commentRow(commentId), { status: 'REMOVED', r: 3 });
  assert.equal((await counts(postId)).c, 0);
});

test('a restored comment is deletable again and the counter stays exact', { skip }, async () => {
  const { writer, postId, commentId } = await hiddenComment(3);
  await service.decideComment(commentId, { action: 'RESTORE' });
  assert.equal((await counts(postId)).c, 1);
  assert.deepEqual(await service.removeComment(writer, postId, commentId), { deleted: true });
  assert.equal((await counts(postId)).c, 0);
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_post_comments WHERE id=$1`, [commentId])).n), 0);
});

test('only the comment owner can delete, and active comments still decrement exactly once', { skip }, async () => {
  const [owner, writer, stranger] = [await user(), await user(), await user()];
  const { id } = await service.create(owner, anon());
  const comment = await service.addComment(writer, id, { content: 'mine' });
  await assert.rejects(service.removeComment(stranger, id, comment.id), NotFoundException);
  assert.equal((await counts(id)).c, 1);
  const results = await Promise.allSettled(Array.from({ length: 6 }, () => service.removeComment(writer, id, comment.id)));
  assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
  assert.equal((await counts(id)).c, 0);
});

// --------------------------------------------------------------- moderation

test('moderators can restore or remove posts under review; others cannot touch them', { skip }, async () => {
  const owner = await user();
  const moderator = await user();
  const a = await service.create(owner, anon());
  const b = await service.create(owner, discoverable());
  for (const post of [a, b]) {
    for (const r of await Promise.all([user(), user(), user()])) await service.report(r, post.id, { reason: 'SPAM' });
    assert.equal((await counts(post.id)).status, 'UNDER_REVIEW');
  }
  // The author cannot edit or delete their way out of review.
  await assert.rejects(service.update(owner, a.id, { content: 'fix' }), NotFoundException);
  const mine = await service.listMine(owner, {});
  assert.deepEqual(mine.items.map((p) => p.id).sort(), [a.id, b.id].sort());

  const queue = await service.moderationQueue();
  assert.ok(queue.posts.some((p) => p.id === a.id && p.reasons.length === 3));
  assert.equal(JSON.stringify(queue).includes(owner), false);

  assert.deepEqual(await service.decidePost(moderator, a.id, { action: 'RESTORE' }), { postId: a.id, status: 'ACTIVE' });
  assert.deepEqual(await counts(a.id), { l: 0, b: 0, c: 0, r: 0, status: 'ACTIVE' });
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_post_reports WHERE post_id=$1 AND status='DISMISSED'`, [a.id])).n), 3);

  assert.deepEqual(await service.decidePost(moderator, b.id, { action: 'REMOVE' }), { postId: b.id, status: 'REMOVED' });
  const removed = await row(`SELECT status, discover_token t, moderated_by m FROM campus_posts WHERE id=$1`, [b.id]);
  assert.deepEqual(removed, { status: 'REMOVED', t: null, m: moderator });
  await assert.rejects(service.removePost(owner, b.id), NotFoundException);
  await assert.rejects(service.decidePost(moderator, b.id, { action: 'RESTORE' }), NotFoundException);
  await assert.rejects(service.decidePost(moderator, a.id, { action: 'REMOVE' }), NotFoundException);
  await assert.rejects(service.decidePost(moderator, randomUUID(), { action: 'REMOVE' }), NotFoundException);
});

test('removing a post deletes its media rows and returns the files to delete', { skip }, async () => {
  const owner = await user();
  const moderator = await user();
  const post = await service.create(owner, anon());
  const key = `${randomUUID()}.png`;
  await ds.query(
    `INSERT INTO campus_post_media (post_id, storage_key, mime_type, size_bytes, width, height, position) VALUES ($1,$2,'image/png',10,5,5,0)`,
    [post.id, key],
  );
  for (const r of await Promise.all([user(), user(), user()])) await service.report(r, post.id, { reason: 'SPAM' });
  removedFiles.length = 0;
  await service.decidePost(moderator, post.id, { action: 'REMOVE' });
  assert.deepEqual(removedFiles, [key]);
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_post_media WHERE post_id=$1`, [post.id])).n), 0);
});

// ------------------------------------------------------------- edit / mine

test('editing: owner only, keeps persona, token and privacy mode', { skip }, async () => {
  const [owner, other] = [await user(), await user()];
  const created = await service.create(owner, discoverable());
  const before = await row(`SELECT author_alias a, author_avatar_seed s, discover_token t, anonymous_mode m FROM campus_posts WHERE id=$1`, [created.id]);
  const edited = await service.update(owner, created.id, { content: 'đã sửa', category: 'STUDY' });
  assert.equal(edited.content, 'đã sửa');
  assert.ok(edited.editedAt);
  assert.deepEqual(await row(`SELECT author_alias a, author_avatar_seed s, discover_token t, anonymous_mode m FROM campus_posts WHERE id=$1`, [created.id]), before);
  await assert.rejects(service.update(other, created.id, { content: 'hack' }), NotFoundException);
  assert.equal((await row(`SELECT content c FROM campus_posts WHERE id=$1`, [created.id])).c, 'đã sửa');
});

test('my posts: only mine, newest first, cursor pages are complete and unique', { skip }, async () => {
  const [me, other] = [await user(), await user()];
  const ids: string[] = [];
  for (let i = 0; i < 7; i += 1) ids.push((await service.create(me, anon({ content: `p${i}` }))).id);
  await service.create(other, anon());
  const seen: string[] = [];
  let cursor: string | undefined;
  do {
    const page = await service.listMine(me, { limit: 3, cursor } as any);
    assert.ok(page.items.every((p) => p.viewer.isAuthor));
    seen.push(...page.items.map((p) => p.id));
    cursor = page.nextCursor ?? undefined;
  } while (cursor);
  assert.deepEqual(seen, ids.slice().reverse());
});

// -------------------------------------------------------------- pagination

test('feed pagination is stable with identical timestamps and across the saved tab', { skip }, async () => {
  const [owner, reader] = [await user(), await user()];
  const author = await row(`SELECT id FROM campus_authors WHERE user_id=$1`, [(await service.create(owner, anon())).id && owner]);
  const stamp = '2030-01-01 10:00:00.123456';
  const category = 'MARKET'; // isolates this test's posts from the rest of the suite
  const ids: string[] = [];
  for (let i = 0; i < 14; i += 1) {
    const inserted = await row(
      `INSERT INTO campus_posts (author_id, content, category, anonymous_mode, status, author_alias, author_avatar_seed, created_at)
       VALUES ($1, $2, $3, 'FULLY_ANONYMOUS', 'ACTIVE', 'Ẩn danh #1111', 3, $4) RETURNING id`,
      [author.id, `same-${i}`, category, i < 10 ? stamp : '2030-01-01 10:00:01'],
    );
    ids.push(inserted.id);
  }
  const collect = async (tab: 'for-you' | 'saved') => {
    const out: string[] = [];
    let cursor: string | undefined;
    let pages = 0;
    do {
      const page = await service.list(reader, { tab, category, limit: 4, cursor } as any);
      out.push(...page.items.map((p) => p.id));
      cursor = page.nextCursor ?? undefined;
      pages += 1;
      assert.ok(pages < 20, 'cursor must terminate');
    } while (cursor);
    return out;
  };
  const feed = await collect('for-you');
  assert.equal(feed.length, 14);
  assert.equal(new Set(feed).size, 14, 'no duplicates and none skipped');

  for (const id of ids.slice(0, 9)) await service.bookmark(reader, id);
  const saved = await collect('saved');
  assert.equal(saved.length, 9);
  assert.equal(new Set(saved).size, 9);
  assert.ok(saved.every((id) => ids.includes(id)));
  const flags = await service.list(reader, { tab: 'saved', category, limit: 30 } as any);
  assert.ok(flags.items.every((p) => p.viewer.bookmarked));
});

test('feed hides blocked authors in both directions and respects category filters', { skip }, async () => {
  const [a, b, c] = [await user(), await user(), await user()];
  const pa = await service.create(a, anon({ category: 'EVENT' }));
  const pb = await service.create(b, anon({ category: 'EVENT' }));
  const pc = await service.create(c, anon({ category: 'EVENT' }));
  await ds.query(`INSERT INTO blocks (blocker_id, blocked_id) VALUES ($1, $2)`, [a, b]);
  const idsFor = async (u: string) =>
    (await service.list(u, { category: 'EVENT', limit: 30 } as any)).items.map((p) => p.id);
  assert.deepEqual((await idsFor(a)).filter((id) => [pa.id, pb.id, pc.id].includes(id)).sort(), [pa.id, pc.id].sort());
  assert.deepEqual((await idsFor(b)).filter((id) => [pa.id, pb.id, pc.id].includes(id)).sort(), [pb.id, pc.id].sort());
  await assert.rejects(service.detail(a, pb.id), NotFoundException);
  assert.equal((await service.list(a, { category: 'STUDY', limit: 30 } as any)).items.some((p) => p.id === pc.id), false);
});

// ------------------------------------------------- settings / discovery

test('disabling discovery revokes every token; enabling issues brand new ones', { skip }, async () => {
  const [owner, viewer] = [await user(), await user()];
  const p1 = await service.create(owner, discoverable());
  const p2 = await service.create(owner, discoverable());
  const hidden = await service.create(owner, anon());
  const [t1, t2] = [p1.author.authorToken!, p2.author.authorToken!];
  assert.ok(t1 && t2 && t1 !== t2);
  assert.equal((await service.authorDetail(viewer, t1)).token, t1);

  const off = await service.updateSettings(owner, { discoveryEnabled: false });
  assert.equal(off.discoveryEnabled, false);
  assert.equal(off.revokedTokens, 2);
  for (const t of [t1, t2]) await assert.rejects(service.authorDetail(viewer, t), NotFoundException);
  for (const t of [t1, t2]) await assert.rejects(service.swipeAuthor(viewer, t, { action: 'LIKE' }), NotFoundException);
  const feed = (await service.list(viewer, { limit: 30 } as any)).items.find((p) => p.id === p1.id)!;
  assert.equal(feed.author.discoverable, false);
  assert.equal('authorToken' in feed.author, false);
  assert.equal(JSON.stringify(await service.listMine(owner, {})).includes(t1), false);
  assert.equal((await counts(hidden.id)).status, 'ACTIVE');

  await assert.rejects(service.create(owner, discoverable()), ConflictException);
  await service.create(owner, anon()); // fully anonymous posts are still fine

  const on = await service.updateSettings(owner, { discoveryEnabled: true });
  assert.equal(on.discoveryEnabled, true);
  assert.equal(on.issuedTokens, 2);
  const fresh = (await service.list(viewer, { limit: 30 } as any)).items.find((p) => p.id === p1.id)!;
  assert.ok(fresh.author.authorToken);
  assert.notEqual(fresh.author.authorToken, t1, 'a revoked link never comes back to life');
  await assert.rejects(service.authorDetail(viewer, t1), NotFoundException);
  assert.equal((await service.authorDetail(viewer, fresh.author.authorToken!)).alias, p1.author.alias);
});

test('visibility settings control what the author card and posts disclose', { skip }, async () => {
  const [owner, viewer] = [await user(), await user()];
  await ds.query(
    `INSERT INTO profiles (user_id, nickname, full_name, age, gender, school, campus, faculty, interests, avatar_seed, mode, profile_complete)
     VALUES ($1,'Tên thật','Họ Tên Thật',20,'OTHER','PTIT','Hà Nội','CNTT','{a,b,c,d}',9,'DATING',true)`,
    [owner],
  );
  const post = await service.create(owner, discoverable());
  const token = post.author.authorToken!;
  const hasKeys = async () => Object.keys(await service.authorDetail(viewer, token)).sort();
  assert.deepEqual(await hasKeys(), ['alias', 'avatarSeed', 'bio', 'discoverable', 'token']);
  assert.equal(post.campus, '');

  await service.updateSettings(owner, { showFaculty: true, showCampus: true, showInterests: true });
  const card: any = await service.authorDetail(viewer, token);
  assert.equal(card.faculty, 'CNTT');
  assert.equal(card.campus, 'Hà Nội');
  assert.ok(card.interests.length <= 2);
  assert.equal((await service.detail(viewer, post.id)).campus, 'Hà Nội');
  const json = JSON.stringify(card);
  for (const secret of ['Họ Tên Thật', 'Tên thật', 'PTIT', owner]) assert.equal(json.includes(secret), false, secret);

  await service.updateSettings(owner, { showFaculty: false, showCampus: false, showInterests: false });
  assert.deepEqual(await hasKeys(), ['alias', 'avatarSeed', 'bio', 'discoverable', 'token']);
  assert.equal((await service.detail(viewer, post.id)).campus, '');
  assert.deepEqual(await service.getSettings(owner), {
    discoveryEnabled: true, showFaculty: false, showCampus: false, showInterests: false, discoverablePosts: 1,
  });
});

test('concurrent first requests create exactly one author record', { skip }, async () => {
  const fresh = await user();
  await Promise.all(Array.from({ length: 10 }, () => service.getSettings(fresh)));
  assert.equal(Number((await row(`SELECT count(*) n FROM campus_authors WHERE user_id=$1`, [fresh])).n), 1);
});

test('deleting a post revokes its token and keeps counters honest', { skip }, async () => {
  const [owner, viewer] = [await user(), await user()];
  const post = await service.create(owner, discoverable());
  await service.removePost(owner, post.id);
  await assert.rejects(service.authorDetail(viewer, post.author.authorToken!), NotFoundException);
  assert.equal((await row(`SELECT discover_token t FROM campus_posts WHERE id=$1`, [post.id])).t, null);
  await assert.rejects(service.removePost(owner, post.id), NotFoundException);
});
