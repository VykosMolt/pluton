// Offline support. The app shell and word data are precached on install: a first visit loads before this worker
// controls the page, so caching only what passes through left a fresh install unusable offline. After that: pages
// network first (so updates land), JSON from cache with a background refresh, everything else (hashed assets, audio
// named by content hash) cache first. The network is only touched when needed.
// ponytail: audio is cached as it's played; precache all ~160 MB only if offline listening from day one matters.
const PREFIX = `pluton:${self.registration.scope}:`
const CACHE = `${PREFIX}v3`
const SHELL = ['./', 'manifest.webmanifest', 'icon.svg', 'icon-192.png', 'icon-512.png', 'icon-maskable.png',
  ...['words', 'forms', 'conj', 'shelf', 'story', 'grammar', 'cognates'].map((f) => `data/fr/${f}.json`)]

self.addEventListener('install', (e) => e.waitUntil((async () => {
  const cache = await caches.open(CACHE)
  const shell = await fetch('./', { cache: 'no-cache' })
  if (!shell.ok) throw new Error(`App shell unavailable: ${shell.status}`)
  const html = await shell.clone().text()
  const assets = [...html.matchAll(/(?:src|href)="(\.\/assets\/[^"]+)"/g)].map((m) => m[1])
  // Keep the previous worker active if any required file is missing. A partially cached install cannot work offline.
  await cache.addAll([...SHELL.filter((url) => url !== './'), ...assets])
  await cache.put('./', shell)
  await self.skipWaiting()
})()))

self.addEventListener('activate', (e) => e.waitUntil((async () => {
  for (const key of await caches.keys()) {
    // v1/v2 predate scope-specific caches. Leave other applications' caches on the same origin alone.
    if (key !== CACHE && (key.startsWith(PREFIX) || /^pluton-v[12]$/.test(key))) await caches.delete(key)
  }
  await self.clients.claim()
})()))

self.addEventListener('fetch', (e) => {
  const req = e.request
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return
  e.respondWith(caches.open(CACHE).then(async (cache) => {
    // ignoreVary: module scripts carry an Origin header the precache request didn't, and servers send Vary: Origin
    const hit = await cache.match(req, { ignoreVary: true })
    const fetchAndStore = async () => {
      const res = await fetch(req)
      // Quota/private-mode cache errors must not turn a successful network response into an app failure.
      if (res.ok) await cache.put(req, res.clone()).catch(() => {})
      return res
    }
    if (req.mode === 'navigate') return fetchAndStore().catch(async () => hit || await cache.match('./') || Response.error())
    if (new URL(req.url).pathname.endsWith('.json')) {
      if (!hit) return fetchAndStore()
      e.waitUntil(fetchAndStore().catch(() => {}))
      return hit
    }
    return hit || fetchAndStore()
  }))
})
