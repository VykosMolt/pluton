package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContinueTest {
    @Test
    fun continuationCarriesThePreviousPartAndLevel() {
        val prompt = continueSystem(levelOf(500), listOf("chat"), listOf("maison"), "Il était une fois un chat.")
        assertTrue(prompt.contains("Il était une fois un chat."))
        assertTrue(prompt.contains("chat"))
        assertTrue(prompt.contains("maison"))
        assertTrue(prompt.contains("JSON"))
    }

    @Test
    fun longPreviousPartsKeepTheirEnding() {
        val previous = "a".repeat(3000) + " THE_END"
        val prompt = continueSystem(levelOf(500), emptyList(), emptyList(), previous)
        assertTrue(prompt.contains("THE_END"), "the continuation must see the end of a long text")
    }

    @Test
    fun parsesAStoryDraftAndRejectsJunk() {
        val body = "Il était une fois un chat noir. ".repeat(5)
        assertNotNull(parseStory("""{"title": "Le chat", "body": "$body"}"""))
        assertNull(parseStory("not json"))
        assertNull(parseStory("""{"title": "", "body": "$body"}"""))
        assertNull(parseStory("""{"title": "Court", "body": "trop court"}"""))
    }
}
