<script>
  import { onMount, tick } from 'svelte'
  import * as db from './lib/db.js'
  import { chat, ready, parseJSON, AIError, PROVIDERS } from './lib/ai.js'
  import { chatSystem, writeSystem, splitCorrection, levelOf, PROMPTS } from './lib/tutor.js'
  import { tokenize, wordDiff, sentenceAt, isWord, fixCard, firstChange } from './lib/logic.js'
  import { LANG } from './lib/lang.js'
  import Cat from './Cat.svelte'
  import Speak from './Speak.svelte'
  import Tokens from './Tokens.svelte'
  import WordSheet from './WordSheet.svelte'
  import AccentBar from './AccentBar.svelte'

  /** vocab: words known or being learned (sets Pluton's level); oncount('talked'|'written') feeds the Road to C1. */
  let { ai, words, forms, cards, statusOf, vocab, onsettings, oncount } = $props()

  const Recognition = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition
  const ORDER = ['A1', 'A2', 'B1', 'B2', 'C1']

  let mode = $state('chat')
  let log = $state([]) // [{role, content}]
  let writings = $state([])
  let draft = $state('')
  let busy = $state(false)
  let error = $state('')
  let sel = $state(null) // { tok, sentence }
  let box = $state()
  let list = $state()
  let speak = $state(false)
  let listening = $state(false)
  let prompt = $state(null)
  let own = $state('')
  let essay = $state('')
  let result = $state(null)
  let essayBox = $state()
  let controller = null

  const level = $derived(levelOf(vocab))
  const suggested = $derived(PROMPTS.filter(([l]) => Math.abs(ORDER.indexOf(l) - ORDER.indexOf(level.code)) <= 1))

  onMount(async () => {
    log = (await db.get('meta', 'talk')) ?? []
    writings = (await db.get('meta', 'writings')) ?? []
    scroll()
  })

  async function scroll() {
    await tick()
    list?.lastElementChild?.scrollIntoView({ block: 'end', behavior: 'smooth' })
  }

  // The words the learner is working on, most recently reviewed first: Pluton recycles some of them.
  const learning = () => [...cards.values()]
    .filter((c) => isWord(c) && statusOf(c.lemma) === 'learning')
    .sort((a, b) => (b.fsrs.last_review ?? 0) - (a.fsrs.last_review ?? 0)).slice(0, 40).map((c) => c.lemma)

  function say(text) {
    if (!globalThis.speechSynthesis) return
    speechSynthesis.cancel()
    const u = new SpeechSynthesisUtterance(text)
    u.lang = LANG.speech
    speechSynthesis.speak(u)
  }

  async function send() {
    const text = draft.trim()
    if (!text || busy) return
    draft = ''
    error = ''
    busy = true
    log = [...log, { role: 'user', content: text }, { role: 'assistant', content: '' }]
    const i = log.length - 1
    scroll()
    controller = new AbortController()
    try {
      const history = $state.snapshot(log).slice(0, -1).slice(-20)
      while (history[0]?.role === 'assistant') history.shift() // the conversation must open with the learner
      const out = await chat(ai, { system: chatSystem(level, learning()), messages: history, signal: controller.signal,
        onText: (d) => { log[i].content += d; scroll() } })
      log[i].content = out
      oncount('talked')
      if (speak) say(splitCorrection(out).body)
    } catch (e) {
      log = log.slice(0, i - 1) // drop the exchange, give the text back
      draft = text
      if (e.name !== 'AbortError') error = e.message
    }
    busy = false
    await db.put('meta', $state.snapshot(log).slice(-40), 'talk')
  }

  function keydown(e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault()
      send()
    }
  }

  function listen() {
    const r = new Recognition()
    r.lang = LANG.speech
    r.interimResults = true
    r.onresult = (e) => (draft = [...e.results].map((x) => x[0].transcript).join(' '))
    r.onend = r.onerror = () => (listening = false)
    listening = true
    r.start()
  }

  async function clear() {
    if (!confirm('Start a new conversation?')) return
    controller?.abort()
    log = []
    await db.put('meta', [], 'talk')
  }

  async function correct() {
    const topic = prompt?.[1] ?? own.trim()
    if (!essay.trim() || !topic || busy) return
    busy = true
    error = ''
    result = null
    try {
      const r = parseJSON(await chat(ai, { system: writeSystem(level, topic), messages: [{ role: 'user', content: essay }], effort: 'medium' }))
      if (typeof r.corrected !== 'string' || !Array.isArray(r.errors)) throw new AIError('The model returned an unexpected format. Try again.')
      result = { ...r, original: essay, topic }
      writings = [{ at: Date.now(), topic, text: essay, result: r }, ...writings].slice(0, 50)
      await db.put('meta', $state.snapshot(writings), 'writings')
      oncount('written')
    } catch (e) {
      error = e.message
    }
    busy = false
  }

  function again() {
    result = null
    essay = ''
    prompt = null
    own = ''
  }

  const pick = (t, sentence) => (sel = { tok: t, sentence })

  // Corrections become review cards: the corrected sentence, with what was fixed to type back.
  let practised = $state(new Set())
  async function practise(id, card) {
    if (!card || practised.has(id)) return
    await db.put('cards', card)
    cards.set(card.lemma, card)
    practised = new Set([...practised, id])
  }
  const fromError = (e) => {
    const i = result.corrected.indexOf(e.fix)
    return i < 0 ? null : fixCard(sentenceAt(result.corrected, i), e.fix, e.original, e.why)
  }
  const fromChat = (k, r) => {
    const change = log[k - 1]?.role === 'user' ? firstChange(log[k - 1].content, r.fix) : null
    return change && fixCard(r.fix, change.answer, change.wrong, r.why)
  }
