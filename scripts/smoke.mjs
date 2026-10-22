// End-to-end smoke check against the production build: plays a run, uses the library, reloads, jumps the clock
// three days and plays the review run (which includes English -> French recall).
// Fails on any console/page error or audio fallback. Screenshots go to argv[2] (default ./smoke-out).
//   npm run smoke [-- outdir]          BROWSER=firefox|webkit npm run smoke   (default chromium)
import { spawn, execFileSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { chromium, firefox, webkit } from 'playwright'
import { produceAnswer, brief } from '../src/lib/logic.js'
import { startMockAI } from './mock-ai.mjs'

const OUT = process.argv[2] ?? 'smoke-out'
mkdirSync(OUT, { recursive: true })
const PORT = 4179
const URL = `http://localhost:${PORT}/`
const deck = JSON.parse(readFileSync('public/data/fr/words.json', 'utf8')).filter((w) => w.sentence)
const conj = JSON.parse(readFileSync('public/data/fr/conj.json', 'utf8'))
const grammar = JSON.parse(readFileSync('public/data/fr/grammar.json', 'utf8'))
const story = JSON.parse(readFileSync('public/data/fr/story.json', 'utf8'))
let fixAnswer = null // the correction the AI section turns into a card
let speaking = null // set when Chromium's fake microphone produces a playable recording

const server = spawn('npx', ['vite', 'preview', '--port', String(PORT), '--strictPort'], { stdio: 'pipe' })
process.on('exit', () => server.kill()) // also on a failed step
await new Promise((ok, fail) => {
  server.stdout.on('data', (d) => String(d).includes(String(PORT)) && ok())
  server.on('exit', (code) => fail(new Error(`vite preview exited (${code}); port ${PORT} busy?`)))
})

const ENGINE = process.env.BROWSER ?? 'chromium'
const browser = await { chromium, firefox, webkit }[ENGINE].launch({
  chromium: { args: ['--autoplay-policy=no-user-gesture-required', '--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] },
  firefox: { firefoxUserPrefs: { 'media.autoplay.default': 0, 'media.autoplay.blocking_policy': 0 } },
}[ENGINE])
console.log(`engine: ${ENGINE} ${browser.version()}`)
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
  await page.locator('.tiles, .wrap').first().waitFor()
  if (await page.getByRole('button', { name: "I'm new to French" }).isVisible())
    await page.getByRole('button', { name: "I'm new to French" }).click()
  await page.locator('.tiles').waitFor()
  return { ctx, page }
}

/** The right answer for the card on screen, recovered from the deck. */
async function answerFor(page, mode) {
  if (mode === 'gram') {
    const p = page.locator('.card .topic')
    const [topic, item] = await Promise.all(['topic', 'item'].map((k) => p.getAttribute(`data-${k}`)))
    return grammar.find((t) => t.id === topic).items[Number(item)].a[0]
  }
  if (mode === 'fix') return fixAnswer
  if (mode === 'conj') {
    const p = page.locator('.card .prompt')
    const [verb, tense, person] = await Promise.all(['verb', 'tense', 'person'].map((k) => p.getAttribute(`data-${k}`)))
    return conj[verb].t[tense][Number(person)]
  }
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

const MODES = { Listen: 'dictation', 'Say it': 'produce', 'Fill the gap': 'cloze', Conjugate: 'conj', Grammar: 'gram', 'own mistake': 'fix' }

/** Plays a whole run; answers the first card wrong if asked (then overrides it if asked), everything else right. */
async function playRun(page, { wrongFirst, override = false, prefix }) {
  await page.getByRole('button', { name: /Start your run|Another run/ }).click()
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

/** Answers a story chapter's comprehension quiz. Opens it unless the form is already showing. */
async function answerQuiz(page, title, { wrong = false, open = true } = {}) {
  const ch = story.find((c) => c.title === title)
  if (!ch) throw new Error(`no chapter "${title}"`)
  if (open) await page.getByRole('button', { name: 'Check understanding' }).click()
  for (const [i, q] of ch.questions.entries()) {
    const k = wrong ? (q.ok + 1) % q.a.length : q.ok
    await page.locator(`[data-q="${i}"] input`).nth(k).check()
  }
  await page.getByRole('button', { name: 'Check answers' }).click()
  await page.locator('.quiz .review').waitFor()
}

const { ctx, page } = await open('light', { clock: true })
await page.screenshot({ path: `${OUT}/0-home.png` })
const run1 = await playRun(page, { wrongFirst: true, prefix: '1' })
const recap = await page.locator('.recap .chip').count()

// The path: stage 1's reading goal opens chapter 1, which reads aloud sentence by sentence and can be marked read.
const stageTag = await page.locator('.stage .tag').textContent()
await page.locator('.task').click()
await page.getByRole('heading', { name: 'Le Mur' }).waitFor()
await page.getByRole('button', { name: '▶ Listen' }).click()
await page.locator('.text .w.hl').first().waitFor()
await page.screenshot({ path: `${OUT}/2-chapter.png` })
await page.waitForTimeout(1500)
await page.getByRole('button', { name: '⏸ Pause' }).click()
await page.getByRole('button', { name: 'Check understanding' }).click()
await answerQuiz(page, 'Le Mur', { wrong: true, open: false })
const quizBlocked = (await page.getByText('Compris !').count()) === 0
await page.getByRole('button', { name: 'Try again' }).click()
await answerQuiz(page, 'Le Mur', { open: false })
await page.getByText('Compris !').waitFor()
const quizScore = await page.locator('.quiz').textContent()
await page.screenshot({ path: `${OUT}/2-comprehension.png` })
// Nothing is locked: the C1 chapter opens and reads at stage 1.
await page.getByRole('button', { name: '← Library' }).click()
await page.locator('.item.chapter', { hasText: 'Le Mur' }).getByText('✓ read').waitFor()
const readMark = await page.locator('.item.chapter', { hasText: 'Le Mur' }).textContent()
await page.locator('.item.chapter', { hasText: "L'Opéra" }).click()
await page.getByRole('heading', { name: "L'Opéra" }).waitFor()
const lastChapterWords = await page.locator('.text .w').count()
await page.getByRole('button', { name: '← Library' }).click()
// The epilogues are story chapters too: the last one must open without a stage lookup.
await page.locator('.item.chapter').last().click()
await page.getByRole('heading', { name: 'Le Chat Noir' }).waitFor()
const epilogueWords = await page.locator('.text .w').count()
await page.getByRole('button', { name: '← Library' }).click()

// Library: open the starter text, tap an unknown word, add it, then queue the rest of the unlock.
await page.locator('nav').getByRole('button', { name: /Library/ }).click()
await page.getByText('Un matin à Paris').click()
const newWord = page.locator('.text .w.new').first()
const word = await newWord.textContent()
await newWord.click()
await page.getByRole('button', { name: '+ Add to deck' }).click()
await page.getByText(/In your deck|Queued for your next runs/).waitFor()
await page.getByRole('button', { name: 'Close' }).click()
const unlockBefore = await page.getByText(/learn \d+ more words/).textContent()
await page.getByRole('button', { name: /Queue the \d+ words/ }).click()
await page.getByRole('button', { name: /Queue the \d+ words/ }).waitFor({ state: 'detached' })
await page.screenshot({ path: `${OUT}/2-library-queued.png` })
const unlockAfter = await page.getByText(/learn \d+ more words/).textContent()
await page.getByRole('button', { name: '← Library' }).click()
const shelfItems = await page.locator('.item.classic').count()
await page.screenshot({ path: `${OUT}/2-shelf.png`, fullPage: true })
await page.getByText('La Parure').click()
const byline = await page.locator('.byline').textContent()
await page.getByRole('button', { name: '← Library' }).click()
// Norman Bridge: Poe in English, French-derived words woven back into French.
await page.getByRole('button', { name: /The Black Cat/ }).click()
await page.locator('.intro').waitFor()
const bridgeIntro = await page.locator('.intro').textContent()
await page.getByRole('radio', { name: 'Weave everything' }).click()
const woven = await page.locator('.cog.fr').count()
await page.screenshot({ path: `${OUT}/2-bridge.png` })
await page.locator('.cog[title="beast"]').first().click()
const beastNote = await page.locator('.sheet .note').textContent()
await page.getByRole('button', { name: 'Close' }).click()
await page.getByRole('button', { name: '← Library' }).click()

// The whole path, and every verb table open whatever the stage (practise the subjonctif on day one).
await page.locator('nav').getByRole('button', { name: /Home/ }).click()
await page.getByRole('button', { name: 'The whole path →' }).click()
const steps = await page.locator('.step').count()
await page.screenshot({ path: `${OUT}/2-path.png`, fullPage: true })
await page.locator('nav').getByRole('button', { name: /Words/ }).click()
await page.getByRole('tab', { name: /Verbs/ }).click()
await page.getByRole('button', { name: 'être', exact: true }).click()
const subj = page.locator('.tense', { hasText: 'subjonctif' })
await subj.getByRole('button', { name: 'Practice' }).click()
await subj.getByText('queued for your runs').waitFor()
await page.screenshot({ path: `${OUT}/2-verbs.png` })
// Grammar: every topic open whatever the stage; practise the B2 subjunctive topic on day one.
await page.getByRole('tab', { name: /Grammar/ }).click()
const topics = await page.locator('.topic').count()
await page.locator('.topic', { hasText: 'subjonctif ou indicatif' }).locator('.head').click()
await page.getByRole('button', { name: 'Show the answer' }).first().click()
const revealed = await page.locator('.topic.open mark').first().textContent()
await page.getByRole('button', { name: 'Practice this topic' }).click()
await page.locator('.topic.open .state').waitFor()
await page.screenshot({ path: `${OUT}/2-grammar.png` })

// Persistence: progress survives a reload.
const counts = () => page.evaluate(() => new Promise((ok) => {
  const r = indexedDB.open('pluton-fr')
  r.onsuccess = () => {
    const t = r.result.transaction(['cards', 'log'])
    const a = t.objectStore('cards').getAllKeys(), b = t.objectStore('log').getAll()
    t.oncomplete = () => ok({ cards: a.result.length, words: a.result.filter((k) => !k.includes('/')).length, // as isWord()
      log: b.result.length, overridden: b.result.filter((e) => e.overridden).map((e) => e.rating) })
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
await page.locator('nav').getByRole('button', { name: /Home/ }).click()
const due = await page.locator('.tile .due').textContent()
const streakLater = await page.locator('.streak').textContent()
await page.screenshot({ path: `${OUT}/3-home-later.png` })
const run2 = await playRun(page, { wrongFirst: true, override: true, prefix: '4' })
const end = await counts()
await page.locator('nav').getByRole('button', { name: /Words/ }).click()
await page.locator('.row').first().waitFor()
const wordRows = await page.locator('.row').count()
await page.screenshot({ path: `${OUT}/6-words.png` })
await page.getByLabel('Search words').fill('to have')
const searchHits = await page.locator('.row').allTextContents()
await ctx.close()

const dark = await open('dark')
await dark.page.screenshot({ path: `${OUT}/5-home-dark.png` })
// Offline right after a first visit: the service worker precaches the shell and the word data on install.
await dark.page.evaluate(() => navigator.serviceWorker.ready)
await dark.page.waitForFunction(() => caches.open('pluton-v2').then((c) => c.match('data/fr/words.json')).then(Boolean))
await dark.ctx.setOffline(true)
await dark.page.reload()
const offlineOk = await dark.page.locator('.tiles').waitFor({ timeout: 15000 }).then(() => true, () => false)
await dark.ctx.setOffline(false)
await dark.page.reload()
await dark.page.locator('.tiles').waitFor()
// Dictée only comes up for well-known words, so seed one: "être", stable for 30 days, due now, 4th review.
const etre = deck.find((w) => w.lemma === 'être')
await dark.page.evaluate(() => new Promise((ok) => {
  const r = indexedDB.open('pluton-fr')
  r.onsuccess = () => {
    const day = 864e5, now = Date.now()
    const fsrs = { due: new Date(now - day), stability: 30, difficulty: 5, elapsed_days: 30, scheduled_days: 30,
      reps: 3, lapses: 0, learning_steps: 0, state: 2, last_review: new Date(now - 31 * day) }
    const t = r.result.transaction('cards', 'readwrite')
    t.objectStore('cards').put({ lemma: 'être', fsrs, added: now - 60 * day })
    t.oncomplete = ok
  }
}))
await dark.page.reload()
await dark.page.getByRole('button', { name: /Start your run|Another run/ }).click()
await dark.page.getByRole('button', { name: 'Got it →' }).click() // a run opens with a new word when one is available
await dark.page.getByText('Dictée').waitFor()
await dark.page.screenshot({ path: `${OUT}/5-dictee.png` })
await dark.page.getByLabel('Your answer').fill(etre.sentence)
await dark.page.getByRole('button', { name: 'Check' }).click()
await dark.page.locator('.msg.right').waitFor()
const dicteeMarks = await dark.page.locator('.dm.ok').count()
await dark.page.screenshot({ path: `${OUT}/5-dictee-right.png` })
await dark.ctx.close()

// Listen: three deck sentences glued into one mp3 + an .srt timed from their durations.
const clips = deck.slice(0, 3).map((w) => ({ text: w.sentence, file: `public/data/fr/audio/${w.audio_sentence}.mp3` }))
const srtTime = (s) => new Date(s * 1000).toISOString().slice(11, 23).replace('.', ',')
let at = 0
const srt = clips.map((c, i) => {
  const d = Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', c.file]))
  const cue = `${i + 1}\n${srtTime(at)} --> ${srtTime(at + d)}\n${c.text}\n`
  at += d
  return cue
}).join('\n')
execFileSync('ffmpeg', ['-y', '-loglevel', 'error', ...clips.flatMap((c) => ['-i', c.file]),
  '-filter_complex', `concat=n=${clips.length}:v=0:a=1`, `${OUT}/listen.mp3`])
writeFileSync(`${OUT}/listen.srt`, srt)

const ear = await open('dark')
await ear.page.locator('nav').getByRole('button', { name: /Listen/ }).click()
await ear.page.locator('input[accept="audio/*,video/*"]').setInputFiles(`${OUT}/listen.mp3`)
await ear.page.locator('input[accept=".srt,.vtt"]').setInputFiles(`${OUT}/listen.srt`)
await ear.page.locator('video').evaluate((v) => v.play())
await ear.page.waitForFunction((t) => document.querySelector('.line')?.textContent.replace(/\s+/g, ' ').trim() === t, // nbsp before ! ? :
  clips[0].text.replace(/\s+/g, ' ').trim())
const lineWords = await ear.page.locator('.line .w').count()
await ear.page.waitForFunction(() => document.querySelector('video').currentTime > 3)
await ear.page.screenshot({ path: `${OUT}/7-listen.png` })
await ear.page.locator('.line .w').first().click() // tapping a word pauses and opens the sheet
await ear.page.getByRole('button', { name: 'Close' }).waitFor()
const listenedSec = await ear.page.evaluate(() => new Promise((ok) => {
  const r = indexedDB.open('pluton-fr')
  r.onsuccess = () => { const g = r.result.transaction('meta').objectStore('meta').get('stats'); g.onsuccess = () => ok(g.result?.listened ?? 0) }
}))
await ear.ctx.close()

// Finishing a stage: a learner with stage 1's words and listening done reads chapter 1 and is celebrated.
const stageCtx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, reducedMotion: 'reduce' })
const sp = await stageCtx.newPage()
sp.on('pageerror', (e) => errors.push(`page: ${e.message}`))
await sp.goto(URL)
await sp.getByRole('button', { name: "I'm new to French" }).click()
await sp.locator('.tiles').waitFor()
await sp.evaluate((lemmas) => new Promise((ok) => {
  const r = indexedDB.open('pluton-fr')
  r.onsuccess = () => {
    const now = Date.now(), t = r.result.transaction(['cards', 'meta'], 'readwrite')
    for (const lemma of lemmas) t.objectStore('cards').put({ lemma, added: now, fsrs: { due: new Date(now + 864e6), stability: 10,
      difficulty: 5, elapsed_days: 0, scheduled_days: 10, reps: 2, lapses: 0, learning_steps: 0, state: 2, last_review: new Date(now) } })
    t.objectStore('meta').put({ streak: 0, lastDay: null, points: 0, bestCombo: 0, newDay: null, newCount: 0, listened: 300 }, 'stats')
    t.oncomplete = ok
  }
}), deck.slice(0, 80).map((w) => w.lemma))
await sp.reload()
await sp.locator('.task').click()
await sp.getByRole('button', { name: 'Check understanding' }).click()
await answerQuiz(sp, 'Le Mur', { open: false })
await sp.locator('.celebrate').waitFor()
const celebration = await sp.locator('.celebrate').textContent()
await sp.screenshot({ path: `${OUT}/2-celebrate.png` })
await sp.getByRole('button', { name: 'Continuer' }).click()
await sp.locator('nav').getByRole('button', { name: /Home/ }).click()
const stageAfter = await sp.locator('.stage .tag').textContent()
await stageCtx.close()

// Placement: someone who knows the 300 most common words skips them.
const placeCtx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, reducedMotion: 'reduce' })
const pp = await placeCtx.newPage()
pp.on('pageerror', (e) => errors.push(`page: ${e.message}`))
await pp.goto(URL)
await pp.getByRole('button', { name: 'I already know some French' }).waitFor()
await pp.screenshot({ path: `${OUT}/0-welcome.png`, fullPage: true })
await pp.getByRole('button', { name: 'I already know some French' }).click()
for (let k = 0; k < 10; k++) { // two bands known
  await pp.getByRole('button', { name: 'I think so' }).click()
  await pp.getByRole('button', { name: '✓ Yes' }).click()
}
for (let k = 0; k < 5; k++) await pp.getByRole('button', { name: 'No idea' }).click() // the third band not
await pp.getByRole('button', { name: "Let's go" }).waitFor()
const placement = await pp.locator('.wrap').textContent()
await pp.getByRole('button', { name: "Let's go" }).click()
const placedKnown = await pp.locator('.tile .known').textContent()
const placedRun = await playRun(pp, { wrongFirst: false, prefix: '9' }) // 300 words known: a grammar topic comes in
await placeCtx.close()

// AI: talk and writing through a mock OpenAI-compatible provider, then Claude through the Anthropic SDK.
const mock = await startMockAI(4180)
const MOCK = 'http://localhost:4180'
const tutor = await open('light')
const tp = tutor.page
await tp.getByRole('button', { name: 'Settings' }).click()
await tp.getByLabel('Provider').selectOption('custom')
await tp.getByLabel('Server URL').fill(`${MOCK}/openai/v1`)
await tp.getByRole('button', { name: 'Load list' }).click()
await tp.locator('select option[value="mock-tutor"]').waitFor({ state: 'attached' })
await tp.getByRole('button', { name: 'Test & save' }).click()
await tp.locator('.note', { hasText: '✓' }).waitFor()
await tp.locator('nav').getByRole('button', { name: /Talk/ }).click()
await tp.getByLabel('Your message').fill('Je suis allé au marché')
await tp.getByRole('button', { name: 'Send' }).click()
await tp.locator('.fix').waitFor()
await tp.locator('.pluton .bubble .w').last().waitFor()
const talkFix = await tp.locator('.fix').textContent()
await tp.screenshot({ path: `${OUT}/8-talk.png` })
await tp.locator('.pluton .bubble .w').first().click()
await tp.getByRole('button', { name: 'Close' }).click()
await tp.getByRole('tab', { name: /Writing/ }).click()
await tp.locator('.topic').first().click()
await tp.getByLabel('Your text').fill("Je m'appelle Léa. J'habite a Paris et j'aime lire.")
await tp.getByRole('button', { name: 'Correct it' }).click()
await tp.locator('.result .diff').waitFor()
const writeAdds = await tp.locator('.diff .add').allTextContents()
await tp.screenshot({ path: `${OUT}/8-writing.png`, fullPage: true })
// The mistake becomes a card and comes back in the next run.
await tp.getByRole('button', { name: '+ Practise this' }).first().click()
await tp.getByText('✓ in your runs').waitFor()
fixAnswer = 'à Paris'
await tp.locator('nav').getByRole('button', { name: /Home/ }).click()
const fixRun = await playRun(tp, { wrongFirst: false, prefix: '8f' })

// More input: a story generated at the learner's level, then an AI comprehension check of the retelling.
await tp.locator('nav').getByRole('button', { name: /Library/ }).click()
await tp.getByRole('button', { name: '✨ Story' }).click()
await tp.getByRole('button', { name: 'Write it' }).click()
await tp.getByRole('heading', { name: 'Le marché de minuit' }).waitFor()
const generatedWords = await tp.locator('.text .w').count()
await tp.getByPlaceholder('De quoi parle ce texte ?').fill('Pluton trouve du pain et rencontre un chien sur le toit.')
await tp.getByRole('button', { name: 'Ask Pluton' }).click()
await tp.locator('.retell .score').waitFor()
const retellText = await tp.locator('.retell').textContent()
await tp.screenshot({ path: `${OUT}/8-reader.png`, fullPage: true })

// Speaking practice: record through Chromium's fake microphone, then make the clip playable.
await tp.locator('nav').getByRole('button', { name: /Talk/ }).click()
await tp.getByRole('tab', { name: /Speaking/ }).click()
if (ENGINE === 'chromium') {
  await tp.getByRole('button', { name: /Record/ }).click()
  await tp.waitForTimeout(800)
  await tp.getByRole('button', { name: /Stop/ }).click()
  await tp.waitForFunction(() => [...document.querySelectorAll('button')].some((b) => b.textContent.includes('Yours') && !b.disabled))
  speaking = true
  await tp.screenshot({ path: `${OUT}/8-speak.png` })
}

await tp.getByRole('button', { name: 'Settings' }).click()
await tp.getByLabel('Provider').selectOption('anthropic')
await tp.getByLabel('API key').fill('sk-test')
await tp.locator('details.adv summary').click()
await tp.getByLabel(/Server URL/).fill(`${MOCK}/anthropic`)
await tp.getByRole('button', { name: 'Load list' }).click()
await tp.locator('select option[value="claude-haiku-5-5"]').waitFor({ state: 'attached' })
await tp.getByRole('button', { name: 'Test & save' }).click()
await tp.locator('.note', { hasText: '✓' }).waitFor()
await tp.screenshot({ path: `${OUT}/8-settings.png`, fullPage: true })
const claude = mock.requests.findLast((r) => r.path.startsWith('/anthropic/v1/messages'))
const [download] = await Promise.all([tp.waitForEvent('download'), tp.getByRole('button', { name: 'Download backup' }).click()])
const backup = readFileSync(await download.path(), 'utf8')
await tutor.ctx.close()
mock.close()

// Accounts, against the local Supabase stack (npx supabase start): two devices sign in with an emailed code and
// share progress; deleting the account removes it and its data from the server.
let accounts = null
const supabaseUp = existsSync('.env.smoke.local') && await fetch(readFileSync('.env.smoke.local', 'utf8').match(/VITE_SUPABASE_URL="?([^"\n]+)/)[1] + '/auth/v1/health')
  .then((r) => r.status < 500, () => false)
if (supabaseUp) {
  const status = JSON.parse(process.env.SUPABASE_STATUS ?? execFileSync('npx', ['supabase', 'status', '-o', 'json'], { encoding: 'utf8' }))
  const API = status.API_URL, ADMIN = { apikey: status.SERVICE_ROLE_KEY, Authorization: `Bearer ${status.SERVICE_ROLE_KEY}` }
  const email = `smoke-${Date.now()}@pluton.test`
  const codeFor = async () => {
    for (let i = 0; i < 40; i++) {
      const { messages } = await (await fetch(`${status.MAILPIT_URL}/api/v1/messages`)).json()
      const hit = messages.find((m) => m.To.some((t) => t.Address === email))
      if (hit) { // read once: the next sign-in to this address must not pick up this (then expired) code
        await fetch(`${status.MAILPIT_URL}/api/v1/messages`, { method: 'DELETE', body: JSON.stringify({ IDs: [hit.ID] }) })
        return hit.Subject.match(/\d{6}/)[0]
      }
      await new Promise((r) => setTimeout(r, 500))
    }
    throw new Error('no sign-in email arrived')
  }
  const signIn = async (p) => {
    await p.getByLabel('Email').fill(email)
    await p.getByRole('button', { name: 'Email me a code' }).click()
    await p.getByLabel('6-digit code').fill(await codeFor())
    await p.getByRole('button', { name: 'Sign in' }).click()
    await p.locator('.note', { hasText: /✓ Synced|⚠️/ }).waitFor()
    const note = await p.locator('.note').textContent()
    if (!note.includes('✓')) throw new Error(`sign-in failed: ${note}`)
    return note
  }
  const a = await open('light')
  await playRun(a.page, { wrongFirst: false, prefix: 'acc-a' })
  const learningA = await a.page.locator('.tile .learning').textContent()
  const pointsA = await a.page.locator('footer').textContent()
  await a.page.getByRole('button', { name: 'Settings' }).click()
  const syncA = await signIn(a.page)
  await a.page.screenshot({ path: `${OUT}/9-account.png` })
  const uid = (await (await fetch(`${API}/auth/v1/admin/users?per_page=1000`, { headers: ADMIN })).json()).users.find((u) => u.email === email)?.id
  const rowsA = (await (await fetch(`${API}/rest/v1/records?select=key&user_id=eq.${uid}`, { headers: ADMIN })).json()).length

  const bctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, reducedMotion: 'reduce' })
  const b = await bctx.newPage()
  b.on('pageerror', (e) => errors.push(`page: ${e.message}`))
  b.on('dialog', (d) => (d.type() === 'prompt' ? d.accept('DELETE') : d.dismiss()))
  await b.goto(URL)
  await b.getByRole('button', { name: 'I have an account: sign in' }).click()
  const syncB = await signIn(b)
  await b.locator('nav').getByRole('button', { name: /Home/ }).click()
  const learningB = await b.locator('.tile .learning').textContent()
  const pointsB = await b.locator('footer').textContent()
  await b.getByRole('button', { name: 'Settings' }).click()
  await b.getByRole('button', { name: 'Delete my account' }).click()
  await b.locator('.note', { hasText: 'Account deleted' }).waitFor()
  const users = (await (await fetch(`${API}/auth/v1/admin/users?per_page=1000`, { headers: ADMIN })).json()).users
  const rowsLeft = (await (await fetch(`${API}/rest/v1/records?select=key&user_id=eq.${uid}`, { headers: ADMIN })).json()).length
  accounts = { syncA, rowsA, syncB, learningA, learningB, pointsA, pointsB, deleted: !users.some((u) => u.email === email), rowsLeft }
  await a.ctx.close()
  await bctx.close()
}

