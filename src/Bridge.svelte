<script>
  import { onMount } from 'svelte'
  import { englishTokens } from './lib/logic.js'
  import { DATA, LANG } from './lib/lang.js'
  import WordSheet from './WordSheet.svelte'

  /** An English text with its French-derived words showing, and woven back into French as you learn them. */
  let { text, words, cards, statusOf } = $props()

  let cognates = $state.raw(null)
  let failed = $state(false)
  let mode = $state('english') // english | mine | all
  let sel = $state(null)

  onMount(async () => {
    try {
      cognates = new Map(Object.entries(await (await fetch(`${DATA}cognates.json`)).json()))
    } catch {
      failed = true
    }
  })

  const toks = $derived(cognates ? englishTokens(text.body, cognates) : [])
  const stats = $derived.by(() => {
    const fr = toks.filter((t) => t.fr && !t.faux)
    return { fr: fr.length, pct: toks.length ? fr.length / toks.length : 0, words: new Set(fr.map((t) => t.fr)).size,
      ready: new Set(fr.filter((t) => statusOf(t.fr) !== 'new').map((t) => t.fr)).size, faux: toks.filter((t) => t.faux).length }
  })
  const segs = $derived.by(() => {
    const out = []
    let at = 0
    for (const t of toks) {
      if (!t.fr) continue
      if (t.start > at) out.push({ text: text.body.slice(at, t.start) })
      out.push({ t })
      at = t.end
    }
    out.push({ text: text.body.slice(at) })
    return out
  })

  const woven = (t) => !t.faux && (mode === 'all' || (mode === 'mine' && statusOf(t.fr) !== 'new'))
  const shown = (t) => (/^[A-Z]/.test(t.text) ? t.fr[0].toUpperCase() + t.fr.slice(1) : t.fr)
  const note = (t) => (t.faux ? `⚠️ Faux ami: ${t.fr} means “${t.clue.slice(5)}”, not “${t.base}”.`
    : LANG.clue(t.clue, t.fr, t.base) || `${t.base} came into English from French: ${t.fr}.`)
  const pct = (x) => `${Math.round(x * 100)}%`
</script>

{#if failed}
  <p class="panel">Couldn't load the Norman Bridge data. Are you offline?</p>
{:else if !cognates}
  <p class="muted">Loading…</p>
{:else}
  <p class="intro">
    After 1066, Norman French poured into English. <b>{stats.fr} words here ({pct(stats.pct)})</b> came that way:
    {stats.words} different French words, {stats.ready} of them already in your deck.
    {#if stats.faux}<br /><span class="muted">⚠️ {stats.faux} false friends are flagged too.</span>{/if}
  </p>
  <div class="seg" role="radiogroup" aria-label="Show">
    <button role="radio" aria-checked={mode === 'english'} class:on={mode === 'english'} onclick={() => (mode = 'english')}>English</button>
    <button role="radio" aria-checked={mode === 'mine'} class:on={mode === 'mine'} onclick={() => (mode = 'mine')}>Weave what I know</button>
    <button role="radio" aria-checked={mode === 'all'} class:on={mode === 'all'} onclick={() => (mode = 'all')}>Weave everything</button>
  </div>
  <div class="panel text" lang="en">
    {#each segs as s}{#if s.t}<span role="button" tabindex="0" title={s.t.text}
          class="cog {s.t.faux ? 'faux' : statusOf(s.t.fr)}" class:fr={woven(s.t)} class:sel={sel === s.t}
          onclick={() => (sel = s.t)} onkeydown={(e) => e.key === 'Enter' && (sel = s.t)}
          lang={woven(s.t) ? 'fr' : undefined}>{woven(s.t) ? shown(s.t) : s.t.text}</span>{:else}{s.text}{/if}{/each}
  </div>
{/if}

{#if sel}
  <WordSheet tok={{ lemma: sel.fr, text: sel.text }} sentence={null} note={note(sel)} {words} {cards} {statusOf} onclose={() => (sel = null)} />
{/if}

<style>
  .intro { margin: 10px 0; }
  .seg { display: flex; gap: 6px; margin: 10px 0 12px; flex-wrap: wrap; }
  .seg button { flex: 1; border: 2px solid var(--line); background: var(--panel); border-radius: 10px; padding: 7px 8px; cursor: pointer; font-weight: 700; font-size: 14px; color: var(--muted); }
  .seg button.on { border-color: var(--blue); color: var(--blue); }
  .text { font-size: 19px; line-height: 1.75; white-space: pre-wrap; margin-bottom: 220px; font-family: Georgia, 'Times New Roman', serif; }
  .cog { cursor: pointer; border-radius: 4px; border-bottom: 2px solid color-mix(in srgb, var(--blue) 45%, transparent); }
  .cog.learning { border-bottom-color: var(--gold); }
  .cog.known { border-bottom-color: var(--green); }
  .cog.faux { border-bottom: 2px dashed var(--red); }
  .cog.fr { font-style: italic; font-weight: 700; color: var(--blue); border-bottom-style: solid; }
  .cog.sel { outline: 2px solid var(--blue); }
  .cog:focus-visible { outline: 2px solid var(--gold); }
</style>
