import assert from 'node:assert/strict';
import { test } from 'node:test';
import { questionMatch } from '../src/questions/questions.bank';
import { startOfVietnamDay } from '../src/social/social.service';

const a = (questionId: string, answer: number, acceptable: number[]) => ({ questionId, answer, acceptable });

test('question match needs 3 common questions and is the geometric mean of both sides', () => {
  assert.equal(questionMatch([a('x', 0, [0]), a('y', 0, [0])], [a('x', 0, [0]), a('y', 0, [0])]), null);
  const mine = [a('x', 0, [0]), a('y', 1, [1]), a('z', 2, [2, 3])];
  const theirs = [a('x', 0, [0, 1]), a('y', 0, [0, 1]), a('z', 3, [3]), a('w', 1, [1])];
  // I accept 2/3 of their answers (x, z) ; they accept 2/3 of mine (x, y).
  assert.deepEqual(questionMatch(mine, theirs), { percent: 67, common: 3 });
});

test('swipe day starts at 00:00 Vietnam time', () => {
  assert.equal(startOfVietnamDay(Date.parse('2026-11-24T16:59:00Z')).toISOString(), '2026-11-23T17:00:00.000Z');
  assert.equal(startOfVietnamDay(Date.parse('2026-11-24T17:00:00Z')).toISOString(), '2026-11-24T17:00:00.000Z');
});
