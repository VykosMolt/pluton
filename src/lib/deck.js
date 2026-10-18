// Card changes made outside runs (library, listening): add a word, mark it known, undo.
import * as db from './db.js'
import { createEmptyCard, State } from './logic.js'
import { activeConfig, chat } from './ai.js'
import { TRANSLATE } from './tutor.js'

async function store(cards, card) {
  await db.put('cards', card)
  cards.set(card.lemma, card)
}

/** Adds a word. Built-in words keep their recorded sentence; anything else is mined with `sentence`, and translated
 *  in the background when an AI is connected (the card works without it). */
export async function addWord(cards, words, tok, sentence) {
  const card = { lemma: tok.lemma, fsrs: createEmptyCard(), added: Date.now() }
  if (!words.get(tok.lemma)?.sentence) card.mined = { sentence: sentence.replace(/’/g, "'"), form: tok.text, translation: '' }
  await store(cards, card)
  const ai = activeConfig()
  if (card.mined && ai)
    chat(ai, { system: TRANSLATE, messages: [{ role: 'user', content: card.mined.sentence }] })
      .then((t) => {
        const now = cards.get(card.lemma)
        if (now?.mined) return store(cards, { ...now, mined: { ...now.mined, translation: t.trim() } })
      })
      .catch(() => {}) // offline or no credit: the card simply has no translation
}

export const markKnown = (cards, lemma) =>
  store(cards, { ...(cards.get(lemma) ?? { lemma, fsrs: createEmptyCard(), added: Date.now() }), known: true })

/** Undoes markKnown: back to learning if it was ever reviewed, otherwise gone. */
export async function unmark(cards, lemma) {
  const card = cards.get(lemma)
  if (card.fsrs.state !== State.New) return store(cards, { ...card, known: false })
  await db.del('cards', lemma)
  cards.delete(lemma)
}
