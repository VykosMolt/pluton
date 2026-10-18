<script>
  import * as db from './lib/db.js'
  import { conjKey, createEmptyCard, status } from './lib/logic.js'
  import { LANG } from './lib/lang.js'
  import { say, unlock } from './lib/sound.js'

  /** Every conjugation table, open at any time; "Practice" puts a tense into your runs whatever your stage. */
  let { conj, cards, words } = $props()

  let q = $state('')
  let open = $state(null)
  const verbs = $derived(Object.keys(conj).filter((v) => !q || v.includes(q.toLowerCase().trim())))
  const STATUS = { new: '', queued: 'queued for your runs', learning: 'practising', known: 'known ✓' }

  async function practice(verb, tense) {
    const card = { lemma: conjKey(verb, tense), fsrs: createEmptyCard(), added: Date.now() }
    await db.put('cards', card)
    cards.set(card.lemma, card)
  }

  function hear(verb, tense, i) {
    unlock()
    say([conj[verb].audio[tense][i], `${LANG.subject(i, tense)} ${conj[verb].t[tense][i]}`])
  }
</script>

<input class="field" type="search" placeholder="Find a verb…" bind:value={q} aria-label="Find a verb" />
<div class="verbs">
  {#each verbs as v (v)}
    <button class="verb" class:on={open === v} onclick={() => (open = open === v ? null : v)}>{v}</button>
  {/each}
</div>

{#if open}
  {@const c = conj[open]}
  <h3>{open} <small class="muted">{words.get(open)?.gloss.split(';')[0] ?? ''} · past participle {c.pp}, with {c.aux}</small></h3>
  {#each LANG.tenses.filter((t) => c.t[t.id]) as t (t.id)}
    {@const st = status(cards.get(conjKey(open, t.id)))}
    <section class="panel tense">
      <div class="row">
        <b>{t.name}</b> <small class="muted">{t.en}</small>
        {#if st === 'new'}<button class="btn ghost small" onclick={() => practice(open, t.id)}>Practice</button>
        {:else}<span class="state {st}">{STATUS[st]}</span>{/if}
      </div>
      <div class="table">
        {#each c.t[t.id] as form, i}
          {#if form}
            <button style="grid-area:{(i % 3) + 1}/{i < 3 ? 1 : 2}" onclick={() => hear(open, t.id, i)}>
              <span>{LANG.subject(i, t.id)}</span> <b>{form}</b>
            </button>
          {/if}
        {/each}
      </div>
      <details><summary>How it's formed</summary><p>{t.how}</p></details>
    </section>
  {/each}
{/if}

<style>
  .field { width: 100%; padding: 12px 14px; border-radius: 14px; border: 2px solid var(--line); background: var(--panel); margin-bottom: 10px; }
  .verbs { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 14px; }
  .verb { border: 2px solid var(--line); background: var(--panel); border-radius: 99px; padding: 5px 12px; cursor: pointer; font-weight: 600; }
  .verb.on { border-color: var(--blue); color: var(--blue); }
  h3 { margin: 8px 0 10px; }
  h3 small { font-size: 13px; font-weight: 500; }
  .tense { margin-bottom: 10px; }
  .row { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
  .row .btn, .row .state { margin-left: auto; }
  .small { padding: 6px 12px; font-size: 14px; }
  .state { font-size: 13px; font-weight: 700; color: var(--muted); }
  .state.known { color: var(--green); }
  .table { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 10px; }
  .table button { display: flex; gap: 6px; align-items: baseline; padding: 7px 10px; border-radius: 10px; cursor: pointer; border: 2px solid var(--line); background: none; text-align: left; font-size: 16px; }
  .table button span { color: var(--muted); font-size: 13px; }
  details { margin-top: 8px; font-size: 14px; }
  summary { cursor: pointer; color: var(--muted); }
  details p { margin: 6px 0 0; }
</style>
