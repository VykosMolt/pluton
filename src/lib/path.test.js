import { test } from 'node:test'
import assert from 'node:assert/strict'
import { STAGES, goalsOf, pathState, focus } from './path.js'

const fresh = { learned: 0, tables: {}, tablesTotal: 0, read: new Set(), listenMin: 0, talked: 0, written: 0 }

test('stages climb from A1 to C1 with growing targets', () => {
  assert.equal(STAGES.length, 10)
  assert.deepEqual([STAGES[0].level, STAGES.at(-1).level], ['A1', 'C1'])
  for (let i = 1; i < STAGES.length; i++) {
    assert.ok(STAGES[i].words > STAGES[i - 1].words && STAGES[i].listen > STAGES[i - 1].listen)
    assert.ok(STAGES[i].read.includes(`story-${i}`)) // each stage reads its own chapter
  }
})

test('a new learner is on stage 1; core goals advance it, bonus goals never block', () => {
  assert.equal(pathState(fresh).current, 0)
  const m = { ...fresh, learned: 210, tables: { 'ind:pre': 5 }, read: new Set(['story-0', 'story-1']), listenMin: 10 }
  const s = pathState(m)
  assert.equal(s.current, 2) // stages 1 and 2 done
  assert.ok(s.stages[1].complete && !s.stages[2].complete)
  const stage3 = { ...m, learned: 400, tables: { 'ind:pre': 12 }, grammar: 1, read: new Set([...m.read, 'story-2']), listenMin: 20 }
  assert.equal(pathState(stage3).current, 3) // done without talking or writing (bonus)
  assert.equal(pathState(stage3).stages[2].mastered, false)
})

test('goals report progress and labels', () => {
  const g = goalsOf(STAGES[6], { ...fresh, learned: 1150, tables: { 'cnd:pre': 2 }, listenMin: 60.7 }, { 'story-6': 'Paris' })
  assert.deepEqual(g.map((x) => x.kind), ['words', 'verbs', 'grammar', 'read', 'read', 'listen', 'talk', 'write'])
  assert.equal(g[0].pct, 0.5)
  assert.equal(g[2].label, '10 grammar topics in review')
  assert.equal(g[3].label, 'Read “Paris”')
  assert.equal(g[5].have, 60)
  assert.ok(g.slice(6).every((x) => x.bonus))
})

test('focus: reading first, then listening, then bonus only with an AI', () => {
  const s = pathState({ ...fresh, learned: 9999 }).stages[0]
  assert.equal(focus(s, false).kind, 'read')
  const read = pathState({ ...fresh, read: new Set(['story-0']), listenMin: 0 }).stages[0]
  assert.equal(focus(read, false).kind, 'listen')
  const s3 = pathState({ ...fresh, read: new Set(['story-2']), listenMin: 999, grammar: 9 }).stages[2]
  assert.equal(focus(s3, false), null)
  assert.equal(focus(s3, true).kind, 'talk')
})
