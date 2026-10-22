<script>
  import { onMount, onDestroy } from 'svelte'
  import * as db from './lib/db.js'
  import { tokenize, coverage, unlockList, fromSubtitles, sentenceAt, sentencesOf, gradeQuiz, isWord, UNLOCK } from './lib/logic.js'
  import { addWord } from './lib/deck.js'
  import { say, stop, unlock } from './lib/sound.js'
  import { chat, ready, parseJSON, AIError } from './lib/ai.js'
  import { levelOf, readerSystem, RETELL } from './lib/tutor.js'
  import { STAGES } from './lib/path.js'
  import Tokens from './Tokens.svelte'
  import WordSheet from './WordSheet.svelte'
  import Bridge from './Bridge.svelte'

  /**
   * Every text is open at any time. Coverage only says how comfortable it will be. openId / listenNow let the
   * home screen open a text, and start reading it aloud.
   */
  let { words, forms, cards, statusOf, texts = $bindable(), shelf = [], story = [], read = new Set(), onread, onlisten,
    openId = $bindable(null), listenNow = $bindable(false), ai = null, vocab = 0, onsettings } = $props()

  // Original starter text (not from any book), so there is something of your own to read on day one.
  const SAMPLE = {
    title: 'Un matin à Paris',
    body: `Ce matin, Léa prend un café avec son ami Hugo. Il fait beau et la rue est pleine de monde.
« Tu as bien dormi ? » demande Hugo.
« Pas vraiment, je pense trop à demain. »
Demain, c'est le grand jour : Léa commence un nouveau travail. Elle a un peu peur, mais elle est contente.
Hugo sourit. « Tu vas être parfaite. »
Ils finissent leur café et marchent ensemble jusqu'au métro.`,
  }

  let draft = $state(null) // { title, body } while adding a text
  let sel = $state(null) // tapped token
  let playing = $state(false)
  let at = $state(-1) // sentence being read aloud
  let run = 0
  let quiz = $state(null) // { answers: [] } while answering a chapter's questions
  let quizResult = $state(null) // { right, total, pass }
  let retell = $state('')
  let retellBusy = $state(false)
  let retellResult = $state(null)
  let retellError = $state('')
  let genOpen = $state(false)
  let genBusy = $state(false)
  let genError = $state('')

  const open = $derived(texts.find((t) => t.id === openId) ?? story.find((t) => t.id === openId) ?? shelf.find((t) => t.id === openId))
  const rate = (t) => {
    const toks = tokenize(t.body, forms)
    return { t, ...coverage(toks, statusOf), need: unlockList(toks, statusOf).length }
  }
  const library = $derived(texts.map(rate))
  const chapters = $derived(story.map(rate))
  const classics = $derived(shelf.filter((t) => t.lang !== 'en').map(rate).sort((a, b) => b.pct - a.pct)) // easiest first
  const bridge = $derived(shelf.filter((t) => t.lang === 'en'))
  const toks = $derived(open && open.lang !== 'en' ? tokenize(open.body, forms) : [])
  const cov = $derived(coverage(toks, statusOf))
  const need = $derived(unlockList(toks, statusOf))
  const addable = $derived(need.filter((l) => statusOf(l) === 'new'))
  const hl = $derived(open?.sentences?.[at]?.slice(0, 2) ?? null)

  onMount(async () => {
    if (!texts.length && !(await db.get('meta', 'seeded'))) {
      await save({ ...SAMPLE, id: 'sample' })
      await db.put('meta', true, 'seeded')
    }
  })
  onDestroy(() => pause())

  // Each text starts with a clean comprehension/retelling panel.
  $effect(() => {
    void openId
    quiz = null
    quizResult = null
    retell = ''
    retellResult = null
    retellError = ''
    genError = ''
  })

  $effect(() => {
    if (listenNow && open?.sentences) {
      listenNow = false
      listen(0)
    }
  })
  $effect(() => {
    if (at >= 0) document.querySelector('.text .w.hl')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })

  /** Read aloud sentence by sentence from `from`, highlighting each; the time counts as listening. */
  async function listen(from = Math.max(0, at)) {
    unlock()
    const me = ++run
    const t0 = performance.now()
    playing = true
    for (let k = from; k < open.sentences.length && me === run; k++) {
      at = k
      const [a, b, id] = open.sentences[k]
      await say([id, open.body.slice(a, b)])
    }
    if (me === run) {
      playing = false
      at = -1
    }
    onlisten?.((performance.now() - t0) / 1000)
  }

  function pause() {
    run++
    stop()
    playing = false
  }

  function close() {
    pause()
    at = -1
    openId = null
    sel = null
  }

  function pick(t) {
    sel = t
    if (!playing && open.sentences) at = open.sentences.findIndex(([a, b]) => t.start >= a && t.start < b) // resume here
  }

  async function save(t) {
    const body = t.body.normalize('NFC').trim()
    if (!body) return
    const rec = { ...(t.id && { id: t.id }), title: t.title.trim() || body.slice(0, 40), body, added: Date.now(),
      sentences: sentencesOf(body) } // browser-voice read-along; built-in texts have recorded clips
    rec.id = await db.put('texts', rec)
    texts = [...texts.filter((x) => x.id !== rec.id), rec]
    draft = null
    return rec
  }

  async function loadFile(e) {
    const f = e.currentTarget.files[0]
    if (!f) return
    const s = await f.text()
    draft.body = /\.(srt|vtt)$/i.test(f.name) ? fromSubtitles(s) : s
    draft.title ||= f.name.replace(/\.\w+$/, '')
  }

  async function remove() {
    if (!confirm(`Delete “${open.title}”?`)) return
    await db.del('texts', open.id)
    texts = texts.filter((t) => t.id !== open.id)
    close()
  }

  async function addAll() {
    for (const lemma of addable) {
      const tok = toks.find((t) => t.lemma === lemma)
      await addWord(cards, words, tok, sentenceAt(open.body, tok.start))
    }
  }

  // Chapter comprehension: a pass marks the chapter read, so the path's reading goal is evidence, not just a tap.
  const startQuiz = () => (quiz = { answers: Array(open.questions.length).fill(-1) })
  const retryQuiz = () => { quiz = { answers: Array(open.questions.length).fill(-1) }; quizResult = null }
  function checkQuiz() {
    quizResult = gradeQuiz(open.questions, quiz.answers)
    if (quizResult.pass && !read.has(open.id)) onread?.(open.id)
  }

  // Optional check for imported/classic texts: the AI judges meaning, not grammar.
  async function askRetell() {
    if (!ready(ai)) return
    retellBusy = true
    retellError = ''
    try {
      const r = parseJSON(await chat(ai, { system: RETELL, effort: 'medium',
        messages: [{ role: 'user', content: `TEXT:\n${open.body.slice(0, 6000)}\n\nLEARNER'S RETELLING:\n${retell.trim()}` }] }))
      const score = Number(r.score)
      if (!(score >= 1 && score <= 5) || typeof r.feedback !== 'string')
        throw new AIError('The model returned an unexpected format. Try again.')
      retellResult = { score, feedback: r.feedback }
    } catch (e) {
      retellError = e.message
    }
    retellBusy = false
  }

  // Unlimited graded input: a story at the learner's level, mostly from words they already know.
  async function generate() {
    if (!ready(ai)) return
    genBusy = true
    genError = ''
    try {
      const known = [...cards.values()].filter((c) => isWord(c) && statusOf(c.lemma) !== 'new' && words.get(c.lemma)?.gloss)
        .map((c) => c.lemma).slice(0, 40)
      const targets = [...cards.values()].filter((c) => isWord(c) && statusOf(c.lemma) === 'learning' && !c.known)
        .map((c) => c.lemma).slice(0, 8)
      const r = parseJSON(await chat(ai, { system: readerSystem(levelOf(vocab), known, targets), effort: 'low',
        messages: [{ role: 'user', content: 'Write a new story for me.' }] }))
      if (typeof r.title !== 'string' || typeof r.body !== 'string' || r.body.trim().length < 80)
        throw new AIError('The model returned an unexpected story. Try again.')
      const rec = await save({ title: r.title, body: r.body })
      if (rec) { genOpen = false; openId = rec.id }
    } catch (e) {
      genError = e.message
    }
    genBusy = false
  }

  const pct = (x) => `${Math.round(x * 100)}%`
  const comfy = (x) => x.pct >= UNLOCK
