import { test } from 'node:test'
import assert from 'node:assert/strict'
import { tokenize, coverage, unlockList, grade, gradeProduce, produceAnswer, hintOf, modeFor, levelFor, brief, liveStreak, points, nextCombo, blank, buildRun, requeue, nextStreak,
  fromSubtitles, sentenceAt, status, createEmptyCard, LEARN_AHEAD_MS, MAX_SEEN_PER_RUN, NEW_PER_RUN } from './logic.js'

const forms = new Map(Object.entries({ homme: 'homme', il: 'il', voit: 'voir', "aujourd'hui": "aujourd'hui", dit: 'dire',
  a: 'avoir', 'à': 'à', ou: 'ou', 'où': 'où', es: 'être', est: 'être', suis: 'être', parle: 'parler', parles: 'parler',
  coeur: 'coeur', mon: 'mon', le: 'le', chat: 'chat', dort: 'dormir' }))

test('tokenize: elision, known compounds, hyphen split, names, offsets', () => {
  const text = "L'homme qu’il voit aujourd'hui, dit-il à Paul. Mon cœur"
  const toks = tokenize(text, forms)
  assert.deepEqual(toks.map((t) => t.lemma), ['le', 'homme', 'que', 'il', 'voir', "aujourd'hui", 'dire', 'il', 'à', 'paul', 'mon', 'coeur'])
  assert.deepEqual(toks.filter((t) => t.name).map((t) => t.text), ['Paul'])
  for (const t of toks) assert.equal(text.slice(t.start, t.end).replace('’', "'"), t.text)
})

test('coverage and words-to-unlock', () => {
  const toks = tokenize('le chat dort le chat dort homme Paul', forms)
  const known = new Set(['le', 'chat'])
  const st = (l) => (known.has(l) ? 'known' : 'new')
  const c = coverage(toks, st)
  assert.deepEqual([c.known, c.new, c.total], [4, 3, 7]) // Paul is a name, not counted
  assert.deepEqual(unlockList(toks, st, 0.95), ['dormir', 'homme']) // most frequent first
  assert.deepEqual(unlockList(toks, st, 0.8), ['dormir']) // dormir alone gets 6/7
  assert.deepEqual(unlockList(toks, () => 'known'), [])
  // queued words are not understood yet, and still need learning
  const q = (l) => (known.has(l) ? 'known' : 'queued')
  assert.equal(coverage(toks, q).pct, 4 / 7)
  assert.deepEqual(unlockList(toks, q, 0.95), ['dormir', 'homme'])
})

test('status', () => {
  const c = { fsrs: createEmptyCard() }
  assert.equal(status(undefined, { pos: 'noun' }), 'new')
  assert.equal(status(undefined, { pos: 'interj' }), 'known')
  assert.equal(status(c), 'queued') // added but never reviewed
  assert.equal(status({ fsrs: { ...c.fsrs, state: 2, stability: 3 } }), 'learning')
  assert.equal(status({ fsrs: { ...c.fsrs, stability: 30 } }), 'known')
  assert.equal(status({ ...c, known: true }), 'known')
})

test('grade', () => {
  assert.deepEqual(grade(' Parle ', 'parle', 'parler', forms), { result: 'right' })
  assert.deepEqual(grade('cœur', 'coeur', 'coeur', forms), { result: 'right' })
  assert.equal(grade('élève', 'eleve', 'élève', forms).why, 'accent')
  assert.equal(grade('eleve', 'élève', 'élève', forms).why, 'accent') // not a real word unaccented
  assert.deepEqual(grade('a', 'à', 'à', forms), { result: 'wrong', why: 'other', other: 'avoir' }) // different word
  assert.equal(grade('ou', 'où', 'où', forms).result, 'wrong')
  assert.equal(grade('suis', 'es', 'être', forms).why, 'form')
  assert.equal(grade('parles', 'parle', 'parler', forms).why, 'form') // real ending mistake, not a typo
  assert.equal(grade('maisno', 'maison', 'maison', forms).result, 'wrong') // transposition = 2 edits
  assert.equal(grade('maisn', 'maison', 'maison', forms).why, 'typo')
  assert.equal(grade('chta', 'chat', 'chat', forms).result, 'wrong')
  assert.equal(grade('', 'chat', 'chat', forms).result, 'wrong')
})

test('production: article gender, hint, answer', () => {
  const maison = { lemma: 'maison', pos: 'noun', gender: 'f', display: 'la maison' }
  const homme = { lemma: 'homme', pos: 'noun', gender: 'm', display: "l'homme" }
  const f = new Map([...forms, ['maison', 'maison']])
  assert.equal(gradeProduce('la maison', maison, f).result, 'right')
  assert.equal(gradeProduce('une Maison', maison, f).result, 'right')
  assert.deepEqual(gradeProduce('le maison', maison, f), { result: 'almost', why: 'gender' })
  assert.deepEqual(gradeProduce('maison', maison, f), { result: 'almost', why: 'article' })
  assert.equal(gradeProduce('la chat', maison, f).result, 'wrong')
  assert.equal(gradeProduce("l’homme", homme, f).result, 'right') // l' hides gender: accepted
  assert.equal(gradeProduce('un homme', homme, f).result, 'right')
  assert.equal(gradeProduce('une homme', homme, f).why, 'gender')
  assert.equal(gradeProduce('parler', { lemma: 'parler', pos: 'verb' }, f).result, 'right')
  assert.equal(gradeProduce('parle', { lemma: 'parler', pos: 'verb' }, f).why, 'form')
  assert.equal(produceAnswer(maison), 'la maison')
  assert.equal(produceAnswer({ lemma: 'parler', pos: 'verb' }), 'parler')
  assert.equal(hintOf({ lemma: "aujourd'hui" }), "a••••••'•••")
})

