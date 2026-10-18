<script>
  import { onMount } from 'svelte'
  import { SvelteMap } from 'svelte/reactivity'
  import * as db from './lib/db.js'
  import { status, buildRun, dayOf, nextStreak, liveStreak, brief, tokenize, coverage, unlockList, levelFor, isWord, parseConj, listenGoal, milestone, NEW_PER_RUN, NEW_PER_DAY, UNLOCK } from './lib/logic.js'
  import { unlock, say } from './lib/sound.js'
  import { DATA, LANG } from './lib/lang.js'
  import Cat from './Cat.svelte'
  import Run from './Run.svelte'
  import Reader from './Reader.svelte'
  import Words from './Words.svelte'
  import Listen from './Listen.svelte'
  import Talk from './Talk.svelte'
  import Settings from './Settings.svelte'
  import { setActive } from './lib/ai.js'

  let words = $state.raw(null) // lemma -> entry, frequency order
  let forms = $state.raw(null) // form -> lemma
  let conj = $state.raw(null) // verb -> conjugation tables
  const cards = new SvelteMap() // lemma -> stored card
  let texts = $state([])
  let shelf = $state.raw([]) // built-in public-domain classics (French) and Norman Bridge texts (English)
  const french = $derived(shelf.filter((t) => t.lang !== 'en'))
  let stats = $state({ streak: 0, lastDay: null, points: 0, bestCombo: 0, newDay: null, newCount: 0 })
  let view = $state('home')
  let now = $state(new Date())
  let error = $state('')
  let summary = $state(null)
  let ai = $state(null) // AI provider config incl. the user's key (this device only)
  let pace = $state({ perRun: NEW_PER_RUN, perDay: NEW_PER_DAY })
  $effect(() => setActive(ai))

  onMount(async () => {
    try {
      const [w, f, cj, sh, cs, st, ts] = await Promise.all([
        fetch(`${DATA}words.json`).then((r) => r.json()),
        fetch(`${DATA}forms.json`).then((r) => r.json()),
        fetch(`${DATA}conj.json`).then((r) => r.json()),
        fetch(`${DATA}shelf.json`).then((r) => r.json()),
        db.getAll('cards'), db.get('meta', 'stats'), db.getAll('texts'),
      ])
      ai = (await db.get('meta', 'ai')) ?? null
      pace = { ...pace, ...(await db.get('meta', 'settings')) }
      words = new Map(w.map((x) => [x.lemma, x]))
      forms = new Map(Object.entries(f))
      conj = cj
      shelf = sh.map((s, i) => ({ ...s, id: `shelf-${i}` }))
      for (const c of cs) cards.set(c.lemma, c)
      if (st) stats = st
      texts = ts
    } catch (e) {
      error = `Couldn't load: ${e.message}. Did you run data/fr/build.py?`
    }
  })

  const today = $derived(dayOf(now))
  const newToday = $derived(stats.newDay === today ? stats.newCount : 0)
  const statusOf = (l) => status(cards.get(l), words.get(l))
  const TENSES = LANG.tenses.map((t) => t.id)
  const plan = $derived(buildRun([...cards.values()], words ?? new Map(), now, newToday, { conj, tenses: TENSES, ...pace }))
  const counts = $derived.by(() => {
    let known = 0, learning = 0
    for (const c of cards.values()) {
      if (!isWord(c)) continue
      const s = status(c)
      if (s === 'known') known++
      if (s === 'learning') learning++
    }
    const due = plan.filter((i) => i.phase === 'quiz').length
    return { known, learning, due, fresh: plan.length - due }
  })
  const waiting = $derived(counts.due + counts.fresh)
  const lvl = $derived(levelFor(stats.points))
  const catMood = $derived(summary?.levelUp ? 'fire' : summary ? 'happy' : waiting ? 'idle' : 'sleep')
  const line = $derived(
    summary?.levelUp ? { fr: `Niveau ${summary.levelUp} ! Incroyable !`, en: `Level ${summary.levelUp}! Amazing!` }
    : !cards.size ? { fr: "Bonjour ! Moi, c'est Pluton.", en: "Hi! I'm Pluto. Let's learn your first words." }
    : summary && waiting ? { fr: 'Bien joué ! On continue ?', en: 'Well done! Another run?' }
    : summary ? { fr: 'Bien joué ! À demain !', en: 'Well done! See you tomorrow.' }
    : waiting ? { fr: 'On y va ?', en: `Shall we? ${waiting} cards are waiting.` }
    : { fr: 'Zzz… À demain !', en: 'All done. See you tomorrow!' })
  const TALK = [25, 100, 250, 500, 1000], WRITE = [3, 10, 25, 50, 100]
  const reading = $derived.by(() => {
    if (!forms) return { next: null, unlocked: 0 }
    let next = null, unlocked = 0
    for (const t of [...texts, ...french]) {
      const toks = tokenize(t.body, forms)
      const c = coverage(toks, statusOf)
      if (c.pct >= UNLOCK) unlocked++
      else if (c.total && (!next || c.pct > next.pct)) next = { title: t.title, pct: c.pct, need: unlockList(toks, statusOf).length }
    }
    return { next, unlocked }
  })
  const nextUnlock = $derived(reading.next)
  const road = $derived.by(() => {
    let deck = 0
    for (const w of words?.values() ?? []) if (w.sentence) deck++
    const tables = conj ? Object.values(conj).reduce((n, c) => n + Object.keys(c.t).length, 0) : 0
    let tablesKnown = 0
    for (const c of cards.values()) if (!isWord(c) && status(c) === 'known') tablesKnown++
    return { deck, tables, tablesKnown }
  })
  const label = (lemma) => {
    if (isWord({ lemma })) return null
    const { verb, tense } = parseConj(lemma)
    return `${verb} · ${LANG.tenses.find((t) => t.id === tense)?.name}`
  }

  function start() {
    now = new Date()
    unlock() // inside the tap, so iOS allows audio for the whole run
    summary = null
    view = 'run'
  }

  async function finish(s) {
    view = 'home'
    now = new Date()
    if (stats.newDay !== today) Object.assign(stats, { newDay: today, newCount: 0 })
    stats.newCount += s.introduced
    const before = lvl.level
    stats.points += s.points
    stats.bestCombo = Math.max(stats.bestCombo, s.best)
    if (s.completed) {
      stats.streak = nextStreak(stats.streak, stats.lastDay, today)
      stats.lastDay = today
      summary = { ...s, levelUp: levelFor(stats.points).level > before ? levelFor(stats.points).level : 0 }
    }
    await db.put('meta', $state.snapshot(stats), 'stats')
  }

  async function listened(seconds) {
    stats.listened = (stats.listened ?? 0) + seconds
    await db.put('meta', $state.snapshot(stats), 'stats')
  }
  const hours = $derived((stats.listened ?? 0) / 3600)

  async function count(kind) {
    stats[kind] = (stats[kind] ?? 0) + 1
    await db.put('meta', $state.snapshot(stats), 'stats')
  }

  async function backup() {
    const blob = new Blob([JSON.stringify(await db.exportAll())], { type: 'application/json' })
    const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(blob), download: `pluton-backup-${today}.json` })
    a.click()
    URL.revokeObjectURL(a.href)
  }

  async function restore(e) {
    const file = e.currentTarget.files[0]
    if (!file || !confirm('Replace all progress on this device with this backup?')) return
    try {
      await db.importAll(JSON.parse(await file.text()))
      location.reload()
    } catch (err) {
      alert(`Restore failed, nothing changed: ${err.message}`)
    }
  }
