// Pure logic: tokenizer, coverage, grading, scoring, run queue, streak. No DOM, no IndexedDB — see logic.test.js.
import { fsrs, createEmptyCard, Rating, State } from 'ts-fsrs'
import { LANG } from './lang.js'

export { createEmptyCard, Rating, State }
export const scheduler = fsrs({ enable_fuzz: true })

export const KNOWN_DAYS = 21 // FSRS stability at which a word counts as "known"
export const UNLOCK = 0.95 // share of known words at which a text is comfortable to read
export const LEARN_AHEAD_MS = 20 * 60_000 // cards due again within this come back in the same run (Anki's learn-ahead)
export const NEW_PER_RUN = 5
export const NEW_PER_DAY = 20
export const MAX_REVIEWS_PER_RUN = 40
export const MAX_SEEN_PER_RUN = 5 // per card, so a run always ends even if you keep missing one
export const CONJ_PER_RUN = 2
export const UNLOCK_NEXT_TENSE = 8 // tables of a tense in long-term review before the next tense opens

// Non-word cards live in the same store as words, keyed with a "/" (French words never contain one):
// conj/<verb>/<tense>, gram/<topic> (a grammar topic), fix/<id> (a correction from your own writing).
export const CONJ = 'conj/', GRAM = 'gram/', FIX = 'fix/'
export const conjKey = (verb, tense) => `${CONJ}${verb}/${tense}`
export const parseConj = (key) => {
  const [, verb, tense] = key.split('/')
  return { verb, tense }
}
export const gramKey = (id) => `${GRAM}${id}`
export const isWord = (c) => !c.lemma.includes('/')
const graduated = (c) => c.known || c.fsrs.state === State.Review || c.fsrs.state === State.Relearning

