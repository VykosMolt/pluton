<script>
  import { STAGES } from './lib/path.js'

  /** The current stage card (home) or the whole path (full). Goals are buttons that go where they're advanced. */
  let { path, reached, full = false, onaction, onmap, onjump } = $props()

  const ICON = { words: '📚', verbs: '📐', grammar: '🧩', read: '📖', listen: '🎧', talk: '💬', write: '✍️' }
  const finished = $derived(reached >= STAGES.length)
  const current = $derived(path.stages[Math.min(reached, STAGES.length - 1)])
  let open = $state(null) // stage expanded in the full view
  const n = (x) => x.toLocaleString('en')
</script>

{#snippet goals(stage)}
  <ul class="goals">
    {#each stage.goals as g (g.kind + (g.id ?? ''))}
      <li class:done={g.done} class:bonus={g.bonus}>
        <button onclick={() => onaction(g)}>
          <span class="ic">{g.done ? '✅' : ICON[g.kind]}</span>
          <span class="lbl">{g.bonus ? '★ ' : ''}{g.label}</span>
          {#if g.kind !== 'read'}<span class="num">{n(Math.min(g.have, g.need))} / {n(g.need)}</span>{/if}
          {#if g.kind !== 'read'}<span class="bar"><i style="width:{g.pct * 100}%"></i></span>{/if}
        </button>
      </li>
    {/each}
  </ul>
{/snippet}

{#if !full}
  <section class="panel stage">
    <div class="head">
      <span class="tag">{finished ? 'Le Chemin · done' : `Étape ${current.index + 1} / ${STAGES.length} · ${current.level}`}</span>
      <button class="link" onclick={onmap}>The whole path →</button>
    </div>
    {#if finished}
      <h2>L'Opéra ✓</h2>
      <p>Pluton made it from Poe's wall to the Paris Opéra, and so did you. Keep reading, listening and talking: C1 is a habit, not a finish line.</p>
    {:else}
      <h2>{current.place} <small>{current.en}</small></h2>
      <div class="bar big"><i style="width:{current.pct * 100}%"></i></div>
      {@render goals(current)}
    {/if}
  </section>
{:else}
  <h2 class="title">Le Chemin</h2>
  <p class="muted">Pluton's journey from Poe's wall to the Paris Opéra, A1 to C1. Nothing here is locked: every text and
    feature is open from day one, and the goals just show the next useful step. ★ goals are a bonus.</p>
  {#each path.stages as s (s.index)}
    {@const expanded = open === s.index || (open === null && s.index === Math.min(reached, STAGES.length - 1))}
    <section class="panel step" class:complete={s.complete} class:current={s.index === reached}>
      <button class="step-head" onclick={() => (open = expanded ? -1 : s.index)} aria-expanded={expanded}>
        <span class="badge">{s.complete ? '✓' : s.index + 1}</span>
        <span class="name"><b>{s.place}</b> <small>{s.level}</small><br /><small class="muted" lang="fr">{s.fr}</small></span>
        <span class="pct">{Math.round(s.pct * 100)}%</span>
      </button>
      {#if expanded}
        <p class="say">{s.say}</p>
        {@render goals(s)}
        {#if s.index > reached}
          <button class="link" onclick={() => onjump(s.index)}>Make this my current stage</button>
        {/if}
      {/if}
    </section>
  {/each}
{/if}

<style>
  .stage { margin-bottom: 16px; }
  .head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
  .tag { font-size: 13px; font-weight: 800; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }
  h2 { margin: 6px 0 8px; font-size: 26px; }
  h2 small { font-size: 15px; color: var(--muted); font-weight: 600; }
  .title { margin-top: 0; }
  .bar { display: block; height: 8px; border-radius: 99px; background: var(--line); overflow: hidden; }
  .bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--gold), var(--green)); border-radius: 99px; }
  .bar.big { height: 12px; margin-bottom: 10px; }
  .goals { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
  .goals button {
    width: 100%; display: grid; grid-template-columns: 28px 1fr auto; grid-template-rows: auto auto; align-items: center;
    gap: 2px 8px; text-align: left; border: 0; background: none; padding: 6px 4px; border-radius: 10px; cursor: pointer;
  }
  .goals button:hover { background: color-mix(in srgb, var(--blue) 7%, transparent); }
  .goals .ic { grid-row: span 2; font-size: 18px; text-align: center; }
  .goals .lbl { font-weight: 600; font-size: 15px; }
  .goals .num { font-size: 13px; color: var(--muted); font-weight: 700; }
  .goals .bar { grid-column: 2 / 4; height: 5px; }
  .goals .done .lbl { color: var(--muted); text-decoration: line-through; }
  .goals .bonus .lbl { color: var(--muted); }
  .link { border: 0; background: none; color: var(--blue); text-decoration: underline; cursor: pointer; font-size: 14px; padding: 0; }
  .step { margin-bottom: 10px; padding: 12px 14px; }
  .step.current { border-color: var(--blue); }
  .step.complete { opacity: 0.85; }
  .step-head { width: 100%; display: flex; align-items: center; gap: 12px; border: 0; background: none; text-align: left; cursor: pointer; padding: 0; }
  .badge { flex: none; width: 34px; height: 34px; border-radius: 99px; display: grid; place-items: center; font-weight: 900; background: var(--line); }
  .complete .badge { background: var(--green); color: #fff; }
  .current .badge { background: var(--btn); color: #fff; }
  .name { flex: 1; }
  .pct { font-weight: 800; color: var(--muted); }
  .say { font-style: italic; margin: 10px 0; }
</style>