</script>

<div class="head">
  <h2>Talk</h2>
  <div class="seg" role="tablist">
    <button role="tab" aria-selected={mode === 'chat'} class:on={mode === 'chat'} onclick={() => (mode = 'chat')}>💬 Conversation</button>
    <button role="tab" aria-selected={mode === 'write'} class:on={mode === 'write'} onclick={() => (mode = 'write')}>✍️ Writing</button>
    <button role="tab" aria-selected={mode === 'speak'} class:on={mode === 'speak'} onclick={() => (mode = 'speak')}>🗣 Speaking</button>
  </div>
</div>

{#if mode === 'speak'}
  <Speak {words} {cards} {statusOf} {oncount} />
{:else if !ready(ai)}
  <section class="panel connect">
    <Cat mood="idle" size={80} />
    <div>
      <p><b>Pluton can talk with you and correct your writing</b>, using an AI model with your own key: Claude, ChatGPT models,
        DeepSeek, anything on OpenRouter, or a free local model with Ollama.</p>
      <div class="row">
        <button class="btn" onclick={onsettings}>Connect an AI</button>
        <button class="btn ghost" onclick={() => (mode = 'speak')}>Speaking needs no key</button>
      </div>
    </div>
  </section>
{:else if mode === 'chat'}
  <p class="muted sub">Pluton speaks at your level ({level.code}, ~{vocab} words) and corrects you as you go. Tap any word to look it up.</p>
  <div class="log" bind:this={list}>
    {#if !log.length}
      <div class="msg pluton"><Cat mood="happy" size={40} /><div class="bubble">Bonjour ! Moi, c'est Pluton. On parle de quoi aujourd'hui ? 🐾</div></div>
    {/if}
    {#each log as m, k (k)}
      {#if m.role === 'user'}
        <div class="msg me"><div class="bubble">{m.content}</div></div>
      {:else}
        {@const r = splitCorrection(m.content)}
        <div class="msg pluton">
          <Cat mood={r.fix ? 'idle' : 'happy'} size={40} />
          <div class="bubble" lang="fr">
            {#if r.fix}
              {@const card = fromChat(k, r)}
              <p class="fix">✏️ {r.fix}{#if r.why}<small>{r.why}</small>{/if}
                {#if card}<button class="practise" disabled={practised.has(`chat-${k}`)} onclick={() => practise(`chat-${k}`, card)}>
                  {practised.has(`chat-${k}`) ? '✓ in your runs' : '+ Practise this'}</button>{/if}</p>
            {/if}
            {#if r.body}
              <Tokens text={r.body} toks={tokenize(r.body, forms)} {statusOf} sel={sel?.tok} onpick={(t) => pick(t, sentenceAt(r.body, t.start))} />
              <button class="speak" onclick={() => say(r.body)} aria-label="Read aloud">🔊</button>
            {:else}<span class="typing">…</span>{/if}
          </div>
        </div>
      {/if}
    {/each}
  </div>
  {#if error}<p class="err">{error}</p>{/if}
  <div class="compose">
    <textarea bind:this={box} bind:value={draft} onkeydown={keydown} rows="2" lang="fr" placeholder="Écris en français… (or in English)"
      aria-label="Your message"></textarea>
    <div class="actions">
      {#if Recognition}
        <button class="btn ghost" class:live={listening} onclick={listen} disabled={listening} aria-label="Speak">🎤</button>
      {/if}
      <button class="btn" onclick={send} disabled={busy || !draft.trim()}>Send</button>
    </div>
  </div>
  <AccentBar target={box} bind:value={draft} />
  <div class="row small">
    <label><input type="checkbox" bind:checked={speak} /> Read Pluton's replies aloud</label>
    {#if log.length}<button class="link" onclick={clear}>New conversation</button>{/if}
  </div>
{:else}
  {#if result}
    <section class="panel result">
      <div class="row"><b>{result.topic}</b>{#if result.level}<span class="badge">{result.level}</span>{/if}</div>
      <p class="diff" lang="fr">
        {#each wordDiff(result.original, result.corrected) as d}<span class={d.op}>{d.text}</span>{' '}{/each}
      </p>
      {#if result.errors.length}
        <ul class="errors">
          {#each result.errors as e}
            {@const card = fromError(e)}
            <li><span class="kind">{e.kind}</span> <s>{e.original}</s> → <b>{e.fix}</b><br /><small>{e.why}</small>
              {#if card}<button class="practise" disabled={practised.has(`err-${e.fix}`)} onclick={() => practise(`err-${e.fix}`, card)}>
                {practised.has(`err-${e.fix}`) ? '✓ in your runs' : '+ Practise this'}</button>{/if}</li>
          {/each}
        </ul>
      {:else}
        <p class="ok">No mistakes. Parfait ! 🎉</p>
      {/if}
      <p class="feedback">{result.feedback}</p>
      <button class="btn" onclick={again}>Write another</button>
    </section>
  {:else}
    <p class="muted sub">Pick a topic (these suit {level.code}), write a few sentences, and Pluton's teacher marks them.</p>
    <div class="topics">
      {#each suggested as t}
        <button class="topic" class:on={prompt === t} onclick={() => (prompt = prompt === t ? null : t)}>
          <span class="badge">{t[0]}</span> <span lang="fr">{t[1]}</span><small>{t[2]}</small>
        </button>
      {/each}
    </div>
    {#if !prompt}<input class="field" bind:value={own} placeholder="…or your own topic" />{/if}
    <textarea class="field essay" bind:this={essayBox} bind:value={essay} rows="8" lang="fr" placeholder="Écris ici…" aria-label="Your text"></textarea>
    <AccentBar target={essayBox} bind:value={essay} />
    <div class="row">
      <span class="muted small">{essay.trim() ? essay.trim().split(/\s+/).length : 0} words</span>
      <button class="btn" onclick={correct} disabled={busy || !essay.trim() || !(prompt || own.trim())}>{busy ? 'Correcting…' : 'Correct it'}</button>
    </div>
    {#if error}<p class="err">{error}</p>{/if}
    {#if writings.length}
      <h3 class="past">Your writing</h3>
      {#each writings.slice(0, 8) as w (w.at)}
        <button class="panel past-item" onclick={() => (result = { ...w.result, original: w.text, topic: w.topic })}>
          <span class="row"><b>{w.topic}</b>{#if w.result.level}<span class="badge">{w.result.level}</span>{/if}</span>
          <small class="muted">{new Date(w.at).toLocaleDateString()} · {w.result.errors.length} corrections</small>
        </button>
      {/each}
    {/if}
  {/if}
{/if}

{#if sel}
  <WordSheet tok={sel.tok} sentence={sel.sentence} {words} {cards} {statusOf} onclose={() => (sel = null)} />
{/if}

<style>
  .head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
  .sub { margin: 6px 0 12px; font-size: 14px; }
  .seg { display: flex; gap: 6px; }
  .seg button { border: 2px solid var(--line); background: var(--panel); border-radius: 10px; padding: 6px 10px; cursor: pointer; font-weight: 700; font-size: 14px; color: var(--muted); }
  .seg button.on { border-color: var(--blue); color: var(--blue); }
  .connect { display: flex; gap: 14px; align-items: center; margin-top: 14px; }
  .connect p { margin: 0 0 12px; }
  .log { display: flex; flex-direction: column; gap: 10px; }
  .msg { display: flex; gap: 8px; align-items: flex-end; }
  .msg.me { justify-content: flex-end; }
  .bubble { position: relative; max-width: 82%; padding: 10px 14px; border-radius: 18px; font-size: 18px; line-height: 1.5; }
  .pluton .bubble { background: var(--panel); border: 2px solid var(--line); border-bottom-left-radius: 4px; padding-right: 40px; }
  .me .bubble { background: var(--btn); color: #fff; border-bottom-right-radius: 4px; white-space: pre-wrap; }
  .fix { margin: 0 0 6px; padding: 6px 8px; border-radius: 10px; font-size: 16px; background: color-mix(in srgb, var(--green) 14%, transparent); }
  .fix small { display: block; color: var(--muted); font-size: 13px; }
  .speak { position: absolute; right: 8px; bottom: 8px; border: 0; background: none; cursor: pointer; font-size: 16px; }
  .typing { color: var(--muted); }
  .err { color: var(--red); font-weight: 600; }
  .compose { display: flex; gap: 8px; align-items: flex-end; margin-top: 14px; }
  .compose textarea { flex: 1; min-width: 0; resize: none; padding: 12px 14px; font-size: 17px; border-radius: 16px; border: 2px solid var(--line); background: var(--panel); }
  .compose textarea:focus, .field:focus { outline: none; border-color: var(--blue); }
  .actions { display: flex; flex-direction: column; gap: 6px; }
  .actions .btn { padding: 10px 14px; }
  .live { animation: pulse 0.8s infinite alternate; }
  .row { display: flex; gap: 10px; justify-content: space-between; align-items: center; flex-wrap: wrap; }
  .small { font-size: 14px; }
  .link { border: 0; background: none; color: var(--blue); text-decoration: underline; cursor: pointer; font-size: 14px; }
  .topics { display: flex; flex-direction: column; gap: 8px; }
  .topic { text-align: left; border: 2px solid var(--line); background: var(--panel); border-radius: 14px; padding: 10px 12px; cursor: pointer; }
  .topic.on { border-color: var(--blue); background: color-mix(in srgb, var(--blue) 10%, var(--panel)); }
  .topic small { display: block; color: var(--muted); }
  .badge { display: inline-block; font-size: 12px; font-weight: 800; padding: 1px 7px; border-radius: 99px; background: color-mix(in srgb, var(--gold) 30%, transparent); }
  .field { display: block; width: 100%; margin: 10px 0 0; padding: 12px 14px; border-radius: 14px; border: 2px solid var(--line); background: var(--panel); font-size: 17px; }
  .essay { resize: vertical; line-height: 1.5; }
  .result { margin-top: 12px; }
  .diff { font-size: 18px; line-height: 1.7; }
  .diff .del { text-decoration: line-through; color: var(--red); }
  .diff .add { color: var(--green); font-weight: 700; }
  .errors { padding-left: 18px; }
  .errors li { margin-bottom: 8px; }
  .practise { display: block; margin-top: 4px; border: 0; background: none; padding: 0; color: var(--blue); font-weight: 700; font-size: 13px; cursor: pointer; text-decoration: underline; }
  .practise:disabled { color: var(--green); text-decoration: none; cursor: default; }
  .kind { font-size: 11px; font-weight: 800; text-transform: uppercase; color: var(--muted); }
  .ok { color: var(--green); font-weight: 700; }
  .feedback { font-style: italic; }
  .past { margin: 22px 0 8px; }
  .past-item { width: 100%; text-align: left; margin-bottom: 8px; cursor: pointer; display: flex; flex-direction: column; gap: 2px; }
  @keyframes pulse { to { opacity: 0.5; } }
</style>
