<script>
  import { onMount, tick } from 'svelte'
  import * as db from './lib/db.js'
  import { buildRun, grade, RATING, points, nextCombo, blank, requeue, scheduler } from './lib/logic.js'
  import { say, sfx, stop } from './lib/sound.js'
  import { LANG } from './lib/lang.js'
  import Cat from './Cat.svelte'

  let { words, forms, cards, newToday, onexit } = $props()

  const COLORS = ['var(--blue)', 'var(--red)', 'var(--gold)', 'var(--green)', 'var(--orange)']

  let queue = $state(buildRun([...cards.values()], words.values(), new Date(), newToday))
  let done = $state(0)
  let mode = $state('cloze') // fixed per card when shown; the card's reps change on answer
  let answer = $state('')
  let fb = $state(null) // feedback for the current answer
  let combo = $state(0)
  let best = $state(0)
  let score = $state(0)
  const tally = $state({ right: 0, almost: 0, wrong: 0 })
  let introduced = 0
  let shownAt = 0
  let saveError = $state('')
  let burst = $state(0)
  let input = $state()
  let gotBtn = $state()

  const cur = $derived(queue[0])
  const w = $derived(cur && content(cur.lemma))
  const parts = $derived(w ? blank(w.sentence, w.form) : ['', '', ''])
  const progress = $derived(done / (done + queue.length || 1))
  const catMood = $derived(
    fb?.result === 'wrong' ? 'sad' : combo >= 5 && fb?.result !== 'almost' ? 'fire' : fb?.result === 'right' ? 'happy' : 'idle')

  function content(lemma) {
    const base = words.get(lemma) ?? { lemma, display: lemma, gloss: '', pos: '' }
    const mined = cards.get(lemma)?.mined
    return mined ? { ...base, ...mined, audio_sentence: null } : base
  }

  const sentenceClip = () => [w.audio_sentence, w.sentence]
  const replay = () => (cur.phase === 'intro' ? say([w.audio_word, w.display], sentenceClip()) : say(sentenceClip()))

  async function show() {
    answer = ''
    fb = null
    if (!cur) return finish(done > 0)
    mode = cur.card.reps % 2 ? 'dictation' : 'cloze'
    shownAt = Date.now()
    if (cur.phase === 'intro' || mode === 'dictation') replay()
    else stop()
    await tick()
    ;(cur?.phase === 'intro' ? gotBtn : input)?.focus()
  }
  onMount(show)

  function finish(completed) {
    stop()
    if (completed) sfx('done')
    onexit({ completed, points: score, best, introduced, ...tally, total: tally.right + tally.almost + tally.wrong })
  }

  function submit(e) {
    e.preventDefault()
    if (cur.phase === 'intro') return gotIt()
    if (fb) return next()
    if (answer.trim()) check(answer)
  }

  function gotIt() {
    const [it, ...rest] = queue
    it.phase = 'quiz'
    introduced++
    queue = [...rest.slice(0, 2), it, ...rest.slice(2)] // a couple of cards between seeing and recalling
    show()
  }

  function check(typed) {
    const g = grade(typed, parts[1], cur.lemma, forms)
    const revenge = cur.missed && g.result === 'right'
    const gained = points(g.result, combo, Date.now() - shownAt, revenge)
    combo = nextCombo(g.result, combo)
    best = Math.max(best, combo)
    score += gained
    tally[g.result]++
    sfx(g.result)
    if (g.result === 'right' && combo % 5 === 0) {
      sfx('combo')
      burst++
    }
    const now = new Date()
    const card = scheduler.next($state.snapshot(cur.card), now, RATING[g.result]).card
    const stored = { ...(cards.get(cur.lemma) ?? { lemma: cur.lemma, added: now.getTime() }), fsrs: card }
    cards.set(cur.lemma, stored)
    db.review(stored, { lemma: cur.lemma, rating: RATING[g.result], mode, at: now.getTime() })
      .catch((err) => (saveError = `Progress not saved (${err.message}). Make a backup soon.`))
    cur.card = card
    cur.seen++
    cur.missed ||= g.result === 'wrong'
    fb = { ...g, gained, revenge, typed, praise: LANG.praise[Math.floor(Math.random() * LANG.praise.length)] }
    say(sentenceClip())
  }

  function next() {
    const [it, ...rest] = queue
    queue = requeue(rest, it, new Date())
    done++
    show()
  }

  async function insert(ch) {
    const s = input.selectionStart ?? answer.length, e = input.selectionEnd ?? s
    answer = answer.slice(0, s) + ch + answer.slice(e)
    await tick()
    input.focus()
    input.setSelectionRange(s + 1, s + 1)
  }

  const pieces = () => Array.from({ length: 26 }, (_, i) => ({
    x: Math.round((Math.random() - 0.5) * 420), y: Math.round(-80 - Math.random() * 260),
    r: Math.round(Math.random() * 720), c: COLORS[i % COLORS.length],
  }))
