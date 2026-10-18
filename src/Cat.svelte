<script>
  // Pluton: Poe's black cat, named as in Baudelaire's French translation "Le Chat noir".
  /** @type {{ mood?: 'idle' | 'happy' | 'sad' | 'fire' | 'sleep', size?: number }} */
  let { mood = 'idle', size = 96 } = $props()
</script>

<svg class="cat {mood}" width={size} height={(size * 128) / 120} viewBox="0 0 120 128" role="img" aria-label="Pluton the cat, {mood}">
  <g class="tail">
    <path class="rim" d="M84 117 C110 113 114 84 100 72" />
    <path d="M84 117 C110 113 114 84 100 72" />
  </g>
  <path class="fur" d="M32 122 C28 94 38 72 60 70 C82 72 92 94 88 122 Z" />
  <path class="patch" d="M60 84 C55 84 54 90 60 96 C66 90 65 84 60 84 Z" />
  <ellipse class="fur" cx="46" cy="122" rx="10" ry="5" />
  <ellipse class="fur" cx="74" cy="122" rx="10" ry="5" />
  <g class="ear l"><path class="fur" d="M36 40 L31 12 L54 28 Z" /><path class="inner" d="M38 33 L35 19 L48 28 Z" /></g>
  <g class="ear r"><path class="fur" d="M84 40 L89 12 L66 28 Z" /><path class="inner" d="M82 33 L85 19 L72 28 Z" /></g>
  <ellipse class="fur" cx="60" cy="50" rx="31" ry="26" />
  {#if mood === 'happy'}
    <path class="arc" d="M41 52 Q48 43 55 52" /><path class="arc" d="M65 52 Q72 43 79 52" />
  {:else if mood === 'sleep'}
    <path class="arc" d="M41 50 Q48 56 55 50" /><path class="arc" d="M65 50 Q72 56 79 50" />
    <text class="zz" x="92" y="24">z</text><text class="zz two" x="100" y="14">z</text>
  {:else}
    <g class="eyes">
      <ellipse class="iris" cx="48" cy="50" rx="7.5" ry="8.5" />
      <ellipse class="iris" cx="72" cy="50" rx="7.5" ry="8.5" />
      <ellipse class="pupil" cx="48" cy="50" rx={mood === 'sad' ? 3.6 : 2.2} ry="6.8" />
      <ellipse class="pupil" cx="72" cy="50" rx={mood === 'sad' ? 3.6 : 2.2} ry="6.8" />
      <circle class="shine" cx="50.5" cy="46" r="1.8" /><circle class="shine" cx="74.5" cy="46" r="1.8" />
    </g>
    {#if mood === 'sad'}
      <path class="lid" d="M38 37 L58 37 L58 43 L38 48 Z" /><path class="lid" d="M62 37 L82 37 L82 48 L62 43 Z" />
    {/if}
  {/if}
  <path class="nose" d="M56.5 60 L63.5 60 L60 64 Z" />
  <path class="mouth" d={mood === 'sad' ? 'M54 69 Q60 64.5 66 69' : 'M60 64 Q57 68 53.5 66 M60 64 Q63 68 66.5 66'} />
  <path class="whiskers" d="M42 61 L19 57 M42 65 L19 66 M78 61 L101 57 M78 65 L101 66" />
</svg>

<style>
  .cat { display: block; overflow: visible; }
  .fur, .lid { fill: var(--cat); stroke: var(--cat-rim); stroke-width: 2; stroke-linejoin: round; }
  .lid { stroke: none; }
  .tail path { fill: none; stroke: var(--cat); stroke-width: 8; stroke-linecap: round; }
  .tail .rim { stroke: var(--cat-rim); stroke-width: 12; }
  .tail { transform-origin: 84px 117px; animation: swish 3s ease-in-out infinite alternate; }
  .fire .tail { animation-duration: 0.35s; }
  .patch { fill: #f3efe6; }
  .inner { fill: #4b3550; }
  .iris { fill: #f5c542; transition: fill 0.2s; }
  .fire .iris { fill: #ff7a1a; }
  .fire .eyes { filter: drop-shadow(0 0 3px #ff7a1a); }
  .pupil { fill: #0d0a12; }
  .shine { fill: #fff; }
  .arc { fill: none; stroke: #f5c542; stroke-width: 3.2; stroke-linecap: round; }
  .nose { fill: #e58fa8; }
  .mouth { fill: none; stroke: #e58fa8; stroke-width: 1.6; stroke-linecap: round; }
  .whiskers { stroke: #9c95b5; stroke-width: 1; }
  .eyes { transform-origin: 60px 50px; animation: blink 5s infinite; }
  .ear { transition: transform 0.25s; }
  .ear.l { transform-origin: 44px 36px; }
  .ear.r { transform-origin: 76px 36px; }
  .sad .ear.l { transform: rotate(-24deg); }
  .sad .ear.r { transform: rotate(24deg); }
  .happy { animation: hop 0.45s ease-out; }
  .sad { animation: droop 0.4s ease-out; }
  .zz { fill: var(--muted); font: 700 14px system-ui; animation: zz 2.4s infinite; }
  .zz.two { font-size: 11px; animation-delay: 1.2s; }
  @keyframes swish { from { transform: rotate(-7deg); } to { transform: rotate(9deg); } }
  @keyframes blink { 0%, 93%, 100% { transform: scaleY(1); } 95% { transform: scaleY(0.1); } }
  @keyframes hop { 40% { transform: translateY(-12px); } 70% { transform: translateY(2px); } }
  @keyframes droop { 50% { transform: translateY(4px) rotate(-3deg); } }
  @keyframes zz { from { opacity: 0; transform: translateY(4px); } 50% { opacity: 1; } to { opacity: 0; transform: translateY(-8px); } }
</style>
