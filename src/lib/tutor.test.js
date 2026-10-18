import { test } from 'node:test'
import assert from 'node:assert/strict'
import { levelOf, splitCorrection, PROMPTS } from './tutor.js'

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

test('writing prompts go from easy to hard', () => {
  const order = ['A1', 'A2', 'B1', 'B2', 'C1']
  const levels = PROMPTS.map(([l]) => order.indexOf(l))
  assert.ok(levels.every((l, i) => l >= 0 && (i === 0 || l >= levels[i - 1])))
})
