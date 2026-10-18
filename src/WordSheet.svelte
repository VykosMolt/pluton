<script>
  import { addWord, markKnown, unmark } from './lib/deck.js'
  import { say } from './lib/sound.js'

  /** tok: the tapped token; sentence: where it was found (used if the word has no built-in sentence). */
  let { tok, sentence, words, cards, statusOf, onclose } = $props()

  const w = $derived(words.get(tok.lemma))
  const st = $derived(statusOf(tok.lemma))
</script>

<div class="panel sheet">
  <button class="close" onclick={onclose} aria-label="Close">✕</button>
  <h3 class={w?.gender}>{w?.display ?? tok.lemma}</h3>
  <p class="muted">{w?.pos ?? 'not in the dictionary'}{tok.text.toLowerCase() !== tok.lemma ? ` · from “${tok.text}”` : ''}</p>
  <p>{w?.gloss || 'No gloss for this one. The sentence will have to teach it.'}</p>
  <div class="row">
    <button class="btn ghost" onclick={() => say([w?.audio_word, w?.display ?? tok.lemma])} aria-label="Play audio">🔊</button>
    {#if st === 'new'}
      <button class="btn" onclick={() => addWord(cards, words, tok, sentence)}>+ Add to deck</button>
      <button class="btn ghost" onclick={() => markKnown(cards, tok.lemma)}>I know it</button>
    {:else if cards.get(tok.lemma)?.known}
      <span class="muted">Marked known</span><button class="btn ghost" onclick={() => unmark(cards, tok.lemma)}>Undo</button>
    {:else if st === 'queued'}
      <span class="muted">Queued for your next runs ✓</span>
    {:else if st === 'learning'}
      <span class="muted">In your deck ✓</span>
    {:else}
      <span class="muted">Known ✓</span>
    {/if}
  </div>
</div>

<style>
  .sheet {
    position: fixed; left: 50%; transform: translateX(-50%); bottom: 76px; width: min(528px, calc(100% - 32px));
    box-shadow: 0 10px 40px var(--shadow); animation: up 0.2s ease-out; z-index: 5;
  }
  h3 { font-size: 28px; padding-right: 30px; }
  p { margin: 6px 0 12px; }
  .row { display: flex; gap: 10px; align-items: center; justify-content: space-between; flex-wrap: wrap; }
  .close { position: absolute; top: 10px; right: 12px; border: 0; background: none; font-size: 18px; cursor: pointer; color: var(--muted); }
  @keyframes up { from { transform: translate(-50%, 20px); opacity: 0; } }
</style>
