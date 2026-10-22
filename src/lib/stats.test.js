import { test } from 'node:test'
import assert from 'node:assert/strict'
import { combine, streakOf, daysOf } from './stats.js'

test('streak from run days on any device', () => {
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

test('two devices combine without losing anything', () => {
  const phone = { points: 100, listened: 600, talked: 3, spoken: 4, bestCombo: 12, stage: 2, read: ['story-0'], days: ['2026-10-17'],
    newDay: '2026-10-18', newCount: 5 }
  const laptop = { points: 50, listened: 60, written: 1, spoken: 2, bestCombo: 7, stage: 1, read: ['story-1'], days: ['2026-10-18'],
    newDay: '2026-10-18', newCount: 10 }
  const t = combine(phone, [laptop], '2026-10-18')
  assert.deepEqual([t.points, t.listened, t.talked, t.written, t.spoken], [150, 660, 3, 1, 6]) // counters add up
  assert.deepEqual([t.bestCombo, t.stage], [12, 2]) // bests take the max
  assert.deepEqual(t.read.sort(), ['story-0', 'story-1'])
  assert.equal(t.streak, 2) // a run on each device on consecutive days
  assert.equal(t.newToday, 15) // the daily new-word budget counts every device
  assert.equal(combine({}, [], '2026-10-18').streak, 0)
})
