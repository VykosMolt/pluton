// Offline support. The app shell and word data are precached on install: a first visit loads before this worker
// controls the page, so caching only what passes through left a fresh install unusable offline. After that: pages
// network first (so updates land), JSON from cache with a background refresh, everything else (hashed assets, audio
// named by content hash) cache first. The network is only touched when needed.
// ponytail: audio is cached as it's played; precache all ~160 MB only if offline listening from day one matters.
const CACHE = 'pluton-v2'
const SHELL = ['./', 'manifest.webmanifest', 'icon.svg', 'icon-192.png', 'icon-512.png',
  ...['words', 'forms', 'conj', 'shelf', 'story', 'grammar'].map((f) => `data/fr/${f}.json`)]

self.addEventListener('install', (e) => e.waitUntil((async () => {
  const cache = await caches.open(CACHE)
  const html = await (await fetch('./', { cache: 'no-cache' })).text()
  const assets = [...html.matchAll(/(?:src|href)="(\.\/assets\/[^"]+)"/g)].map((m) => m[1])
  await Promise.allSettled([...SHELL, ...assets].map((url) => cache.add(url))) // one miss mustn't block install
  await self.skipWaiting()
})()))

self.addEventListener('activate', (e) => e.waitUntil((async () => {
  for (const key of await caches.keys()) if (key !== CACHE) await caches.delete(key)
  await self.clients.claim()
})()))

self.addEventListener('fetch', (e) => {
  const req = e.request
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return
  e.respondWith(caches.open(CACHE).then(async (cache) => {
    // ignoreVary: module scripts carry an Origin header the precache request didn't, and servers send Vary: Origin
    const hit = await cache.match(req, { ignoreVary: true })
    const fetchAndStore = () => fetch(req).then((res) => {
      if (res.ok) cache.put(req, res.clone())
      return res
    })
    if (req.mode === 'navigate') return fetchAndStore().catch(() => hit || Response.error())
    if (req.url.endsWith('.json')) {
      if (!hit) return fetchAndStore()
      e.waitUntil(fetchAndStore().catch(() => {}))
      return hit
    }
    return hit || fetchAndStore()
  }))
})
