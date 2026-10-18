<script>
  import { tick } from 'svelte'
  import { LANG } from './lib/lang.js'

  /** Accent buttons that type into `target` (an input or textarea) without stealing its focus. */
  let { target, value = $bindable() } = $props()

  async function insert(ch) {
    const s = target.selectionStart ?? value.length, e = target.selectionEnd ?? s
    value = value.slice(0, s) + ch + value.slice(e)
    await tick()
    target.focus()
    target.setSelectionRange(s + 1, s + 1)
  }
</script>

<div class="accents">
  {#each LANG.accents as ch}
    <button type="button" onpointerdown={(e) => e.preventDefault()} onclick={() => insert(ch)}>{ch}</button>
  {/each}
</div>

<style>
  .accents { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
  button {
    min-width: 40px; padding: 8px; border-radius: 10px; border: 2px solid var(--line); background: var(--panel);
    font-size: 18px; cursor: pointer;
  }
</style>
