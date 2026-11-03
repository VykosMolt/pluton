import { test } from 'node:test'
import assert from 'node:assert/strict'
import { combine, streakOf, daysOf } from './stats.js'

test('streak from run days', () => {
  assert.equal(streakOf(['2026-10-16', '2026-10-17', '2026-10-18'], '2026-10-18'), 3)
  assert.equal(streakOf(['2026-10-14', '2026-10-16', '2026-10-18'], '2026-10-18'), 3) // one missed day forgiven
  assert.equal(streakOf(['2026-10-13', '2026-10-16'], '2026-10-18'), 1)
  assert.equal(streakOf(['2026-10-15'], '2026-10-18'), 0) // lapsed
  assert.equal(streakOf([], '2026-10-18'), 0)
  assert.equal(streakOf(['2026-10-17', '2026-10-18', '2026-10-18'], '2026-10-18'), 2) // a second run the same day counts once
})

test('old records without days keep their streak', () => {
  assert.deepEqual(daysOf({ streak: 3, lastDay: '2026-10-18' }), ['2026-10-16', '2026-10-17', '2026-10-18'])
  assert.deepEqual(daysOf({}), [])
})

test('combine normalizes this device record and its daily budget', () => {
  const day = { points: 100, listened: 600, talked: 3, spoken: 4, bestCombo: 12, stage: 2, read: ['story-0'], days: ['2026-10-17'],
    newDay: '2026-10-18', newCount: 5 }
  const t = combine(day, '2026-10-18')
  assert.deepEqual([t.points, t.listened, t.talked, t.spoken], [100, 600, 3, 4])
  assert.equal(t.bestCombo, 12)
  assert.equal(t.stage, 2)
  assert.deepEqual(t.read, ['story-0'])
  assert.equal(t.streak, 1)
  assert.equal(t.newToday, 5)
  assert.equal(combine({}, '2026-10-18').streak, 0)
  const next = combine({ ...day, newDay: '2026-10-19' }, '2026-10-19')
  assert.equal(next.newToday, 5)
})
