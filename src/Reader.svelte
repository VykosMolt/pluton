<script>
  import { onMount } from 'svelte'
  import * as db from './lib/db.js'
  import { tokenize, coverage, unlockList, fromSubtitles, sentenceAt, createEmptyCard, State, UNLOCK } from './lib/logic.js'
  import { say } from './lib/sound.js'

  let { words, forms, cards, statusOf, texts = $bindable() } = $props()

  // Original starter text (not from any book), so the unlock mechanic has something to show on day one.
  const SAMPLE = {
    title: 'Un matin à Paris',
    body: `Ce matin, Léa prend un café avec son ami Hugo. Il fait beau et la rue est pleine de monde.
« Tu as bien dormi ? » demande Hugo.
« Pas vraiment, je pense trop à demain. »
Demain, c'est le grand jour : Léa commence un nouveau travail. Elle a un peu peur, mais elle est contente.
Hugo sourit. « Tu vas être parfaite. »
Ils finissent leur café et marchent ensemble jusqu'au métro.`,
  }

  let openId = $state(null)
  let draft = $state(null) // { title, body } while adding a text
  let sel = $state(null) // tapped token

  const open = $derived(texts.find((t) => t.id === openId))
  const library = $derived(texts.map((t) => {
    const toks = tokenize(t.body, forms)
    return { t, ...coverage(toks, statusOf), need: unlockList(toks, statusOf).length }
  }))
  const toks = $derived(open ? tokenize(open.body, forms) : [])
  const cov = $derived(coverage(toks, statusOf))
  const need = $derived(unlockList(toks, statusOf))
  const addable = $derived(need.filter((l) => statusOf(l) === 'new'))
  const segs = $derived.by(() => {
    const out = []
    let at = 0
    toks.forEach((t, i) => {
      if (t.start > at) out.push({ text: open.body.slice(at, t.start) })
      out.push({ text: open.body.slice(t.start, t.end), i })
      at = t.end
    })
    if (open) out.push({ text: open.body.slice(at) })
    return out
  })

  onMount(async () => {
    if (!texts.length && !(await db.get('meta', 'seeded'))) {
      await save(SAMPLE)
      await db.put('meta', true, 'seeded')
    }
  })

  async function save(t) {
    const body = t.body.normalize('NFC').trim()
    if (!body) return
    const rec = { title: t.title.trim() || body.slice(0, 40), body, added: Date.now() }
    rec.id = await db.put('texts', rec)
    texts = [...texts, rec]
    draft = null
  }

  async function loadFile(e) {
    const f = e.currentTarget.files[0]
    if (!f) return
    const s = await f.text()
    draft.body = /\.(srt|vtt)$/i.test(f.name) ? fromSubtitles(s) : s
    draft.title ||= f.name.replace(/\.\w+$/, '')
  }

  async function remove() {
    if (!confirm(`Delete “${open.title}”?`)) return
    await db.del('texts', open.id)
    texts = texts.filter((t) => t.id !== open.id)
    openId = null
  }

  async function store(card) {
    await db.put('cards', card)
    cards.set(card.lemma, card)
  }

  function add(tok) {
    const card = { lemma: tok.lemma, fsrs: createEmptyCard(), added: Date.now() }
    // Built-in words keep their recorded sentence; anything else is mined with this text's sentence.
    if (!words.get(tok.lemma)?.sentence)
      card.mined = { sentence: sentenceAt(open.body, tok.start).replace(/’/g, "'"), form: tok.text, translation: '' }
    return store(card)
  }

  async function addAll() {
    for (const lemma of addable) await add(toks.find((t) => t.lemma === lemma))
  }

  const markKnown = () => store({ ...(cards.get(sel.lemma) ?? { lemma: sel.lemma, fsrs: createEmptyCard(), added: Date.now() }), known: true })

  async function unmark() {
    const card = cards.get(sel.lemma)
    if (card.fsrs.state !== State.New) return store({ ...card, known: false })
    await db.del('cards', card.lemma)
    cards.delete(card.lemma)
  }

  const pct = (x) => `${Math.round(x * 100)}%`
  // French puts a space before ? ! : ; » and after «; keep it from breaking the line (display only).
  const nbsp = (s) => s.replace(/ ([?!:;»])/g, '\u00a0$1').replace(/« /g, '«\u00a0')
