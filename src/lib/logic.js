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

// --- tokenizer: keep in sync with data/fr/build.py ---
const WORD = /\p{L}+(?:['-]\p{L}+)*'?/gu
const PART = /[^'-]+'?/g
const key = (s) => LANG.fold(s.toLowerCase())

/** Words in `text` with offsets. Compounds Lexique knows stay whole ("aujourd'hui"), others split on ' and -. */
export function tokenize(text, forms) {
  const out = []
  const tok = (start, word, lemma) =>
    out.push({ start, end: start + word.length, text: word, lemma: lemma ?? key(word), name: !lemma && /^\p{Lu}/u.test(word) })
  for (const m of text.replace(/’/g, "'").matchAll(WORD)) { // same length, so offsets match `text` (store texts NFC)
    const whole = forms.get(key(m[0]))
    if (whole && !m[0].endsWith("'")) { tok(m.index, m[0], whole); continue }
    for (const p of m[0].matchAll(PART))
      tok(m.index + p.index, p[0], p[0].endsWith("'") ? LANG.elision[key(p[0].slice(0, -1))] : forms.get(key(p[0])))
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

export const RATING = { right: Rating.Good, almost: Rating.Hard, wrong: Rating.Again }

/** Points for one answer. `combo` is the streak before this answer. */
export function points(result, combo, ms, revenge) {
  if (result === 'wrong') return 0
  if (result === 'almost') return 4
  return 10 * (1 + Math.floor(combo / 5)) + (ms < 6000 ? 5 : 0) + (revenge ? 15 : 0)
}

export const nextCombo = (result, combo) => (result === 'right' ? combo + 1 : result === 'almost' ? combo : 0)

/** [before, match, after] around the first whole-word occurrence of `form`. */
export function blank(sentence, form) {
  const m = new RegExp(`(?<!\\p{L})${form.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?!\\p{L})`, 'iu').exec(sentence)
  return m ? [sentence.slice(0, m.index), m[0], sentence.slice(m.index + m[0].length)] : [sentence, '', '']
}

// --- runs ---
const item = (card, isNew) => ({ lemma: card.lemma, card: card.fsrs, phase: isNew ? 'intro' : 'quiz', seen: 0, missed: false })

/** Due reviews (oldest first) with up to NEW_PER_RUN new words spread through them. Mined cards go first. */
export function buildRun(cards, words, now, newToday) {
  const due = cards.filter((c) => !c.known && c.fsrs.state !== State.New && c.fsrs.due <= now)
    .sort((a, b) => a.fsrs.due - b.fsrs.due).slice(0, MAX_REVIEWS_PER_RUN).map((c) => item(c, false))
  const have = new Set(cards.map((c) => c.lemma))
  const n = Math.max(0, Math.min(NEW_PER_RUN, NEW_PER_DAY - newToday))
  const fresh = [...cards.filter((c) => !c.known && c.fsrs.state === State.New)]
  for (const w of words) {
    if (fresh.length >= n) break
    if (w.sentence && !have.has(w.lemma)) fresh.push({ lemma: w.lemma, fsrs: createEmptyCard(now) })
  }
  const queue = [...due]
  fresh.slice(0, n).forEach((c, i) => queue.splice(Math.min(queue.length, i * 5), 0, item(c, true)))
  return queue
}

/** Queue with `it` reinserted 3–5 places on if its card is due again soon; otherwise unchanged. */
export function requeue(queue, it, now, rnd = Math.random) {
  if (it.card.due - now >= LEARN_AHEAD_MS || it.seen >= MAX_SEEN_PER_RUN) return queue
  const at = Math.min(queue.length, 3 + Math.floor(rnd() * 3))
  return [...queue.slice(0, at), it, ...queue.slice(at)]
}

export const dayOf = (d) => d.toLocaleDateString('sv') // local YYYY-MM-DD

/** Streak after finishing a run today. One missed day is forgiven. */
export function nextStreak(streak, lastDay, today) {
  if (lastDay === today) return streak
  return (Date.parse(today) - Date.parse(lastDay)) / 864e5 <= 2 ? streak + 1 : 1
}

// --- reader helpers ---
/** Plain text from .srt/.vtt: drops cue numbers, timestamps, header and tags. */
export const fromSubtitles = (s) =>
  s.split(/\r?\n/).filter((l) => l.trim() && !/^\d+$/.test(l.trim()) && !l.includes('-->') && !/^WEBVTT/.test(l))
    .map((l) => l.replace(/<[^>]+>|\{[^}]+\}/g, '')).join('\n')

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
