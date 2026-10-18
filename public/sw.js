// Offline support. Pages: network first (so updates land). Hashed assets and audio (named by content hash):
// cache first. JSON data: serve cached, refresh in the background.
// ponytail: caches lazily as things are used; precache all audio if fully-offline-from-day-one matters.
const CACHE = 'pluton-v1'

self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()))

self.addEventListener('fetch', (e) => {
  const req = e.request
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return
  e.respondWith(caches.open(CACHE).then(async (cache) => {
    const hit = await cache.match(req)
    const fresh = fetch(req).then((res) => {
      if (res.ok) cache.put(req, res.clone())
      return res
    })
    if (req.mode === 'navigate') return fresh.catch(() => hit || Response.error())
    if (req.url.endsWith('.json')) {
      if (hit) { e.waitUntil(fresh.catch(() => {})); return hit }
      return fresh
    }
    return hit || fresh
  }))
})
