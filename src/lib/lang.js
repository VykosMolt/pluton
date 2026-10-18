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
}

export const DATA = `data/${LANG.code}/`
