// Le Chemin: ten stages from A1 to C1, framed as Pluton's journey from Poe's wall to the Paris Opéra.
// The path suggests and celebrates; it never locks anything. French-specific, like tutor.js.
//
// Targets: words learned follow ~20 new words a day; reading = each stage's chapter of Pluton's story (written to fit
// that stage's vocabulary), plus a classic from B2 on, in the order measured coverage makes them readable (data/fr);
// listening minutes are minimums, C1 takes far more real listening than any app can hold.

const TENSE = { 'ind:pre': 'présent', pc: 'passé composé', 'ind:imp': 'imparfait', 'ind:fut': 'futur', 'cnd:pre': 'conditionnel', 'sub:pre': 'subjonctif' }

export const STAGES = [
  { place: 'Le Mur', grammar: 0, en: 'The Wall', level: 'A1', words: 75, verbs: null, read: ['story-0'], listen: 3,
    fr: 'Il fait noir. Pluton se réveille derrière un mur.', say: 'Pitch dark. Pluton wakes up behind a wall.' },
  { place: 'La Cave', grammar: 0, en: 'The Cellar', level: 'A1', words: 200, verbs: ['ind:pre', 5], read: ['story-1'], listen: 10,
    fr: 'Une cave froide, une porte fermée… et une petite fenêtre.', say: 'A cold cellar, a locked door… and a small window.' },
  { place: 'La Maison', grammar: 1, en: 'The House', level: 'A2', words: 400, verbs: ['ind:pre', 12], read: ['story-2'], listen: 20,
    talk: 5, write: 1, fr: 'Une vieille maison, du lait, et une petite fille qui n\'a pas peur.', say: 'An old house, some milk, and a girl who isn\'t afraid.' },
  { place: 'Le Village', grammar: 4, en: 'The Village', level: 'A2', words: 700, verbs: ['pc', 8], read: ['story-3'], listen: 40,
    talk: 10, write: 2, fr: 'Le marché du village. Un chat noir, ça porte malheur ?', say: 'Market day. Are black cats really bad luck?' },
  { place: 'La Route', grammar: 6, en: 'The Road', level: 'B1', words: 1100, verbs: ['ind:imp', 8], read: ['story-4'], listen: 75,
    talk: 20, write: 3, fr: 'La route de Paris est longue. Heureusement, il y a Brutus.', say: 'The road to Paris is long. Luckily there\'s Brutus.' },
  { place: 'Le Fleuve', grammar: 8, en: 'The River', level: 'B1', words: 1600, verbs: ['ind:fut', 8], read: ['story-5'], listen: 120,
    talk: 30, write: 5, fr: 'Remonter la Seine à bord d\'un vieux bateau.', say: 'Up the Seine on an old boat.' },
  { place: 'Paris', grammar: 10, en: 'Paris', level: 'B2', words: 2300, verbs: ['cnd:pre', 8], read: ['story-6', 'Les Étoiles'], listen: 240,
    talk: 50, write: 8, fr: 'Les toits de Montmartre, et une chatte qui connaît les règles.', say: 'The roofs of Montmartre, and a cat who knows the rules.' },
  { place: 'Les Catacombes', grammar: 12, en: 'The Catacombs', level: 'B2', words: 3200, verbs: ['sub:pre', 8], read: ['story-7', 'La Parure'],
    listen: 420, talk: 80, write: 12, fr: 'Sous Paris, une ville de morts. Il ne fallait pas descendre.', say: 'Under Paris, a city of the dead. He shouldn\'t have gone down.' },
  { place: 'La Bibliothèque', grammar: 13, en: 'The Library', level: 'C1', words: 4500, verbs: [null, 150],
    read: ['story-8', 'Le Tour du monde en quatre-vingts jours, chapitre 1'], listen: 720, talk: 120, write: 16,
    fr: 'Les notes de Baudelaire, et une lettre sans signature.', say: 'Baudelaire\'s notes, and an unsigned letter.' },
  { place: 'L\'Opéra', grammar: 15, en: 'The Opéra', level: 'C1', words: 6000, verbs: [null, 300], read: ['story-9', 'La Chèvre de monsieur Seguin'],
    listen: 1200, talk: 200, write: 20, fr: 'Un bal masqué, un lac sous la scène, et quelqu\'un qui attend.', say: 'A masked ball, a lake under the stage, and someone waiting.' },
]

/**
 * Goals of a stage measured against `m`: { learned, tables: {tense: n}, tablesTotal, grammar (topics in review),
 * read: Set of text ids, listenMin, talked, written }, with `titles` mapping text ids to titles. Bonus goals (★) never block a stage.
 */
export function goalsOf(stage, m, titles = {}) {
  const goals = [{ kind: 'words', label: `Learn ${stage.words.toLocaleString('en')} words`, have: m.learned, need: stage.words }]
  if (stage.verbs) {
    const [tense, n] = stage.verbs
    goals.push({ kind: 'verbs', label: tense ? `${n} verb tables in the ${TENSE[tense]}` : `${n} verb tables in all`,
      have: tense ? m.tables[tense] ?? 0 : m.tablesTotal, need: n })
  }
  if (stage.grammar) goals.push({ kind: 'grammar', label: `${stage.grammar} grammar topic${stage.grammar > 1 ? 's' : ''} in review`,
    have: m.grammar ?? 0, need: stage.grammar })
  for (const id of stage.read) goals.push({ kind: 'read', id, label: `Read “${titles[id] ?? id}”`, have: m.read.has(id) ? 1 : 0, need: 1 })
  goals.push({ kind: 'listen', label: `Listen for ${stage.listen} minutes in all`, have: Math.floor(m.listenMin), need: stage.listen })
  if (stage.talk) goals.push({ kind: 'talk', bonus: true, label: `Send Pluton ${stage.talk} messages`, have: m.talked, need: stage.talk })
  if (stage.write) goals.push({ kind: 'write', bonus: true, label: `Have ${stage.write} texts corrected`, have: m.written, need: stage.write })
  return goals.map((g) => ({ ...g, done: g.have >= g.need, pct: Math.min(1, g.have / g.need) }))
}

/** Every stage with its goals; `current` = index of the first stage whose core goals aren't all met (10 when done). */
export function pathState(m, titles) {
  const stages = STAGES.map((s, i) => {
    const goals = goalsOf(s, m, titles)
    const core = goals.filter((g) => !g.bonus)
    return { ...s, index: i, goals, complete: core.every((g) => g.done), mastered: goals.every((g) => g.done),
      pct: core.reduce((a, g) => a + g.pct, 0) / core.length }
  })
  const current = stages.findIndex((s) => !s.complete)
  return { stages, current: current < 0 ? stages.length : current }
}

/**
 * The one thing to do next besides the daily run: the current stage's first unmet reading goal, else listening,
 * else bonus practice when an AI is connected. Words and verbs advance through runs, so they need no separate task.
 */
export function focus(stage, hasAI) {
  if (!stage) return null
  const open = stage.goals.filter((g) => !g.done)
  return open.find((g) => g.kind === 'read') ?? open.find((g) => g.kind === 'listen') ?? open.find((g) => g.kind === 'grammar')
    ?? (hasAI ? open.find((g) => g.bonus) : null) ?? null
}
