import assert from 'node:assert/strict';
import { test } from 'node:test';
import { BadRequestException, ConflictException, NotFoundException } from '@nestjs/common';
import {
  CampusAuthor,
  CampusPost,
  CampusPostComment,
  Profile,
  Message,
  Match,
  Notification,
} from '../src/database/entities';
import { CampusService } from '../src/campus/campus.service';
import { ChatService } from '../src/chat/chat.service';
import { ProfilesService } from '../src/profiles/profiles.service';
import { SocialService } from '../src/social/social.service';
import {
  pickPublicInterests,
  toCampusAuthorProfile,
  toCampusCommentView,
  toCampusPostView,
} from '../src/campus/campus.view';

const TOKEN = '3f2b8c1e-5d4a-4e7b-9a10-2c6d8e9f0a1b';
const MATCH_ID = '9b1d6c2e-7a3f-4c58-8e21-5d0f4a7b9c13';
const POST_ID = '6a1f0c52-8d3e-4b7a-9c21-0e5d7f3a1b44';
const COMMENT_ID = 'c0a8e3d1-2b4f-4a6c-8d9e-1f2a3b4c5d6e';

function author(overrides: Partial<CampusAuthor> = {}): CampusAuthor {
  return {
    id: 'author-token-1',
    userId: 'private-user-1',
    alias: 'Account Alias',
    avatarSeed: 3,
    bio: '',
    showFaculty: false,
    showCampus: false,
    showInterests: false,
    discoveryEnabled: true,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  } as CampusAuthor;
}

function profile(overrides: Partial<Profile> = {}): Profile {
  return {
    id: 'private-profile-1',
    userId: 'private-user-1',
    nickname: 'Ten That',
    fullName: 'Nguyen Van That',
    age: 20,
    gender: 'OTHER',
    school: 'PTIT',
    campus: 'Ha Noi',
    faculty: 'CNTT',
    major: 'KTPM',
    academicYear: 'K66',
    alumni: false,
    gpa: 3.4,
    gpaTier: 'GOOD',
    bio: 'Bio that',
    interests: ['Cà phê', 'Bóng đá', 'Âm nhạc', 'Du lịch'],
    avatarSeed: 7,
    mode: 'DATING',
    lat: 0,
    lng: 0,
    location: null,
    verified: true,
    showGpaPublic: true,
    showLocationPublic: true,
    profileComplete: true,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  } as Profile;
}

function post(
  mode: 'FULLY_ANONYMOUS' | 'DISCOVERABLE',
  overrides: Partial<CampusPost> = {},
): CampusPost {
  return {
    id: POST_ID,
    authorId: 'author-token-1',
    author: author(),
    authorAlias: 'Sao Băng #1234',
    authorAvatarSeed: 11,
    discoverToken: mode === 'DISCOVERABLE' ? TOKEN : null,
    content: 'Noi dung',
    category: 'GENERAL',
    anonymousMode: mode,
    campus: '',
    status: 'ACTIVE',
    likeCount: 2,
    commentCount: 1,
    bookmarkCount: 0,
    reportCount: 0,
    editedAt: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    media: [],
    ...overrides,
  } as CampusPost;
}

const noViewer = { liked: false, bookmarked: false, isAuthor: false };

// ---------------------------------------------------------------- views

test('fully anonymous post never exposes a token or private identity', () => {
  const view = toCampusPostView(post('FULLY_ANONYMOUS'), noViewer);
  assert.equal(view.author.alias, 'Sao Băng #1234');
  assert.equal(view.author.discoverable, false);
  assert.equal('authorToken' in view.author, false);
  const json = JSON.stringify(view);
  for (const secret of ['private-user-1', 'author-token-1', 'private-profile-1']) {
    assert.equal(json.includes(secret), false);
  }
});

test('discoverable post exposes only the per-post token, not the account author id', () => {
  const view = toCampusPostView(post('DISCOVERABLE'), noViewer);
  assert.equal(view.author.authorToken, TOKEN);
  assert.equal(view.author.discoverable, true);
  const json = JSON.stringify(view);
  assert.equal(json.includes('author-token-1'), false);
  assert.equal(json.includes('private-user-1'), false);
});

test('opting out of discovery removes the token from existing posts', () => {
  const view = toCampusPostView(
    post('DISCOVERABLE', { author: author({ discoveryEnabled: false }) }),
    noViewer,
  );
  assert.equal(view.author.discoverable, false);
  assert.equal('authorToken' in view.author, false);
});

test('author card hides everything that was not explicitly enabled', () => {
  const view = toCampusAuthorProfile(post('DISCOVERABLE'), author(), profile());
  assert.equal(view.token, TOKEN);
  assert.equal(view.alias, 'Sao Băng #1234');
  assert.equal(view.avatarSeed, 11);
  assert.equal(view.bio, '');
  for (const key of ['age', 'mode', 'faculty', 'campus', 'interests']) {
    assert.equal(key in view, false, key);
  }
  const json = JSON.stringify(view);
  for (const secret of [
    'Nguyen Van That',
    'Ten That',
    'Bio that',
    'PTIT',
    'private-user-1',
    'private-profile-1',
    'author-token-1',
  ]) {
    assert.equal(json.includes(secret), false, secret);
  }
});

