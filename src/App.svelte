<script>
  import { onMount } from 'svelte'
  import { SvelteMap } from 'svelte/reactivity'
  import * as db from './lib/db.js'
  import { status, buildRun, dayOf, nextStreak, tokenize, coverage, unlockList, UNLOCK } from './lib/logic.js'
  import { unlock } from './lib/sound.js'
  import { DATA } from './lib/lang.js'
  import Cat from './Cat.svelte'
  import Run from './Run.svelte'
  import Reader from './Reader.svelte'

  let words = $state.raw(null) // lemma -> entry, frequency order
  let forms = $state.raw(null) // form -> lemma
  const cards = new SvelteMap() // lemma -> stored card
  let texts = $state([])
  let stats = $state({ streak: 0, lastDay: null, points: 0, bestCombo: 0, newDay: null, newCount: 0 })
  let view = $state('home')
  let now = $state(new Date())
  let error = $state('')
  let summary = $state(null)

  onMount(async () => {
    try {
      const [w, f, cs, st, ts] = await Promise.all([
        fetch(`${DATA}words.json`).then((r) => r.json()),
        fetch(`${DATA}forms.json`).then((r) => r.json()),
        db.getAll('cards'), db.get('meta', 'stats'), db.getAll('texts'),
      ])
      words = new Map(w.map((x) => [x.lemma, x]))
      forms = new Map(Object.entries(f))
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
  const plan = $derived(buildRun([...cards.values()], words?.values() ?? [], now, newToday))
  const counts = $derived.by(() => {
    let known = 0, learning = 0
    for (const c of cards.values()) {
      const s = status(c)
      if (s === 'known') known++
      if (s === 'learning') learning++
    }
    const due = plan.filter((i) => i.phase === 'quiz').length
    return { known, learning, due, fresh: plan.length - due }
  })
  const waiting = $derived(counts.due + counts.fresh)
  const catMood = $derived(summary ? 'happy' : waiting ? 'idle' : 'sleep')
  const line = $derived(
    !cards.size ? { fr: "Bonjour ! Moi, c'est Pluton.", en: "Hi! I'm Pluto. Let's learn your first words." }
    : summary && waiting ? { fr: 'Bien joué ! On continue ?', en: 'Well done! Another run?' }
    : summary ? { fr: 'Bien joué ! À demain !', en: 'Well done! See you tomorrow.' }
    : waiting ? { fr: 'On y va ?', en: `Shall we? ${waiting} cards are waiting.` }
    : { fr: 'Zzz… À demain !', en: 'All done. See you tomorrow!' })
  const nextUnlock = $derived.by(() => {
    if (!forms) return null
    let best = null
    for (const t of texts) {
      const toks = tokenize(t.body, forms)
      const c = coverage(toks, statusOf)
      if (c.total && c.pct < UNLOCK && (!best || c.pct > best.pct)) best = { title: t.title, pct: c.pct, need: unlockList(toks, statusOf).length }
    }
    return best
  })

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
    stats.points += s.points
    stats.bestCombo = Math.max(stats.bestCombo, s.best)
    if (s.completed) {
      stats.streak = nextStreak(stats.streak, stats.lastDay, today)
      stats.lastDay = today
      summary = s
    }
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
  <Run {words} {forms} {cards} {newToday} onexit={finish} />
{:else}
  <header>
    <h1>Pluton<span class="dot">.</span></h1>
    <div class="streak" class:cold={stats.lastDay !== today} title="Day streak">🔥 {stats.streak}</div>
  </header>

  {#if view === 'home'}
    {#if summary}
      <section class="panel summary">
        <h2>Terminé ! 🎉</h2>
        <div class="grid">
          <div><b>{summary.points}</b><span>points</span></div>
          <div><b>{summary.total ? Math.round((100 * (summary.right + summary.almost)) / summary.total) : 0}%</b><span>accuracy</span></div>
          <div><b>×{summary.best}</b><span>best combo</span></div>
          <div><b>{summary.introduced}</b><span>new words</span></div>
        </div>
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

    <footer class="muted">
      <span>⭐ {stats.points} pts · best combo ×{stats.bestCombo}</span>
      <span>
        <button class="link" onclick={backup}>Backup</button> ·
        <label class="link">Restore<input type="file" accept="application/json" onchange={restore} hidden /></label>
      </span>
    </footer>
  {:else}
    <Reader {words} {forms} {cards} {statusOf} bind:texts />
  {/if}

  <nav>
    <button class:on={view === 'home'} onclick={() => { view = 'home'; now = new Date() }}>🏠 Home</button>
    <button class:on={view === 'read'} onclick={() => (view = 'read')}>📖 Library</button>
  </nav>
{/if}

<style>
  header { display: flex; justify-content: space-between; align-items: center; margin: 4px 0 18px; }
  h1 { font-size: 34px; font-weight: 900; letter-spacing: -0.03em; }
  .dot { color: var(--red); }
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
  footer { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 8px; font-size: 14px; padding-bottom: 90px; }
  .link { background: none; border: 0; padding: 0; color: var(--blue); cursor: pointer; text-decoration: underline; font-size: inherit; }
  nav {
    position: fixed; left: 0; right: 0; bottom: 0; display: flex; justify-content: center; gap: 8px;
    padding: 10px 16px max(10px, env(safe-area-inset-bottom)); background: var(--panel); border-top: 2px solid var(--line);
  }
  nav button { flex: 0 1 200px; border: 0; background: none; padding: 10px; border-radius: 12px; font-weight: 700; cursor: pointer; color: var(--muted); }
  nav button.on { background: color-mix(in srgb, var(--blue) 14%, transparent); color: var(--blue); }
  @keyframes pop { from { transform: scale(0.9); opacity: 0; } }
  @media (max-width: 380px) { .grid { grid-template-columns: repeat(2, 1fr); } }
</style>
