<script>
  import { onMount, onDestroy } from 'svelte'
  import { isWord, gradeDictee } from './lib/logic.js'
  import { LANG } from './lib/lang.js'
  import { say, stop } from './lib/sound.js'

  /** Read-aloud practice: record, compare with Pluton, and where the browser can listen, get word-level feedback. */
  let { words, cards, statusOf, oncount } = $props()

  const Recognition = globalThis.SpeechRecognition || globalThis.webkitSpeechRecognition
  const canRecord = !!(navigator.mediaDevices?.getUserMedia && globalThis.MediaRecorder)

  let queue = $state([])
  let i = $state(0)
  let showEn = $state(false)
  let recording = $state(false)
  let listening = $state(false)
  let url = $state(null) // the last recording
  let error = $state('')
  let transcript = $state('')
  let check = $state(null)
  let player = $state()
  let recorder = null
  let stream = null
  let timer = null
  let recog = null
  let gen = 0 // invalidates late recognition callbacks after Next / unmount

  const item = $derived(queue[i])
  const sentence = $derived(item?.sentence ?? '')

  /** Known/learning sentences first; a new learner still gets deck sentences. Shuffled so "de" isn't always first. */
  function pick() {
    const learned = [...cards.values()]
      .filter((c) => isWord(c) && statusOf(c.lemma) !== 'new')
      .map((c) => words.get(c.lemma))
      .filter((w) => w?.sentence)
    const pool = (learned.length ? learned : [...words.values()].filter((w) => w.sentence).slice(0, 60))
      .map((w) => ({ sentence: w.sentence, translation: w.translation ?? '', audio: w.audio_sentence ?? null }))
    for (let k = pool.length - 1; k > 0; k--) {
      const j = Math.floor(Math.random() * (k + 1))
      ;[pool[k], pool[j]] = [pool[j], pool[k]]
    }
    queue = pool.slice(0, 12)
    i = 0
  }
  onMount(pick)

  const clearClip = () => { if (url) URL.revokeObjectURL(url); url = null }
  const resetCheck = () => { check = null; transcript = '' }

  async function record() {
    if (recording) return stopRecord()
    error = ''
    clearClip()
    resetCheck()
    const me = ++gen
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      if (me !== gen) { stream.getTracks().forEach((t) => t.stop()); stream = null; return } // moved on while asking
      const media = stream // this recording's stream, not whatever `stream` points at later
      const chunks = []
      recorder = new MediaRecorder(media)
      recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data)
      recorder.onstop = () => {
        media.getTracks().forEach((t) => t.stop())
        if (stream === media) stream = null
        if (me !== gen) return // moved on to another sentence: drop the late clip
        recording = false
        clearTimeout(timer)
        if (chunks.length) url = URL.createObjectURL(new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }))
        if (url) oncount?.('spoken')
      }
      recorder.start()
      recording = true
      timer = setTimeout(stopRecord, 15000) // don't leave the mic on forever
    } catch (e) {
      recording = false
      stream?.getTracks().forEach((t) => t.stop())
      stream = null
      error = e.name === 'NotAllowedError'
        ? 'Microphone access was denied. Allow it in the browser or system settings.'
        : `Could not start the microphone (${e.message}).`
    }
  }
  function stopRecord() {
    clearTimeout(timer)
    try { recorder?.stop() } catch {}
  }

  function hear() {
    if (item) say([item.audio, sentence])
  }
  async function playMine() {
    if (!player) return
    player.currentTime = 0
    await player.play().catch(() => {})
  }
  async function hearBoth() {
    if (!url) return hear()
    stop()
    player.currentTime = 0
    const played = await player.play().then(() => true, () => false)
    if (!played) return hear()
    await new Promise((resolve) => { player.onended = resolve })
    hear()
  }

  /** The browser's speech recognition is the free ASR path; it is absent in Firefox/Zen and native WebViews. */
  function listen() {
    if (!Recognition || listening) return
    stop()
    resetCheck()
    error = ''
    const target = sentence // a new sentence while recognition runs must not be graded against this transcript
    const me = ++gen
    const r = new Recognition()
    recog = r
    r.lang = LANG.speech
    r.interimResults = false
    r.maxAlternatives = 1
    r.onresult = (e) => { if (me === gen) transcript = [...e.results].map((x) => x[0].transcript).join(' ').trim() }
    r.onerror = (e) => { if (me === gen && e.error !== 'no-speech') error = `Could not listen (${e.error}).` }
    r.onend = () => {
      if (me !== gen) return
      listening = false
      recog = null
      if (transcript) {
        check = gradeDictee(transcript, target)
        oncount?.('spoken')
      }
    }
    listening = true
    try { r.start() } catch (e) { listening = false; error = e.message }
  }

  function next() {
    gen++
    if (recording) {
      clearTimeout(timer)
      try { recorder?.stop() } catch {}
      recording = false
    }
    try { recog?.abort() } catch {}
    recog = null
    listening = false
    stop()
    clearClip()
    resetCheck()
    error = ''
    showEn = false
    if (!queue.length) return
    i = (i + 1) % queue.length
    if (i === 0) pick()
  }

  onDestroy(() => {
    gen++
    clearTimeout(timer)
    try { if (recorder?.state !== 'inactive') recorder?.stop() } catch {}
    stream?.getTracks().forEach((t) => t.stop())
    try { recog?.abort() } catch {}
    stop()
    clearClip()
  })
