// Convert a web export (IndexedDB dump, backup version 1) into the native backup shape (version 4).
// Pure and tested: Settings can offer a one-click hand-off from the PWA to the Swift/Kotlin apps without
// asking the learner to run a script or lose their cards, review log, texts, read history or writing history.

const STATES = ['New', 'Learning', 'Review', 'Relearning']

const finite = (value, fallback = 0) => (Number.isFinite(Number(value)) ? Number(value) : fallback)

/** Epoch millis from a number, Date or ISO string; web backups store dates as ISO strings. */
export function millis(value, fallback = 0) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (value instanceof Date) return value.getTime()
  if (typeof value === 'string') {
    const parsed = Date.parse(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

function nativeCard(lemma, card, now) {
  const fsrs = card?.fsrs ?? {}
  const added = millis(card?.added, now)
  return {
    lemma: card?.lemma ?? String(lemma),
    fsrs: {
      due: millis(fsrs.due, now),
      stability: finite(fsrs.stability),
      difficulty: finite(fsrs.difficulty),
      elapsedDays: finite(fsrs.elapsed_days),
      scheduledDays: finite(fsrs.scheduled_days),
      reps: finite(fsrs.reps),
      lapses: finite(fsrs.lapses),
      learningSteps: finite(fsrs.learning_steps),
      state: STATES[finite(fsrs.state)] ?? 'New',
      lastReview: fsrs.last_review == null ? null : millis(fsrs.last_review, now),
    },
    known: !!card?.known,
    added,
    mined: card?.mined
      ? {
          sentence: String(card.mined.sentence ?? ''),
          form: String(card.mined.form ?? ''),
          translation: String(card.mined.translation ?? ''),
        }
      : null,
    updated: millis(card?.updated, added),
  }
}

function nativeTexts(data, now) {
  const rows = data.texts ?? []
  const numeric = rows.map(([key]) => (typeof key === 'number' && key > 0 ? key : 0))
  let next = Math.max(0, ...numeric) + 1
  const ids = new Map() // web key (including "sample" or "device:id") -> native numeric id
  const texts = rows.map(([key, text]) => {
    const id = typeof key === 'number' && key > 0 ? key : next++
    ids.set(String(key), id)
    return {
      title: String(text?.title ?? 'Untitled'),
      body: String(text?.body ?? ''),
      added: millis(text?.added, now),
      id,
      updated: millis(text?.updated, text?.added ?? now),
    }
  })
  return { texts, ids }
}

function readId(id, textIds) {
  const raw = String(id)
  if (raw.startsWith('text-')) {
    const key = raw.slice(5)
    return textIds.has(key) ? `text-${textIds.get(key)}` : raw
  }
  // Older readers marked the seeded sample text by its string id rather than "text-<id>".
  return textIds.has(raw) ? `text-${textIds.get(raw)}` : raw
}

function nativeStats(meta, textIds, now) {
  const s = meta.get('stats') ?? {}
  const today = new Date(now).toLocaleDateString('sv')
  return {
    day: typeof s.newDay === 'string' ? s.newDay : typeof s.day === 'string' ? s.day : today,
    newCount: Math.max(0, Math.round(finite(s.newCount))),
    read: (Array.isArray(s.read) ? s.read : []).map((id) => readId(id, textIds)),
    listened: Math.max(0, Math.round(finite(s.listened))),
    spoken: Math.max(0, Math.round(finite(s.spoken))),
    talked: Math.max(0, Math.round(finite(s.talked))),
    written: Math.max(0, Math.round(finite(s.written))),
  }
}

function nativeWritings(meta, now) {
  const writings = meta.get('writings')
  if (!Array.isArray(writings)) return { writings: [], corrections: [] }
  const out = []
  const corrections = []
  for (const w of writings) {
    if (!w || typeof w !== 'object') continue
    const when = millis(w.at, now)
    const result = w.result ?? {}
    const errors = (Array.isArray(result.errors) ? result.errors : []).map((e) => ({
      original: String(e?.original ?? ''),
      fix: String(e?.fix ?? ''),
      why: String(e?.why ?? ''),
      kind: String(e?.kind ?? ''),
    }))
    out.push({
      prompt: String(w.topic ?? ''),
      draft: String(w.text ?? ''),
      corrected: String(result.corrected ?? ''),
      feedback: String(result.feedback ?? ''),
      level: String(result.level ?? ''),
      errors,
      revised: typeof w.revised === 'string' ? w.revised : null,
      created: when,
      updated: millis(w.updated, when),
    })
    for (const e of errors) {
      corrections.push({
        source: 'writing',
        kind: e.kind,
        original: e.original,
        fix: e.fix,
        why: e.why,
        at: when,
        resolved: false,
        nextDue: 0,
        reps: 0,
      })
    }
  }
  return { writings: out, corrections }
}

/**
 * `data`: the object returned by db.exportAll() (backup version 1). Returns a native backup version 4.
 * Native `Store.import` validates this before replacing anything.
 */
export function toNativeBackup(data, now = Date.now()) {
  const meta = new Map(data?.meta ?? [])
  const { texts, ids } = nativeTexts(data, now)
  const settings = meta.get('settings') ?? {}
  const pace = {
    perRun: Math.max(0, Math.round(finite(settings.perRun, 5))),
    perDay: Math.max(0, Math.round(finite(settings.perDay, 20))),
  }
  const cards = (data?.cards ?? []).map(([key, card]) => nativeCard(key, card, now))
  const reviews = (data?.log ?? []).map(([, entry]) => ({
    lemma: String(entry?.lemma ?? ''),
    rating: Math.max(1, Math.min(4, Math.round(finite(entry?.rating, 3)))),
    mode: String(entry?.mode ?? 'cloze'),
    at: millis(entry?.at, now),
    overridden: !!entry?.overridden,
  }))
  const stats = nativeStats(meta, ids, now)
  const { writings, corrections } = nativeWritings(meta, now)
  return {
    version: 4,
    cards,
    reviews,
    texts,
    writings,
    corrections,
    stats,
    canDo: [],
    pace,
    practiceDays: [],
  }
}
