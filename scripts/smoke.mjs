// End-to-end smoke check against the production build: plays a run, uses the library, reloads, jumps the clock
// three days and plays the review run (which includes English -> French recall).
// Fails on any console/page error or audio fallback. Screenshots go to argv[2] (default ./smoke-out).
//   npm run smoke [-- outdir]
import { spawn } from 'node:child_process'
import { mkdirSync, readFileSync } from 'node:fs'
import { chromium } from 'playwright'
import { produceAnswer, brief } from '../src/lib/logic.js'

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

async function open(colorScheme, { clock = false } = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, colorScheme, reducedMotion: 'reduce' })
  const page = await ctx.newPage()
  if (clock) await page.clock.install()
  page.on('console', (m) => {
    if (m.type() === 'error' || m.text().startsWith('audio fallback')) errors.push(`console: ${m.text()}`)
  })
  page.on('pageerror', (e) => errors.push(`page: ${e.message}`))
  page.on('response', (r) => r.url().includes('/audio/') && (r.ok() ? audio.ok++ : audio.bad++))
  await page.goto(URL)
  await page.locator('.tiles').waitFor()
  return { ctx, page }
}

/** The right answer for the card on screen, recovered from the deck. */
async function answerFor(page, mode) {
  if (mode === 'produce') {
    const gloss = await page.locator('.card .prompt').textContent()
    const w = deck.find((x) => brief(x.gloss) === gloss)
    if (w) return produceAnswer(w)
    throw new Error(`no deck word with gloss "${gloss}"`)
  }
  const shown = (await page.locator('.card .sentence').textContent()).replace(/\s+/g, ' ').trim()
  for (const w of deck) {
    const m = new RegExp(`(?<!\\p{L})${w.form}(?!\\p{L})`, 'iu').exec(w.sentence)
    if (m && (w.sentence.slice(0, m.index) + w.sentence.slice(m.index + m[0].length)).replace(/\s+/g, ' ').trim() === shown) return m[0]
  }
  throw new Error(`no deck sentence matches "${shown}"`)
}

const MODES = { Listen: 'dictation', 'Say it': 'produce', 'Fill the gap': 'cloze' }

/** Plays a whole run; answers the first card wrong if asked (then overrides it if asked), everything else right. */
async function playRun(page, { wrongFirst, override = false, prefix }) {
  await page.getByRole('button', { name: /Start run/ }).click()
  const modes = {}
  let answered = 0, wrongDone = !wrongFirst
  while (answered < 80) {
    await page.locator('.tiles, .card').first().waitFor()
    if (await page.locator('.tiles').isVisible()) break
    if (await page.getByRole('button', { name: 'Got it →' }).isVisible()) {
      if (!modes.intro) await page.screenshot({ path: `${OUT}/${prefix}-intro.png` })
      modes.intro = (modes.intro ?? 0) + 1
      await page.getByRole('button', { name: 'Got it →' }).click()
      continue
    }
    const input = page.getByLabel('Your answer')
    await input.waitFor()
    const tag = await page.locator('.card .tag').textContent()
    const mode = Object.entries(MODES).find(([k]) => tag.includes(k))[1]
    if (!modes[mode]) await page.screenshot({ path: `${OUT}/${prefix}-${mode}.png` })
    modes[mode] = (modes[mode] ?? 0) + 1
    await input.fill(wrongDone ? await answerFor(page, mode) : 'zzzz')
    await page.getByRole('button', { name: 'Check' }).click()
    await page.locator(wrongDone ? '.msg.right' : '.msg.wrong').waitFor()
    if (!wrongDone || modes[mode] === 1) await page.screenshot({ path: `${OUT}/${prefix}-${mode}-${wrongDone ? 'right' : 'wrong'}.png` })
    if (!wrongDone && override) {
      await page.getByRole('button', { name: 'I was right' }).click()
      await page.getByText('counted as right').waitFor()
    }
    wrongDone = true
    answered++
    await page.getByRole('button', { name: 'Next →' }).click()
  }
  await page.locator('.summary').waitFor()
  await page.screenshot({ path: `${OUT}/${prefix}-summary.png`, fullPage: true })
  return { answered, modes }
}

