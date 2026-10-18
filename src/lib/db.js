// Tiny promise wrapper over IndexedDB.
// Stores: cards (by lemma), log (append-only reviews, the source of truth for sync later), texts, meta.
import { LANG } from './lang.js'

const db = new Promise((resolve, reject) => {
  const r = indexedDB.open(`pluton-${LANG.code}`, 1) // one database per language
  r.onupgradeneeded = () => {
    r.result.createObjectStore('cards', { keyPath: 'lemma' })
    r.result.createObjectStore('log', { autoIncrement: true })
    r.result.createObjectStore('texts', { keyPath: 'id', autoIncrement: true })
    r.result.createObjectStore('meta')
  }
  r.onsuccess = () => resolve(r.result)
  r.onerror = () => reject(r.error)
})

const done = (t) => new Promise((resolve, reject) => {
  t.oncomplete = resolve
  t.onerror = t.onabort = () => reject(t.error)
})
const result = (r) => new Promise((resolve, reject) => {
  r.onsuccess = () => resolve(r.result)
  r.onerror = () => reject(r.error)
})

export const getAll = async (store) => result((await db).transaction(store).objectStore(store).getAll())
export const get = async (store, key) => result((await db).transaction(store).objectStore(store).get(key))
export const put = async (store, value, key) =>
  result((await db).transaction(store, 'readwrite').objectStore(store).put(value, key))
export const del = async (store, key) => result((await db).transaction(store, 'readwrite').objectStore(store).delete(key))

/** Many records in one transaction (keyPath stores). */
export async function putMany(store, values) {
  const t = (await db).transaction(store, 'readwrite')
  for (const v of values) t.objectStore(store).put(v)
  return done(t)
}

/** Card state + its review log entry in one transaction, so they never disagree. */
export async function review(card, entry) {
  const t = (await db).transaction(['cards', 'log'], 'readwrite')
  t.objectStore('cards').put(card)
  t.objectStore('log').add(entry)
  return done(t)
}

const STORES = ['cards', 'log', 'texts', 'meta']
const SECRET = 'ai' // meta key holding the AI provider config and API key: never in backups

export async function exportAll() {
  const t = (await db).transaction(STORES)
  const out = { version: 1, exported: new Date().toISOString() }
  for (const s of STORES) {
    const os = t.objectStore(s)
    const [keys, values] = await Promise.all([result(os.getAllKeys()), result(os.getAll())])
    out[s] = keys.map((k, i) => [k, values[i]]).filter(([k]) => !(s === 'meta' && k === SECRET))
  }
  return out
}

const reviveDates = (c) => {
  for (const k of ['due', 'last_review']) if (c.fsrs?.[k]) c.fsrs[k] = new Date(c.fsrs[k])
  return c
}

/** Replaces everything with a backup from exportAll(). All-or-nothing. */
export async function importAll(data) {
  if (data?.version !== 1 || !STORES.every((s) => Array.isArray(data[s]))) throw new Error('Not a Pluton backup')
  const keep = await get('meta', SECRET) // this device's AI key survives a restore
  const t = (await db).transaction(STORES, 'readwrite')
  for (const s of STORES) {
    const os = t.objectStore(s)
    os.clear()
    for (const [k, v] of data[s]) if (!(s === 'meta' && k === SECRET)) os.keyPath ? os.put(s === 'cards' ? reviveDates(v) : v) : os.put(v, k)
  }
  if (keep) t.objectStore('meta').put(keep, SECRET)
  return done(t)
}
