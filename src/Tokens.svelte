<script>
  /** `text` with each token from `toks` tappable and coloured by status (global .w.* classes). */
  let { text, toks, statusOf, sel = null, onpick } = $props()

  const segs = $derived.by(() => {
    const out = []
    let at = 0
    toks.forEach((t, i) => {
      if (t.start > at) out.push({ text: text.slice(at, t.start) })
      out.push({ text: text.slice(t.start, t.end), i })
      at = t.end
    })
    out.push({ text: text.slice(at) })
    return out
  })
  // French puts a space before ? ! : ; » and after «; keep it from breaking the line (display only).
  const nbsp = (s) => s.replace(/ ([?!:;»])/g, '\u00a0$1').replace(/« /g, '«\u00a0')
</script>

{#each segs as s}{#if s.i === undefined}{nbsp(s.text)}{:else if toks[s.i].name}{s.text}{:else}<span role="button" tabindex="0"
      class="w {statusOf(toks[s.i].lemma)}" class:sel={sel === toks[s.i]} onclick={() => onpick(toks[s.i])}
      onkeydown={(e) => e.key === 'Enter' && onpick(toks[s.i])}>{s.text}</span>{/if}{/each}
