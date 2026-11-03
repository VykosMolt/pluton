// Tiny promise wrapper over IndexedDB.
// Stores: cards (by lemma), log (append-only reviews), texts, meta. Everything stays on this device.
import { LANG } from './lang.js'

let opened = null // opened on first use, so modules importing this also load where there's no IndexedDB (tests)
const db = () => (opened ??= new Promise((resolve, reject) => {
  const r = indexedDB.open(`pluton-${LANG.code}`, 1) // one database per language
  r.onupgradeneeded = () => {
    r.result.createObjectStore('cards', { keyPath: 'lemma' })
    r.result.createObjectStore('log', { autoIncrement: true })
    r.result.createObjectStore('texts', { keyPath: 'id', autoIncrement: true })
    r.result.createObjectStore('meta')
  }
  r.onsuccess = () => resolve(r.result)
  r.onerror = () => reject(r.error)
}))

const done = (t) => new Promise((resolve, reject) => {
  t.oncomplete = resolve
  t.onerror = t.onabort = () => reject(t.error)
})
const result = (r) => new Promise((resolve, reject) => {
  r.onsuccess = () => resolve(r.result)
  r.onerror = () => reject(r.error)
})

// The AI key and legacy sync bookkeeping never enter a backup.
const PRIVATE = new Set(['ai', '_sync', '_stamps', '_tomb'])

export const getAll = async (store) => result((await db()).transaction(store).objectStore(store).getAll())
export const get = async (store, key) => result((await db()).transaction(store).objectStore(store).get(key))

/** [key, value] pairs of a store. */
export async function entries(store) {
  const os = (await db()).transaction(store).objectStore(store)
  const [keys, values] = await Promise.all([result(os.getAllKeys()), result(os.getAll())])
  return keys.map((k, i) => [k, values[i]])
}

export async function put(store, value, key) {
  const t = (await db()).transaction(store === 'meta' ? 'meta' : [store], 'readwrite')
  const r = t.objectStore(store).put(value, key)
  await done(t)
  return r.result
}

export async function del(store, key) {
  const t = (await db()).transaction(store, 'readwrite')
  t.objectStore(store).delete(key)
  return done(t)
}

/** Many records in one transaction (keyPath stores). */
export async function putMany(store, values) {
  const t = (await db()).transaction(store, 'readwrite')
  for (const v of values) t.objectStore(store).put(v)
  return done(t)
}

/** Card state + its review log entry in one transaction, so they never disagree. */
export async function review(card, entry) {
  const t = (await db()).transaction(['cards', 'log'], 'readwrite')
  t.objectStore('cards').put(card)
  t.objectStore('log').add(entry)
  return done(t)
}

const STORES = ['cards', 'log', 'texts', 'meta']

export async function exportAll() {
  const out = { version: 1, exported: new Date().toISOString() }
  const t = (await db()).transaction(STORES)
  await Promise.all(STORES.map(async (s) => {
    const os = t.objectStore(s)
    const [keys, values] = await Promise.all([result(os.getAllKeys()), result(os.getAll())])
    out[s] = keys.map((k, i) => [k, values[i]]).filter(([k]) => !(s === 'meta' && PRIVATE.has(k)))
  }))
  return out
}

const reviveDates = (c) => {
  for (const k of ['due', 'last_review']) if (c.fsrs?.[k] != null) {
    c.fsrs[k] = new Date(c.fsrs[k])
    if (!Number.isFinite(c.fsrs[k].getTime())) throw new Error('Invalid review date in backup')
  }
  return c
}

// Validate and clone before opening a write transaction: malformed records must never partially replace progress.
function prepareBackup(data) {
  if (data?.version !== 1 || !STORES.every((s) => Array.isArray(data[s]))) throw new Error('Not a Pluton backup')
  const out = {}
  const validKey = (k) => (typeof k === 'string' && k.length > 0) || (typeof k === 'number' && Number.isFinite(k))
  for (const s of STORES) {
    const keys = new Set()
    out[s] = data[s].map((entry) => {
      if (!Array.isArray(entry) || entry.length !== 2 || !validKey(entry[0]) || keys.has(entry[0]))
        throw new Error(`Invalid ${s} record in backup`)
      const [k, value] = entry
      keys.add(k)
      const v = structuredClone(value)
      if (s === 'cards') {
        const memory = v?.fsrs
        const counters = ['elapsed_days', 'scheduled_days', 'reps', 'lapses', 'learning_steps']
        if (!v || typeof v.lemma !== 'string' || v.lemma !== k || !Number.isFinite(v.added) || v.added < 0 ||
            !memory || memory.due == null || ![0, 1, 2, 3].includes(memory.state) ||
            !counters.every((name) => Number.isSafeInteger(memory[name]) && memory[name] >= 0) ||
            !Number.isFinite(memory.stability) || memory.stability < 0 ||
            !Number.isFinite(memory.difficulty) || memory.difficulty < 0 || memory.difficulty > 10 ||
            (memory.state !== 0 && (memory.stability < 0.001 || memory.difficulty < 1)))
          throw new Error('Invalid card in backup')
        reviveDates(v)
      } else if (s === 'texts' && (!v || v.id !== k || typeof v.title !== 'string' || typeof v.body !== 'string')) {
        throw new Error('Invalid text in backup')
      } else if (s === 'log' && (!v || typeof v.lemma !== 'string' || !Number.isFinite(v.at) || ![1, 2, 3, 4].includes(v.rating))) {
        throw new Error('Invalid review in backup')
      }
      return [k, v]
    })
  }
  return out
}

/** Replaces everything with a backup from exportAll(). All-or-nothing. The AI key stays local. */
export async function importAll(data) {
  const records = prepareBackup(data)
  const keep = Object.fromEntries((await entries('meta')).filter(([k]) => k === 'ai'))
  const t = (await db()).transaction(STORES, 'readwrite')
  const finished = done(t)
  try {
    for (const s of STORES) {
      const os = t.objectStore(s)
      os.clear()
      for (const [k, v] of records[s]) if (!(s === 'meta' && PRIVATE.has(k))) os.keyPath ? os.put(v) : os.put(v, k)
    }
    for (const [k, v] of Object.entries(keep)) t.objectStore('meta').put(v, k)
  } catch (e) {
    t.abort()
    await finished.catch(() => {})
    throw e
  }
  return finished
}

/** Erase local data on the existing connection; deleting an open IndexedDB database would remain blocked. */
export async function clearAll() {
  const t = (await db()).transaction(STORES, 'readwrite')
  for (const s of STORES) t.objectStore(s).clear()
  return done(t)
}