</script>

{#if error}
  <p class="panel">{error}</p>
{:else if !words}
  <p class="muted loading">Chargement…</p>
{:else if view === 'run'}
  <Run {words} {forms} {cards} {conj} {newToday} {pace} onexit={finish} />
{:else}
  <header>
    <h1>Pluton<span class="dot">.</span></h1>
    <div class="chips">
      <div class="level" title="{lvl.into} / {lvl.span} points to level {lvl.level + 1}">
        Niv. {lvl.level}<i style="width:{(100 * lvl.into) / lvl.span}%"></i>
      </div>
      <div class="streak" class:cold={stats.lastDay !== today} title="Day streak">🔥 {liveStreak(stats.streak, stats.lastDay, today)}</div>
      <button class="gear" class:on={view === 'settings'} onclick={() => (view = 'settings')} aria-label="Settings">⚙️</button>
    </div>
  </header>

  {#if view === 'home'}
    {#if summary}
      <section class="panel summary">
        <h2>{summary.levelUp ? `Niveau ${summary.levelUp} ! ⬆️` : 'Terminé ! 🎉'}</h2>
        <div class="grid">
          <div><b>{summary.points}</b><span>points</span></div>
          <div><b>{summary.total ? Math.round((100 * (summary.right + summary.almost)) / summary.total) : 0}%</b><span>accuracy</span></div>
          <div><b>×{summary.best}</b><span>best combo</span></div>
          <div><b>{summary.introduced}</b><span>new words</span></div>
        </div>
        {#if summary.words?.length}
          <div class="recap">
            {#each summary.words as { lemma, result } (lemma)}
              {@const w = words.get(lemma)}
              {#if label(lemma)}
                <span class="chip {result}"><b>📐 {label(lemma)}</b></span>
              {:else}
                <button class="chip {result}" onclick={() => say([w?.audio_word, w?.display ?? lemma])}>
                  <b class={w?.gender}>{w?.display ?? lemma}</b> <small>{brief(w?.gloss ?? '', 1)}</small>
                </button>
              {/if}
            {/each}
          </div>
        {/if}
      </section>
    {/if}

    <section class="pluto">
      <Cat mood={catMood} size={104} />
      <p class="bubble" lang="fr">{line.fr}<small lang="en">{line.en}</small></p>
    </section>

    <section class="tiles">
      <div class="panel tile"><b class="learning">{counts.learning}</b><span>🌱 learning</span></div>
      <div class="panel tile" title="Remembered for 3+ weeks"><b class="known">{counts.known}</b><span>🌳 known</span></div>
      <div class="panel tile"><b class="due">{counts.due}</b><span>⏰ due now</span></div>
    </section>

    <button class="btn go" onclick={start} disabled={!counts.due && !counts.fresh}>
      {#if counts.due || counts.fresh}
        <span>Start run</span>
        <small>{counts.due} reviews · {counts.fresh} new</small>
      {:else}
        <span>All done for today</span>
        <small>Read something in the library</small>
      {/if}
    </button>

    {#if nextUnlock}
      <button class="panel unlock" onclick={() => (view = 'read')}>
        <span class="muted">Next unlock</span>
        <b>{nextUnlock.title}</b>
        <div class="bar"><i style="width:{(100 * nextUnlock.pct) / UNLOCK}%"></i></div>
        <span>{Math.round(nextUnlock.pct * 100)}% understood · learn <b>{nextUnlock.need}</b> words to unlock</span>
      </button>
    {/if}

    <section class="panel road">
      <h3>Road to C1</h3>
      <div class="track">
        <span>📚 Vocabulary</span><span class="bar"><i style="width:{(100 * counts.known) / (road.deck || 1)}%"></i></span>
        <small>{counts.known} / {road.deck.toLocaleString('en')} words known</small>
      </div>
      <div class="track">
        <span>📐 Verbs</span><span class="bar"><i style="width:{(100 * road.tablesKnown) / (road.tables || 1)}%"></i></span>
        <small>{road.tablesKnown} / {road.tables} tables known</small>
      </div>
      <div class="track">
        <span>📖 Reading</span><span class="bar"><i style="width:{(100 * reading.unlocked) / (texts.length + french.length || 1)}%"></i></span>
        <small>{reading.unlocked} / {texts.length + french.length} texts unlocked (incl. {french.length} classics)</small>
      </div>
      <div class="track">
        <span>🎧 Listening</span><span class="bar"><i style="width:{(100 * hours) / listenGoal(hours)}%"></i></span>
        <small>{hours < 10 ? hours.toFixed(1) : Math.round(hours)} h listened · next milestone {listenGoal(hours)} h</small>
      </div>
      <div class="track">
        <span>🗣️ Talking</span><span class="bar"><i style="width:{(100 * (stats.talked ?? 0)) / milestone(stats.talked ?? 0, TALK)}%"></i></span>
        <small>{stats.talked ?? 0} messages in French · next milestone {milestone(stats.talked ?? 0, TALK)}</small>
      </div>
      <div class="track">
        <span>✍️ Writing</span><span class="bar"><i style="width:{(100 * (stats.written ?? 0)) / milestone(stats.written ?? 0, WRITE)}%"></i></span>
        <small>{stats.written ?? 0} texts corrected · next milestone {milestone(stats.written ?? 0, WRITE)}</small>
      </div>
      {#if !ai}<button class="link" onclick={() => (view = 'settings')}>Talking and writing need an AI: connect one</button>{/if}
    </section>

    <footer class="muted">
      <span>⭐ {stats.points} pts · best combo ×{stats.bestCombo}</span>
    </footer>
  {:else if view === 'read'}
    <Reader {words} {forms} {cards} {statusOf} {shelf} bind:texts />
  {:else if view === 'listen'}
    <Listen {words} {forms} {cards} {statusOf} onlisten={listened} />
  {:else if view === 'talk'}
    <Talk {ai} {words} {forms} {cards} {statusOf} vocab={counts.known + counts.learning} onsettings={() => (view = 'settings')} oncount={count} />
  {:else if view === 'settings'}
    <Settings bind:ai bind:pace onbackup={backup} onrestore={restore} />
  {:else}
    <Words {words} {cards} {now} />
  {/if}

  <nav>
    <button class:on={view === 'home'} onclick={() => { view = 'home'; now = new Date() }}><i>🏠</i>Home</button>
    <button class:on={view === 'read'} onclick={() => (view = 'read')}><i>📖</i>Library</button>
    <button class:on={view === 'listen'} onclick={() => (view = 'listen')}><i>🎧</i>Listen</button>
    <button class:on={view === 'talk'} onclick={() => (view = 'talk')}><i>💬</i>Talk</button>
    <button class:on={view === 'words'} onclick={() => { view = 'words'; now = new Date() }}><i>📚</i>Words</button>
  </nav>
{/if}

<style>
  header { display: flex; justify-content: space-between; align-items: center; margin: 4px 0 18px; }
  h1 { font-size: 34px; font-weight: 900; letter-spacing: -0.03em; }
  .dot { color: var(--red); }
  .chips { display: flex; gap: 8px; align-items: center; }
  .gear { border: 0; background: none; font-size: 22px; cursor: pointer; padding: 4px; border-radius: 10px; filter: grayscale(0.4); }
  .gear.on { background: color-mix(in srgb, var(--blue) 14%, transparent); filter: none; }
  .level {
    position: relative; overflow: hidden; font-weight: 900; font-size: 16px; padding: 7px 12px; border-radius: 99px;
    background: color-mix(in srgb, var(--gold) 20%, transparent);
  }
  .level i { position: absolute; left: 0; bottom: 0; height: 4px; background: var(--gold); }
  .road h3 { margin-bottom: 10px; }
  .track { display: grid; grid-template-columns: 110px 1fr; align-items: center; gap: 2px 10px; margin-bottom: 10px; }
  .track > span:first-child { font-weight: 700; font-size: 15px; }
  .track small { grid-column: 2; color: var(--muted); font-size: 13px; }
  .track .bar { height: 10px; }
  .track .bar i { background: linear-gradient(90deg, var(--blue), var(--green)); }
  .soon { margin: 4px 0 0; font-size: 13px; color: var(--muted); }
  .road { margin-bottom: 16px; }
  .recap { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
  .chip {
    border: 2px solid var(--line); background: var(--panel); border-radius: 12px; padding: 6px 10px; cursor: pointer;
    text-align: left; max-width: 100%; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  .chip small { color: var(--muted); }
  .chip.almost { border-color: var(--orange); }
  .chip.wrong { border-color: var(--red); }
  .streak { font-weight: 900; font-size: 20px; padding: 6px 14px; border-radius: 99px; background: color-mix(in srgb, var(--orange) 18%, transparent); }
  .streak.cold { filter: grayscale(1); opacity: 0.7; }
  .loading { text-align: center; margin-top: 40vh; }
  section { margin-bottom: 16px; }
  .pluto { display: flex; align-items: flex-end; gap: 10px; margin-bottom: 14px; }
  .bubble {
    position: relative; flex: 1; margin: 0 0 52px; padding: 10px 14px; font-weight: 800; font-size: 18px;
    background: var(--panel); border: 2px solid var(--line); border-radius: 18px 18px 18px 4px; animation: pop 0.3s;
  }
  .bubble::after {
    content: ''; position: absolute; left: -9px; bottom: 12px; width: 14px; height: 14px; background: var(--panel);
    border-left: 2px solid var(--line); border-bottom: 2px solid var(--line); transform: rotate(45deg);
  }
  .bubble small { display: block; font-weight: 500; font-size: 14px; color: var(--muted); }
  .tiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
  .tile { text-align: center; padding: 14px 6px; }
  .tile b { display: block; font-size: 36px; font-weight: 900; line-height: 1.1; letter-spacing: -0.03em; }
  .tile span { font-size: 13px; font-weight: 700; color: var(--muted); }
  .learning { color: var(--orange); }
  .known { color: var(--green); }
  .due { color: var(--blue); }
  .go { width: 100%; flex-direction: column; gap: 2px; font-size: 22px; padding: 18px; margin-bottom: 20px; }
  .go small { font-weight: 600; font-size: 14px; opacity: 0.85; }
  .unlock { width: 100%; display: flex; flex-direction: column; gap: 6px; text-align: left; cursor: pointer; margin-bottom: 16px; }
  .bar { height: 12px; border-radius: 99px; background: var(--line); overflow: hidden; }
  .bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--gold), var(--orange)); border-radius: 99px; }
  .summary { animation: pop 0.4s; }
  .summary h2 { text-align: center; margin-bottom: 12px; }
  .grid { display: grid; grid-template-columns: repeat(4, 1fr); text-align: center; gap: 8px; }
  .grid b { display: block; font-size: 24px; }
  .grid span { font-size: 13px; color: var(--muted); }
  footer { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; font-size: 14px; }
  .link { background: none; border: 0; padding: 0; color: var(--blue); cursor: pointer; text-decoration: underline; font-size: inherit; }
  nav {
    position: fixed; left: 0; right: 0; bottom: 0; display: flex; justify-content: center; gap: 8px;
    padding: 10px 16px max(10px, env(safe-area-inset-bottom)); background: var(--panel); border-top: 2px solid var(--line);
  }
  nav button {
    flex: 0 1 120px; display: flex; flex-direction: column; align-items: center; gap: 1px; border: 0; background: none;
    padding: 6px 4px; border-radius: 12px; font-weight: 700; font-size: 13px; cursor: pointer; color: var(--muted);
  }
  nav button i { font-style: normal; font-size: 20px; line-height: 1.1; }
  nav button.on { background: color-mix(in srgb, var(--blue) 14%, transparent); color: var(--blue); }
  @keyframes pop { from { transform: scale(0.9); opacity: 0; } }
  @media (max-width: 380px) { .grid { grid-template-columns: repeat(2, 1fr); } }
</style>
