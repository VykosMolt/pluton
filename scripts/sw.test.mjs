import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'

const source = readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8')
const scope = 'https://example.test/pluton/'
const currentCache = `pluton:${scope}:v3`

function worker() {
  const handlers = new Map(), stores = new Map(), requests = []
  let offline = false, missing = null, failWrites = false, skipped = false, claimed = false
  const key = (req) => new URL(typeof req === 'string' ? req : req.url, scope).href
  const network = async (req) => {
    const url = key(req)
    requests.push(url)
    if (offline) throw new TypeError('offline')
    if (url === missing) return new Response('missing', { status: 404 })
    return new Response(url === scope
      ? '<script src="./assets/app-abc.js"></script><link href="./assets/app-abc.css" rel="stylesheet">'
      : `network: ${url}`)
  }
  const caches = {
    async keys() { return [...stores.keys()] },
    async delete(name) { return stores.delete(name) },
    async open(name) {
      if (!stores.has(name)) stores.set(name, new Map())
      const entries = stores.get(name)
      const cache = {
        async match(req) { return entries.get(key(req))?.clone() },
        async put(req, response) {
          if (failWrites) throw new Error('quota exceeded')
          entries.set(key(req), response.clone())
        },
        async addAll(urls) {
          const fetched = await Promise.all(urls.map(async (url) => {
            const response = await network(url)
            if (!response.ok) throw new TypeError(`Failed to cache ${url}`)
            return [url, response]
          }))
          for (const [url, response] of fetched) await cache.put(url, response)
        },
      }
      return cache
    },
  }
  const self = {
    registration: { scope },
    addEventListener: (name, handler) => handlers.set(name, handler),
    skipWaiting: async () => { skipped = true },
    clients: { claim: async () => { claimed = true } },
  }
  runInNewContext(source, { self, caches, fetch: network, URL, Response, location: new URL(scope) })
  return {
    caches, requests,
    set offline(value) { offline = value },
    set missing(value) { missing = value },
    set failWrites(value) { failWrites = value },
    get skipped() { return skipped },
    get claimed() { return claimed },
    async event(name) {
      const pending = []
      handlers.get(name)({ waitUntil: (promise) => pending.push(promise) })
      await Promise.all(pending)
    },
    async request(path, mode = 'cors') {
      let response
      const pending = []
      handlers.get('fetch')({
        request: { url: new URL(path, scope).href, method: 'GET', mode },
        respondWith: (promise) => { response = promise },
        waitUntil: (promise) => pending.push(promise),
      })
      const result = await response
      await Promise.all(pending)
      return result
    },
  }
}

test('first install makes every app data file, icon and entry asset available offline', async () => {
  const w = worker()
  await w.event('install')
  assert.equal(w.skipped, true)
  w.offline = true
  for (const name of ['words', 'forms', 'conj', 'shelf', 'story', 'grammar', 'cognates']) {
    assert.equal((await w.request(`data/fr/${name}.json`)).ok, true, name)
  }
  for (const path of ['icon-maskable.png', 'assets/app-abc.js', 'assets/app-abc.css']) {
    assert.equal((await w.request(path)).ok, true, path)
  }
  assert.match(await (await w.request('?source=installed', 'navigate')).text(), /app-abc\.js/)
})

test('a missing required data file prevents replacement of the active worker', async () => {
  const w = worker()
  w.missing = new URL('data/fr/cognates.json', scope).href
  await assert.rejects(w.event('install'), /Failed to cache/)
  assert.equal(w.skipped, false)
  assert.equal(w.claimed, false)
})

test('an unavailable app shell prevents activation', async () => {
  const w = worker()
  w.missing = scope
  await assert.rejects(w.event('install'), /App shell unavailable: 404/)
  assert.equal(w.skipped, false)
})

test('activation removes old Pluton caches and preserves unrelated apps and other scopes', async () => {
  const w = worker()
  const keep = ['other-app-v1', 'pluton:https://example.test/other/:v3', currentCache]
  for (const name of [...keep, `pluton:${scope}:v2`, 'pluton-v2']) await w.caches.open(name)
  await w.event('activate')
  assert.deepEqual(await w.caches.keys(), keep)
  assert.equal(w.claimed, true)
})

test('cache quota errors preserve successful network responses', async () => {
  const w = worker()
  w.failWrites = true
  const response = await w.request('audio/new.mp3')
  assert.equal(response.ok, true)
  assert.match(await response.text(), /network:/)
})

test('JSON URLs with query strings return cached data and refresh in the background', async () => {
  const w = worker()
  const path = 'data/fr/cognates.json?revision=3'
  await (await w.caches.open(currentCache)).put(path, new Response('cached cognates'))
  assert.equal(await (await w.request(path)).text(), 'cached cognates')
  assert.equal(w.requests.at(-1), new URL(path, scope).href)
  const cache = await w.caches.open(currentCache)
  assert.match(await (await cache.match(path)).text(), /network:/)
})
