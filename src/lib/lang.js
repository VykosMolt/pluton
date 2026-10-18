const LANG_PRONOUNS = ['je', 'tu', 'il', 'nous', 'vous', 'ils']

// Everything language-specific on the app side. A second language = another object like this,
// its own data/<code>/build.py, and a way to pick between them (not built yet: one language so far).
export const LANG = {
  code: 'fr',
  speech: 'fr-FR', // browser voice fallback for clips without an mp3
  accents: [...'àâçéèêëîïôùûœ'],
  praise: ['Parfait !', 'Bien joué !', 'Exactement !', 'Bravo !', 'Nickel !', 'Impeccable !', 'Super !'],
  // Elided forms the tokenizer expands: "l'homme" -> le + homme. Keep in sync with data/fr/build.py.
  elision: { l: 'le', d: 'de', j: 'je', m: 'me', t: 'te', s: 'se', n: 'ne', c: 'ce', qu: 'que',
    jusqu: 'jusque', lorsqu: 'lorsque', puisqu: 'puisque', quoiqu: 'quoique' },
  fold: (s) => s.replace(/œ/g, 'oe'), // spelling variants folded before lookup (Lexique spells "coeur")
  pronouns: LANG_PRONOUNS,
  // Conjugation drill order: each tense unlocks once enough tables of the one before are in long-term review.
  tenses: [
    { id: 'ind:pre', name: 'présent', en: 'present',
      how: 'Regular -er verbs: -e, -es, -e, -ons, -ez, -ent. The most common verbs are irregular: learn them as tables.' },
    { id: 'pc', name: 'passé composé', en: 'past: did, have done',
      how: 'avoir or être in the present + past participle. Verbs of movement and change of state (aller, venir, partir, mourir…) take être, and then the participle agrees: elle est allée, ils sont venus.' },
    { id: 'ind:imp', name: 'imparfait', en: 'past: was doing, used to',
      how: 'Stem of the present nous form (nous parlons → parl-) + -ais, -ais, -ait, -ions, -iez, -aient. Only être is irregular: ét-.' },
    { id: 'ind:fut', name: 'futur', en: 'future: will',
      how: 'Infinitive (drop a final -e) + -ai, -as, -a, -ons, -ez, -ont. Irregular stems: ir- (aller), ser- (être), aur- (avoir), fer- (faire), pourr-, voudr-, viendr-, verr-, devr-, saur-.' },
    { id: 'cnd:pre', name: 'conditionnel', en: 'would',
      how: 'Future stem + imparfait endings: -ais, -ais, -ait, -ions, -iez, -aient. (je pourrais, nous irions)' },
    { id: 'sub:pre', name: 'subjonctif', en: 'subjunctive: that … (after il faut que, vouloir que, bien que…)',
      how: 'je, tu, il, ils: stem of the present ils form (ils prennent → prenn-) + -e, -es, -e, -ent. nous, vous: same as the imparfait. Irregular: sois, aie, aille, fasse, puisse, sache, veuille.' },
  ],
  /** What precedes the verb in a table row: "que je", "qu'il", "nous". */
  subject: (i, tense) => (tense === 'sub:pre' ? (i === 2 || i === 5 ? "qu'" : 'que ') : '') + LANG_PRONOUNS[i],
}

export const DATA = `data/${LANG.code}/`