test('mode rotation', () => {
  const noun = { pos: 'noun', gloss: 'house' }
  const review = (reps) => ({ reps, state: 2 })
  assert.deepEqual([0, 1, 2, 3, 4, 5].map((reps) => modeFor(review(reps), noun)),
    ['cloze', 'dictation', 'produce', 'dictation', 'cloze', 'produce'])
  assert.equal(modeFor({ reps: 2, state: 1 }, noun), 'cloze') // still in learning steps: keep the sentence
  assert.equal(modeFor(review(2), { pos: 'article', gloss: 'the' }), 'cloze') // function words stay in context
  assert.equal(modeFor(review(2), { pos: 'noun', gloss: '' }), 'cloze') // no gloss, nothing to recall from
})

test('levels', () => {
  assert.deepEqual(levelFor(0), { level: 1, into: 0, span: 100 })
  assert.deepEqual(levelFor(99).level, 1)
  assert.deepEqual(levelFor(100), { level: 2, into: 0, span: 200 })
  assert.deepEqual(levelFor(650), { level: 4, into: 50, span: 400 })
})

test('points and combo', () => {
  assert.equal(points('wrong', 9, 1000, true), 0)
  assert.equal(points('almost', 9, 1000, true), 4)
  assert.equal(points('right', 0, 9000, false), 10)
  assert.equal(points('right', 5, 1000, true), 20 + 5 + 15)
  assert.deepEqual(['right', 'almost', 'wrong'].map((r) => nextCombo(r, 3)), [4, 3, 0])
})

test('blank finds whole words only, any case', () => {
  assert.deepEqual(blank('Le chat le voit.', 'le'), ['', 'Le', ' chat le voit.'])
  assert.deepEqual(blank("J'ai faim, j'aime ça.", 'aime'), ["J'ai faim, j'", 'aime', ' ça.'])
  assert.deepEqual(blank('peut-être', 'être'), ['peut-', 'être', '']) // build.py rejects these sentences
})

test('buildRun: due oldest first, new words spread, mined first, daily cap', () => {
  const now = new Date('2026-10-18T12:00:00')
  const card = (lemma, dueMinAgo, state = 2) => ({ lemma, fsrs: { ...createEmptyCard(now), state, due: new Date(now - dueMinAgo * 60e3) } })
  const cards = [card('b', 5), card('a', 50), card('c', -60), card('mined', 0, 0), { ...card('k', 99), known: true }]
  const words = ['x', 'y', 'z', 'w', 'v', 'u'].map((lemma) => ({ lemma, sentence: '…' })).concat([{ lemma: 'nosentence' }])
  const q = buildRun(cards, words, now, 0)
  assert.deepEqual(q.filter((i) => i.phase === 'quiz').map((i) => i.lemma), ['a', 'b']) // c not due, k known
  assert.deepEqual(q.filter((i) => i.phase === 'intro').map((i) => i.lemma), ['mined', 'x', 'y', 'z', 'w'].slice(0, NEW_PER_RUN))
  assert.equal(q[0].phase, 'intro')
  assert.equal(buildRun(cards, words, now, 19).filter((i) => i.phase === 'intro').length, 1)
})

test('requeue: only soon-due cards, capped per run', () => {
  const now = new Date()
  const soon = { card: { due: new Date(+now + 10 * 60e3) }, seen: 1 }
  const later = { card: { due: new Date(+now + LEARN_AHEAD_MS + 1) }, seen: 1 }
  assert.deepEqual(requeue([1, 2, 3, 4, 5, 6], soon, now, () => 0), [1, 2, 3, soon, 4, 5, 6])
  assert.deepEqual(requeue([], soon, now), [soon])
  assert.deepEqual(requeue([1], later, now), [1])
  assert.deepEqual(requeue([1], { ...soon, seen: MAX_SEEN_PER_RUN }, now), [1])
})

test('streak', () => {
  assert.equal(nextStreak(4, '2026-10-17', '2026-10-18'), 5)
  assert.equal(nextStreak(4, '2026-10-16', '2026-10-18'), 5) // one missed day forgiven
  assert.equal(nextStreak(4, '2026-10-15', '2026-10-18'), 1)
  assert.equal(nextStreak(4, '2026-10-18', '2026-10-18'), 4)
  assert.equal(nextStreak(0, undefined, '2026-10-18'), 1)
  assert.equal(liveStreak(4, '2026-10-16', '2026-10-18'), 4)
  assert.equal(liveStreak(4, '2026-10-15', '2026-10-18'), 0)
  assert.equal(liveStreak(0, null, '2026-10-18'), 0)
})

test('brief gloss', () => {
  assert.equal(brief('to have; to own; to possess'), 'to have; to own')
  assert.equal(brief('dog', 1), 'dog')
})

test('subtitles and sentence extraction', () => {
  const srt = '1\n00:00:01,000 --> 00:00:02,000\n<i>Bonjour</i> toi.\n\n2\n00:00:03,000 --> 00:00:04,000\nÇa va ?\n'
  assert.equal(fromSubtitles(srt), 'Bonjour toi.\nÇa va ?')
  assert.equal(fromSubtitles('WEBVTT\n\n00:01.000 --> 00:02.000\nSalut.'), 'Salut.')
  const text = 'Il pleut. Le chat dort ici ! Fin'
  assert.equal(sentenceAt(text, text.indexOf('chat')), 'Le chat dort ici !')
  assert.equal(sentenceAt(text, text.indexOf('Fin')), 'Fin')
  const long = 'mot '.repeat(100) + 'cible ' + 'mot '.repeat(100)
  const s = sentenceAt(long, long.indexOf('cible'))
  assert.ok(s.includes('cible') && s.length < 170, s)
})