test('author card shows only the fields the author enabled, with a limited interest sample', () => {
  const view = toCampusAuthorProfile(
    post('DISCOVERABLE'),
    author({ showFaculty: true, showInterests: true }),
    profile(),
  );
  assert.equal(view.faculty, 'CNTT');
  assert.equal('campus' in view, false);
  assert.ok(view.interests && view.interests.length <= 2);
  assert.deepEqual(
    view.interests,
    pickPublicInterests(profile().interests, TOKEN),
  );
  for (const item of view.interests ?? []) {
    assert.ok(profile().interests.includes(item));
  }
});

test('comment view uses the thread persona and no identifiers', () => {
  const comment = {
    id: 'comment-1',
    postId: 'post-1',
    authorId: 'author-token-1',
    authorUserId: 'private-user-1',
    authorAlias: 'Mây Nhỏ #4321',
    authorAvatarSeed: 5,
    content: 'Hay qua',
    createdAt: new Date(),
  } as CampusPostComment;
  const view = toCampusCommentView(comment, 'private-user-1');
  assert.equal(view.isMine, true);
  assert.equal(view.author.alias, 'Mây Nhỏ #4321');
  assert.equal(view.author.avatarSeed, 5);
  const json = JSON.stringify(view);
  assert.equal(json.includes('private-user-1'), false);
  assert.equal(json.includes('author-token-1'), false);
});

// -------------------------------------------------------------- service

function fakePostRepo(items: CampusPost[]) {
  return {
    findOne: async ({ where }: { where: any }) =>
      items.find((item) =>
        Object.entries(where).every(([k, v]) => (item as any)[k] === v),
      ) ?? null,
    save: async (item: CampusPost) => item,
    update: async (criteria: any, patch: any) => {
      const row = items.find((item) => item.id === criteria.id && item.status === criteria.status);
      if (row) Object.assign(row, patch);
      return { affected: row ? 1 : 0 };
    },
    count: async () => items.length,
    create: (item: Partial<CampusPost>) => item,
  };
}

function fakeStore(overrides: Record<string, any> = {}) {
  return {
    flaggedPostIds: async () => new Set<string>(),
    deleteMedia: async () => undefined,
    updateSettings: async () => ({ revoked: 0, issued: 0 }),
    ...overrides,
  };
}

function buildService(overrides: {
  posts?: any;
  comments?: any;
  authors?: any;
  profiles?: any;
  blocks?: any;
  social?: any;
  store?: any;
  campusReports?: any;
}) {
  return new CampusService(
    (overrides.authors ?? {}) as any,
    (overrides.posts ?? {}) as any,
    (overrides.comments ?? {}) as any,
    {} as any,
    (overrides.campusReports ?? { findOne: async () => null, find: async () => [] }) as any,
    {} as any,
    (overrides.profiles ?? {}) as any,
    (overrides.blocks ?? { findOne: async () => null, find: async () => [] }) as any,
    (overrides.social ?? {}) as any,
    {
      remove: async () => undefined,
      urlFor: (key: string) => `/campus/media/${key}?exp=1&sig=x`,
    } as any,
    (overrides.store ?? fakeStore()) as any,
  );
}

test('only the post owner can delete a post and its token is revoked', async () => {
  const target = post('DISCOVERABLE');
  const service = buildService({ posts: fakePostRepo([target]) });
  await assert.rejects(service.removePost('another-user', target.id), NotFoundException);
  const result = await service.removePost('private-user-1', target.id);
  assert.deepEqual(result, { deleted: true });
  assert.equal(target.status, 'DELETED');
  assert.equal(target.content, '');
  assert.equal(target.discoverToken, null);
});

test('a moderator-removed post cannot be touched by its author', async () => {
  const target = post('FULLY_ANONYMOUS', { status: 'REMOVED' });
  const service = buildService({ posts: fakePostRepo([target]) });
  await assert.rejects(service.removePost('private-user-1', target.id), NotFoundException);
  assert.equal(target.status, 'REMOVED');
});

test('only the comment owner can delete a comment (decided by the store)', async () => {
  const target = post('FULLY_ANONYMOUS');
  const service = buildService({
    posts: fakePostRepo([target]),
    store: fakeStore({
      deleteComment: async (_c: string, _p: string, userId: string) =>
        userId === 'private-user-1',
    }),
  });
  await assert.rejects(
    service.removeComment('another-user', target.id, COMMENT_ID),
    NotFoundException,
  );
  assert.deepEqual(
    await service.removeComment('private-user-1', target.id, COMMENT_ID),
    { deleted: true },
  );
});

