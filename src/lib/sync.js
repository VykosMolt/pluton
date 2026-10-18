// Accounts and sync (Supabase). Optional: without VITE_SUPABASE_URL the app is purely local and this never loads
// the SDK. Sign-in is by a 6-digit code sent by email. Sync pulls what changed on other devices, then pushes local
// changes; the server keeps the newest version of each record (see supabase/migrations). The AI key never syncs.
import * as db from './db.js'
import { LANG } from './lang.js'

const env = import.meta.env ?? {} // undefined outside Vite (unit tests)
const URL = env.VITE_SUPABASE_URL ?? ''
const KEY = env.VITE_SUPABASE_ANON_KEY ?? ''
const STORAGE_KEY = 'pluton-auth'
export const configured = !!(URL && KEY)

let sdk = null
async function client() {
  if (!sdk) {
    const { createClient } = await import('@supabase/supabase-js') // ~50 KB: only for people who sign in
    sdk = createClient(URL, KEY, { auth: { storageKey: STORAGE_KEY, persistSession: true, autoRefreshToken: true, detectSessionInUrl: false } })
  }
  return sdk
}

const readable = (error) => new Error(error?.message?.includes('fetch') ? "Couldn't reach the server. Are you offline?" : error?.message ?? 'Something went wrong.')

/** Whether a session is stored on this device (no network, no SDK). */
export const hasSession = () => configured && !!globalThis.localStorage?.getItem(STORAGE_KEY)

export async function sendCode(email) {
  const { error } = await (await client()).auth.signInWithOtp({ email, options: { shouldCreateUser: true } })
  if (error) throw readable(error)
}

export async function verifyCode(email, token) {
  const { data, error } = await (await client()).auth.verifyOtp({ email, token, type: 'email' })
  if (error) throw readable(error)
  return data.user
}

export async function account() {
  if (!hasSession()) return null
  const { data } = await (await client()).auth.getSession()
  return data.session?.user ?? null
}

export async function signOut() {
  await (await client()).auth.signOut()
}

/** Deletes the account and everything stored with it on the server; this device's data stays. */
export async function deleteAccount() {
  const sb = await client()
  const { error } = await sb.rpc('delete_account')
  if (error) throw readable(error)
  await sb.auth.signOut({ scope: 'local' })
  await db.put('meta', { ...(await state()), pulledAt: '1970-01-01T00:00:00Z', pushedAt: 0, logPushed: 0 }, '_sync')
}

async function state() {
  return (await db.get('meta', '_sync')) ?? { device: crypto.randomUUID().slice(0, 8), pulledAt: '1970-01-01T00:00:00Z', pushedAt: 0, logPushed: 0 }
}

const iso = (ms) => new Date(ms).toISOString()
const revive = (c) => ({ ...c, fsrs: { ...c.fsrs, due: new Date(c.fsrs.due), ...(c.fsrs.last_review && { last_review: new Date(c.fsrs.last_review) }) } })

/** Remote rows → local writes. Pure apart from its inputs; exported for tests. */
export function plan(rows, { device, cards, texts, stamps }) {
  const ops = [], stamped = {}
  const mine = `${device}:`
  for (const r of rows) {
    const at = Date.parse(r.updated_at)
    if (r.store === 'log') {
      if (!r.key.startsWith(mine)) ops.push(['put', 'log', r.data, r.key])
    } else if (r.store === 'meta') {
      if (r.key.startsWith('stats:')) {
        if (r.key !== `stats:${device}`) ops.push(['put', 'meta', r.data, r.key]) // other devices' counters
      } else if ((stamps[r.key] ?? 0) < at) {
        ops.push(['put', 'meta', r.data.v, r.key])
        stamped[r.key] = at
      }
    } else {
      const own = r.store === 'texts' && r.key.startsWith(mine)
      const key = own ? Number(r.key.slice(mine.length)) : r.key
      const local = (r.store === 'cards' ? cards : texts).get(key)
      if (r.data.deleted) {
        if (local && (local.updated ?? 0) <= at) ops.push(['del', r.store, key])
      } else if (!own && (!local || (local.updated ?? 0) < at)) {
        const data = { ...r.data, updated: at, pulled: at }
        ops.push(['put', r.store, r.store === 'cards' ? revive(data) : data])
      }
    }
  }
  return { ops, stamped }
}

