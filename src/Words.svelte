<script>
  import { status, brief, KNOWN_DAYS } from './lib/logic.js'
  import { say } from './lib/sound.js'

  let { words, cards, now } = $props()

  let q = $state('')
  let only = $state('all')

  const LABEL = { queued: 'queued', learning: 'learning', known: 'known' }
  const rows = $derived([...cards.values()]
    .map((c) => ({ c, w: words.get(c.lemma), st: status(c) }))
    .sort((a, b) => b.c.added - a.c.added))
  const shown = $derived(rows.filter(({ c, w, st }) =>
    (only === 'all' || st === only) && (!q || c.lemma.includes(q.toLowerCase()) || w?.gloss.toLowerCase().includes(q.toLowerCase()))))
  const tally = $derived(rows.reduce((t, r) => ({ ...t, [r.st]: (t[r.st] ?? 0) + 1 }), {}))

  // Stability on a log scale: full bar at KNOWN_DAYS.
  const strength = (c) => (c.known ? 1 : Math.min(1, Math.log1p(c.fsrs.stability) / Math.log1p(KNOWN_DAYS)))
  function due(c) {
    if (c.known) return ''
    if (!c.fsrs.reps) return 'not started'
    const d = (c.fsrs.due - now) / 864e5
    return d <= 0 ? 'due now' : d < 1 ? 'later today' : `in ${Math.round(d)} d`
  }
</script>

<h2>Words</h2>
<p class="muted">{rows.length} in your deck · {tally.known ?? 0} known · {tally.learning ?? 0} learning · {tally.queued ?? 0} queued</p>

<div class="filters">
  <input class="field" type="search" placeholder="Search French or English…" bind:value={q} aria-label="Search words" />
  <div class="seg" role="group" aria-label="Filter by status">
    {#each ['all', 'learning', 'queued', 'known'] as f}
      <button class:on={only === f} onclick={() => (only = f)}>{f}</button>
    {/each}
  </div>
</div>

{#each shown as { c, w, st } (c.lemma)}
  <button class="row" onclick={() => say([w?.audio_word, w?.display ?? c.lemma])}>
    <span class="word"><b class={w?.gender}>{w?.display ?? c.lemma}</b><small>{brief(w?.gloss ?? '', 1)}</small></span>
    <span class="meta">
      <span class="pill {st}">{LABEL[st]}</span>
      <span class="bar" title="Memory strength"><i style="width:{strength(c) * 100}%"></i></span>
      <small>{due(c)}</small>
    </span>
  </button>
{:else}
  <p class="muted">{rows.length ? 'No match.' : 'Words you meet in runs and the library collect here.'}</p>
{/each}

<style>
  h2 { margin-bottom: 4px; }
  .filters { position: sticky; top: 0; background: var(--bg); padding: 8px 0; z-index: 1; }
  .field { width: 100%; padding: 12px 14px; border-radius: 14px; border: 2px solid var(--line); background: var(--panel); }
  .field:focus { outline: none; border-color: var(--blue); }
  .seg { display: flex; gap: 6px; margin-top: 8px; }
  .seg button {
    flex: 1; border: 2px solid var(--line); background: var(--panel); border-radius: 10px; padding: 6px; cursor: pointer;
    font-weight: 700; font-size: 14px; color: var(--muted); text-transform: capitalize;
  }
  .seg button.on { border-color: var(--blue); color: var(--blue); }
  .row {
    width: 100%; display: flex; justify-content: space-between; gap: 10px; align-items: center; text-align: left;
    padding: 10px 2px; border: 0; border-bottom: 1px solid var(--line); background: none; cursor: pointer;
  }
  .row:last-of-type { margin-bottom: 90px; }
  .word { min-width: 0; display: flex; flex-direction: column; }
  .word b { font-size: 18px; }
  .word small, .meta small { color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .meta { flex: none; width: 112px; display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
  .pill { font-size: 12px; font-weight: 800; padding: 1px 8px; border-radius: 99px; }
  .pill.known { background: color-mix(in srgb, var(--green) 20%, transparent); color: var(--green); }
  .pill.learning { background: color-mix(in srgb, var(--orange) 20%, transparent); color: var(--orange); }
  .pill.queued { background: color-mix(in srgb, var(--blue) 16%, transparent); color: var(--blue); }
  .bar { width: 100%; height: 6px; border-radius: 99px; background: var(--line); overflow: hidden; }
  .bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--orange), var(--green)); }
</style>