function createFixtures(accountAuthor: CampusAuthor) {
  const saved: any[] = [];
  const settingsCalls: any[] = [];
  const service = buildService({
    authors: { findOne: async () => accountAuthor },
    profiles: { findOne: async () => profile() },
    store: fakeStore({
      createPost: async (row: any) => {
        const created = { ...row, id: `${POST_ID.slice(0, -1)}${saved.length}`, media: [] };
        created.createdAt = new Date();
        saved.push(created);
        return created;
      },
      updateSettings: async (_id: string, patch: any) => {
        settingsCalls.push(patch);
        return { revoked: 0, issued: 0 };
      },
    }),
  });
  return { service, saved, settingsCalls };
}

const publicPost = (mode: string) =>
  ({ content: 'xin chao', category: 'GENERAL', anonymousMode: mode, images: [] }) as any;

test('new posts get an independent persona and per-post token', async () => {
  const { service, saved } = createFixtures(author());
  const first = await service.create('private-user-1', publicPost('DISCOVERABLE'));
  const second = await service.create('private-user-1', publicPost('DISCOVERABLE'));
  for (const row of saved) {
    assert.match(row.authorAlias, /#\d{4}$/);
    assert.notEqual(row.authorAvatarSeed, profile().avatarSeed);
    assert.equal(row.campus, '');
  }
  assert.notEqual(first.author.authorToken, second.author.authorToken);
  assert.notEqual(first.author.authorToken, 'author-token-1');
  const json = JSON.stringify([first, second]);
  for (const secret of ['Ten That', 'Bio that', 'private-user-1']) {
    assert.equal(json.includes(secret), false, secret);
  }
});

test('fully anonymous posts get no discover token', async () => {
  const { service, saved } = createFixtures(author());
  const view = await service.create('private-user-1', publicPost('FULLY_ANONYMOUS'));
  assert.equal(saved[0].discoverToken, null);
  assert.equal('authorToken' in view.author, false);
});

test('campus is only published when the author allowed it', async () => {
  const { service, saved } = createFixtures(author({ showCampus: true }));
  await service.create('private-user-1', publicPost('FULLY_ANONYMOUS'));
  assert.equal(saved[0].campus, 'Ha Noi');
});

test('a discoverable post enables discovery once, but never overrides an explicit opt-out', async () => {
  const fresh = createFixtures(author({ discoveryEnabled: false }));
  await fresh.service.create('private-user-1', publicPost('DISCOVERABLE'));
  assert.deepEqual(fresh.settingsCalls, [{ discoveryEnabled: true }]);

  const optedOut = createFixtures(
    author({ discoveryEnabled: false, discoveryDisabledByUser: true } as any),
  );
  await assert.rejects(
    optedOut.service.create('private-user-1', publicPost('DISCOVERABLE')),
    ConflictException,
  );
  assert.equal(optedOut.saved.length, 0);
  // Fully anonymous posts remain allowed while discovery is off.
  await optedOut.service.create('private-user-1', publicPost('FULLY_ANONYMOUS'));
  assert.equal(optedOut.saved.length, 1);
});

test('empty posts are rejected', async () => {
  const { service } = createFixtures(author());
  await assert.rejects(
    service.create('private-user-1', { category: 'GENERAL', anonymousMode: 'FULLY_ANONYMOUS' } as any),
    BadRequestException,
  );
});

function commentFixtures() {
  const target = post('FULLY_ANONYMOUS');
  const stored: any[] = [];
  const comments = {
    findOne: async ({ where }: any) =>
      stored.find((c) => Object.entries(where).every(([k, v]) => c[k] === v)) ?? null,
  };
  const store = fakeStore({
    addComment: async (values: any) => {
      const row = { id: `c0a8e3d1-2b4f-4a6c-8d9e-1f2a3b4c5d0${stored.length}`, createdAt: new Date(), ...values };
      stored.push(row);
      return row.id;
    },
  });
  const as = (userId: string, authorRow: CampusAuthor) =>
    buildService({
      posts: fakePostRepo([target]),
      comments,
      authors: { findOne: async () => authorRow },
      store,
    });
  return { target, as, stored };
}

test('comment personas are stable inside a thread and the owner appears as OP', async () => {
  const { target, as } = commentFixtures();
  const viewerRow = author({ id: 'author-other', userId: 'viewer-user' });
  const one = await as('viewer-user', viewerRow).addComment('viewer-user', target.id, { content: 'a' });
  const two = await as('viewer-user', viewerRow).addComment('viewer-user', target.id, { content: 'b' });
  assert.equal(one.author.alias, two.author.alias);
  assert.equal(one.author.avatarSeed, two.author.avatarSeed);
  assert.equal(JSON.stringify([one, two]).includes('viewer-user'), false);

  const op = await as('private-user-1', author()).addComment('private-user-1', target.id, { content: 'c' });
  assert.equal(op.author.alias, target.authorAlias);
  assert.equal(op.author.avatarSeed, target.authorAvatarSeed);
});

test('whitespace-only comments are rejected', async () => {
  const { target, as } = commentFixtures();
  await assert.rejects(
    as('viewer-user', author({ id: 'x', userId: 'viewer-user' })).addComment('viewer-user', target.id, { content: '   ' }),
    BadRequestException,
  );
});

// ------------------------------------------------------------ edit own post

test('editing changes text and category only, for the owner of an active post', async () => {
  const target = post('DISCOVERABLE');
  const service = buildService({ posts: fakePostRepo([target]) });
  const view = await service.update('private-user-1', target.id, {
    content: '  moi  ',
    category: 'STUDY',
  });
  assert.equal(view.content, 'moi');
  assert.equal(view.category, 'STUDY');
  assert.ok(view.editedAt);
  // Privacy mode, persona and token are untouched.
  assert.equal(target.anonymousMode, 'DISCOVERABLE');
  assert.equal(target.discoverToken, TOKEN);
  assert.equal(target.authorAlias, 'Sao Băng #1234');
  assert.equal(JSON.stringify(view).includes('private-user-1'), false);
});

test('editing is refused for strangers, unknown ids, empty bodies and non-active posts', async () => {
  const target = post('FULLY_ANONYMOUS');
  const service = buildService({ posts: fakePostRepo([target]) });
  await assert.rejects(service.update('another-user', target.id, { content: 'x' }), NotFoundException);
  await assert.rejects(service.update('private-user-1', 'nope', { content: 'x' }), NotFoundException);
  await assert.rejects(service.update('private-user-1', target.id, {}), BadRequestException);
  await assert.rejects(service.update('private-user-1', target.id, { content: '  ' }), BadRequestException);
  assert.equal(target.content, 'Noi dung');

  for (const status of ['UNDER_REVIEW', 'REMOVED', 'DELETED'] as const) {
    const blocked = post('FULLY_ANONYMOUS', { status });
    const svc = buildService({ posts: fakePostRepo([blocked]) });
    await assert.rejects(svc.update('private-user-1', blocked.id, { content: 'x' }), NotFoundException, status);
    assert.equal(blocked.content, 'Noi dung');
  }
});

// -------------------------------------------------------------- settings

function settingsFixtures(row: CampusAuthor) {
  const calls: any[] = [];
  const service = buildService({
    authors: { findOne: async () => row },
    profiles: { findOne: async () => profile() },
    posts: fakePostRepo([post('DISCOVERABLE')]),
    store: fakeStore({
      updateSettings: async (_id: string, patch: any, campus: string | null) => {
        calls.push({ patch, campus });
        Object.assign(row, patch);
        return { revoked: patch.discoveryEnabled === false ? 2 : 0, issued: 0 };
      },
    }),
  });
  return { service, calls };
}

test('turning discovery off records the opt-out and reports revoked tokens', async () => {
  const { service, calls } = settingsFixtures(author({ discoveryEnabled: true }));
  const result = await service.updateSettings('private-user-1', { discoveryEnabled: false });
  assert.deepEqual(calls[0].patch, { discoveryEnabled: false, discoveryDisabledByUser: true });
  assert.equal(result.discoveryEnabled, false);
  assert.equal(result.revokedTokens, 2);
});

test('turning discovery back on clears the opt-out flag', async () => {
  const { service, calls } = settingsFixtures(author({ discoveryEnabled: false }));
  await service.updateSettings('private-user-1', { discoveryEnabled: true });
  assert.deepEqual(calls[0].patch, { discoveryEnabled: true, discoveryDisabledByUser: false });
});

test('visibility flags are applied; campus on posts follows showCampus', async () => {
  const on = settingsFixtures(author({ showCampus: false }));
  await on.service.updateSettings('private-user-1', { showCampus: true, showFaculty: true });
  assert.deepEqual(on.calls[0].patch, { showCampus: true, showFaculty: true });
  assert.equal(on.calls[0].campus, 'Ha Noi');

  const off = settingsFixtures(author({ showCampus: true }));
  await off.service.updateSettings('private-user-1', { showCampus: false });
  assert.equal(off.calls[0].campus, '');

  const same = settingsFixtures(author({ showCampus: true }));
  await same.service.updateSettings('private-user-1', { showFaculty: true });
  assert.equal(same.calls[0].campus, null);
});

test('settings output never contains identifiers', async () => {
  const { service } = settingsFixtures(author());
  const json = JSON.stringify(await service.getSettings('private-user-1'));
  for (const secret of ['private-user-1', 'author-token-1']) {
    assert.equal(json.includes(secret), false, secret);
  }
});

// --------------------------------------------------------------- reports

test('reporting: not your own post/comment, duplicates are idempotent and invisible', async () => {
  const target = post('FULLY_ANONYMOUS');
  let created = true;
  const service = buildService({
    posts: fakePostRepo([target]),
    comments: {
      findOne: async () => ({ id: COMMENT_ID, postId: target.id, authorUserId: 'commenter', status: 'ACTIVE' }),
    },
    store: fakeStore({
      reportPost: async () => ({ created, underReview: false }),
      reportComment: async () => ({ created, underReview: false }),
    }),
  });
  await assert.rejects(service.report('private-user-1', target.id, { reason: 'SPAM' }), BadRequestException);
  assert.deepEqual(await service.report('viewer-user', target.id, { reason: 'SPAM' }), { reported: true });
  created = false;
  assert.deepEqual(await service.report('viewer-user', target.id, { reason: 'SPAM' }), {
    reported: true,
    duplicate: true,
  });
  assert.deepEqual(
    await service.reportComment('viewer-user', target.id, COMMENT_ID, { reason: 'SPAM' }),
    { reported: true, duplicate: true },
  );
  await assert.rejects(
    service.reportComment('commenter', target.id, COMMENT_ID, { reason: 'SPAM' }),
    BadRequestException,
  );
});

test('comments that are hidden or missing cannot be reported', async () => {
  const target = post('FULLY_ANONYMOUS');
  for (const found of [null, { id: COMMENT_ID, postId: target.id, authorUserId: 'c', status: 'UNDER_REVIEW' }]) {
    const service = buildService({
      posts: fakePostRepo([target]),
      comments: { findOne: async () => found },
    });
    await assert.rejects(
      service.reportComment('viewer-user', target.id, COMMENT_ID, { reason: 'SPAM' }),
      NotFoundException,
    );
  }
});

// ------------------------------------------------ input validation (400/404)

test('malformed ids and cursors give 404/400, never a database error', async () => {
  const service = buildService({
    posts: fakePostRepo([post('FULLY_ANONYMOUS')]),
    authors: { findOne: async () => author() },
  });
  await assert.rejects(service.detail('viewer-user', 'not-a-uuid'), NotFoundException);
  await assert.rejects(service.like('viewer-user', "1' OR '1'='1"), NotFoundException);
  await assert.rejects(service.decidePost('mod', 'nope', { action: 'REMOVE' }), NotFoundException);
  await assert.rejects(service.decideComment('nope', { action: 'REMOVE' }), NotFoundException);

  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const bad = [
    '%%%not-base64%%%',
    Buffer.from('not json').toString('base64url'),
    encode({ createdAt: 'yesterday', id: POST_ID }),
    encode({ createdAt: new Date().toISOString(), id: "x'; DROP TABLE campus_posts;--" }),
    encode({ createdAt: new Date().toISOString() }),
    encode(null),
    encode([1, 2]),
  ];
  for (const cursor of bad) {
    await assert.rejects(service.list('viewer-user', { cursor } as any), BadRequestException, cursor);
    await assert.rejects(service.listMine('viewer-user', { cursor } as any), BadRequestException, cursor);
  }
});

// ------------------------------------------------------- discover author

const swipeDto = { action: 'LIKE' } as const;

function discoverService(found: CampusPost | null, extra: any = {}) {
  return buildService({
    posts: {
      findOne: async ({ where }: any) =>
        found && where.discoverToken === found.discoverToken ? found : null,
    },
    profiles: { findOne: async () => profile() },
    ...extra,
  });
}

test('malformed, unknown, anonymous, opted-out and inactive tokens all give the same 404', async () => {
  const cases: Array<[string, CampusPost | null]> = [
    ['not-a-uuid', post('DISCOVERABLE')],
    [TOKEN, null],
    [TOKEN, post('FULLY_ANONYMOUS', { discoverToken: TOKEN })],
    [TOKEN, post('DISCOVERABLE', { author: author({ discoveryEnabled: false }) })],
    [TOKEN, post('DISCOVERABLE', { status: 'UNDER_REVIEW' })],
    [TOKEN, post('DISCOVERABLE', { status: 'DELETED', discoverToken: null })],
  ];
  for (const [token, found] of cases) {
    const service = discoverService(found);
    await assert.rejects(service.authorDetail('viewer-user', token), NotFoundException);
    await assert.rejects(
      service.swipeAuthor('viewer-user', token, swipeDto),
      NotFoundException,
    );
  }
});

test('the account author id is not a valid discover token', async () => {
  const service = discoverService(post('DISCOVERABLE'));
  await assert.rejects(
    service.authorDetail('viewer-user', 'author-token-1'),
    NotFoundException,
  );
});

test('author detail is limited and carries no compatibility score', async () => {
  const view: any = await discoverService(post('DISCOVERABLE')).authorDetail(
    'viewer-user',
    TOKEN,
  );
  assert.equal('compatibility' in view, false);
  assert.equal('age' in view, false);
  assert.equal(JSON.stringify(view).includes('private-user-1'), false);
});

test('blocked users can neither view nor swipe a discoverable author', async () => {
  const service = discoverService(post('DISCOVERABLE'), {
    blocks: { findOne: async () => ({ id: 'block-1' }), find: async () => [] },
  });
  await assert.rejects(service.authorDetail('viewer-user', TOKEN), NotFoundException);
  await assert.rejects(
    service.swipeAuthor('viewer-user', TOKEN, swipeDto),
    NotFoundException,
  );
});

test('an author cannot swipe themselves through their own token', async () => {
  await assert.rejects(
    discoverService(post('DISCOVERABLE')).swipeAuthor('private-user-1', TOKEN, swipeDto),
    BadRequestException,
  );
});

test('discoverable swipe reuses the match logic anonymously', async () => {
  let captured: any = null;
  const service = discoverService(post('DISCOVERABLE'), {
    social: {
      swipe: async (userId: string, dto: any, options: any) => {
        captured = { userId, dto, options };
        return { matched: true, match: { id: 'match-1' } };
      },
    },
  });
  const result = await service.swipeAuthor('viewer-user', TOKEN, swipeDto);
  assert.deepEqual(result, { anonymous: true, matched: true, matchId: 'match-1' });
  assert.equal(captured.dto.targetUserId, 'private-user-1');
  assert.equal(captured.options.anonymous, true);
  assert.equal(JSON.stringify(result).includes('private-user-1'), false);
});

// ---------------------------------------------------------- swipe / match

function socialFixtures(options: { blocked?: boolean } = {}) {
  const storedSwipes: any[] = [];
  const storedMatches: any[] = [];
  const storedNotifications: any[] = [];
  const storedBlocks: any[] = [];
  const state = { messageCount: 0 };
  const viewer = profile({
    id: 'viewer-profile',
    userId: 'viewer-user',
    nickname: 'Viewer',
    fullName: 'Viewer Name',
  });
  const target = profile();
  const profiles = [viewer, target];

  const matches = {
    findOne: async ({ where }: any) =>
      where.id
        ? storedMatches.find((m) => m.id === where.id) ?? null
        : storedMatches.find(
            (m) => m.userAId === where.userAId && m.userBId === where.userBId,
          ) ?? null,
    save: async (item: any) => {
      if (!item.id) item.id = MATCH_ID;
      const existing = storedMatches.find((row) => row.id === item.id);
      if (existing) Object.assign(existing, item);
      else storedMatches.push(item);
      return item;
    },
    create: (item: any) => ({
      revealOptInA: false,
      revealOptInB: false,
      healthLevel: 'FADING',
      unmatchedAt: null,
      lastMessageAt: null,
      createdAt: new Date(),
      ...item,
    }),
  };
  const swipes = {
    findOne: async ({ where }: any) =>
      storedSwipes.find((item) =>
        Object.entries(where).every(([key, value]) => item[key] === value),
      ) ?? null,
    save: async (item: any) => {
      if (!item.id) item.id = `swipe-${storedSwipes.length + 1}`;
      const existing = storedSwipes.find(
        (row) =>
          row.fromUserId === item.fromUserId &&
          row.targetUserId === item.targetUserId,
      );
      if (existing) Object.assign(existing, item);
      else storedSwipes.push(item);
      return item;
    },
    create: (item: any) => item,
  };
  const notifications = {
    save: async (item: any) => {
      storedNotifications.push(item);
      return item;
    },
    create: (item: any) => item,
  };
  const social = new SocialService(
    swipes as any,
    matches as any,
    {
      findOne: async ({ where }: any) =>
        profiles.find((item) => item.userId === where.userId || item.id === where.id) ?? null,
      find: async () => profiles,
    } as any,
    {
      findOne: async () => null,
      count: async () => state.messageCount,
    } as any,
    notifications as any,
    {
      findOne: async () => (options.blocked ? { id: 'block-1' } : null),
      save: async (item: any) => {
        storedBlocks.push(item);
        return item;
      },
      create: (item: any) => item,
    } as any,
    {} as any,
  );
  return {
    social,
    state,
    storedSwipes,
    storedMatches,
    storedNotifications,
    storedBlocks,
    viewer,
    target,
  };
}

const AUTHOR = 'private-user-1';
const VIEWER = 'viewer-user';
const campusLike = { targetUserId: AUTHOR, action: 'LIKE' } as const;
const likeViewer = { targetUserId: VIEWER, action: 'LIKE' } as const;

function assertAnonymousNotifications(notifications: any[]) {
  assert.equal(notifications.length, 2);
  for (const n of notifications) {
    assert.equal(n.title, 'Bạn có match ẩn danh');
    assert.equal('profileId' in n.data, false);
    assert.equal(n.body.includes('Nguyen Van That'), false);
    assert.equal(n.body.includes('Viewer Name'), false);
  }
}

test('author swipes first, viewer swipes via Campus: anonymous match, masked counterpart', async () => {
  const env = socialFixtures();
  await env.social.swipe(AUTHOR, likeViewer);
  const result: any = await env.social.swipe(VIEWER, campusLike, { anonymous: true });

  assert.equal(result.matched, true);
  assert.equal(env.storedMatches.length, 1);
  assert.equal(env.storedMatches[0].anonymous, true);
  assertAnonymousNotifications(env.storedNotifications);
  assert.equal(result.match.anonymous, true);
  assert.equal(result.match.other.anonymous, true);
  assert.equal(result.match.compatibilityScore, 0);
  const json = JSON.stringify(result);
  for (const secret of ['private-user-1', 'private-profile-1', 'Ten That', 'Nguyen Van That']) {
    assert.equal(json.includes(secret), false, secret);
  }
});

test('viewer likes via Campus first, author likes back through ordinary discovery: still anonymous', async () => {
  const env = socialFixtures();
  await env.social.swipe(VIEWER, campusLike, { anonymous: true });
  const result: any = await env.social.swipe(AUTHOR, likeViewer);

  assert.equal(result.matched, true);
  assert.equal(env.storedMatches[0].anonymous, true);
  assertAnonymousNotifications(env.storedNotifications);
  assert.equal(result.match.other.anonymous, true);
  assert.equal(JSON.stringify(result).includes('Viewer Name'), false);
  assert.equal(JSON.stringify(result).includes('viewer-profile'), false);
});

test('ordinary mutual likes stay non-anonymous and keep existing behaviour', async () => {
  const env = socialFixtures();
  await env.social.swipe(AUTHOR, likeViewer);
  const result: any = await env.social.swipe(VIEWER, campusLike);

  assert.equal(env.storedMatches[0].anonymous, false);
  assert.equal(result.match.other.nickname, 'Ten That');
  for (const n of env.storedNotifications) {
    assert.equal(n.title, 'Bạn có match mới');
    assert.equal('profileId' in n.data, true);
  }
});

test('duplicate likes are idempotent and the anonymous origin cannot be downgraded', async () => {
  const env = socialFixtures();
  await env.social.swipe(AUTHOR, likeViewer);
  await env.social.swipe(VIEWER, campusLike, { anonymous: true });
  await env.social.swipe(VIEWER, campusLike, { anonymous: true });
  await env.social.swipe(VIEWER, campusLike);

  assert.equal(env.storedSwipes.filter((s) => s.fromUserId === VIEWER).length, 1);
  assert.equal(env.storedSwipes.find((s) => s.fromUserId === VIEWER).anonymous, true);
  assert.equal(env.storedMatches.length, 1);
  assert.equal(env.storedNotifications.length, 2);
});

test('users cannot swipe themselves or a blocked user', async () => {
  const env = socialFixtures();
  await assert.rejects(
    env.social.swipe(VIEWER, { targetUserId: VIEWER, action: 'LIKE' }),
    BadRequestException,
  );
  const blocked = socialFixtures({ blocked: true });
  await assert.rejects(
    blocked.social.swipe(VIEWER, campusLike, { anonymous: true }),
    NotFoundException,
  );
  assert.equal(blocked.storedSwipes.length, 0);
});

test('anonymous counterpart is only unmasked when both opted in and thresholds are met', async () => {
  const env = socialFixtures();
  const base = {
    id: MATCH_ID,
    userAId: AUTHOR,
    userBId: VIEWER,
    anonymous: true,
    compatibilityScore: 90,
    healthLevel: 'FADING',
    lastMessageAt: null,
    createdAt: new Date(),
  };
  const both = { ...base, revealOptInA: true, revealOptInB: true };

  env.state.messageCount = 3;
  const early: any = await env.social.summary(both as any, env.target, VIEWER);
  assert.equal(early.other.anonymous, true);

  env.state.messageCount = 8;
  const oneSided: any = await env.social.summary(
    { ...base, revealOptInA: true, revealOptInB: false } as any,
    env.target,
    VIEWER,
  );
  assert.equal(oneSided.other.anonymous, true);

  const lowScore: any = await env.social.summary(
    { ...both, compatibilityScore: 40 } as any,
    env.target,
    VIEWER,
  );
  assert.equal(lowScore.other.anonymous, true);

  const revealed: any = await env.social.summary(both as any, env.target, VIEWER);
  assert.equal(revealed.other.nickname, 'Ten That');
  assert.equal(revealed.compatibilityScore, 90);
});

test('a masked counterpart can be blocked without learning their real id', async () => {
  const env = socialFixtures();
  await env.social.swipe(AUTHOR, likeViewer);
  await env.social.swipe(VIEWER, campusLike, { anonymous: true });

  const result: any = await env.social.block(VIEWER, `anon:${MATCH_ID}`);
  assert.deepEqual(result, { blocked: true, userId: `anon:${MATCH_ID}` });
  assert.equal(env.storedBlocks[0].blockedId, AUTHOR);
  assert.ok(env.storedMatches[0].unmatchedAt);

  await assert.rejects(env.social.block('someone-else', `anon:${MATCH_ID}`), NotFoundException);
  await assert.rejects(env.social.block(VIEWER, 'anon:not-a-uuid'), NotFoundException);
});

// ------------------------------------------------------------- discovery

function profilesService(swipeRows: any[]) {
  return new ProfilesService(
    {
      findOne: async () => profile({ id: 'viewer-profile', userId: VIEWER }),
      find: async () => [profile({ id: 'viewer-profile', userId: VIEWER }), profile()],
    } as any,
    {
      find: async ({ where }: any) =>
        swipeRows.filter((row) =>
          Object.entries(where).every(([k, v]) => row[k] === v),
        ),
    } as any,
    { find: async () => [] } as any,
  );
}

test('an author swiped through Campus is not removed from ordinary discovery', async () => {
  const anonymousSwipe = [{ fromUserId: VIEWER, targetUserId: AUTHOR, anonymous: true }];
  const result: any = await profilesService(anonymousSwipe).discover(VIEWER, {} as any);
  assert.equal(result.items.some((p: any) => p.userId === AUTHOR), true);

  const ordinarySwipe = [{ fromUserId: VIEWER, targetUserId: AUTHOR, anonymous: false }];
  const normal: any = await profilesService(ordinarySwipe).discover(VIEWER, {} as any);
  assert.equal(normal.items.some((p: any) => p.userId === AUTHOR), false);
});

test('anonymous Campus likes never appear in incoming likes', async () => {
  const rows = [
    {
      fromUserId: AUTHOR,
      targetUserId: VIEWER,
      action: 'LIKE',
      anonymous: true,
      createdAt: new Date(),
    },
  ];
  const result: any = await profilesService(rows).incomingLikes(VIEWER);
  assert.equal(result.total, 0);
  assert.deepEqual(result.items, []);
});

// ------------------------------------------------------------------ chat

function chatFixtures(anonymous: boolean) {
  const stored: any[] = [];
  const notifications: any[] = [];
  const match: any = {
    id: MATCH_ID,
    userAId: AUTHOR,
    userBId: VIEWER,
    anonymous,
    revealOptInA: false,
    revealOptInB: false,
    compatibilityScore: 90,
    unmatchedAt: null,
  };
  const chat = new ChatService(
    {
      create: (i: any) => i,
      save: async (i: any) => {
        const row = { id: `m${stored.length + 1}`, createdAt: new Date(), ...i };
        stored.push(row);
        return row;
      },
      count: async () => stored.length,
      find: async () => stored.slice().reverse(),
      createQueryBuilder: () => {
        const query: any = {
          where: () => query, addSelect: () => query, orderBy: () => query,
          addOrderBy: () => query, take: () => query,
          getRawAndEntities: async () => ({ entities: stored.slice().reverse(), raw: [] }),
        };
        return query;
      },
    } as any,
    { findOne: async () => match, save: async (m: any) => m } as any,
    {
      findOne: async ({ where }: any) =>
        where.userId === VIEWER
          ? profile({ id: 'viewer-profile', userId: VIEWER, nickname: 'Viewer' })
          : profile(),
    } as any,
    {
      create: (i: any) => i,
      save: async (i: any) => {
        notifications.push(i);
        return i;
      },
    } as any,
  );
  // Keep the privacy fixture in-memory; transaction/concurrency semantics are
  // covered separately against PostgreSQL in social-chat.test.ts.
  const repositories = [Message, Match, Profile, Notification];
  const values = [(chat as any).messages, (chat as any).matches, (chat as any).profiles, (chat as any).notifications];
  (chat as any).messages.manager = {
    transaction: async (work: any) => work({ getRepository: (entity: any) => values[repositories.indexOf(entity)] }),
  };
  return { chat, notifications };
}

test('messages in an anonymous match hide sender id, name and profile id', async () => {
  const env = chatFixtures(true);
  const sent: any = await env.chat.send(VIEWER, MATCH_ID, { text: 'hello' } as any);
  assert.equal(sent.senderId, VIEWER);

  assert.equal(env.notifications[0].title, 'Tin nhắn từ người ẩn danh');
  assert.equal('profileId' in env.notifications[0].data, false);
  assert.equal(JSON.stringify(env.notifications[0]).includes('Viewer'), false);

  const listed: any = await env.chat.list(AUTHOR, MATCH_ID);
  assert.equal(listed.items[0].senderId, `anon:${MATCH_ID}`);
  assert.equal(JSON.stringify(listed).includes(VIEWER), false);
});

test('messages in an ordinary match keep the existing behaviour', async () => {
  const env = chatFixtures(false);
  await env.chat.send(VIEWER, MATCH_ID, { text: 'hello' } as any);
  assert.equal(env.notifications[0].title, 'Viewer');
  assert.equal(env.notifications[0].data.profileId, 'viewer-profile');
  const listed: any = await env.chat.list(AUTHOR, MATCH_ID);
  assert.equal(listed.items[0].senderId, VIEWER);
});
