import { test } from 'node:test'
import assert from 'node:assert/strict'
import { plan, collect } from './sync.js'

const T = (s) => new Date(s).toISOString()
const card = (lemma, updated, extra = {}) => ({ lemma, updated, fsrs: { due: new Date(0), state: 2 }, ...extra })

test('pull: newer remote versions win, older ones and own echoes are ignored', () => {
  const cards = new Map([['chat', card('chat', 2000)], ['chien', card('chien', 9000)]])
  const texts = new Map([[3, { id: 3, title: 'mine', updated: 1000 }]])
  const rows = [
    { store: 'cards', key: 'chat', data: card('chat', 5000), updated_at: T(5000) }, // newer: applied
    { store: 'cards', key: 'chien', data: card('chien', 5000), updated_at: T(5000) }, // older: ignored
    { store: 'cards', key: 'maison', data: card('maison', 5000), updated_at: T(5000) }, // new card: applied
    { store: 'texts', key: 'dev1:3', data: { id: 'dev1:3' }, updated_at: T(9000) }, // our own text echoed back: ignored
    { store: 'texts', key: 'dev2:7', data: { id: 'dev2:7', title: 'theirs' }, updated_at: T(4000) },
    { store: 'log', key: 'dev1:5', data: {}, updated_at: T(1) }, // own review: ignored
    { store: 'log', key: 'dev2:5', data: { rating: 3 }, updated_at: T(1) },
    { store: 'meta', key: 'stats:dev2', data: { points: 9 }, updated_at: T(1) },
    { store: 'meta', key: 'stats:dev1', data: { points: 1 }, updated_at: T(1) }, // own stats: local is the truth
    { store: 'meta', key: 'settings', data: { v: { perRun: 8 } }, updated_at: T(7000) },
  ]
  const { ops, stamped } = plan(rows, { device: 'dev1', cards, texts, stamps: { settings: 6000 } })
  const puts = ops.map(([op, store, v, k]) => `${op} ${store} ${k ?? v.lemma ?? v.id}`)
  assert.deepEqual(puts, ['put cards chat', 'put cards maison', 'put texts dev2:7', 'put log dev2:5', 'put meta stats:dev2', 'put meta settings'])
  assert.ok(ops[0][2].fsrs.due instanceof Date) // dates revived from JSON
  assert.equal(ops[0][2].pulled, 5000) // so it isn't pushed straight back
  assert.deepEqual(stamped, { settings: 7000 })
})

test('pull: deletions apply unless the local copy changed later', () => {
  const cards = new Map([['a', card('a', 1000)], ['b', card('b', 9000)]])
  const texts = new Map([[4, { id: 4, updated: 1000 }]])
  const rows = [
    { store: 'cards', key: 'a', data: { deleted: true }, updated_at: T(5000) },
    { store: 'cards', key: 'b', data: { deleted: true }, updated_at: T(5000) },
    { store: 'texts', key: 'dev1:4', data: { deleted: true }, updated_at: T(5000) }, // deleted elsewhere: our id 4
  ]
  assert.deepEqual(plan(rows, { device: 'dev1', cards, texts, stamps: {} }).ops, [['del', 'cards', 'a'], ['del', 'texts', 4]])
})

test('push: only what changed here since the last push', () => {
  const { rows, maxLog } = collect({ device: 'dev1', since: 3000, logPushed: 2 }, {
    cards: [card('old', 1000), card('new', 5000), card('pulled', 6000, { pulled: 6000 })],
    texts: [{ id: 7, updated: 4000 }, { id: 'dev2:1', updated: 9000, pulled: 9000 }],
    log: [[1, { at: 1 }], [2, { at: 2 }], [3, { at: 3 }], ['dev2:9', { at: 9 }]],
    meta: [['stats', { points: 5 }], ['settings', { perRun: 5 }], ['talk', []]],
    stamps: { stats: 4000, settings: 1000, talk: 3500 },
    tomb: { 'texts\t8': 4500, 'cards\tchat': 100 },
  })
  assert.deepEqual(rows.map((r) => `${r.store} ${r.key}`),
    ['cards new', 'texts dev1:7', 'log dev1:3', 'meta stats:dev1', 'meta talk', 'texts dev1:8'])
  assert.equal(rows.find((r) => r.key === 'dev1:7').data.id, 'dev1:7') // texts get a device-unique id
  assert.deepEqual(rows.at(-1).data, { deleted: true })
  assert.equal(maxLog, 3)
})