</script>

<h2>Speak</h2>
<p class="muted">Read the sentence aloud. Record yourself and compare with Pluton; where your browser can listen, it also shows which words it heard.</p>

{#if !sentence}
  <p class="muted">Your deck needs a few words with example sentences first.</p>
{:else}
  <section class="panel">
    <div class="row">
      <span class="tag">{i + 1} / {queue.length}</span>
      {#if item.translation}<button class="link" onclick={() => (showEn = !showEn)}>{showEn ? 'Hide English' : 'Show English'}</button>{/if}
    </div>
    <p class="sentence" lang="fr">{sentence}</p>
    {#if showEn && item.translation}<p class="muted">{item.translation}</p>{/if}

    <div class="row tools">
      <button class="btn ghost" onclick={hear}>🔊 Hear it</button>
      {#if recording}
        <button class="btn rec" onclick={stopRecord}>⏹ Stop</button>
      {:else}
        <button class="btn" onclick={record} disabled={!canRecord || listening}>🎤 Record</button>
      {/if}
      <button class="btn ghost" onclick={playMine} disabled={!url}>▶ Yours</button>
      {#if url}<button class="btn ghost" onclick={hearBoth}>🔁 Hear both</button>{/if}
    </div>
    {#if !canRecord}<p class="muted small">This browser cannot record audio. You can still listen, and use the speech check if it is available.</p>{/if}
    {#if Recognition}
      <button class="btn ghost wide" onclick={listen} disabled={listening || recording}>
        {listening ? 'Listening…' : '👂 Check what I said'}
      </button>
    {:else}
      <p class="muted small">Speech recognition is not available in this browser; record and compare by ear.</p>
    {/if}

    {#if url}
      <audio bind:this={player} src={url} hidden></audio>
    {/if}
    {#if error}<p class="err">{error}</p>{/if}

    {#if check}
      <div class="result {check.result}">
        <p><b>{check.result === 'right' ? 'Tout est là !' : check.result === 'almost' ? 'Almost — a word or an accent slipped.' : 'Pluton heard something different.'}</b></p>
        <p class="muted">Heard: “{transcript}”</p>
        <p class="sentence" lang="fr">
          {#each check.marks as m, k}{sentence.slice(k ? check.marks[k - 1].end : 0, m.start)}<span class="dm {m.mark}">{sentence.slice(m.start, m.end)}</span>{/each}{sentence.slice(check.marks.at(-1)?.end ?? 0)}
        </p>
      </div>
    {/if}

    <button class="btn ghost next" onclick={next}>Next sentence →</button>
  </section>
{/if}

<style>
  h2 { margin-bottom: 4px; }
  .panel { margin-top: 14px; }
  .row { display: flex; gap: 10px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
  .tag { font-size: 13px; font-weight: 800; color: var(--muted); }
  .link { border: 0; background: none; color: var(--blue); text-decoration: underline; cursor: pointer; font-size: 14px; padding: 0; }
  .sentence { font-size: 24px; font-weight: 600; line-height: 1.45; margin: 14px 0; }
  .tools { justify-content: flex-start; }
  .rec { background: var(--red); box-shadow: 0 5px 0 color-mix(in srgb, var(--red) 60%, #000); animation: pulse 0.8s infinite alternate; }
  .wide { width: 100%; margin-top: 12px; }
  .small { font-size: 13px; }
  .err { color: var(--red); font-weight: 700; }
  .result { margin-top: 14px; padding: 12px; border-radius: 12px; background: color-mix(in srgb, var(--line) 45%, transparent); }
  .result.right { background: color-mix(in srgb, var(--green) 14%, transparent); }
  .result.almost { background: color-mix(in srgb, var(--orange) 16%, transparent); }
  .result.wrong { background: color-mix(in srgb, var(--red) 12%, transparent); }
  .result p { margin: 4px 0; }
  .dm.bad { background: color-mix(in srgb, var(--red) 28%, transparent); border-radius: 5px; }
  .dm.accent { background: color-mix(in srgb, var(--orange) 30%, transparent); border-radius: 5px; }
  .next { margin-top: 16px; }
  @keyframes pulse { to { opacity: 0.6; } }
</style>