/** Local changes since the last push → rows for the server. Exported for tests. */
export function collect({ device, since, logPushed }, { cards, texts, log, meta, stamps, tomb }) {
  const rows = []
  const textKey = (id) => (typeof id === 'number' ? `${device}:${id}` : id)
  const changed = (x) => (x.updated ?? 0) > since && x.updated !== x.pulled // pulled records aren't echoed back
  for (const c of cards) if (changed(c)) rows.push({ store: 'cards', key: c.lemma, data: c, updated_at: iso(c.updated) })
  for (const t of texts) if (changed(t)) rows.push({ store: 'texts', key: textKey(t.id), data: { ...t, id: textKey(t.id) }, updated_at: iso(t.updated) })
  let maxLog = logPushed
  for (const [k, v] of log) {
    if (typeof k !== 'number' || k <= logPushed) continue // string keys came from other devices
    rows.push({ store: 'log', key: `${device}:${k}`, data: v, updated_at: iso(v.at ?? Date.now()) })
    maxLog = Math.max(maxLog, k)
  }
  for (const [k, v] of meta) {
    if ((stamps[k] ?? 0) <= since) continue
    if (k === 'stats') rows.push({ store: 'meta', key: `stats:${device}`, data: v, updated_at: iso(stamps[k]) })
    else rows.push({ store: 'meta', key: k, data: { v }, updated_at: iso(stamps[k]) })
  }
  for (const [k, at] of Object.entries(tomb)) {
    if (at <= since) continue
    const [store, key] = k.split('\t')
    rows.push({ store, key: store === 'texts' && /^\d+$/.test(key) ? textKey(Number(key)) : key, data: { deleted: true }, updated_at: iso(at) })
  }
  return { rows, maxLog }
}

let running = null
/** Pull, then push. Resolves to { pulled, pushed } (null when signed out); one sync at a time. */
export const sync = () => (running ??= run().finally(() => (running = null)))

async function run() {
  if (!(await account())) return null
  const sb = await client()
  const st = await state()
  const started = Date.now()

  // 1. pull: everything that changed since the last pull, with a minute's overlap for slow concurrent pushes
  const since = iso(Date.parse(st.pulledAt) - 60_000)
  const rows = []
  for (let from = 0; ; from += 1000) {
    const { data, error } = await sb.from('records').select('store,key,data,updated_at,synced_at').eq('lang', LANG.code).gt('synced_at', since)
      .order('synced_at').order('store').order('key').range(from, from + 999)
    if (error) throw readable(error)
    rows.push(...data)
    if (data.length < 1000) break
  }
  const local = { device: st.device, stamps: (await db.get('meta', '_stamps')) ?? {},
    cards: new Map((await db.getAll('cards')).map((c) => [c.lemma, c])), texts: new Map((await db.getAll('texts')).map((t) => [t.id, t])) }
  const { ops, stamped } = plan(rows, local)
  if (Object.keys(stamped).length) ops.push(['put', 'meta', { ...local.stamps, ...stamped }, '_stamps'])
  await db.apply(ops)

  // 2. push: local changes since the last push (server keeps the newest version of each record)
  const { rows: out, maxLog } = collect({ device: st.device, since: st.pushedAt, logPushed: st.logPushed }, {
    cards: await db.getAll('cards'), texts: await db.getAll('texts'), log: await db.entries('log'),
    meta: (await db.entries('meta')).filter(([k]) => db.SYNCED_META.has(k)), stamps: (await db.get('meta', '_stamps')) ?? {},
    tomb: (await db.get('meta', '_tomb')) ?? {} })
  for (let i = 0; i < out.length; i += 500) {
    const { error } = await sb.rpc('push', { lang: LANG.code, batch: out.slice(i, i + 500) })
    if (error) throw readable(error)
  }

  const pulledAt = rows.reduce((m, r) => (r.synced_at > m ? r.synced_at : m), st.pulledAt)
  await db.apply([['put', 'meta', { ...st, pulledAt, pushedAt: started, logPushed: maxLog, syncedAt: Date.now() }, '_sync']])
  return { pulled: ops.length, pushed: out.length }
}

export const lastSynced = async () => (await db.get('meta', '_sync'))?.syncedAt ?? null
