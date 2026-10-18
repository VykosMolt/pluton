<script>
  import * as db from './lib/db.js'
  import { gramKey, createEmptyCard, status } from './lib/logic.js'

  /** Every grammar topic, open at any time; "Practice" puts it into your runs whatever your stage. */
  let { grammar, cards } = $props()

  let open = $state(null)
  let shown = $state(new Set()) // exercises whose answer is revealed
  const STATUS = { new: '', queued: 'queued for your runs', learning: 'practising', known: 'known ✓' }

  async function practice(id) {
    const card = { lemma: gramKey(id), fsrs: createEmptyCard(), added: Date.now() }
    await db.put('cards', card)
    cards.set(card.lemma, card)
  }

  const reveal = (k) => (shown = new Set([...shown, k]))
</script>

{#each grammar as t (t.id)}
  {@const st = status(cards.get(gramKey(t.id)))}
  <section class="panel topic" class:open={open === t.id}>
    <button class="head" onclick={() => (open = open === t.id ? null : t.id)} aria-expanded={open === t.id}>
      <span class="lvl">{t.level}</span>
      <span class="name"><b>{t.name}</b><small>{t.en}</small></span>
      {#if st !== 'new'}<span class="state {st}">{STATUS[st]}</span>{/if}
    </button>
    {#if open === t.id}
      <p class="how">{t.how}</p>
      <ol class="items" lang="fr">
        {#each t.items as it, i}
          {@const [a, b] = it.q.split('___')}
          {@const k = `${t.id}/${i}`}
          <li>
            {a}{#if shown.has(k)}<mark>{it.a.join(' / ')}</mark>{:else}<button class="gap" onclick={() => reveal(k)} aria-label="Show the answer">?</button>{/if}{b}
          </li>
        {/each}
      </ol>
      {#if st === 'new'}<button class="btn" onclick={() => practice(t.id)}>Practice this topic</button>{/if}
    {/if}
  </section>
{/each}

<style>
  .topic { margin-bottom: 10px; padding: 12px 14px; }
  .topic.open { border-color: var(--blue); }
  .head { width: 100%; display: flex; gap: 10px; align-items: center; border: 0; background: none; text-align: left; cursor: pointer; padding: 0; }
  .lvl { flex: none; font-size: 12px; font-weight: 800; padding: 2px 8px; border-radius: 99px; background: color-mix(in srgb, var(--gold) 30%, transparent); }
  .name { flex: 1; display: flex; flex-direction: column; }
  .name small { color: var(--muted); }
  .state { font-size: 12px; font-weight: 700; color: var(--muted); }
  .state.known { color: var(--green); }
  .how { font-size: 15px; line-height: 1.5; }
  .items { padding-left: 22px; font-size: 17px; line-height: 1.6; }
  .gap { border: 0; border-bottom: 2px solid var(--blue); background: none; min-width: 3ch; cursor: pointer; color: var(--blue); font-weight: 700; }
</style>