// --- tokenizer: keep in sync with data/fr/build.py ---
const WORD = /\p{L}+(?:['-]\p{L}+)*'?/gu
const PART = /[^'-]+'?/g
const key = (s) => LANG.fold(s.toLowerCase())

const SENTENCE_START = /(?:[.!?…:«»"“—\n]\s*|^\s*)$/ // what may come right before a sentence's first word
const upper = (s) => /^\p{Lu}/u.test(s)

/**
 * Words in `text` with offsets. Compounds Lexique knows stay whole ("aujourd'hui"), others split on ' and -.
 * French capitalises little mid-sentence, so a capital there marks a name ("à Paris" is not the plural of "pari").
 */
export function tokenize(text, forms) {
  const out = []
  const tok = (start, word, lemma, name = !lemma && upper(word)) =>
    out.push({ start, end: start + word.length, text: word, lemma: lemma ?? key(word), name })
  const plain = text.replace(/’/g, "'") // same length, so offsets match `text` (store texts NFC)
  for (const m of plain.matchAll(WORD)) {
    if (upper(m[0]) && !SENTENCE_START.test(plain.slice(Math.max(0, m.index - 3), m.index))) {
      tok(m.index, m[0], null, true)
      continue
    }
    const whole = forms.get(key(m[0]))
    if (whole && !m[0].endsWith("'")) { tok(m.index, m[0], whole); continue }
    for (const p of m[0].matchAll(PART)) {
      if (p.index > 0 && upper(p[0])) tok(m.index + p.index, p[0], null, true) // L'Italie
      else if (p.index > 0 && key(p[0]) === 't') continue // the euphonic -t- in a-t-il
      else tok(m.index + p.index, p[0], p[0].endsWith("'") ? LANG.elision[key(p[0].slice(0, -1))] : forms.get(key(p[0])))
    }
  }
  return out
}

/** 'known' | 'learning' | 'queued' (added, never reviewed) | 'new' for a lemma, given its card and dictionary entry. */
export function status(card, word) {
  if (card?.known || card?.fsrs.stability >= KNOWN_DAYS || (!card && word?.pos === 'interj')) return 'known'
  if (!card) return 'new'
  return card.fsrs.state === State.New ? 'queued' : 'learning'
}

/** Coverage of a token list; names don't count. Only known + learning count as understood: queuing isn't learning. */
export function coverage(tokens, statusOf) {
  const c = { known: 0, learning: 0, queued: 0, new: 0 }
  for (const t of tokens) if (!t.name) c[statusOf(t.lemma)]++
  const total = c.known + c.learning + c.queued + c.new
  return { ...c, total, pct: total ? (c.known + c.learning) / total : 0 }
}

/** The lemmas to learn (most frequent in the text first) to reach `target` coverage. */
export function unlockList(tokens, statusOf, target = UNLOCK) {
  const c = coverage(tokens, statusOf)
  let gap = c.queued + c.new - Math.floor(c.total * (1 - target))
  const counts = new Map()
  for (const t of tokens)
    if (!t.name && ['new', 'queued'].includes(statusOf(t.lemma))) counts.set(t.lemma, (counts.get(t.lemma) ?? 0) + 1)
  const out = []
  for (const [lemma, n] of [...counts].sort((a, b) => b[1] - a[1])) {
    if (gap <= 0) break
    gap -= n
    out.push(lemma)
  }
  return out
}

// --- grading ---
const norm = (s) => key(s.normalize('NFC').trim().replace(/’/g, "'").replace(/\s+/g, ' '))
const strip = (s) => s.normalize('NFD').replace(/\p{M}/gu, '')

export function levenshtein(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i)
  for (let i = 1; i <= a.length; i++) {
    const cur = [i]
    for (let j = 1; j <= b.length; j++)
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1))
    prev = cur
  }
  return prev[b.length]
}

/**
 * {result: 'right'|'almost'|'wrong', why?, other?}. A real French word for a different lemma is wrong even
 * if it's one accent away (a/à, ou/où); typo leniency only applies to non-words, because French endings
 * (-e/-es/-ent, -é/-ée) differ by one character and those mistakes are real.
 */
export function grade(typed, expected, lemma, forms) {
  const t = norm(typed), e = norm(expected)
  if (t === e) return { result: 'right' }
  const other = forms.get(t)
  if (other && other !== lemma) return { result: 'wrong', why: 'other', other }
  if (t && strip(t) === strip(e)) return { result: 'almost', why: 'accent' }
  if (other === lemma) return { result: 'almost', why: 'form' }
  if (e.length >= 4 && levenshtein(t, e) === 1) return { result: 'almost', why: 'typo' }
  return { result: 'wrong' }
}

const ARTICLE_GENDER = { le: 'm', un: 'm', la: 'f', une: 'f', "l'": null }

/** Grading for English -> French recall: nouns must come with an article of the right gender. */
export function gradeProduce(typed, word, forms) {
  if (word.pos !== 'noun' || !word.gender) return grade(typed, word.lemma, word.lemma, forms)
  const m = /^(une |un |le |la |l')\s*(.+)$/.exec(norm(typed)) // the space keeps "une" from parsing as "un" + "e…"
  const g = grade(m ? m[2] : typed, word.lemma, word.lemma, forms)
  if (g.result === 'wrong') return g
  if (!m) return { result: 'almost', why: 'article' }
  const gender = ARTICLE_GENDER[m[1].trim()]
  if (gender && gender !== word.gender) return { result: 'almost', why: 'gender' }
  return g
}

/** What a production card expects: "la maison" for nouns, the lemma otherwise. */
export const produceAnswer = (w) => (w.pos === 'noun' && w.gender ? w.display : w.lemma)

/** The first `n` senses of a Wiktionary gloss: "to have; to own; to possess; …" -> "to have; to own". */
export const brief = (gloss, n = 2) => gloss.split('; ').slice(0, n).join('; ')

/** First letter, then a dot per letter: "maison" -> "m•••••". */
export const hintOf = (w) => w.lemma[0] + w.lemma.slice(1).replace(/\p{L}/gu, '•')

const PRODUCE_POS = new Set(['noun', 'verb', 'adj', 'adv'])

/** Exercise for a card's next review: fill the gap, then listen, then (every third rep, once the word has
 *  graduated from learning steps) recall from English with no sentence to lean on. Words already known get a
 *  full-sentence dictée every fourth rep. */
export function modeFor(card, word) {
  if (card.reps % 4 === 3 && card.stability >= KNOWN_DAYS && word.audio_sentence) return 'dictee'
  if (card.reps % 3 === 2 && card.state === State.Review && PRODUCE_POS.has(word.pos) && word.gloss) return 'produce'
  return card.reps % 2 ? 'dictation' : 'cloze'
}

const WORDS = /[\p{L}\p{N}'’-]+/gu

/**
 * Dictée: what was typed, aligned word by word against the sentence (edit distance over words). marks give each
 * sentence word's offsets and 'ok' | 'accent' | 'bad', for highlighting. One slip, or accents only, is "almost".
 */
export function gradeDictee(typed, sentence) {
  const ref = [...sentence.matchAll(WORDS)]
  const a = ref.map((m) => norm(m[0])), b = [...norm(typed).matchAll(WORDS)].map((m) => m[0])
  const cost = (i, j) => (a[i] === b[j] ? 0 : 1)
  const d = Array.from({ length: a.length + 1 }, (_, i) => Array.from({ length: b.length + 1 }, (_, j) => (i ? (j ? 0 : i) : j)))
  for (let i = 1; i <= a.length; i++)
    for (let j = 1; j <= b.length; j++)
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost(i - 1, j - 1))
  const marks = Array(a.length).fill('bad')
  for (let i = a.length, j = b.length; i > 0 && j >= 0;) {
    if (j > 0 && d[i][j] === d[i - 1][j - 1] + cost(i - 1, j - 1)) {
      marks[i - 1] = a[i - 1] === b[j - 1] ? 'ok' : strip(a[i - 1]) === strip(b[j - 1]) ? 'accent' : 'bad'
      i--, j--
    } else if (d[i][j] === d[i - 1][j] + 1) i--
    else j--
  }
  const errors = d[a.length][b.length]
  const accentsOnly = errors > 0 && marks.filter((m) => m === 'accent').length === errors
  const result = errors === 0 ? 'right' : errors === 1 || accentsOnly ? 'almost' : 'wrong'
  return { result, ...(accentsOnly && { why: 'accent' }), marks: ref.map((m, i) => ({ start: m.index, end: m.index + m[0].length, mark: marks[i] })) }
}

export const RATING = { right: Rating.Good, almost: Rating.Hard, wrong: Rating.Again }

/** Points for one answer. `combo` is the streak before this answer. */
export function points(result, combo, ms, revenge) {
  if (result === 'wrong') return 0
  if (result === 'almost') return 4
  return 10 * (1 + Math.floor(combo / 5)) + (ms < 6000 ? 5 : 0) + (revenge ? 15 : 0)
}

export const nextCombo = (result, combo) => (result === 'right' ? combo + 1 : result === 'almost' ? combo : 0)

/** Level from lifetime points. Level n+1 starts at 50·n·(n+1): 100, 300, 600, 1000, … (a run is ~150–300). */
export function levelFor(points) {
  let n = 1
  while (50 * n * (n + 1) <= points) n++
  const from = 50 * (n - 1) * n, to = 50 * n * (n + 1)
  return { level: n, into: points - from, span: to - from }
}

/** [before, match, after] around the first whole-word occurrence of `form`. */
export function blank(sentence, form) {
  const m = new RegExp(`(?<!\\p{L})${form.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\p{L})`, 'iu').exec(sentence)
  return m ? [sentence.slice(0, m.index), m[0], sentence.slice(m.index + m[0].length)] : [sentence, '', '']
}

const PRONOUN = /^(que |qu')?(je |j'|tu |il |elle |on |nous |vous |ils |elles )/

/** Grading for a conjugation cell. A leading pronoun is fine; être-verbs' participle agreement isn't tested. */
export function gradeConj(typed, expected, agrees = false) {
  const clean = (s) => {
    const x = norm(s).replace(PRONOUN, '')
    return agrees ? x.replace(/(es|e|s)$/, '') : x
  }
  const t = clean(typed), e = clean(expected)
  if (t === e) return { result: 'right' }
  if (t && strip(t) === strip(e)) return { result: 'almost', why: 'accent' }
  return { result: 'wrong' }
}

/**
 * Conjugation tables to introduce next, in tense order. A tense opens once UNLOCK_NEXT_TENSE tables of the previous
 * one are in long-term review; a verb's tables wait until the verb itself has been learned (or marked known).
 */
export function newConj(cards, conj, order, n) {
  const have = new Map(cards.map((c) => [c.lemma, c]))
  const out = []
  for (let k = 0; k < order.length && out.length < n; k++) {
    if (k > 0) {
      const ready = cards.filter((c) => c.lemma.startsWith(CONJ) && parseConj(c.lemma).tense === order[k - 1]
        && c.fsrs.state === State.Review).length
      if (ready < UNLOCK_NEXT_TENSE) break
    }
    for (const verb of Object.keys(conj)) {
      if (out.length >= n) break
      const word = have.get(verb)
      const learned = word && (word.known || word.fsrs.state !== State.New)
      if (learned && conj[verb].t[order[k]] && !have.has(conjKey(verb, order[k]))) out.push(conjKey(verb, order[k]))
    }
  }
  return out
}

/** Whether a stored card can be shown with the current data: a rebuild can drop a sentence, a table or a topic. */
export function playable(c, words, conj, grammar) {
  if (isWord(c)) return !!(c.mined || words.get(c.lemma)?.sentence)
  if (c.lemma.startsWith(FIX)) return !!c.fix
  if (c.lemma.startsWith(GRAM)) return !!grammar?.some((t) => gramKey(t.id) === c.lemma)
  const { verb, tense } = parseConj(c.lemma)
  return !!conj?.[verb]?.t[tense]
}

// Grammar topics start once the vocabulary can carry them, one level at a time.
export const GRAMMAR_FROM = { A2: 150, B1: 600, B2: 1500, C1: 3000 }

/** The next grammar topics to introduce (in order): its level's vocabulary reached, the previous topic graduated. */
export function newGrammar(cards, grammar, n) {
  const have = new Map(cards.map((c) => [c.lemma, c]))
  const learned = cards.filter((c) => isWord(c) && graduated(c)).length
  const out = []
  for (const [i, t] of (grammar ?? []).entries()) {
    if (out.length >= n || learned < (GRAMMAR_FROM[t.level] ?? Infinity)) break
    const prev = i && have.get(gramKey(grammar[i - 1].id))
    if (i && !(prev && graduated(prev))) break
    if (!have.has(gramKey(t.id))) out.push(gramKey(t.id))
  }
  return out
}

/** Grading for a grammar gap or a correction: any accepted answer is right; accents or a lost apostrophe, almost. */
export function gradeGram(typed, answers) {
  const t = norm(typed), loose = (s) => strip(s).replace(/'/g, '')
  if (answers.some((a) => norm(a) === t)) return { result: 'right' }
  if (t && answers.some((a) => loose(norm(a)) === loose(t))) return { result: 'almost', why: 'accent' }
  return { result: 'wrong' }
}

/** A practice card from a correction: the corrected sentence with the fixed words to type back. */
export function fixCard(sentence, answer, wrong, why, now = Date.now()) {
  if (!sentence || !answer || !sentence.includes(answer)) return null
  return { lemma: `${FIX}${now.toString(36)}${Math.random().toString(36).slice(2, 6)}`, fsrs: createEmptyCard(new Date(now)),
    added: now, fix: { sentence, answer, wrong, why } }
}

/** The first change a correction made: the words added (answer) and the ones they replaced (wrong). */
export function firstChange(from, to) {
  const d = wordDiff(from, to)
  const i = d.findIndex((x) => x.op !== 'same')
  if (i < 0) return null
  let j = i
  while (j < d.length && d[j].op !== 'same') j++
  const run = d.slice(i, j)
  const answer = run.filter((x) => x.op === 'add').map((x) => x.text).join(' ').replace(/[.,!?;:…]+$/, '')
  return answer ? { answer, wrong: run.filter((x) => x.op === 'del').map((x) => x.text).join(' ') } : null
}

// --- runs ---
const item = (card, isNew) => ({ lemma: card.lemma, card: card.fsrs, phase: isNew ? 'intro' : 'quiz', seen: 0, missed: false })

/** Due reviews (oldest first) with up to perRun new words spread through them (mined cards first), then up to
 *  CONJ_PER_RUN new conjugation tables. New words and tables share the perDay budget. */
export function buildRun(cards, words, now, newToday,
  { conj = null, tenses = [], grammar = null, perRun = NEW_PER_RUN, perDay = NEW_PER_DAY } = {}) {
  cards = cards.filter((c) => playable(c, words, conj, grammar))
  const due = cards.filter((c) => !c.known && c.fsrs.state !== State.New && c.fsrs.due <= now)
    .sort((a, b) => a.fsrs.due - b.fsrs.due).slice(0, MAX_REVIEWS_PER_RUN).map((c) => item(c, false))
  const have = new Set(cards.map((c) => c.lemma))
  const n = Math.max(0, Math.min(perRun, perDay - newToday))
  const fresh = [...cards.filter((c) => !c.known && c.fsrs.state === State.New)]
  for (const w of words.values()) {
    if (fresh.length >= n) break
    if (w.sentence && !have.has(w.lemma)) fresh.push({ lemma: w.lemma, fsrs: createEmptyCard(now) })
  }
  const queue = [...due]
  fresh.slice(0, n).forEach((c, i) => queue.splice(Math.min(queue.length, i * 5), 0, item(c, true)))
  const room = Math.max(0, Math.min(CONJ_PER_RUN, perDay - newToday - Math.min(n, fresh.length)))
  const tables = conj ? newConj(cards, conj, tenses, room) : []
  tables.forEach((key, i) => queue.splice(Math.min(queue.length, 3 + i * 6), 0, item({ lemma: key, fsrs: createEmptyCard(now) }, true)))
  if (room > tables.length) newGrammar(cards, grammar, 1)
    .forEach((key) => queue.splice(Math.min(queue.length, 8), 0, item({ lemma: key, fsrs: createEmptyCard(now) }, true)))
  return queue
}

/** Queue with `it` reinserted 3–5 places on if its card is due again soon; otherwise unchanged. */
export function requeue(queue, it, now, rnd = Math.random) {
  if (it.card.due - now >= LEARN_AHEAD_MS || it.seen >= MAX_SEEN_PER_RUN) return queue
  const at = Math.min(queue.length, 3 + Math.floor(rnd() * 3))
  return [...queue.slice(0, at), it, ...queue.slice(at)]
}

export const dayOf = (d) => d.toLocaleDateString('sv') // local YYYY-MM-DD

const daysBetween = (a, b) => (Date.parse(b) - Date.parse(a)) / 864e5

/** Streak after finishing a run today. One missed day is forgiven. */
export function nextStreak(streak, lastDay, today) {
  if (lastDay === today) return streak
  return daysBetween(lastDay, today) <= 2 ? streak + 1 : 1
}

/** The streak as it stands today: still alive until more than one day has been missed. */
export const liveStreak = (streak, lastDay, today) => (daysBetween(lastDay, today) <= 2 ? streak : 0)

/** Word-level diff of `from` -> `to` as [{text, op: 'same'|'del'|'add'}], to show what a correction changed. */
export function wordDiff(from, to) {
  const a = from.split(/\s+/).filter(Boolean), b = to.split(/\s+/).filter(Boolean)
  const L = Array.from({ length: a.length + 1 }, () => Array(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--) L[i][j] = a[i] === b[j] ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1])
  const out = []
  let i = 0, j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) out.push({ text: a[i++], op: 'same' }), j++
    else if (L[i + 1][j] >= L[i][j + 1]) out.push({ text: a[i++], op: 'del' })
    else out.push({ text: b[j++], op: 'add' })
  }
  while (i < a.length) out.push({ text: a[i++], op: 'del' })
  while (j < b.length) out.push({ text: b[j++], op: 'add' })
  return out
}

// --- Norman Bridge: English text with its French-derived words ---
const EN_WORD = /[A-Za-z]+(?:['’][a-z]+)?/g
const EN_FORMS = [(w) => w, (w) => w.replace(/ies$/, 'y'), (w) => w.replace(/es$/, ''), (w) => w.replace(/s$/, ''),
  (w) => w.replace(/ed$/, ''), (w) => w.replace(/ed$/, 'e'), (w) => w.replace(/d$/, ''), (w) => w.replace(/ing$/, ''),
  (w) => w.replace(/ing$/, 'e'), (w) => w.replace(/ily$/, 'y'), (w) => w.replace(/ly$/, ''), (w) => w.replace(/ness$/, '')]

/** [french, clue, base English form] for an English word via the cognate map, trying common inflections. */
export function enLookup(word, cognates) {
  const w = word.toLowerCase().replace(/['’]s$/, '')
  for (const f of EN_FORMS) {
    const base = f(w)
    if (base.length >= 4 && cognates.has(base)) return [...cognates.get(base), base]
  }
  return null
}

/** English tokens with offsets and, where English got the word from French, its French counterpart. */
export function englishTokens(text, cognates) {
  return [...text.matchAll(EN_WORD)].map((m) => {
    const hit = enLookup(m[0], cognates)
    return { start: m.index, end: m.index + m[0].length, text: m[0], fr: hit?.[0] ?? null,
      clue: hit?.[1] ?? '', base: hit?.[2] ?? '', faux: !!hit?.[1].startsWith('faux:') }
  })
}

// --- reader helpers ---
/** Plain text from .srt/.vtt: drops cue numbers, timestamps, header and tags. */
export const fromSubtitles = (s) =>
  s.split(/\r?\n/).filter((l) => l.trim() && !/^\d+$/.test(l.trim()) && !l.includes('-->') && !/^WEBVTT/.test(l))
    .map((l) => l.replace(/<[^>]+>|\{[^}]+\}/g, '')).join('\n')

const TIME = /(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{3})/
const secs = (m) => (+(m[1] ?? 0)) * 3600 + +m[2] * 60 + +m[3] + +m[4] / 1000

/** Timed lines from .srt / .vtt: [{start, end, text}] in seconds, sorted. Tags and cue settings dropped. */
export function parseCues(s) {
  const cues = []
  for (const block of s.replace(/\r/g, '').split(/\n{2,}/)) {
    const lines = block.split('\n')
    const i = lines.findIndex((l) => l.includes('-->'))
    if (i < 0) continue
    const [a, b] = lines[i].split('-->').map((x) => TIME.exec(x))
    const text = lines.slice(i + 1).join(' ').replace(/<[^>]+>|\{[^}]+\}/g, '').replace(/\s+/g, ' ').trim()
    if (a && b && text) cues.push({ start: secs(a), end: secs(b), text: text.normalize('NFC') })
  }
  return cues.sort((x, y) => x.start - y.start)
}

/** Index of the cue showing at time t: the last one started (kept on screen through gaps), or -1 before the first. */
export function cueAt(cues, t) {
  let lo = 0, hi = cues.length - 1, found = -1
  while (lo <= hi) {
    const mid = (lo + hi) >> 1
    if (cues[mid].start <= t) { found = mid; lo = mid + 1 } else hi = mid - 1
  }
  return found
}

/** Next milestone above n, for progress bars that always have somewhere to go; past the list, the next multiple of its last step. */
export const milestone = (n, steps) => steps.find((g) => g > n) ?? (Math.floor(n / steps.at(-1)) + 1) * steps.at(-1)
export const listenGoal = (h) => milestone(h, [10, 50, 100, 250, 500, 1000])

/** The sentence around offset `at`, cropped to ~160 chars so a mined card stays readable. */
export function sentenceAt(text, at) {
  for (const m of text.matchAll(/[^.!?…\n]+[.!?…]*/g)) {
    if (at < m.index || at >= m.index + m[0].length) continue
    const s = m[0].trim()
    if (s.length <= 160) return s
    const off = at - m.index - (m[0].length - m[0].trimStart().length)
    return '…' + s.slice(Math.max(0, off - 80), off + 80).trim() + '…'
  }
  return ''
}
