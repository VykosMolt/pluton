package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrammarFocusTest {
    @Test
    fun aLearningGrammarTopicBecomesTheFocus() {
        val topic = GrammarTopic("objets", "A2", "les pronoms objets", "object pronouns")
        val card = CardState(gramKey("objets"), createEmptyCard(1).copy(state = State.Learning))
        assertEquals("the grammar topic “les pronoms objets” (object pronouns)", grammarFocus(listOf(card), listOf(topic)))
    }

    @Test
    fun aLearningConjugationTableBecomesTheFocus() {
        val table = ConjTable(t = mapOf("ind:pre" to listOf("suis", "es", "est", "sommes", "êtes", "sont")))
        val card = CardState(conjKey("être", "ind:pre"), createEmptyCard(1).copy(state = State.Learning))
        assertEquals("the présent forms of “être”", grammarFocus(listOf(card), emptyList(), mapOf("être" to table)))
    }

    @Test
    fun nothingIsFocusedWithoutLearningMaterialOrEnoughVocabulary() {
        val topic = GrammarTopic("objets", "A2", "les pronoms objets", "object pronouns")
        assertNull(grammarFocus(emptyList(), emptyList()))
        assertNull(grammarFocus(emptyList(), listOf(topic)))
        val graduated = CardState(gramKey("objets"), createEmptyCard(1).copy(state = State.Review))
        assertNull(grammarFocus(listOf(graduated), listOf(topic)))
        val fresh = CardState(gramKey("objets"), createEmptyCard(1).copy(state = State.New))
        assertEquals("the grammar topic “les pronoms objets” (object pronouns)", grammarFocus(listOf(fresh), listOf(topic)))
    }

    @Test
    fun promptsMentionTheFocusAndStayUnchangedWithoutIt() {
        val level = levelOf(500)
        val focus = "the présent forms of “être”"
        assertTrue(chatSystem(level, emptyList(), focus).contains("Learner focus"))
        assertTrue(writeSystem(level, "Raconte ta journée", focus).contains("particular attention"))
        assertTrue(writeSystem(level, "Raconte ta journée", focus).contains("JSON"))
        assertTrue(!chatSystem(level, emptyList(), null).contains("Learner focus"))
        assertTrue(!writeSystem(level, "Raconte ta journée", null).contains("particular attention"))
    }
}
