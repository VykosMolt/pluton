import { test } from 'node:test'
import assert from 'node:assert/strict'
import { levelOf, splitCorrection, readerSystem, RETELL, PROMPTS } from './tutor.js'
import { chat, ready } from './ai.js'

test('level from vocabulary size', () => {
  assert.deepEqual([0, 149, 150, 1999, 2000, 3999, 9000].map((n) => levelOf(n).code), ['A1', 'A1', 'A2', 'B1', 'B2', 'B2', 'C1'])
})

test('correction line', () => {
  assert.deepEqual(splitCorrection("✏️ Je suis allée au cinéma. — être takes agreement: allée\nAh, quel film ?"),
    { fix: 'Je suis allée au cinéma.', why: 'être takes agreement: allée', body: 'Ah, quel film ?' })
  assert.deepEqual(splitCorrection('✏️ Je mange une pomme.\n\nMiam !'), { fix: 'Je mange une pomme.', why: '', body: 'Miam !' })
  assert.deepEqual(splitCorrection('Bonjour ! Ça va ?'), { fix: null, why: '', body: 'Bonjour ! Ça va ?' })
  assert.equal(splitCorrection('✏️ only a fix, nothing after').fix, null) // needs the reply below it
})

test('story prompt asks for a level-matched JSON story with the learner words', () => {
  const s = readerSystem(levelOf(300), ['chat', 'maison'], ['forêt'])
  assert.ok(s.includes('A2') && s.includes('chat, maison') && s.includes('forêt'))
  assert.ok(s.includes('"title"') && s.includes('"body"') && s.includes('150-250'))
})

test('retell prompt scores comprehension, not grammar', () => {
  assert.ok(RETELL.includes('"score"') && RETELL.includes('comprehension'))
})

test('writing prompts go from easy to hard', () => {
  const order = ['A1', 'A2', 'B1', 'B2', 'C1']
  const levels = PROMPTS.map(([l]) => order.indexOf(l))
  assert.ok(levels.every((l, i) => l >= 0 && (i === 0 || l >= levels[i - 1])))
})

test('AI stream keeps the final unterminated event and rejects failed or truncated replies', async (t) => {
  const cfg = { provider: 'custom', model: 'test', base: 'https://example.test/v1' }
  let body = 'data: {"choices":[{"delta":{"content":"Bonjour"}}]}'
  t.mock.method(globalThis, 'fetch', async () => new Response(body))
  const deltas = []
  assert.equal(await chat(cfg, { system: '', messages: [], onText: (text) => deltas.push(text) }), 'Bonjour')
  assert.deepEqual(deltas, ['Bonjour'])
  body = 'data: {"error":{"message":"Out of credit"}}\n\n'
  await assert.rejects(chat(cfg, { system: '', messages: [] }), /Out of credit/)
  body = 'data: {"choices":[{"delta":{"content":"Partial"}}]}\n' +
    'data: {"choices":[{"delta":{},"finish_reason":"length"}]}\n'
  await assert.rejects(chat(cfg, { system: '', messages: [] }), /reply limit/)
  body = 'data: [DONE]\n'
  await assert.rejects(chat(cfg, { system: '', messages: [] }), /empty reply/)
})

test('custom AI configuration requires a server URL', () => {
  assert.equal(ready({ provider: 'custom', model: 'model', base: '' }), false)
  assert.equal(ready({ provider: 'custom', model: 'model', base: 'http://localhost:8080/v1' }), true)
})