</script>

{#if draft}
  <h2>Add a text</h2>
  <p class="muted">Paste lyrics, an article, a chapter, or load subtitles (.srt / .vtt).</p>
  <input class="field" placeholder="Title" bind:value={draft.title} />
  <textarea class="field" rows="10" lang="fr" placeholder="Colle ton texte ici…" bind:value={draft.body}></textarea>
  <label class="btn ghost file">📄 Load file<input type="file" accept=".txt,.srt,.vtt,text/plain" onchange={loadFile} hidden /></label>
  <div class="row">
    <button class="btn ghost" onclick={() => (draft = null)}>Cancel</button>
    <button class="btn" onclick={() => save(draft)} disabled={!draft.body.trim()}>Save</button>
  </div>
{:else if open}
  <div class="row head">
    <button class="btn ghost" onclick={() => { openId = null; sel = null }}>← Library</button>
    <button class="btn ghost" onclick={remove} aria-label="Delete text">🗑</button>
  </div>
  <h2>{open.title}</h2>
  <div class="stack" title="known · learning · new">
    <i class="k" style="flex:{cov.known}"></i><i class="l" style="flex:{cov.learning}"></i><i class="q" style="flex:{cov.queued}"></i><i class="n" style="flex:{cov.new}"></i>
  </div>
  <p class="muted">
    {pct(cov.pct)} understood ·
    {#if cov.pct >= UNLOCK}<b class="ready">🔓 Unlocked, enjoy it!</b>{:else}🔒 learn {need.length} words to unlock{/if}
    <br /><small>Tap a word: <span class="w new">new</span> <span class="w queued">queued</span> <span class="w learning">learning</span> known</small>
  </p>
  {#if addable.length}
    <button class="btn queue" onclick={addAll}>🎯 Queue the {addable.length} words that unlock this</button>
  {/if}
  <div class="panel text" lang="fr">
    {#each segs as s}{#if s.i === undefined}{nbsp(s.text)}{:else if toks[s.i].name}{s.text}{:else}<span role="button" tabindex="0"
          class="w {statusOf(toks[s.i].lemma)}" class:sel={sel === toks[s.i]} onclick={() => (sel = toks[s.i])}
          onkeydown={(e) => e.key === 'Enter' && (sel = toks[s.i])}>{s.text}</span>{/if}{/each}
  </div>
{:else}
  <div class="row head">
    <h2>Library</h2>
    <button class="btn" onclick={() => (draft = { title: '', body: '' })}>+ Add text</button>
  </div>
  <p class="muted">Every text unlocks at {pct(UNLOCK)}: that's where reading stops being decoding and starts being fun.</p>
  {#each library as x (x.t.id)}
    <button class="panel item" onclick={() => (openId = x.t.id)}>
      <span class="row"><b>{x.t.title}</b>{#if x.pct >= UNLOCK}<span class="ready">🔓 Ready</span>{:else}<span class="muted">🔒 {x.need} to go</span>{/if}</span>
      <span class="bar"><i class:full={x.pct >= UNLOCK} style="width:{Math.min(100, (100 * x.pct) / UNLOCK)}%"></i></span>
      <span class="muted">{pct(x.pct)} understood · {x.total} words</span>
    </button>
  {:else}
    <p class="muted">Nothing here yet.</p>
  {/each}
{/if}

{#if sel && open}
  {@const w = words.get(sel.lemma)}
  {@const st = statusOf(sel.lemma)}
  <div class="panel sheet">
    <button class="close" onclick={() => (sel = null)} aria-label="Close">✕</button>
    <h3 class={w?.gender}>{w?.display ?? sel.lemma}</h3>
    <p class="muted">{w?.pos ?? 'not in the dictionary'}{sel.text.toLowerCase() !== sel.lemma ? ` · from “${sel.text}”` : ''}</p>
    <p>{w?.gloss || 'No gloss for this one. The sentence will have to teach it.'}</p>
    <div class="row">
      <button class="btn ghost" onclick={() => say([w?.audio_word, w?.display ?? sel.lemma])} aria-label="Play audio">🔊</button>
      {#if st === 'new'}
        <button class="btn" onclick={() => add(sel)}>+ Add to deck</button>
        <button class="btn ghost" onclick={markKnown}>I know it</button>
      {:else if cards.get(sel.lemma)?.known}
        <span class="muted">Marked known</span><button class="btn ghost" onclick={unmark}>Undo</button>
      {:else if st === 'queued'}
        <span class="muted">Queued for your next runs ✓</span>
      {:else if st === 'learning'}
        <span class="muted">In your deck ✓</span>
      {:else}
        <span class="muted">Known ✓</span>
      {/if}
    </div>
  </div>
{/if}

<style>
  h2 { margin-bottom: 8px; }
  .row { display: flex; gap: 10px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
  .head { margin-bottom: 10px; }
  .field { display: block; width: 100%; margin: 10px 0; padding: 12px 14px; border-radius: 14px; border: 2px solid var(--line); background: var(--panel); }
  .field:focus { outline: none; border-color: var(--blue); }
  textarea.field { resize: vertical; }
  .file { margin-bottom: 14px; }
  .item { width: 100%; display: flex; flex-direction: column; gap: 6px; text-align: left; cursor: pointer; margin-bottom: 12px; }
  .bar { display: block; height: 10px; border-radius: 99px; background: var(--line); overflow: hidden; }
  .bar i { display: block; height: 100%; background: linear-gradient(90deg, var(--gold), var(--orange)); border-radius: 99px; }
  .bar i.full { background: var(--green); }
  .ready { color: var(--green); font-weight: 800; }
  .stack { display: flex; height: 12px; border-radius: 99px; overflow: hidden; background: var(--line); margin: 10px 0 6px; }
  .stack .k { background: var(--green); }
  .stack .l { background: var(--gold); }
  .stack .q { background: var(--blue); }
  .stack .n { background: var(--red); }
  .text { font-size: 20px; line-height: 1.75; white-space: pre-wrap; margin-bottom: 220px; }
  .w { cursor: pointer; border-radius: 5px; }
  .w:focus-visible { outline: 2px solid var(--gold); }
  .w.new { background: color-mix(in srgb, var(--red) 16%, transparent); text-decoration: underline dotted var(--red); text-underline-offset: 4px; }
  .w.learning { background: color-mix(in srgb, var(--gold) 26%, transparent); }
  .w.queued { background: color-mix(in srgb, var(--blue) 14%, transparent); text-decoration: underline dashed var(--blue); text-underline-offset: 4px; }
  .queue { width: 100%; margin-bottom: 14px; }
  .w.sel { outline: 2px solid var(--blue); }
  small .w { padding: 0 4px; cursor: default; }
  .sheet {
    position: fixed; left: 50%; transform: translateX(-50%); bottom: 76px; width: min(528px, calc(100% - 32px));
    box-shadow: 0 10px 40px var(--shadow); animation: up 0.2s ease-out; z-index: 5;
  }
  .sheet h3 { font-size: 28px; padding-right: 30px; }
  .sheet p { margin: 6px 0 12px; }
  .close { position: absolute; top: 10px; right: 12px; border: 0; background: none; font-size: 18px; cursor: pointer; color: var(--muted); }
  @keyframes up { from { transform: translate(-50%, 20px); opacity: 0; } }
</style>
