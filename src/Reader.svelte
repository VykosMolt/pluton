<script>
  import { onMount } from 'svelte'
  import * as db from './lib/db.js'
  import { tokenize, coverage, unlockList, fromSubtitles, sentenceAt, UNLOCK } from './lib/logic.js'
  import { addWord } from './lib/deck.js'
  import Tokens from './Tokens.svelte'
  import WordSheet from './WordSheet.svelte'

  let { words, forms, cards, statusOf, texts = $bindable(), shelf = [] } = $props()

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

  const open = $derived(texts.find((t) => t.id === openId) ?? shelf.find((t) => t.id === openId))
  const rate = (t) => {
    const toks = tokenize(t.body, forms)
    return { t, ...coverage(toks, statusOf), need: unlockList(toks, statusOf).length }
  }
  const library = $derived(texts.map(rate))
  const classics = $derived(shelf.map(rate).sort((a, b) => b.pct - a.pct)) // closest to unlocking first
  const toks = $derived(open ? tokenize(open.body, forms) : [])
  const cov = $derived(coverage(toks, statusOf))
  const need = $derived(unlockList(toks, statusOf))
  const addable = $derived(need.filter((l) => statusOf(l) === 'new'))

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

  async function addAll() {
    for (const lemma of addable) {
      const tok = toks.find((t) => t.lemma === lemma)
      await addWord(cards, words, tok, sentenceAt(open.body, tok.start))
    }
  }

  const pct = (x) => `${Math.round(x * 100)}%`
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
    {#if !open.author}<button class="btn ghost" onclick={remove} aria-label="Delete text">🗑</button>{/if}
  </div>
  <h2>{open.title}</h2>
  {#if open.author}<p class="byline">{open.author}, {open.year} · <a href={open.source} target="_blank" rel="noopener">source</a></p>{/if}
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
    <Tokens text={open.body} {toks} {statusOf} {sel} onpick={(t) => (sel = t)} />
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

  {#if classics.length}
    <h3 class="shelf">📜 Classics shelf</h3>
    <p class="muted">Real French literature, public domain. Closest to unlocking first.</p>
    {#each classics as x (x.t.id)}
      <button class="panel item" onclick={() => (openId = x.t.id)}>
        <span class="row"><b>{x.t.title}</b>{#if x.pct >= UNLOCK}<span class="ready">🔓 Ready</span>{:else}<span class="muted">🔒 {x.need} to go</span>{/if}</span>
        <span class="muted">{x.t.author}, {x.t.year}</span>
        <span class="bar"><i class:full={x.pct >= UNLOCK} style="width:{Math.min(100, (100 * x.pct) / UNLOCK)}%"></i></span>
        <span class="muted">{pct(x.pct)} understood · {x.total} words</span>
      </button>
    {/each}
  {/if}
{/if}

{#if sel && open}
  <WordSheet tok={sel} sentence={sentenceAt(open.body, sel.start)} {words} {cards} {statusOf} onclose={() => (sel = null)} />
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
  .shelf { margin: 22px 0 4px; }
  .byline { margin: 2px 0 0; color: var(--muted); font-style: italic; }
  .byline a { color: var(--blue); }
  .stack { display: flex; height: 12px; border-radius: 99px; overflow: hidden; background: var(--line); margin: 10px 0 6px; }
  .stack .k { background: var(--green); }
  .stack .l { background: var(--gold); }
  .stack .q { background: var(--blue); }
  .stack .n { background: var(--red); }
  .text { font-size: 20px; line-height: 1.75; white-space: pre-wrap; margin-bottom: 220px; }
  .queue { width: 100%; margin-bottom: 14px; }
  small .w { padding: 0 4px; cursor: default; }
</style>
