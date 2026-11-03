import test from 'node:test'
import assert from 'node:assert/strict'
import { millis, toNativeBackup } from './native-backup.js'

const NOW = Date.parse('2026-11-03T12:00:00Z')

function webExport() {
  return {
    version: 1,
    cards: [
      ['chat', {
        lemma: 'chat',
        fsrs: {
          due: '2026-11-04T12:00:00.000Z',
          stability: 3.5,
          difficulty: 4.2,
          elapsed_days: 2,
          scheduled_days: 3,
          reps: 5,
          lapses: 1,
          learning_steps: 0,
          state: 2,
          last_review: '2026-11-01T12:00:00.000Z',
        },
        known: false,
        added: 1762000000000,
        mined: { sentence: 'Le chat dort.', form: 'chat', translation: 'The cat is sleeping.' },
        updated: '2026-11-01T12:00:00.000Z',
      }],
    ],
    log: [[1, { lemma: 'chat', rating: 3, mode: 'cloze', at: 1762000000000 }]],
    texts: [
      [3, { id: 3, title: 'Article', body: 'Bonjour.', added: 1762000000000 }],
      ['sample', { id: 'sample', title: 'Sample', body: 'Un matin.', added: 1762000000000 }],
    ],
    meta: [
      ['stats', {
        points: 120,
        newDay: '2026-11-03',
        newCount: 4,
        read: ['text-3', 'sample', 'story-0'],
        listened: 95.7,
        spoken: 3,
        talked: 2,
        written: 1,
      }],
      ['settings', { perRun: 8, perDay: 30 }],
      ['writings', [{
        at: 1762000000000,
        topic: 'Raconte',
        text: 'je suis alle',
        result: {
          corrected: 'Je suis allé.',
          errors: [{ original: 'je suis alle', fix: 'je suis allé', why: 'accent', kind: 'spelling' }],
          feedback: 'Bien',
          level: 'A2',
        },
      }]],
      ['ai', { key: 'must-not-leak' }],
    ],
  }
}

test('native backup carries cards, reviews and dates in the native shape', () => {
  const out = toNativeBackup(webExport(), NOW)
  assert.equal(out.version, 4)
  assert.equal(out.cards.length, 1)
  const card = out.cards[0]
  assert.equal(card.lemma, 'chat')
  assert.equal(card.fsrs.state, 'Review')
  assert.equal(card.fsrs.due, Date.parse('2026-11-04T12:00:00.000Z'))
  assert.equal(card.fsrs.elapsedDays, 2)
  assert.equal(card.fsrs.learningSteps, 0)
  assert.equal(card.fsrs.lastReview, Date.parse('2026-11-01T12:00:00.000Z'))
  assert.equal(card.mined.translation, 'The cat is sleeping.')
  assert.equal(out.reviews[0].rating, 3)
  assert.equal(out.reviews[0].at, 1762000000000)
})

test('native backup assigns ids to string texts and rewrites read references', () => {
  const out = toNativeBackup(webExport(), NOW)
  assert.deepEqual(out.texts.map((t) => t.id), [3, 4])
  assert.deepEqual(out.stats.read, ['text-3', 'text-4', 'story-0'])
  assert.equal(out.texts[1].title, 'Sample')
})

test('native backup carries stats, pace, writing history and derived corrections', () => {
  const out = toNativeBackup(webExport(), NOW)
  assert.deepEqual(out.stats, {
    day: '2026-11-03',
    newCount: 4,
    read: ['text-3', 'text-4', 'story-0'],
    listened: 96,
    spoken: 3,
    talked: 2,
    written: 1,
  })
  assert.deepEqual(out.pace, { perRun: 8, perDay: 30 })
  assert.equal(out.writings.length, 1)
  assert.equal(out.writings[0].prompt, 'Raconte')
  assert.equal(out.writings[0].corrected, 'Je suis allé.')
  assert.equal(out.corrections.length, 1)
  assert.equal(out.corrections[0].fix, 'je suis allé')
  assert.equal(out.corrections[0].source, 'writing')
})

test('native backup never includes private meta such as the AI key', () => {
  const out = toNativeBackup(webExport(), NOW)
  assert.equal(JSON.stringify(out).includes('must-not-leak'), false)
  assert.equal(Object.hasOwn(out, 'meta'), false)
})

test('millis accepts numbers, Dates and ISO strings', () => {
  assert.equal(millis(123), 123)
  assert.equal(millis(new Date(456)), 456)
  assert.equal(millis('2026-11-03T12:00:00Z'), NOW)
  assert.equal(millis(undefined, 7), 7)
  assert.equal(millis('not a date', 7), 7)
})
