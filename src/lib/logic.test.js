import { test } from 'node:test'
import assert from 'node:assert/strict'
import { tokenize, coverage, unlockList, grade, gradeProduce, produceAnswer, hintOf, modeFor, levelFor, brief, playable, gradeDictee, gradeGram, newGrammar, gramKey, fixCard, firstChange, gradeConj, newConj, conjKey, parseConj, isWord, points, nextCombo, blank, buildRun, requeue,
  fromSubtitles, sentenceAt, sentencesOf, wordDiff, enLookup, englishTokens, parseCues, cueAt, listenGoal, status, createEmptyCard, gradeQuiz, replayCard, Rating, LEARN_AHEAD_MS, MAX_SEEN_PER_RUN, NEW_PER_RUN } from './logic.js'

const forms = new Map(Object.entries({ homme: 'homme', il: 'il', voit: 'voir', "aujourd'hui": "aujourd'hui", dit: 'dire',
  a: 'avoir', 'à': 'à', ou: 'ou', 'où': 'où', es: 'être', est: 'être', suis: 'être', parle: 'parler', parles: 'parler',
  coeur: 'coeur', mon: 'mon', le: 'le', chat: 'chat', dort: 'dormir' }))

test('tokenize: elision, known compounds, hyphen split, names, offsets', () => {
  const text = "L'homme qu’il voit aujourd'hui, dit-il à Paul. Mon cœur"
  const toks = tokenize(text, forms)
  assert.deepEqual(toks.map((t) => t.lemma), ['le', 'homme', 'que', 'il', 'voir', "aujourd'hui", 'dire', 'il', 'à', 'paul', 'mon', 'coeur'])
  assert.deepEqual(toks.filter((t) => t.name).map((t) => t.text), ['Paul'])
  for (const t of toks) assert.equal(text.slice(t.start, t.end).replace('’', "'"), t.text)
  const f = new Map([...forms, ['paris', 'pari'], ['va', 'aller'], ['va-t-il', undefined], ['à', 'à']])
  const names = (s) => tokenize(s, f).filter((t) => t.name).map((t) => t.text)
  assert.deepEqual(names('Il va à Paris. « Le chat dort. »'), ['Paris']) // not the plural of "pari"
  assert.deepEqual(names("Il dit : « Le chat ! » L'Italie, Marie et Paul."), ['Italie', 'Marie', 'Paul'])
  assert.deepEqual(tokenize('Où va-t-il ?', f).map((t) => t.lemma), ['où', 'aller', 'il']) // no euphonic t
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

test('dictée', () => {
  const s = "Je n’ai pas vu le chat, d'accord ?"
  const marks = (r) => r.marks.map((m) => m.mark).join(' ')
  assert.equal(gradeDictee("je n'ai pas vu le chat d'accord", s).result, 'right') // case, apostrophe, punctuation
  const one = gradeDictee("je n'ai pas vue le chat d'accord", s)
  assert.deepEqual([one.result, marks(one)], ['almost', 'ok ok ok bad ok ok ok'])
  const missing = gradeDictee("je n'ai vu chat d'accord", s)
  assert.deepEqual([missing.result, marks(missing)], ['wrong', 'ok ok bad ok bad ok ok'])
  const acc = gradeDictee('Il était là, déjà.', 'Il etait la, deja.')
  assert.deepEqual([acc.result, acc.why], ['almost', 'accent'])
  assert.equal(gradeDictee('', s).result, 'wrong')
  const m = gradeDictee('x', 'Bon, ça va.').marks[1]
  assert.equal('Bon, ça va.'.slice(m.start, m.end), 'ça') // offsets point into the sentence
})

test('mode rotation', () => {
  const noun = { pos: 'noun', gloss: 'house' }
  const review = (reps) => ({ reps, state: 2 })
  assert.deepEqual([0, 1, 2, 3, 4, 5].map((reps) => modeFor(review(reps), noun)),
    ['cloze', 'dictation', 'produce', 'dictation', 'cloze', 'produce'])
  assert.equal(modeFor({ reps: 2, state: 1 }, noun), 'cloze') // still in learning steps: keep the sentence
  assert.equal(modeFor(review(2), { pos: 'article', gloss: 'the' }), 'cloze') // function words stay in context
  assert.equal(modeFor(review(2), { pos: 'noun', gloss: '' }), 'cloze') // no gloss, nothing to recall from
  const known = { reps: 3, state: 2, stability: 30 }
  assert.equal(modeFor(known, { ...noun, audio_sentence: 'x' }), 'dictee')
  assert.equal(modeFor({ ...known, stability: 5 }, { ...noun, audio_sentence: 'x' }), 'dictation') // not yet known
  assert.equal(modeFor(known, noun), 'dictation') // no recording, no dictée
})

test('levels', () => {
  assert.deepEqual(levelFor(0), { level: 1, into: 0, span: 100 })
  assert.deepEqual(levelFor(99).level, 1)
  assert.deepEqual(levelFor(100), { level: 2, into: 0, span: 200 })
  assert.deepEqual(levelFor(650), { level: 4, into: 50, span: 400 })
})

test('conjugation grading', () => {
  assert.equal(gradeConj('irons', 'irons').result, 'right')
  assert.equal(gradeConj('nous irons', 'irons').result, 'right') // pronoun allowed
  assert.equal(gradeConj("qu'il aille", 'aille').result, 'right')
  assert.equal(gradeConj("j'irai", 'irai').result, 'right')
  assert.equal(gradeConj('irez', 'irons').result, 'wrong') // another person is the mistake being drilled
  assert.equal(gradeConj('etes', 'êtes').why, 'accent')
  assert.equal(gradeConj('sommes allées', 'sommes allés', true).result, 'right') // agreement not tested
  assert.equal(gradeConj('suis allée', 'suis allé', true).result, 'right')
  assert.equal(gradeConj('ai allé', 'suis allé', true).result, 'wrong') // wrong auxiliary is
})

test('conjugation unlocks', () => {
  const conj = { être: { t: { 'ind:pre': [], pc: [] } }, avoir: { t: { 'ind:pre': [] } }, aller: { t: { 'ind:pre': [] } } }
  const order = ['ind:pre', 'pc']
  const card = (lemma, state, extra = {}) => ({ lemma, fsrs: { ...createEmptyCard(), state }, ...extra })
  // only learned verbs get tables; marked-known counts as learned
  assert.deepEqual(newConj([card('être', 2), card('avoir', 0), card('aller', 0, { known: true })], conj, order, 5),
    [conjKey('être', 'ind:pre'), conjKey('aller', 'ind:pre')])
  // pc stays shut until UNLOCK_NEXT_TENSE présent tables are in review
  const some = [card('être', 2), card(conjKey('être', 'ind:pre'), 2)]
  assert.deepEqual(newConj(some, conj, order, 5), [])
  const many = [card('être', 2), ...Array.from({ length: 8 }, (_, i) => card(conjKey(`v${i}`, 'ind:pre'), 2))]
  assert.deepEqual(newConj(many, conj, order, 5), [conjKey('être', 'ind:pre'), conjKey('être', 'pc')])
  assert.deepEqual(parseConj(conjKey('aller', 'ind:fut')), { verb: 'aller', tense: 'ind:fut' })
  assert.equal(isWord({ lemma: conjKey('aller', 'pc') }), false)
})

test('buildRun introduces a grammar topic when the vocabulary is there', () => {
  const now = new Date()
  const learned = Array.from({ length: 160 }, (_, i) => ({ lemma: `w${i}`, fsrs: { ...createEmptyCard(now), state: 2, due: new Date(+now + 864e5) } }))
  const words = new Map(learned.map((c) => [c.lemma, { lemma: c.lemma, sentence: '…' }]).concat([['x', { lemma: 'x', sentence: '…' }]]))
  const grammar = [{ id: 'objets', level: 'A2' }]
  const intros = buildRun(learned, words, now, 0, { grammar }).filter((i) => i.phase === 'intro').map((i) => i.lemma)
  assert.deepEqual(intros, ['x', gramKey('objets')])
  // its own slot: verb tables waiting don't crowd it out
  const withVerbs = [...learned, { lemma: 'aller', fsrs: { ...createEmptyCard(now), state: 2, due: new Date(+now + 864e5) } },
    { lemma: 'être', fsrs: { ...createEmptyCard(now), state: 2, due: new Date(+now + 864e5) } }]
  const conj = { aller: { t: { 'ind:pre': [] } }, être: { t: { 'ind:pre': [] } } }
  for (const v of ['aller', 'être']) words.set(v, { lemma: v, sentence: '…' })
  const all = buildRun(withVerbs, words, now, 0, { grammar, conj, tenses: ['ind:pre'] }).filter((i) => i.phase === 'intro').map((i) => i.lemma)
  assert.ok(all.includes(gramKey('objets')) && all.filter((l) => l.startsWith('conj/')).length === 2, all.join())
})

test('buildRun adds conjugation tables within the daily budget', () => {
  const now = new Date()
  const cards = [{ lemma: 'être', fsrs: { ...createEmptyCard(now), state: 2, due: new Date(+now + 864e5) } }]
  const words = new Map([['x', { lemma: 'x', sentence: '…' }], ['être', { lemma: 'être', sentence: '…' }]])
  const conj = { être: { t: { 'ind:pre': [] } } }
  const intros = (q) => q.filter((i) => i.phase === 'intro').map((i) => i.lemma)
  assert.deepEqual(intros(buildRun(cards, words, now, 0, { conj, tenses: ['ind:pre'] })), ['x', conjKey('être', 'ind:pre')])
  assert.deepEqual(intros(buildRun(cards, words, now, 19, { conj, tenses: ['ind:pre'] })), ['x']) // budget spent on the word
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
  const cards = [card('b', 5), card('a', 50), card('c', -60), { ...card('mined', 0, 0), mined: {} }, { ...card('k', 99), known: true },
    card('gone', 70)] // "gone" lost its sentence in a data rebuild: skipped, not crashed on
  const words = new Map(['x', 'y', 'z', 'w', 'v', 'u', 'a', 'b', 'c', 'k'].map((lemma) => [lemma, { lemma, sentence: '…' }])
    .concat([['nosentence', { lemma: 'nosentence' }]]))
  const q = buildRun(cards, words, now, 0)
  assert.deepEqual(q.filter((i) => i.phase === 'quiz').map((i) => i.lemma), ['a', 'b']) // c not due, k known
  assert.deepEqual(q.filter((i) => i.phase === 'intro').map((i) => i.lemma), ['mined', 'x', 'y', 'z', 'w'].slice(0, NEW_PER_RUN))
  assert.equal(q[0].phase, 'intro')
  assert.equal(buildRun(cards, words, now, 19).filter((i) => i.phase === 'intro').length, 1)
  assert.equal(buildRun(cards, words, now, 0, { perRun: 2 }).filter((i) => i.phase === 'intro').length, 2) // pace setting
  assert.equal(buildRun(cards, words, now, 8, { perDay: 10 }).filter((i) => i.phase === 'intro').length, 2)
})

test('playable', () => {
  const words = new Map([['chat', { sentence: 'Le chat.' }], ['x', {}]])
  const conj = { aller: { t: { 'ind:pre': [] } } }
  assert.equal(playable({ lemma: 'chat' }, words, conj), true)
  assert.equal(playable({ lemma: 'x' }, words, conj), false)
  assert.equal(playable({ lemma: 'x', mined: { sentence: 's' } }, words, conj), true)
  assert.equal(playable({ lemma: conjKey('aller', 'ind:pre') }, words, conj), true)
  assert.equal(playable({ lemma: conjKey('souvenir', 'ind:pre') }, words, conj), false)
  assert.equal(playable({ lemma: conjKey('aller', 'pc') }, words, null), false)
})

test('grammar: grading, introduction order, playability', () => {
  assert.equal(gradeGram('Le lui', ['le lui']).result, 'right')
  assert.equal(gradeGram('étiez arrivée', ['étiez arrivés', 'étiez arrivée']).result, 'right')
  assert.equal(gradeGram('ou', ['où']).why, 'accent')
  assert.equal(gradeGram('l', ["l'"]).result, 'almost') // lost apostrophe
  assert.equal(gradeGram('lui', ['leur']).result, 'wrong')
  const G = [{ id: 'a', level: 'A2' }, { id: 'b', level: 'A2' }, { id: 'c', level: 'B1' }]
  const word = (i) => ({ lemma: `w${i}`, fsrs: { ...createEmptyCard(), state: 2 } })
  const words = (n) => Array.from({ length: n }, (_, i) => word(i))
  assert.deepEqual(newGrammar(words(100), G, 1), []) // not enough vocabulary yet
  assert.deepEqual(newGrammar(words(200), G, 1), [gramKey('a')])
  const learningA = [...words(200), { lemma: gramKey('a'), fsrs: { ...createEmptyCard(), state: 1 } }]
  assert.deepEqual(newGrammar(learningA, G, 2), []) // b waits until a has graduated
  const doneA = [...words(200), { lemma: gramKey('a'), fsrs: { ...createEmptyCard(), state: 2 } }]
  assert.deepEqual(newGrammar(doneA, G, 2), [gramKey('b')]) // c needs B1 vocabulary
  const w = new Map()
  assert.equal(playable({ lemma: gramKey('a') }, w, null, G), true)
  assert.equal(playable({ lemma: gramKey('zzz') }, w, null, G), false)
  assert.equal(playable({ lemma: 'fix/x', fix: { sentence: 's' } }, w, null, G), true)
})

test('corrections become practice cards', () => {
  assert.deepEqual(firstChange("Je suis allé a la plage", "Je suis allée à la plage."), { answer: 'allée à', wrong: 'allé a' })
  assert.deepEqual(firstChange('Il mange', 'Il mange bien.'), { answer: 'bien', wrong: '' })
  assert.equal(firstChange('Pareil.', 'Pareil.'), null)
  const c = fixCard("J'habite à Paris.", 'à', 'a', 'Accent on the preposition.', 0)
  assert.ok(c.lemma.startsWith('fix/') && c.fix.answer === 'à' && c.fsrs.state === 0)
  assert.equal(fixCard('Bonjour.', 'absent', '', ''), null) // the answer must be in the sentence
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

test('brief gloss', () => {
  assert.equal(brief('to have; to own; to possess'), 'to have; to own')
  assert.equal(brief('dog', 1), 'dog')
})

test('timed cues', () => {
  const srt = '1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>Bonjour</i>\r\ntoi.\r\n\r\n2\r\n01:00:03,250 --> 01:00:04,000\r\nÇa va ?\r\n'
  assert.deepEqual(parseCues(srt), [{ start: 1, end: 2.5, text: 'Bonjour toi.' }, { start: 3603.25, end: 3604, text: 'Ça va ?' }])
  const vtt = 'WEBVTT\n\n00:05.000 --> 00:06.000 align:start\nSalut.\n\nNOTE skipped\n\n00:02.000 --> 00:03.000\nAvant.'
  assert.deepEqual(parseCues(vtt).map((c) => c.text), ['Avant.', 'Salut.']) // sorted by start
  const cues = parseCues(vtt)
  assert.deepEqual([0, 2, 2.5, 4, 5.5, 99].map((t) => cueAt(cues, t)), [-1, 0, 0, 0, 1, 1])
  assert.equal(cueAt([], 3), -1)
  assert.deepEqual([0, 9.9, 10, 600, 2500].map(listenGoal), [10, 10, 50, 1000, 3000])
})

test('norman bridge lookup', () => {
  const cog = new Map(Object.entries({ chamber: ['chambre', ''], melancholy: ['mélancolie', 'ie>y'], study: ['étude', 'es'],
    library: ['librairie', 'faux:bookshop'], arrive: ['arriver', 'er>'] }))
  assert.deepEqual(enLookup('Chambers', cog), ['chambre', '', 'chamber'])
  assert.deepEqual(enLookup('studies', cog)?.[0], 'étude')
  assert.deepEqual(enLookup('arrived', cog)?.[0], 'arriver') // -ed on a word ending in e
  assert.deepEqual(enLookup('arriving', cog)?.[0], 'arriver')
  assert.equal(enLookup('the', cog), null)
  const toks = englishTokens('The melancholy library’s chambers.', cog)
  assert.deepEqual(toks.map((t) => t.fr), [null, 'mélancolie', 'librairie', 'chambre'])
  assert.deepEqual(toks.map((t) => t.faux), [false, false, true, false])
  assert.equal(toks[2].text, 'library’s')
})

test('word diff', () => {
  const d = wordDiff("Je suis allé a la plage hier", "Je suis allée à la plage hier.")
  assert.deepEqual(d.filter((x) => x.op !== 'same').map((x) => `${x.op}:${x.text}`),
    ['del:allé', 'del:a', 'add:allée', 'add:à', 'del:hier', 'add:hier.'])
  assert.deepEqual(wordDiff('a b', 'a b').map((x) => x.op), ['same', 'same'])
  assert.deepEqual(wordDiff('', 'Bonjour').map((x) => x.op), ['add'])
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

test('comprehension quiz passes at two thirds', () => {
  const qs = [{ ok: 1 }, { ok: 2 }, { ok: 0 }]
  assert.deepEqual(gradeQuiz(qs, [1, 2, 0]), { right: 3, total: 3, pass: true })
  assert.deepEqual(gradeQuiz(qs, [1, 2, 1]), { right: 2, total: 3, pass: true })
  assert.deepEqual(gradeQuiz(qs, [1, 0, 1]), { right: 1, total: 3, pass: false })
  assert.deepEqual(gradeQuiz(qs, [-1, -1, -1]), { right: 0, total: 3, pass: false })
  assert.deepEqual(gradeQuiz([{ ok: 0 }, { ok: 0 }], [0, 0]), { right: 2, total: 2, pass: true })
})

test('sentencesOf: offsets point at the text, quotes are not spoken', () => {
  const text = "Il fait noir. Très noir.\n« Il y a quelqu'un ? » Oui !"
  const out = sentencesOf(text)
  assert.equal(out.length, 4)
  for (const [a, b, id] of out) {
    assert.equal(id, null)
    assert.ok(text.slice(a, b).trim())
  }
  assert.equal(text.slice(...out[2].slice(0, 2)), "Il y a quelqu'un ?")
  assert.equal(out[2][2], null)
})

test('replayCard applies both devices reviews in time order', () => {
  const a = { key: 'devA:1', at: 1000, rating: Rating.Good }
  const b = { key: 'devB:1', at: 2000, rating: Rating.Good }
  const both = replayCard([b, a], 0)
  assert.equal(both.reps, 2)
  assert.deepEqual(replayCard([a, b], 0), both) // input order does not matter
  // a tie is broken by key so every device lands on the same state
  const t1 = { key: 'devA:1', at: 1000, rating: Rating.Good }
  const t2 = { key: 'devB:1', at: 1000, rating: Rating.Again }
  assert.deepEqual(replayCard([t1, t2], 0), replayCard([t2, t1], 0))
})