const { ctx, page } = await open('light', { clock: true })
await page.screenshot({ path: `${OUT}/0-home.png` })
const run1 = await playRun(page, { wrongFirst: true, prefix: '1' })
const recap = await page.locator('.recap .chip').count()

// Library: open the starter text, tap an unknown word, add it, then queue the rest of the unlock.
await page.getByRole('button', { name: /Library/ }).click()
await page.getByText('Un matin à Paris').click()
const newWord = page.locator('.text .w.new').first()
const word = await newWord.textContent()
await newWord.click()
await page.getByRole('button', { name: '+ Add to deck' }).click()
await page.getByText(/In your deck|Queued for your next runs/).waitFor()
await page.getByRole('button', { name: 'Close' }).click()
const unlockBefore = await page.getByText(/learn \d+ words to unlock/).textContent()
await page.getByRole('button', { name: /Queue the \d+ words/ }).click()
await page.getByRole('button', { name: /Queue the \d+ words/ }).waitFor({ state: 'detached' })
await page.screenshot({ path: `${OUT}/2-library-queued.png` })
const unlockAfter = await page.getByText(/learn \d+ words to unlock/).textContent()

// Persistence: progress survives a reload.
const counts = () => page.evaluate(() => new Promise((ok) => {
  const r = indexedDB.open('pluton-fr')
  r.onsuccess = () => {
    const t = r.result.transaction(['cards', 'log'])
    const a = t.objectStore('cards').count(), b = t.objectStore('log').getAll()
    t.oncomplete = () => ok({ cards: a.result, log: b.result.length, overridden: b.result.filter((e) => e.overridden).map((e) => e.rating) })
  }
}))
const before = await counts()
await page.reload()
await page.locator('.tiles').waitFor()
const after = await counts()
const streak = await page.locator('.streak').textContent()
const level = await page.locator('.level').textContent()
const sw = await page.evaluate(() => navigator.serviceWorker.ready.then((r) => !!r.active))

// Three days later: graduated cards are due, some as English -> French recall.
await page.clock.setSystemTime(new Date(Date.now() + 3 * 864e5))
await page.getByRole('button', { name: /Home/ }).click()
const due = await page.locator('.tile .due').textContent()
const streakLater = await page.locator('.streak').textContent()
await page.screenshot({ path: `${OUT}/3-home-later.png` })
const run2 = await playRun(page, { wrongFirst: true, override: true, prefix: '4' })
const end = await counts()
await page.getByRole('button', { name: /Words/ }).click()
await page.locator('.row').first().waitFor()
const wordRows = await page.locator('.row').count()
await page.screenshot({ path: `${OUT}/6-words.png` })
await page.getByLabel('Search words').fill('to have')
const searchHits = await page.locator('.row').allTextContents()
await ctx.close()

const dark = await open('dark')
await dark.page.screenshot({ path: `${OUT}/5-home-dark.png` })
await dark.ctx.close()
await browser.close()
server.kill()

const report = { wordRows, searchHits, end, streakLater, run1, recap, mined: word, unlockBefore, unlockAfter, before, after, streak, level, sw, due, run2, audio, errors }
console.log(JSON.stringify(report, null, 2))
const checks = {
  'no errors': !errors.length,
  'run 1 logged': run1.answered > 0 && before.log >= run1.answered,
  'recap shows run words': recap === run1.modes.intro,
  'reload keeps progress': after.log === before.log && after.cards === before.cards,
  'streak 1': streak.includes('1'),
  'streak lapses after 3 days away': streakLater.includes('0'),
  'levelled up': !level.includes('Niv. 1'),
  'service worker': sw,
  'audio loads': audio.ok > 0 && !audio.bad,
  'queuing does not fake coverage': unlockBefore === unlockAfter,
  'reviews due after 3 days': Number(due) > 0,
  'production cards appear': (run2.modes.produce ?? 0) > 0,
  'one log entry per answer': end.log === run1.answered + run2.answered,
  'override logged as Good': JSON.stringify(end.overridden) === '[3]',
  'words tab lists every card': wordRows === end.cards,
  'words search by English': searchHits.length === 1 && searchHits[0].includes('avoir'),
}
for (const [name, ok] of Object.entries(checks)) console.log(`${ok ? '✔' : '✖'} ${name}`)
process.exit(Object.values(checks).every(Boolean) ? 0 : 1)
