package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ContextTest {
    @Test
    fun minedAndDictionaryContextsAlternateWithTheirOwnForms() {
        val word = Word("chat", form = "chat", sentence = "Le chat dort.")
        val mined = Mined("Les chats dorment.", "chats")
        val even = CardState("chat", createEmptyCard(1).copy(reps = 0), mined = mined)
        val odd = even.copy(fsrs = even.fsrs.copy(reps = 1))
        assertEquals("Les chats dorment.", contextSentence(even, word))
        assertEquals("chats", contextForm(even, word))
        assertEquals("Le chat dort.", contextSentence(odd, word))
        assertEquals("chat", contextForm(odd, word))
    }

    @Test
    fun everyContextAndFormPairCanBeBlanked() {
        val word = Word("manger", form = "mange", sentence = "Il mange une pomme.")
        val mined = Mined("Je mangeais une pomme.", "mangeais")
        for (reps in 0..3) {
            val card = CardState("manger", createEmptyCard(1).copy(reps = reps), mined = mined)
            val sentence = contextSentence(card, word)
            val form = contextForm(card, word)
            assertEquals(form, blank(sentence, form).second, "reps=$reps: $sentence / $form")
        }
    }

    @Test
    fun aCardWithoutAMinedSentenceUsesTheDictionaryPair() {
        val word = Word("chat", form = "chat", sentence = "Le chat dort.")
        val card = CardState("chat", createEmptyCard(1))
        assertEquals("Le chat dort.", contextSentence(card, word))
        assertEquals("chat", contextForm(card, word))
    }
}
