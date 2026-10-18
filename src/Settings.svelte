<script>
  import * as db from './lib/db.js'
  import { PROVIDERS, listModels, chat, ready } from './lib/ai.js'

  /** ai: the saved AI config (or null); pace: {perRun, perDay}. Both saved here. */
  let { ai = $bindable(), pace = $bindable(), onbackup, onrestore } = $props()

  const blank = (provider) => ({ provider, key: '', model: PROVIDERS[provider].model ?? '', base: '' })
  let draft = $state(ai ? { ...ai } : blank('anthropic'))
  let models = $state([])
  let note = $state('')
  let busy = $state(false)
  const p = $derived(PROVIDERS[draft.provider])
  const origin = location.origin

  function pick(provider) {
    draft = provider === ai?.provider ? { ...ai } : blank(provider)
    models = []
    note = ''
  }

  async function load() {
    busy = true
    note = ''
    try {
      models = await listModels(draft)
      if (!draft.model || !models.includes(draft.model)) draft.model = models.includes(p.model) ? p.model : models[0] ?? ''
      if (!models.length) note = 'No models returned. Type a model name instead.'
    } catch (e) {
      note = `⚠️ ${e.message}`
    }
    busy = false
  }

  async function test() {
    busy = true
    note = '…'
    let out = ''
    try {
      await chat(draft, { system: 'Reply in French in one short, friendly sentence.', messages: [{ role: 'user', content: 'Dis bonjour !' }],
        onText: (d) => (note = out += d) })
      await save()
      note = `✓ ${out}`
    } catch (e) {
      note = `⚠️ ${e.message}`
    }
    busy = false
  }

  async function save() {
    const plain = $state.snapshot(draft) // `ai` reads back as a reactive proxy, which IndexedDB can't store
    ai = plain
    await db.put('meta', plain, 'ai')
  }

  async function disconnect() {
    ai = null
    await db.del('meta', 'ai')
    draft = blank(draft.provider)
    note = 'Disconnected. The key is deleted from this device.'
  }

  async function setPace(k, v) {
    pace = { ...pace, [k]: v }
    await db.put('meta', $state.snapshot(pace), 'settings')
  }
</script>

<h2>Settings</h2>