</script>

{#snippet item(x, cls, sub)}
  <button class="panel item {cls}" onclick={() => (openId = x.t.id)}>
    <span class="row"><b>{x.t.title}</b>
      {#if read.has(x.t.id)}<span class="ready">✓ read</span>{:else if comfy(x)}<span class="ready">✅ comfortable</span>{:else}<span class="muted">{x.need} words to go</span>{/if}</span>
    {#if sub}<span class="muted">{sub}</span>{/if}
    <span class="bar"><i class:full={comfy(x)} style="width:{Math.min(100, (100 * x.pct) / UNLOCK)}%"></i></span>
    <span class="muted">{pct(x.pct)} understood · {x.total} words</span>
  </button>
{/snippet}

{#if draft}
  <h2>Add a text</h2>
  <p class="muted">Paste lyrics, an article, a chapter, or load subtitles (.srt / .vtt).</p>
  <input class="field" placeholder="Title" bind:value={draft.title} />
  <textarea class="field" rows="10" lang="fr" placeholder="Colle ton texte ici…" bind:value={draft.body}></textarea>
  <label class="btn ghost file">📄 Load file<input type="file" accept=".txt,.srt,.vtt,text/plain" onchange={loadFile} hidden /></label>
  <div class="row">
    <button class="btn ghost" onclick={() => (draft = null)}>Cancel</button>
    <button class="btn" onclick={() => save(draft)} disabled={!draft.body.trim()}>Save</button>
  </div>
{:else if open}
  <div class="row head">
    <button class="btn ghost" onclick={close}>← Library</button>
    {#if texts.some((t) => t.id === open.id)}<button class="btn ghost" onclick={remove} aria-label="Delete text">🗑</button>{/if}
  </div>
  {#if open.stage != null}<p class="kicker">Chapitre {open.stage + 1} · {STAGES[open.stage]?.level ?? 'C1'}</p>{/if}
  <h2>{open.title}</h2>
  {#if open.author}<p class="byline">{open.author}, {open.year} · <a href={open.source} target="_blank" rel="noopener">source</a></p>{/if}
  {#if open.lang === 'en'}
    <Bridge text={open} {words} {cards} {statusOf} />
  {:else}
    <div class="stack" title="known · learning · queued · new">
      <i class="k" style="flex:{cov.known}"></i><i class="l" style="flex:{cov.learning}"></i><i class="q" style="flex:{cov.queued}"></i><i class="n" style="flex:{cov.new}"></i>
    </div>
    <p class="muted">
      {pct(cov.pct)} understood ·
      {#if cov.pct >= UNLOCK}<b class="ready">✅ you can read this comfortably</b>{:else}learn {need.length} more words to read it comfortably{/if}
      <br /><small>Tap a word: <span class="w new">new</span> <span class="w queued">queued</span> <span class="w learning">learning</span> known</small>
    </p>
    <div class="row tools">
      {#if open.sentences}
        {#if playing}
          <button class="btn" onclick={pause}>⏸ Pause</button>
        {:else}
          <button class="btn" onclick={() => listen()}>▶ {at > 0 ? 'Resume' : 'Listen'}</button>
        {/if}
      {/if}
      {#if addable.length}
        <button class="btn ghost queue" onclick={addAll}>🎯 Queue the {addable.length} words that make this comfortable</button>
      {/if}
    </div>
    <div class="panel text" lang="fr">
      <Tokens text={open.body} {toks} {statusOf} {sel} {hl} onpick={pick} />
    </div>
    {#if open.stage != null && open.questions?.length}
      <section class="panel quiz">
        {#if quizResult}
          <h3>{quizResult.pass ? '✓ Compris !' : 'Pas encore…'}</h3>
          <p class="muted">{quizResult.right} / {quizResult.total} correct.
            {quizResult.pass ? 'This chapter counts as read.' : 'Read it again, then try once more.'}</p>
          <ol class="review">
            {#each open.questions as q, i}
              <li>
                <p>{q.q}</p>
                <p>
                  <span class:right={quiz.answers[i] === q.ok} class:wrong={quiz.answers[i] !== q.ok}>{q.a[quiz.answers[i]] ?? '—'}</span>
                  → <b>{q.a[q.ok]}</b>
                </p>
                <p class="why">{q.why}</p>
              </li>
            {/each}
          </ol>
          {#if !quizResult.pass}<button class="btn" onclick={retryQuiz}>Try again</button>{/if}
        {:else if read.has(open.id)}
          <p class="ready">✓ You read and understood this chapter.</p>
        {:else if !quiz}
          <div class="row">
            <span><b>Compréhension</b><br /><small class="muted">{open.questions.length} questions · {Math.ceil(open.questions.length * 2 / 3)} right to count the chapter as read</small></span>
            <button class="btn" onclick={startQuiz}>Check understanding</button>
          </div>
        {:else}
          <form onsubmit={(e) => { e.preventDefault(); checkQuiz() }}>
            {#each open.questions as q, i}
              <fieldset data-q={i}>
                <legend>{i + 1}. {q.q}</legend>
                {#each q.a as a, k}
                  <label>
                    <input type="radio" name={`q${i}`} checked={quiz.answers[i] === k} onchange={() => (quiz.answers[i] = k)} />
                    {a}
                  </label>
                {/each}
              </fieldset>
            {/each}
            <button class="btn" disabled={quiz.answers.includes(-1)}>Check answers</button>
          </form>
        {/if}
      </section>
    {:else}
      <div class="finish">
        {#if read.has(open.id)}<span class="ready">✓ You read this one</span>
        {:else}<button class="btn" onclick={() => onread?.(open.id)}>✓ I read it</button>{/if}
      </div>
    {/if}
    {#if open.stage == null}
      <section class="panel retell">
        <h3>Did you understand it?</h3>
        {#if !ready(ai)}
          <p class="muted">Connect an AI with your own key and Pluton will check your retelling. Reading stays open without one.</p>
          {#if onsettings}<button class="btn ghost" onclick={onsettings}>Open Settings</button>{/if}
        {:else if retellResult}
          <p class="score">Pluton's score: <b>{retellResult.score} / 5</b></p>
          <p>{retellResult.feedback}</p>
          <button class="btn ghost" onclick={() => (retellResult = null)}>Try again</button>
        {:else}
          <p class="muted">Tell Pluton what this text was about, in your own words. He checks the meaning, not your grammar.</p>
          <textarea class="field" rows="4" bind:value={retell} placeholder="De quoi parle ce texte ?" aria-label="Your retelling"></textarea>
          <button class="btn" onclick={askRetell} disabled={retellBusy || retell.trim().length < 10}>
            {retellBusy ? 'Reading…' : 'Ask Pluton'}
          </button>
          {#if retellError}<p class="err">{retellError}</p>{/if}
        {/if}
      </section>
    {/if}
  {/if}
{:else}
  <div class="row head">
    <h2>Library</h2>
    <span class="row actions">
      <button class="btn ghost" onclick={() => (genOpen = true)}>✨ Story</button>
      <button class="btn" onclick={() => (draft = { title: '', body: '' })}>+ Add text</button>
    </span>
  </div>
  <p class="muted">Everything here is open. At {pct(UNLOCK)} of the words known, reading stops being decoding and starts being fun.</p>

  {#if genOpen}
    <section class="panel gen">
      <h3>✨ A story at your level</h3>
      {#if !ready(ai)}
        <p class="muted">Connect an AI with your own key and Pluton will write a short story using the words you know.</p>
        <div class="row">
          {#if onsettings}<button class="btn" onclick={onsettings}>Connect an AI</button>{/if}
          <button class="btn ghost" onclick={() => (genOpen = false)}>Cancel</button>
        </div>
      {:else}
        <p class="muted">Pluton writes 150–250 words at your level (~{vocab} words), recycling words you are learning. It lands in Your texts.</p>
        <div class="row">
          <button class="btn" onclick={generate} disabled={genBusy}>{genBusy ? 'Writing…' : 'Write it'}</button>
          <button class="btn ghost" onclick={() => (genOpen = false)} disabled={genBusy}>Cancel</button>
        </div>
        {#if genError}<p class="err">{genError}</p>{/if}
      {/if}
    </section>
  {/if}

  {#if chapters.length}
    <h3 class="shelf">🐈‍⬛ L'histoire de Pluton</h3>
    <p class="muted">The story of the path, chapter by chapter, written for each stage's French. The two epilogues follow the Opéra. Read along with audio.</p>
    {#each chapters as x (x.t.id)}{@render item(x, 'chapter', `Chapitre ${x.t.stage + 1} · ${STAGES[x.t.stage]?.level ?? 'C1'}`)}{/each}
  {/if}

  <h3 class="shelf">📝 Your texts</h3>
  {#each library as x (x.t.id)}{@render item(x, 'mine')}{:else}<p class="muted">Nothing here yet.</p>{/each}

  {#if classics.length}
    <h3 class="shelf">📜 Classics shelf</h3>
    <p class="muted">Real French literature, public domain, with read-along audio. Easiest first.</p>
    {#each classics as x (x.t.id)}{@render item(x, 'classic', `${x.t.author}, ${x.t.year}`)}{/each}
  {/if}

  {#if bridge.length}
    <h3 class="shelf">🏰 Norman Bridge</h3>
    <p class="muted">English Gothic classics, read through the French hiding inside them. Weave in the French you know.</p>
    {#each bridge as t (t.id)}
      <button class="panel item english" onclick={() => (openId = t.id)}>
        <b>{t.title}</b><span class="muted">{t.author}, {t.year} · in English</span>
      </button>
    {/each}
  {/if}
{/if}

{#if sel && open && open.lang !== 'en'}
  <WordSheet tok={sel} sentence={sentenceAt(open.body, sel.start)} {words} {cards} {statusOf} onclose={() => (sel = null)} />
{/if}

<style>
  h2 { margin-bottom: 8px; }
  .row { display: flex; gap: 10px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
  .head { margin-bottom: 10px; }
  .kicker { margin: 0; font-size: 13px; font-weight: 800; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }
  .field { display: block; width: 100%; margin: 10px 0; padding: 12px 14px; border-radius: 14px; border: 2px solid var(--line); background: var(--panel); }
  .field:focus { outline: none; border-color: var(--blue); }
  textarea.field { resize: vertical; }
  .file { margin-bottom: 14px; }
  .item { width: 100%; display: flex; flex-direction: column; gap: 6px; text-align: left; cursor: pointer; margin-bottom: 12px; }
  .bar { display: block; height: 10px; border-radius: 99px; background: var(--line); overflow: hidden; }
  .bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--gold), var(--orange)); border-radius: 99px; }
  .bar i.full { background: var(--green); }
  .ready { color: var(--green); font-weight: 800; }
  .shelf { margin: 22px 0 4px; }
  .byline { margin: 2px 0 0; color: var(--muted); font-style: italic; }
  .byline a { color: var(--blue); }
  .stack { display: flex; height: 12px; border-radius: 99px; overflow: hidden; background: var(--line); margin: 10px 0 6px; }
  .stack .k { background: var(--green); }
  .stack .l { background: var(--gold); }
  .stack .q { background: var(--blue); }
  .stack .n { background: var(--red); }
  .tools { justify-content: flex-start; margin-bottom: 12px; }
  .text { font-size: 20px; line-height: 1.75; white-space: pre-wrap; }
  .queue { font-size: 15px; }
  .finish { display: flex; justify-content: center; margin: 18px 0 24px; }
  .actions { flex: none; gap: 8px; }
  .gen { margin-bottom: 14px; }
  .quiz, .retell { margin: 18px 0 24px; }
  .quiz .row { justify-content: space-between; }
  fieldset { border: 0; padding: 0; margin: 0 0 14px; min-width: 0; }
  legend { font-weight: 700; margin-bottom: 6px; }
  .quiz label { display: flex; gap: 8px; align-items: baseline; padding: 7px 4px; border-radius: 8px; cursor: pointer; }
  .quiz label:hover { background: color-mix(in srgb, var(--blue) 7%, transparent); }
  .quiz input { accent-color: var(--blue); }
  .review { padding-left: 18px; }
  .review li { margin-bottom: 12px; }
  .review p { margin: 2px 0; }
  .review .right { color: var(--green); }
  .review .wrong { color: var(--red); text-decoration: line-through; }
  .why { color: var(--muted); font-size: 14px; }
  .score { font-size: 18px; }
  .err { color: var(--red); font-weight: 700; }
  small .w { padding: 0 4px; cursor: default; }
</style>
