// End-to-end smoke check against the production build: plays a full run, mines a word, reloads.
// Fails on any console/page error. Screenshots go to argv[2] (default ./smoke-out).
//   npm run build && node scripts/smoke.mjs [outdir]
import { spawn } from 'node:child_process'
import { mkdirSync, readFileSync } from 'node:fs'
import { chromium } from 'playwright'

const OUT = process.argv[2] ?? 'smoke-out'
mkdirSync(OUT, { recursive: true })
const PORT = 4179
const URL = `http://localhost:${PORT}/`
const deck = JSON.parse(readFileSync('public/data/fr/words.json', 'utf8')).filter((w) => w.sentence)

const server = spawn('npx', ['vite', 'preview', '--port', String(PORT), '--strictPort'], { stdio: 'pipe' })
process.on('exit', () => server.kill()) // also on a failed step
await new Promise((ok, fail) => {
  server.stdout.on('data', (d) => String(d).includes(String(PORT)) && ok())
  server.on('exit', (code) => fail(new Error(`vite preview exited (${code}); port ${PORT} busy?`)))
})

const browser = await chromium.launch()
const errors = []
const audio = { ok: 0, bad: 0 }

async function open(colorScheme) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme, reducedMotion: 'reduce' })
  const page = await ctx.newPage()
  page.on('console', (m) => m.type() === 'error' && errors.push(`console: ${m.text()}`))
  page.on('pageerror', (e) => errors.push(`page: ${e.message}`))
  page.on('response', (r) => r.url().includes('/audio/') && (r.ok() ? audio.ok++ : audio.bad++))
  await page.goto(URL)
  await page.locator('.tiles').waitFor()
  return { ctx, page }
}

/** The expected answer for the quiz on screen, found by matching the gapped sentence against the deck. */
async function expected(page) {
  const shown = (await page.locator('.card .sentence').textContent()).replace(/\s+/g, ' ').trim()
  for (const w of deck) {
    const m = new RegExp(`(?<!\\p{L})${w.form}(?!\\p{L})`, 'iu').exec(w.sentence)
    if (m && (w.sentence.slice(0, m.index) + w.sentence.slice(m.index + m[0].length)).replace(/\s+/g, ' ').trim() === shown) return m[0]
  }
  throw new Error(`no deck sentence matches "${shown}"`)
}

const { ctx, page } = await open('light')
await page.screenshot({ path: `${OUT}/1-home.png` })

await page.getByRole('button', { name: /Start run/ }).click()
await page.getByText('New word').waitFor()
await page.screenshot({ path: `${OUT}/2-intro.png` })

let answered = 0, wrongDone = false, shots = new Set()
while (answered < 60) {
  if (await page.locator('.tiles').isVisible()) break
  if (await page.getByRole('button', { name: 'Got it →' }).isVisible()) {
    await page.getByRole('button', { name: 'Got it →' }).click()
    continue
  }
  const input = page.getByLabel('Your answer')
  await input.waitFor()
  const mode = (await page.locator('.card .tag').textContent()).includes('Listen') ? 'dictation' : 'cloze'
  if (!shots.has(mode)) { shots.add(mode); await page.screenshot({ path: `${OUT}/3-quiz-${mode}.png` }) }
  if (!wrongDone) {
    await input.fill('zzzz')
    await page.getByRole('button', { name: 'Check' }).click()
    await page.locator('.msg.wrong').waitFor()
    await page.screenshot({ path: `${OUT}/4-wrong.png` })
    wrongDone = true
  } else {
    await input.fill(await expected(page))
    await page.getByRole('button', { name: 'Check' }).click()
    await page.locator('.msg.right').waitFor()
    if (!shots.has('right')) { shots.add('right'); await page.screenshot({ path: `${OUT}/5-right.png` }) }
  }
  answered++
  await page.getByRole('button', { name: 'Next →' }).click()
}
await page.getByText('Terminé').waitFor()
await page.screenshot({ path: `${OUT}/6-summary.png` })

// Library: open the starter text, tap an unknown word, add it.
await page.getByRole('button', { name: /Library/ }).click()
await page.getByText('Un matin à Paris').click()
await page.screenshot({ path: `${OUT}/7-reader.png` })
const newWord = page.locator('.text .w.new').first()
const word = await newWord.textContent()
await newWord.click()
await page.getByRole('button', { name: '+ Add to deck' }).click()
await page.getByText(/In your deck|Queued for your next runs/).waitFor()
await page.screenshot({ path: `${OUT}/8-mined.png` })
await page.getByRole('button', { name: 'Close' }).click()
const unlockBefore = await page.getByText(/learn \d+ words to unlock/).textContent()
await page.getByRole('button', { name: /Queue the \d+ words/ }).click()
await page.getByRole('button', { name: /Queue the \d+ words/ }).waitFor({ state: 'detached' })
await page.screenshot({ path: `${OUT}/8b-queued.png` })
const unlockAfter = await page.getByText(/learn \d+ words to unlock/).textContent()

// Persistence: progress survives a reload.
const counts = () => page.evaluate(() => new Promise((ok) => {
  const r = indexedDB.open('pluton-fr')
  r.onsuccess = () => {
    const t = r.result.transaction(['cards', 'log'])
    const a = t.objectStore('cards').count(), b = t.objectStore('log').count()
    t.oncomplete = () => ok({ cards: a.result, log: b.result })
  }
}))
const before = await counts()
await page.reload()
await page.locator('.tiles').waitFor()
const after = await counts()
const streak = await page.locator('.streak').textContent()
const sw = await page.evaluate(() => navigator.serviceWorker.ready.then((r) => !!r.active))
await ctx.close()

const dark = await open('dark')
await dark.page.screenshot({ path: `${OUT}/9-home-dark.png` })
await dark.ctx.close()
await browser.close()
server.kill()

const report = { answered, mined: word, unlockBefore, unlockAfter, before, after, streak, sw, audio, errors }
console.log(JSON.stringify(report, null, 2))
const fail = errors.length || !answered || before.log < answered || after.log !== before.log || !streak.includes('1') || !audio.ok || audio.bad || !sw || unlockBefore !== unlockAfter
process.exit(fail ? 1 : 0)
