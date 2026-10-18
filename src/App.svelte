<script>
  import { onMount } from 'svelte'
  import { SvelteMap } from 'svelte/reactivity'
  import * as db from './lib/db.js'
  import { status, buildRun, dayOf, nextStreak, liveStreak, brief, levelFor, isWord, parseConj, State, NEW_PER_RUN, NEW_PER_DAY } from './lib/logic.js'
  import { unlock, say, sfx } from './lib/sound.js'
  import { STAGES, pathState, focus } from './lib/path.js'
  import Path from './Path.svelte'
  import { DATA, LANG } from './lib/lang.js'
  import Cat from './Cat.svelte'
  import Run from './Run.svelte'
  import Reader from './Reader.svelte'
  import Words from './Words.svelte'
  import Listen from './Listen.svelte'
  import Talk from './Talk.svelte'
  import Settings from './Settings.svelte'
  import Welcome from './Welcome.svelte'
  import { setActive } from './lib/ai.js'

  let words = $state.raw(null) // lemma -> entry, frequency order
  let forms = $state.raw(null) // form -> lemma
  let conj = $state.raw(null) // verb -> conjugation tables
  const cards = new SvelteMap() // lemma -> stored card
  let texts = $state([])
  let shelf = $state.raw([]) // built-in public-domain classics (French) and Norman Bridge texts (English)
  let story = $state.raw([]) // Pluton's story: one chapter per stage of the path
  let openText = $state(null) // text the Library shows (set from home goals too)
  let listenNow = $state(false)
  let celebrate = $state(null) // index of the stage just reached
  let stats = $state({ streak: 0, lastDay: null, points: 0, bestCombo: 0, newDay: null, newCount: 0 })
  let view = $state('home')
  let now = $state(new Date())
  let error = $state('')
  let summary = $state(null)
  let onboarded = $state(true)
  let ai = $state(null) // AI provider config incl. the user's key (this device only)
  let pace = $state({ perRun: NEW_PER_RUN, perDay: NEW_PER_DAY })
  $effect(() => setActive(ai))

  onMount(async () => {
    try {
      const [w, f, cj, sh, sy, cs, st, ts] = await Promise.all([
        fetch(`${DATA}words.json`).then((r) => r.json()),
        fetch(`${DATA}forms.json`).then((r) => r.json()),
        fetch(`${DATA}conj.json`).then((r) => r.json()),
        fetch(`${DATA}shelf.json`).then((r) => r.json()),
        fetch(`${DATA}story.json`).then((r) => r.json()),
        db.getAll('cards'), db.get('meta', 'stats'), db.getAll('texts'),
      ])
      ai = (await db.get('meta', 'ai')) ?? null
      onboarded = !!(await db.get('meta', 'onboarded')) || cs.length > 0
      pace = { ...pace, ...(await db.get('meta', 'settings')) }
      words = new Map(w.map((x) => [x.lemma, x]))
      forms = new Map(Object.entries(f))
      conj = cj
      shelf = sh.map((s) => ({ ...s, id: s.title })) // titles are unique; the path refers to classics by title
      story = sy.map((s, i) => ({ ...s, id: `story-${i}`, stage: i, lang: 'fr' }))
      for (const c of cs) cards.set(c.lemma, c)
      if (st) stats = st
      texts = ts
    } catch (e) {
      error = `Pluton couldn't load his words (${e.message}). Check your connection and reload.`
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
    : stage && waiting ? { fr: stage.fr, en: stage.say }
    : waiting ? { fr: 'On y va ?', en: `Shall we? ${waiting} cards are waiting.` }
    : { fr: 'Zzz… À demain !', en: 'All done for today. See you tomorrow!' })
  // Progression: measured from real cards and stats; the stage reached is stored, so forgetting never demotes you.
  const metrics = $derived.by(() => {
    let learned = 0, tablesTotal = 0
    const tables = {}
    for (const c of cards.values()) {
      if (!(c.known || c.fsrs.state === State.Review || c.fsrs.state === State.Relearning)) continue
      if (isWord(c)) learned++
      else if (!c.known) {
        const { tense } = parseConj(c.lemma)
        tables[tense] = (tables[tense] ?? 0) + 1
        tablesTotal++
      }
    }
    return { learned, tables, tablesTotal, read: new Set(stats.read ?? []), listenMin: (stats.listened ?? 0) / 60,
      talked: stats.talked ?? 0, written: stats.written ?? 0 }
  })
  const titles = $derived(Object.fromEntries(story.map((c) => [c.id, `Chapitre ${c.stage + 1} · ${c.title}`])))
  const path = $derived(pathState(metrics, titles))
  const reached = $derived(Math.max(path.current, stats.stage ?? 0))
  const stage = $derived(reached < STAGES.length ? path.stages[reached] : null)
  const todo = $derived(focus(stage, !!ai))
  $effect(() => {
    if (!words || path.current <= (stats.stage ?? 0)) return
    celebrate = path.current
    stats.stage = path.current
    sfx('done')
    db.put('meta', $state.snapshot(stats), 'stats')
  })

  /** Go where a goal is advanced. */
  function act(g) {
    unlock()
    if (g.kind === 'words' || g.kind === 'verbs') return counts.due || counts.fresh ? start() : (view = 'words')
    if (g.kind === 'talk' || g.kind === 'write') return (view = 'talk')
    if (g.kind === 'read') openText = g.id
    if (g.kind === 'listen') {
      openText = `story-${Math.min(reached, STAGES.length - 1)}`
      listenNow = true
    }
    view = 'read'
  }

  async function jump(i) {
    if (!confirm(`Make “${STAGES[i].place}” your current stage? Earlier goals stay as they are; nothing gets locked or unlocked.`)) return
    stats.stage = i
    await db.put('meta', $state.snapshot(stats), 'stats')
    view = 'home'
  }

  async function markRead(id) {
    stats.read = [...new Set([...(stats.read ?? []), id])]
    await db.put('meta', $state.snapshot(stats), 'stats')
  }

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
  <div class="loading"><Cat mood="sad" size={100} /><p class="panel">{error}</p></div>
{:else if !words}
  <div class="loading"><Cat mood="sleep" size={100} /><p class="muted">Chargement…</p></div>
{:else if !onboarded}
  <Welcome {words} {cards} ondone={() => (onboarded = true)} />
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

    <Path {path} {reached} onaction={act} onmap={() => (view = 'path')} />

    <section class="today">
      <h3>Today</h3>
      <button class="btn go" onclick={start} disabled={!counts.due && !counts.fresh}>
        {#if counts.due || counts.fresh}
          <span>{stats.lastDay === today ? 'Another run' : 'Start your run'}</span>
          <small>{counts.due} reviews · {counts.fresh} new</small>
        {:else}
          <span>Run done for today ✓</span>
          <small>Come back tomorrow for your reviews</small>
        {/if}
      </button>
      {#if todo}
        <button class="panel task" onclick={() => act(todo)}>
          <span class="ic">{{ read: '📖', listen: '🎧', talk: '💬', write: '✍️' }[todo.kind]}</span>
          <span><b>Then: {todo.label}</b><small>{todo.kind === 'listen' ? `${todo.have} / ${todo.need} min · read-along of your chapter` : todo.bonus ? 'Bonus ★' : 'Opens it in the library'}</small></span>
        </button>
      {/if}
    </section>

    <section class="tiles">
      <div class="panel tile"><b class="learning">{counts.learning}</b><span>🌱 learning</span></div>
      <div class="panel tile" title="Remembered for 3+ weeks"><b class="known">{counts.known}</b><span>🌳 known</span></div>
      <div class="panel tile"><b class="due">{counts.due}</b><span>⏰ due now</span></div>
    </section>

    <footer class="muted">
      <span>⭐ {stats.points} pts · best combo ×{stats.bestCombo}</span>
    </footer>
  {:else if view === 'path'}
    <Path full {path} {reached} onaction={act} onjump={jump} />
  {:else if view === 'read'}
    <Reader {words} {forms} {cards} {statusOf} {shelf} {story} read={metrics.read} onread={markRead} onlisten={listened}
      bind:texts bind:openId={openText} bind:listenNow />
  {:else if view === 'listen'}
    <Listen {words} {forms} {cards} {statusOf} onlisten={listened} />
  {:else if view === 'talk'}
    <Talk {ai} {words} {forms} {cards} {statusOf} vocab={counts.known + counts.learning} onsettings={() => (view = 'settings')} oncount={count} />
  {:else if view === 'settings'}
    <Settings bind:ai bind:pace onbackup={backup} onrestore={restore} />
  {:else}
    <Words {words} {cards} {now} {conj} />
  {/if}

  {#if celebrate != null}
    <div class="overlay" role="dialog" aria-modal="true" aria-label="Stage complete">
      <div class="panel celebrate">
        <Cat mood="fire" size={110} />
        <h2>{STAGES[celebrate - 1].place} ✓</h2>
        {#if celebrate < STAGES.length}
          <p>Étape {celebrate} terminée ! Next stop: <b>{STAGES[celebrate].place}</b> ({STAGES[celebrate].level}).</p>
          <p class="next" lang="fr">{STAGES[celebrate].fr}<small lang="en">{STAGES[celebrate].say}</small></p>
          <p class="muted">Chapter {celebrate + 1} of Pluton's story is waiting in the library.</p>
        {:else}
          <p>Pluton made it to the Opéra, and you walked the whole path. Félicitations !</p>
        {/if}
        <button class="btn" onclick={() => (celebrate = null)}>Continuer</button>
      </div>
    </div>
  {/if}

  <nav>
    <button class:on={view === 'home' || view === 'path'} onclick={() => { view = 'home'; now = new Date() }}><i>🏠</i>Home</button>
    <button class:on={view === 'read'} onclick={() => { view = 'read'; openText = null }}><i>📖</i>Library</button>
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
  .today h3 { margin-bottom: 8px; }
  .task { width: 100%; display: flex; gap: 12px; align-items: center; text-align: left; cursor: pointer; margin-top: -6px; }
  .task .ic { font-size: 26px; }
  .task small { display: block; color: var(--muted); font-size: 13px; }
  .overlay { position: fixed; inset: 0; background: rgba(10, 8, 25, 0.55); display: grid; place-items: center; padding: 16px; z-index: 20; }
  .celebrate { max-width: 420px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 6px; animation: pop 0.35s; }
  .celebrate p { margin: 4px 0; }
  .next { font-weight: 700; font-style: italic; }
  .next small { display: block; font-weight: 400; color: var(--muted); }
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
  .loading { display: flex; flex-direction: column; align-items: center; gap: 8px; margin-top: 28vh; text-align: center; }
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
  .go { width: 100%; flex-direction: column; gap: 2px; font-size: 22px; padding: 18px; margin-bottom: 16px; }
  .go small { font-weight: 600; font-size: 14px; opacity: 0.85; }
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
