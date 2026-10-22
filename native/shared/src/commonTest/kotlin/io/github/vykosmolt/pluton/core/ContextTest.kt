package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ContextTest {
    @Test
    fun minedAndDictionaryContextsAlternateWithTheirOwnForms() {
        val word = Word("chat", form = "chat", sentence = "Le chat dort.")
        val mined = Mined("Un chat noir.", "chats")
        val even = CardState("chat", createEmptyCard(1).copy(reps = 0), mined = mined)
        val odd = even.copy(fsrs = even.fsrs.copy(reps = 1))
        assertEquals("Un chat noir.", contextSentence(even, word))
        assertEquals("chats", contextForm(even, word))
        assertEquals("Le chat dort.", contextSentence(odd, word))
        assertEquals("chat", contextForm(odd, word))
    }
}
