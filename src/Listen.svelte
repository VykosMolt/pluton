<script>
  import { onDestroy } from 'svelte'
  import { tokenize, parseCues, cueAt } from './lib/logic.js'
  import Tokens from './Tokens.svelte'
  import WordSheet from './WordSheet.svelte'

  /** onlisten(seconds): wall-clock time spent playing, reported in batches. */
  let { words, forms, cards, statusOf, onlisten } = $props()

  let src = $state(null) // object URL of the chosen media file
  let name = $state('')
  let subsName = $state('')
  let cues = $state.raw([])
  let media = $state()
  let time = $state(0)
  let rate = $state(1)
  let autoPause = $state(false)
  let sel = $state(null)

  const i = $derived(cueAt(cues, time))
  const cue = $derived(cues[i])
  const toks = $derived(cue ? tokenize(cue.text, forms) : [])

  function pickMedia(e) {
    const f = e.currentTarget.files[0]
    if (!f) return
    if (src) URL.revokeObjectURL(src)
    src = URL.createObjectURL(f)
    name = f.name
  }

  async function pickSubs(e) {
    const f = e.currentTarget.files[0]
    if (!f) return
    cues = parseCues(await f.text())
    subsName = `${f.name} · ${cues.length} lines`
  }

  // Listening time: real seconds while playing, so seeking and 1.25× don't inflate it.
  let since = null
  let pending = 0
  function clock() {
    const now = performance.now()
    if (since != null) pending += Math.min(now - since, 2000) / 1000
    const stopped = media?.paused ?? true // the element, not the binding: it may update after this handler
    since = stopped ? null : now
    if (stopped || pending >= 30) flush()
  }
  function flush() {
    if (pending < 1) return // keep sub-second remainders for the next report
    onlisten(pending)
    pending = 0
  }
  onDestroy(() => {
    clock()
    flush()
    if (src) URL.revokeObjectURL(src)
  })

  // Pause at the end of the line being played (intensive listening).
  let armed = -1
  function update() {
    clock()
    const t = media.currentTime // not the bound `time`: it may update after this handler
    const k = cueAt(cues, t)
    const inside = k >= 0 && t < cues[k].end ? k : -1
    if (inside >= 0 && inside !== armed && !media.paused) armed = inside
    if (autoPause && armed >= 0 && t >= cues[armed].end) {
      media.pause()
      armed = -2 // spent; re-arms when the next line starts
    }
  }

  function seek(k) {
    if (!cues[k]) return
    media.currentTime = cues[k].start
    armed = k
    media.play()
  }

  function pick(t) {
    sel = t
    media.pause()
  }
</script>

<h2>Listen</h2>
<p class="muted">
  Play any French video or podcast you have with its French subtitles. Tap a word in the line to look it up or add it.
  Time spent here counts toward your listening hours.
</p>

<div class="pick">
  <label class="btn ghost">🎬 {name || 'Media file'}<input type="file" accept="audio/*,video/*" onchange={pickMedia} hidden /></label>
  <label class="btn ghost">💬 {subsName || 'Subtitles (.srt / .vtt)'}<input type="file" accept=".srt,.vtt" onchange={pickSubs} hidden /></label>
</div>

{#if src}
  <video bind:this={media} {src} controls playsinline preload="metadata" bind:currentTime={time}
    bind:playbackRate={rate} ontimeupdate={update} onplay={clock} onpause={clock} onended={clock}></video>

  <div class="panel line" lang="fr">
    {#if cue}
      <Tokens text={cue.text} {toks} {statusOf} {sel} onpick={pick} />
    {:else}
      <span class="muted">{cues.length ? '…' : 'Load subtitles to see and tap each line.'}</span>
    {/if}
  </div>

  {#if cues.length}
    <div class="controls">
      <button class="btn ghost" onclick={() => seek(i - 1)} disabled={i <= 0} aria-label="Previous line">⏮</button>
      <button class="btn ghost" onclick={() => seek(i)} disabled={i < 0} aria-label="Replay line">↺</button>
      <button class="btn ghost" onclick={() => seek(i + 1)} disabled={i + 1 >= cues.length} aria-label="Next line">⏭</button>
      <select bind:value={rate} aria-label="Speed">
        <option value={0.75}>0.75×</option><option value={1}>1×</option><option value={1.25}>1.25×</option>
      </select>
    </div>
    <label class="auto"><input type="checkbox" bind:checked={autoPause} /> Pause after each line</label>
  {/if}
{/if}

{#if sel && cue}
  <WordSheet tok={sel} sentence={cue.text} {words} {cards} {statusOf} onclose={() => (sel = null)} />
{/if}

<style>
  h2 { margin-bottom: 4px; }
  .pick { display: flex; flex-direction: column; gap: 10px; margin: 14px 0; }
  .pick label { justify-content: flex-start; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  video { width: 100%; max-height: 40vh; border-radius: 16px; background: #000; }
  .line { font-size: 22px; line-height: 1.7; min-height: 92px; margin: 12px 0; }
  .controls { display: flex; gap: 8px; align-items: center; }
  .controls .btn { padding: 10px 16px; font-size: 18px; }
  select { margin-left: auto; padding: 10px; border-radius: 12px; border: 2px solid var(--line); background: var(--panel); }
  .auto { display: block; margin: 12px 0 100px; color: var(--muted); }
</style>
