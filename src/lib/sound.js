import { DATA, LANG } from './lang.js'

// Speech clips and sound effects through one AudioContext: once unlocked by a tap, iOS lets it play freely.
let ctx
let token = 0
let source

export function unlock() {
  ctx ??= new AudioContext()
  if (ctx.state === 'suspended') ctx.resume()
}

export function stop() {
  token++
  try { source?.stop() } catch {}
  globalThis.speechSynthesis?.cancel()
}

/** Plays [audioId, text] clips in order: the mp3 from public/data/<lang>/audio, else the browser's voice.
 *  Resolves when finished; a later say()/stop() cuts it off. */
export async function say(...clips) {
  stop()
  const me = token
  for (const [id, text] of clips) if (me === token) await play(id, text, me)
}

async function play(audioId, text, me) {
  try {
    if (!audioId) throw new Error('no clip')
    unlock()
    const res = await fetch(`${DATA}audio/${audioId}.mp3`)
    if (!res.ok) throw new Error(res.status)
    const buffer = await ctx.decodeAudioData(await res.arrayBuffer())
    if (me !== token) return
    source = ctx.createBufferSource()
    source.buffer = buffer
    source.connect(ctx.destination)
    await new Promise((resolve) => { source.onended = resolve; source.start() })
  } catch {
    if (me !== token || !text || !globalThis.speechSynthesis) return
    const u = new SpeechSynthesisUtterance(text)
    u.lang = LANG.speech
    await new Promise((resolve) => { u.onend = u.onerror = resolve; speechSynthesis.speak(u) })
  }
}

function tone(freq, at, dur, type = 'sine', gain = 0.12) {
  const o = ctx.createOscillator(), g = ctx.createGain()
  o.type = type
  o.frequency.value = freq
  g.gain.setValueAtTime(gain, at)
  g.gain.exponentialRampToValueAtTime(0.001, at + dur)
  o.connect(g).connect(ctx.destination)
  o.start(at)
  o.stop(at + dur)
}

export function sfx(kind) {
  unlock()
  const t = ctx.currentTime
  if (kind === 'right') { tone(660, t, 0.09); tone(990, t + 0.07, 0.14) }
  if (kind === 'almost') tone(520, t, 0.14, 'triangle')
  if (kind === 'wrong') tone(140, t, 0.28, 'sawtooth', 0.06)
  if (kind === 'combo') [523, 659, 784, 1047].forEach((f, i) => tone(f, t + i * 0.06, 0.12, 'square', 0.05))
  if (kind === 'done') [392, 523, 659, 784, 1047].forEach((f, i) => tone(f, t + i * 0.09, 0.25, 'triangle', 0.08))
}
