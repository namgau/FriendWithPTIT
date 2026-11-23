import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ProfilesService } from '../src/profiles/profiles.service';

test('discover explains shared-tag ranking without revealing private GPA or location', async () => {
  const base = { nickname: 'Person', fullName: 'Name', age: 20, gender: 'OTHER', school: 'PTIT',
    campus: 'HN', faculty: 'IT', major: 'IT', academicYear: 'K', alumni: false, gpa: 3.5,
    gpaTier: 'GOOD', bio: '', avatarSeed: 1, mode: 'FRIENDS', lat: 21, lng: 105,
    verified: true, profileComplete: true, showGpaPublic: false, showLocationPublic: false,
    createdAt: new Date('2026-01-01') };
  const viewer = { ...base, id: 'v', userId: 'viewer', interests: [' A ', 'B'] };
  const one = { ...base, id: 'a', userId: 'one', interests: ['a'] };
  const two = { ...base, id: 'b', userId: 'two', interests: ['a', ' B ', 'b'] };
  const profiles = { findOne: async () => viewer, find: async () => [one, viewer, two] };
  const service = new ProfilesService(profiles as any, { find: async () => [] } as any, { find: async () => [] } as any);
  const result = await service.discover('viewer', {});
  assert.deepEqual(result.items.map((item) => item.userId), ['two', 'one']);
  assert.deepEqual(result.items[0].rankingReasons, ['2 sở thích chung', 'Cùng chế độ kết nối']);
  assert.equal(result.items[0].compatibility, 100);
  assert.ok(!('gpa' in result.items[0]) && !('gpaTier' in result.items[0]));
  assert.equal(result.items[0].distanceKm, 0);
  assert.deepEqual((await service.discover('viewer', { sort: 'distance' })).items[0].rankingReasons, []);
});