await browser.close()
server.kill()

const claudeSent = { model: claude?.body.model, fallbacks: claude?.body.fallbacks, effort: claude?.body.output_config?.effort,
  beta: claude?.headers['anthropic-beta'], browser: claude?.headers['anthropic-dangerous-direct-browser-access'] }
const report = { accounts, topics, revealed, placedRun, fixRun, offlineOk, stageTag, lastChapterWords, epilogueWords, steps, celebration, stageAfter,
  placement, placedKnown, bridgeIntro, woven, beastNote, talkFix, writeAdds, claudeSent, dicteeMarks, lineWords, listenedSec,
  shelfItems, byline, wordRows, searchHits, end, streakLater, run1, recap, mined: word, unlockBefore, unlockAfter, before, after,
  streak, level, sw, due, run2, quizBlocked, quizScore, readMark, generatedWords, retellText, speaking, audio, errors }
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
  'works offline right after the first visit': offlineOk,
  'audio loads': audio.ok > 0 && !audio.bad,
  'queuing does not fake coverage': unlockBefore === unlockAfter,
  'reviews due after 3 days': Number(due) > 0,
  'production cards appear': (run2.modes.produce ?? 0) > 0,
  'conjugation tables appear once verbs are learned': !run1.modes.conj && (run2.modes.conj ?? 0) > 0,
  'one log entry per answer': end.log === run1.answered + run2.answered,
  'override logged as Good': JSON.stringify(end.overridden) === '[3]',
  'words tab lists every word card': wordRows === end.words,
  'words search by English': searchHits.length === 1 && searchHits[0].includes('avoir'),
  'classics shelf lists 7 texts': shelfItems === 7,
  'shelf text shows author': byline.includes('Maupassant'),
  'chapter quiz: wrong answers do not count the chapter as read': quizBlocked,
  'chapter quiz: a pass marks it read and shows the score': /3 \/ 3/.test(quizScore) && readMark.includes('✓ read'),
  'generated story lands in the library and opens': generatedWords > 20,
  'retell is scored by the AI': /4 \/ 5/.test(retellText),
  'home shows stage 1 of the path': /Étape 1 \/ 10/.test(stageTag),
  'every chapter is open at stage 1 (C1 chapter readable)': lastChapterWords > 150,
  'epilogue chapter opens and reads at stage 1': epilogueWords > 150,
  'the path map lists 10 stages': steps === 10,
  'grammar: all 15 topics open, answers revealable, B2 topic practisable on day one': topics === 15 && revealed.length > 0,
  'grammar topic comes into runs once the vocabulary is there': (placedRun.modes.gram ?? 0) > 0,
  'a writing mistake becomes a card and comes back in a run': (fixRun.modes.fix ?? 0) > 0,
  'finishing a stage is celebrated and moves you on': celebration.includes('Le Mur ✓') && /Étape 2 \/ 10/.test(stageAfter),
  'placement estimates ~300 words and marks them known': /~300 words/.test(placement) && placedKnown.trim() === '300',
  'bridge counts the French in Poe': /\d+ words here \(\d+%\) came that way/.test(bridgeIntro),
  'bridge weaves French into the English': woven > 100,
  'bridge explains the lost s (bête → beast)': beastNote.includes('circumflex') && beastNote.includes('bête'),
  'dictée for a well-known word, graded word by word': dicteeMarks === [...etre.sentence.matchAll(/[\p{L}\p{N}'’-]+/gu)].length,
  'listen shows the playing line, tappable': lineWords > 0,
  'listening time saved': listenedSec > 2,
  'talk: streamed reply with its correction line': talkFix.includes('Je suis allée au marché'),
  'writing: correction shown as a diff': writeAdds.includes('à'),
  'claude: SDK call from the browser with key, default model, effort, fallbacks': claudeSent.model === 'claude-opus-5-5'
    && claudeSent.fallbacks === 'default' && claudeSent.effort === 'low' && claudeSent.browser === 'true'
    && (claudeSent.beta ?? '').includes('server-side-fallback-2026-07-01'),
  'backup never contains the API key': !backup.includes('sk-test') && backup.includes('"talk"'),
}
if (accounts) Object.assign(checks, {
  'accounts: sign in with an emailed code uploads progress': accounts.syncA.includes('✓ Synced') && accounts.rowsA > 10,
  'accounts: a second device signs in and gets the same progress': accounts.syncB.includes('✓ Synced')
    && accounts.learningB === accounts.learningA && accounts.pointsB === accounts.pointsA,
  'accounts: deleting removes the account and all its data': accounts.deleted && accounts.rowsLeft === 0,
})
else console.log('accounts: SKIPPED (local Supabase not reachable; npx supabase start, then npx vite build --mode smoke)')
if (speaking !== null) checks['speaking: recording produces a playable clip'] = speaking === true
else console.log('speaking: SKIPPED (record/playback smoke runs on Chromium only)')
for (const [name, ok] of Object.entries(checks)) console.log(`${ok ? '✔' : '✖'} ${name}`)
process.exit(Object.values(checks).every(Boolean) ? 0 : 1)
