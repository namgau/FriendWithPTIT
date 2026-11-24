import assert from 'node:assert/strict';
import { test } from 'node:test';
import { questionMatch } from '../src/questions/questions.bank';
import { startOfVietnamDay } from '../src/social/social.service';

const a = (questionId: string, answer: number, acceptable: number[]) => ({ questionId, answer, acceptable });

test('question match needs 3 common questions and is the geometric mean of both sides', () => {
  assert.equal(questionMatch([a('horror', 0, [0]), a('weird', 0, [0])], [a('horror', 0, [0]), a('weird', 0, [0])]), null);
  const mine = [a('horror', 0, [0]), a('messy', 1, [1]), a('promises', 2, [2, 3])];
  const theirs = [a('horror', 0, [0, 1]), a('messy', 0, [0, 1]), a('promises', 3, [3]), a('weird', 1, [1]), a('dropped', 0, [0])];
  // I accept 2/3 (horror, promises) ; they accept 2/3 (horror, messy) ; unknown ids are ignored.
  assert.deepEqual(questionMatch(mine, theirs), { percent: 67, common: 3 });
});

test('swipe day starts at 00:00 Vietnam time', () => {
  assert.equal(startOfVietnamDay(Date.parse('2026-11-24T16:59:00Z')).toISOString(), '2026-11-23T17:00:00.000Z');
  assert.equal(startOfVietnamDay(Date.parse('2026-11-24T17:00:00Z')).toISOString(), '2026-11-24T17:00:00.000Z');
});
