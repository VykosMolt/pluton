<script>
  import * as db from './lib/db.js'
  import { createEmptyCard } from './lib/logic.js'
  import { levelOf } from './lib/tutor.js'
  import { say, unlock } from './lib/sound.js'
  import Cat from './Cat.svelte'

  /** First launch: what Pluton is, and an optional placement check so people who know some French skip ahead. */
  let { words, cards, ondone } = $props()

  // Frequency bands of the deck (by rank) and how many words to sample from each.
  const BANDS = [[0, 100], [100, 300], [300, 700], [700, 1500], [1500, 3000], [3000, 5000], [5000, 8000]]
  const PER_BAND = 5
  const KNOWN = 0.8 // a band counts as known when this share of its sample was known (and confirmed)

  const deck = [...words.values()].filter((w) => w.sentence)
  let step = $state('hello') // hello | quiz | result
  let band = $state(0)
  let sample = $state([])
  let i = $state(0)
  let reveal = $state(false)
  let scores = $state([]) // known share per band tested
  let result = $state(null)

  function pickBand(b) {
    const [lo, hi] = BANDS[b]
    const pool = deck.slice(lo, hi)
    sample = Array.from({ length: PER_BAND }, () => pool.splice(Math.floor(Math.random() * pool.length), 1)[0])
    band = b
    i = 0
    reveal = false
    hits = 0
  }

  let hits = 0
  const w = $derived(sample[i])

  function start() {
    unlock()
    pickBand(0)
    step = 'quiz'
    say([w.audio_word, w.display])
  }

  function answer(known) {
    hits += known ? 1 : 0
    reveal = false
    if (++i < sample.length) return say([w.audio_word, w.display])
    scores = [...scores, hits / sample.length]
    if (hits / sample.length >= 0.4 && band + 1 < BANDS.length) {
      pickBand(band + 1)
      return say([w.audio_word, w.display])
    }
    finish()
  }

  async function finish() {
    // Estimated vocabulary: each tested band's known share times its size. Marked known: the leading bands that
    // clearly passed, so runs start where the learner actually is.
    const estimate = Math.round(scores.reduce((n, s, b) => n + s * (BANDS[b][1] - BANDS[b][0]), 0))
    let upTo = 0
    for (let b = 0; b < scores.length && scores[b] >= KNOWN; b++) upTo = BANDS[b][1]
    const now = Date.now()
    const known = deck.slice(0, upTo).filter((x) => !cards.has(x.lemma))
      .map((x) => ({ lemma: x.lemma, fsrs: createEmptyCard(), added: now, known: true, placed: true }))
    await db.putMany('cards', known)
    for (const c of known) cards.set(c.lemma, c)
    result = { estimate, marked: known.length, level: levelOf(estimate).code }
    step = 'result'
  }

  async function done() {
    await db.put('meta', true, 'onboarded')
    ondone()
  }
</script>

<div class="wrap">
  {#if step === 'hello'}
    <Cat mood="happy" size={130} />
    <h1>Bonjour ! Moi, c'est Pluton.</h1>
    <p class="lead">Poe's black cat, now your French tutor. Here's how this works:</p>
    <ul>
      <li><b>🃏 Runs.</b> Ten-minute sessions. Each word comes back right before you'd forget it, and you <i>type</i> it, not tap it.</li>
      <li><b>📖 Reading & listening.</b> Real French unlocks as your vocabulary grows: songs, subtitles, Maupassant.</li>
      <li><b>🏰 The Norman Bridge.</b> Nearly a third of English came from French. Read Poe and watch it turn back as you learn.</li>
      <li><b>💬 Talking.</b> Chat and writing practice with Pluton, using your own AI key (optional).</li>
    </ul>
    <button class="btn wide" onclick={done}>I'm new to French</button>
    <button class="btn ghost wide" onclick={start}>I already know some French</button>
  {:else if step === 'quiz' && w}
    <p class="muted">Placement · {BANDS[band][0] === 0 ? 'most common words' : `words ranked ${BANDS[band][0]}–${BANDS[band][1]}`}</p>
    <div class="dots">{#each sample as _, k}<i class:on={k < i} class:now={k === i}></i>{/each}</div>
    <section class="panel card">
      <h2 class={w.gender ?? ''}>{w.display}</h2>
      <button class="btn ghost" onclick={() => say([w.audio_word, w.display])} aria-label="Play audio">🔊</button>
      {#if reveal}
        <p class="gloss">{w.gloss}</p>
        <p>Did you know it?</p>
        <div class="row">
          <button class="btn" onclick={() => answer(true)}>✓ Yes</button>
          <button class="btn ghost" onclick={() => answer(false)}>✗ Not quite</button>
        </div>
      {:else}
        <p>Do you know what this means?</p>
        <div class="row">
          <button class="btn" onclick={() => (reveal = true)}>I think so</button>
          <button class="btn ghost" onclick={() => answer(false)}>No idea</button>
        </div>
      {/if}
    </section>
    <button class="link" onclick={finish}>Stop here</button>
  {:else if step === 'result'}
    <Cat mood="fire" size={120} />
    <h1>~{result.estimate.toLocaleString('en')} words</h1>
    <p class="lead">That's roughly {result.level} vocabulary.
      {#if result.marked}I've marked the {result.marked.toLocaleString('en')} most common words as known, so your runs start with new material.
      If one comes up in a text and you don't know it after all, tap it and choose Undo.
      {:else}Your runs will start from the most common words.{/if}</p>
    <button class="btn wide" onclick={done}>Let's go</button>
  {/if}
</div>

<style>
  .wrap { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 12px; padding-top: 20px; }
  h1 { font-size: 28px; }
  .lead { margin: 0; }
  ul { text-align: left; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 10px; margin: 6px 0 10px; }
  li { background: var(--panel); border: 2px solid var(--line); border-radius: 14px; padding: 10px 12px; }
  .wide { width: 100%; }
  .card { width: 100%; display: flex; flex-direction: column; align-items: center; gap: 8px; }
  .card h2 { font-size: 40px; margin-top: 6px; }
  .gloss { font-size: 20px; font-weight: 700; margin: 4px 0; }
  .row { display: flex; gap: 10px; }
  .dots { display: flex; gap: 6px; }
  .dots i { width: 10px; height: 10px; border-radius: 99px; background: var(--line); }
  .dots i.on { background: var(--green); }
  .dots i.now { background: var(--blue); }
  .link { border: 0; background: none; color: var(--muted); text-decoration: underline; cursor: pointer; }
</style>
