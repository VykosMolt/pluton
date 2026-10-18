// Tiny promise wrapper over IndexedDB.
// Stores: cards (by lemma), log (append-only reviews), texts, meta.
// Every write also records what account sync needs, in the same transaction: an `updated` stamp on cards and texts,
// a change time for synced meta keys (meta "_stamps"), and a tombstone for deletions (meta "_tomb").
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

const STAMPED = new Set(['cards', 'texts'])
export const SYNCED_META = new Set(['stats', 'settings', 'onboarded', 'seeded', 'talk', 'writings'])
const PRIVATE = new Set(['ai', '_sync', '_stamps', '_tomb']) // the AI key and sync bookkeeping: never in backups

const stamp = (store, v) => (STAMPED.has(store) && v && typeof v === 'object' ? { ...v, updated: Date.now() } : v)

/** Merge `patch` into the meta record `key` inside transaction `t`. */
function bump(t, key, patch) {
  const os = t.objectStore('meta')
  const r = os.get(key)
  r.onsuccess = () => os.put({ ...(r.result ?? {}), ...patch }, key)
}

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
  const r = t.objectStore(store).put(stamp(store, value), key)
  if (store === 'meta' && SYNCED_META.has(key)) bump(t, '_stamps', { [key]: Date.now() })
  await done(t)
  return r.result
}

export async function del(store, key) {
  const t = (await db()).transaction(STAMPED.has(store) ? [store, 'meta'] : store, 'readwrite')
  t.objectStore(store).delete(key)
  if (STAMPED.has(store)) bump(t, '_tomb', { [`${store}\t${key}`]: Date.now() })
  return done(t)
}

/** Many records in one transaction (keyPath stores). */
export async function putMany(store, values) {
  const t = (await db()).transaction(store, 'readwrite')
  for (const v of values) t.objectStore(store).put(stamp(store, v))
  return done(t)
}

/** Card state + its review log entry in one transaction, so they never disagree. */
export async function review(card, entry) {
  const t = (await db()).transaction(['cards', 'log'], 'readwrite')
  t.objectStore('cards').put(stamp('cards', card))
  t.objectStore('log').add(entry)
  return done(t)
}

/** Writes from sync: stored as received, no new stamps or tombstones. ops = [['put', store, value, key?] | ['del', store, key]]. */
export async function apply(ops) {
  if (!ops.length) return
  const t = (await db()).transaction(['cards', 'log', 'texts', 'meta'], 'readwrite')
  for (const [op, store, a, b] of ops) op === 'put' ? t.objectStore(store).put(a, b) : t.objectStore(store).delete(a)
  return done(t)
}

const STORES = ['cards', 'log', 'texts', 'meta']

export async function exportAll() {
  const out = { version: 1, exported: new Date().toISOString() }
  for (const s of STORES) out[s] = (await entries(s)).filter(([k]) => !(s === 'meta' && PRIVATE.has(k)))
  return out
}

const reviveDates = (c) => {
  for (const k of ['due', 'last_review']) if (c.fsrs?.[k]) c.fsrs[k] = new Date(c.fsrs[k])
  return c
}

/** Replaces everything with a backup from exportAll(). All-or-nothing. The AI key and sync identity stay; the
 *  restored records are marked for upload, so a signed-in account gets them too. */
export async function importAll(data) {
  if (data?.version !== 1 || !STORES.every((s) => Array.isArray(data[s]))) throw new Error('Not a Pluton backup')
  const keep = Object.fromEntries((await entries('meta')).filter(([k]) => PRIVATE.has(k)))
  const t = (await db()).transaction(STORES, 'readwrite')
  for (const s of STORES) {
    const os = t.objectStore(s)
    os.clear()
    for (const [k, v] of data[s]) if (!(s === 'meta' && PRIVATE.has(k))) os.keyPath ? os.put(stamp(s, s === 'cards' ? reviveDates(v) : v)) : os.put(v, k)
  }
  t.objectStore('meta').put(Object.fromEntries(data.meta.filter(([k]) => SYNCED_META.has(k)).map(([k]) => [k, Date.now()])), '_stamps')
  for (const [k, v] of Object.entries(keep)) if (k !== '_stamps' && k !== '_tomb') t.objectStore('meta').put(k === '_sync' ? { ...v, pushedAt: 0, logPushed: 0 } : v, k)
  return done(t)
}