</script>

<div class="top">
  <button class="x" onclick={() => finish(false)} aria-label="Quit run">✕</button>
  <div class="progress"><i style="width:{progress * 100}%"></i></div>
  <div class="combo" class:hot={combo >= 5} title="Combo">×{combo}</div>
  <div class="score" title="Points">⭐{score}</div>
</div>

{#if saveError}<p class="err">{saveError}</p>{/if}

{#if cur}
  <form onsubmit={submit}>
    {#key `${done}:${cur.lemma}:${cur.phase}`}
      {#if cur.phase === 'intro'}
        <div class="panel card enter">
          <span class="tag new">✨ New word</span>
          <h2 class="word {w.gender ?? ''}">{w.display}</h2>
          <p class="muted">{w.pos}{w.gender ? ` · ${w.gender === 'm' ? 'masculine' : 'feminine'}` : ''}</p>
          <p class="gloss">{w.gloss}</p>
          <p class="sentence">{parts[0]}<mark>{parts[1]}</mark>{parts[2]}</p>
          {#if w.translation}<p class="muted">{w.translation}</p>{/if}
          <button type="button" class="btn ghost replay" onclick={replay} aria-label="Play audio">🔊</button>
        </div>
        <button class="btn wide" bind:this={gotBtn}>Got it →</button>
      {:else}
        <div class="panel card enter" class:shake={fb?.result === 'wrong'} class:glow={fb?.result === 'right'}>
          <span class="tag">{mode === 'dictation' ? '🎧 Listen, type the missing word' : '✍️ Fill the gap'}</span>
          <p class="sentence">
            {parts[0]}{#if fb}<mark class={fb.result}>{parts[1]}</mark>{:else}<span class="gap" style="min-width:{parts[1].length + 1}ch"></span>{/if}{parts[2]}
          </p>
          {#if w.translation && (mode === 'cloze' || fb)}<p class="muted">{w.translation}</p>{/if}
          {#if fb}
            <p class="wordline"><b class={w.gender}>{w.display}</b> — {w.gloss}</p>
          {:else if mode === 'cloze'}
            <p class="hint">{w.pos} · {w.gloss}</p>
          {/if}
          <button type="button" class="btn ghost replay" onclick={replay} aria-label="Play audio">🔊</button>
          {#if fb?.gained}<span class="plus">+{fb.gained}</span>{/if}
        </div>

        <div class="react">
          <Cat mood={catMood} size={60} />
          <p class="msg {fb?.result ?? ''}">
            {#if !fb}<span class="muted">{mode === 'dictation' ? 'Écoute bien… (listen closely)' : 'Quel mot manque ? (which word is missing?)'}</span>
            {:else if fb.result === 'right'}{fb.revenge ? 'Revanche ! 💥' : fb.praise}
            {:else if fb.why === 'accent'}Watch the accent: <b>{parts[1]}</b>
            {:else if fb.why === 'form'}Right word, wrong form: <b>{parts[1]}</b>
            {:else if fb.why === 'typo'}Typo, it's <b>{parts[1]}</b>
            {:else if fb.why === 'other'}“{fb.typed}” is a different word ({words.get(fb.other)?.gloss || fb.other}). Here: <b>{parts[1]}</b>
            {:else}It's <b>{parts[1]}</b>{/if}
          </p>
        </div>

        <div class="answer">
          <input bind:this={input} bind:value={answer} class:wrong={fb?.result === 'wrong'} lang="fr" autocomplete="off" autocapitalize="none"
            autocorrect="off" spellcheck="false" enterkeyhint="go" placeholder="Type the word…" aria-label="Your answer" />
          <button class="btn">{fb ? 'Next →' : 'Check'}</button>
        </div>
        {#if !fb}
          <div class="accents">
            {#each LANG.accents as ch}
              <button type="button" onpointerdown={(e) => e.preventDefault()} onclick={() => insert(ch)}>{ch}</button>
            {/each}
          </div>
          <button type="button" class="dunno" onclick={() => check('')}>🤷 Show me</button>
        {/if}
      {/if}
    {/key}
  </form>
{/if}

{#key burst}
  {#if burst}
    <div class="confetti" aria-hidden="true">
      {#each pieces() as p}<i style="--x:{p.x}px;--y:{p.y}px;--r:{p.r}deg;background:{p.c}"></i>{/each}
    </div>
  {/if}
{/key}

<style>
  .top { display: flex; align-items: center; gap: 12px; margin: 4px 0 20px; }
  .x { border: 0; background: none; font-size: 22px; color: var(--muted); cursor: pointer; padding: 4px; }
  .progress { flex: 1; height: 14px; border-radius: 99px; background: var(--line); overflow: hidden; }
  .progress i { display: block; height: 100%; background: var(--green); border-radius: 99px; transition: width 0.3s; }
  .combo { font-weight: 900; min-width: 44px; text-align: center; color: var(--muted); transition: transform 0.2s; }
  .combo.hot { color: var(--orange); transform: scale(1.25); animation: pulse 0.8s infinite alternate; }
  .score { font-weight: 800; }
  .err { color: var(--red); font-weight: 700; }
  .card { position: relative; min-height: 220px; margin-bottom: 14px; }
  .enter { animation: enter 0.25s ease-out; }
  .shake { animation: shake 0.35s; }
  .glow { border-color: var(--green); box-shadow: 0 0 0 4px color-mix(in srgb, var(--green) 25%, transparent); }
  .tag { display: inline-block; font-size: 13px; font-weight: 800; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
  .tag.new { color: var(--gold); }
  .word { font-size: 44px; font-weight: 900; margin: 10px 0 2px; letter-spacing: -0.02em; }
  .gloss { font-size: 20px; font-weight: 700; margin: 8px 0 14px; }
  .sentence { font-size: 24px; font-weight: 600; margin: 16px 0 8px; padding-right: 44px; }
  .gap { display: inline-block; border-bottom: 3px solid var(--blue); height: 1em; vertical-align: baseline; }
  mark { background: color-mix(in srgb, var(--gold) 35%, transparent); color: inherit; border-radius: 6px; padding: 0 4px; }
  mark.right { background: color-mix(in srgb, var(--green) 30%, transparent); }
  mark.almost { background: color-mix(in srgb, var(--orange) 30%, transparent); }
  mark.wrong { background: color-mix(in srgb, var(--red) 30%, transparent); }
  .hint, .wordline { margin: 12px 0 0; }
  .hint { color: var(--muted); font-style: italic; }
  .replay { position: absolute; top: 12px; right: 12px; padding: 8px 10px; font-size: 18px; }
  .plus { position: absolute; right: 18px; bottom: 14px; font-weight: 900; font-size: 26px; color: var(--gold); animation: float 0.9s ease-out forwards; }
  .react { display: flex; align-items: center; gap: 10px; min-height: 66px; margin: 0 0 10px; }
  .msg { flex: 1; font-weight: 700; font-size: 18px; margin: 0; }
  .msg .muted { font-weight: 600; font-size: 15px; }
  .msg.right { color: var(--green); font-size: 22px; animation: enter 0.3s; }
  .msg.almost { color: var(--orange); }
  .msg.wrong { color: var(--red); }
  .answer { display: flex; gap: 10px; }
  .answer input {
    flex: 1; min-width: 0; padding: 14px 16px; font-size: 20px; border-radius: 16px;
    border: 2px solid var(--line); background: var(--panel);
  }
  .answer input:focus { outline: none; border-color: var(--blue); }
  .answer input.wrong { color: var(--red); text-decoration: line-through; }
  .accents { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
  .accents button {
    min-width: 40px; padding: 8px; border-radius: 10px; border: 2px solid var(--line); background: var(--panel);
    font-size: 18px; cursor: pointer;
  }
  .dunno { border: 0; background: none; color: var(--muted); cursor: pointer; padding: 6px 0; }
  .wide { width: 100%; font-size: 20px; }
  .confetti i {
    position: fixed; left: 50%; top: 45%; width: 9px; height: 14px; border-radius: 2px; pointer-events: none;
    animation: fly 1s ease-out forwards;
  }
  @keyframes enter { from { transform: translateY(10px) scale(0.98); opacity: 0; } }
  @keyframes shake { 20%, 60% { transform: translateX(-8px); } 40%, 80% { transform: translateX(8px); } }
  @keyframes float { from { transform: translateY(10px); opacity: 0; } 30% { opacity: 1; } to { transform: translateY(-30px); opacity: 0; } }
  @keyframes pulse { to { transform: scale(1.4); } }
  @keyframes fly { to { transform: translate(var(--x), var(--y)) rotate(var(--r)); opacity: 0; } }
</style>