<section class="panel">
  <h3>🐈‍⬛ AI tutor</h3>
  <p class="muted">
    Talking and writing practice use an AI model with <b>your own API key</b>. Chat subscriptions (Claude Pro,
    ChatGPT Plus…) can't be connected to other apps; an API key is billed per use, usually cents per session.
    The key stays on this device, goes only to the provider you pick, and is never included in backups.
  </p>
  <label class="field-label">Provider
    <select class="field" value={draft.provider} onchange={(e) => pick(e.currentTarget.value)}>
      {#each Object.entries(PROVIDERS) as [id, x]}<option value={id}>{x.name}</option>{/each}
    </select>
  </label>
  {#if !p.noKey}
    <label class="field-label">API key
      <input class="field" type="password" autocomplete="off" spellcheck="false" bind:value={draft.key} placeholder="paste your key" />
    </label>
    {#if p.keys}<p class="small"><a href={p.keys} target="_blank" rel="noopener">Get a key ↗</a></p>{/if}
  {/if}
  {#if draft.provider === 'ollama' || draft.provider === 'custom'}
    <label class="field-label">Server URL
      <input class="field" bind:value={draft.base} placeholder={p.base || 'https://…/v1'} spellcheck="false" />
    </label>
  {:else}
    <details class="adv">
      <summary>Advanced</summary>
      <label class="field-label">Server URL (proxy or gateway)
        <input class="field" bind:value={draft.base} placeholder={p.base} spellcheck="false" />
      </label>
    </details>
  {/if}
  {#if draft.provider === 'ollama'}
    <p class="small muted">Start Ollama so this page may call it: <code>OLLAMA_ORIGINS={origin} ollama serve</code></p>
  {/if}
  <label class="field-label">Model
    <span class="row">
      {#if models.length}
        <select class="field" bind:value={draft.model}>{#each models as m}<option value={m}>{m}</option>{/each}</select>
      {:else}
        <input class="field" bind:value={draft.model} placeholder="model name" spellcheck="false" />
      {/if}
      <button class="btn ghost" onclick={load} disabled={busy}>Load list</button>
    </span>
  </label>
  {#if draft.provider === 'anthropic'}
    <p class="small muted">Default: Claude Opus 5.5. Claude Haiku 5.5 costs far less per message if you chat a lot.</p>
  {/if}
  <div class="row">
    <button class="btn" onclick={test} disabled={busy || !ready(draft)}>Test & save</button>
    {#if ai}<button class="btn ghost" onclick={disconnect}>Disconnect</button>{/if}
  </div>
  {#if note}<p class="note" class:bad={note.startsWith('⚠️')}>{note}</p>{/if}
  {#if ai}<p class="small muted">Connected: {PROVIDERS[ai.provider]?.name} · {ai.model}</p>{/if}
</section>

<section class="panel">
  <h3>⏱️ Pace</h3>
  <label class="field-label">New words per run
    <select class="field" value={pace.perRun} onchange={(e) => setPace('perRun', +e.currentTarget.value)}>
      {#each [3, 5, 8, 10] as n}<option value={n}>{n}</option>{/each}
    </select>
  </label>
  <label class="field-label">New words per day
    <select class="field" value={pace.perDay} onchange={(e) => setPace('perDay', +e.currentTarget.value)}>
      {#each [10, 15, 20, 30, 40] as n}<option value={n}>{n}{n === 20 ? ' (recommended)' : ''}</option>{/each}
    </select>
  </label>
  <p class="small muted">Every new word comes back for reviews, so a higher pace means more reviews in the weeks after.</p>
</section>

<section class="panel">
  <h3>💾 Your data</h3>
  <p class="muted">Progress lives on this device. Back it up to move it to another one.</p>
  <div class="row">
    <button class="btn ghost" onclick={onbackup}>Download backup</button>
    <label class="btn ghost">Restore…<input type="file" accept="application/json" onchange={onrestore} hidden /></label>
  </div>
</section>

<section class="panel about">
  <h3>📜 About</h3>
  <p>Pluton is the black cat from Edgar Allan Poe's “The Black Cat” (1843), named as in Baudelaire's French translation, “Le Chat noir”.</p>
  <p class="small">
    Word frequencies, lemmas and verb forms: Lexique 3.83 (New &amp; Pallier), CC BY-SA 4.0. Example sentences: Tatoeba
    contributors, CC BY 2.0 FR. Definitions: English Wiktionary via kaikki.org, CC BY-SA 4.0. Voice: Piper TTS, SIWIS
    dataset (CC BY 4.0). Classics: public-domain texts via Wikisource. Scheduling: FSRS via ts-fsrs.
  </p>
</section>

<style>
  h2 { margin-bottom: 12px; }
  section { margin-bottom: 14px; }
  h3 { margin-bottom: 8px; }
  .field-label { display: block; font-weight: 700; font-size: 14px; margin: 12px 0 0; }
  .field {
    display: block; width: 100%; margin-top: 4px; padding: 11px 12px; border-radius: 12px; border: 2px solid var(--line);
    background: var(--panel); font-weight: 400; font-size: 16px;
  }
  .field:focus { outline: none; border-color: var(--blue); }
  .row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 12px; }
  .row .field { flex: 1; min-width: 0; margin: 0; }
  .field-label .row { margin-top: 4px; flex-wrap: nowrap; }
  .small { font-size: 13px; margin: 6px 0 0; }
  .small a { color: var(--blue); }
  code { font-size: 12px; background: var(--line); padding: 2px 5px; border-radius: 6px; word-break: break-all; }
  .note { margin: 10px 0 0; font-weight: 600; color: var(--green); }
  .note.bad { color: var(--red); }
  .about p { margin: 6px 0; }
  .adv { margin-top: 10px; font-size: 14px; }
  .adv summary { cursor: pointer; color: var(--muted); }
</style>
