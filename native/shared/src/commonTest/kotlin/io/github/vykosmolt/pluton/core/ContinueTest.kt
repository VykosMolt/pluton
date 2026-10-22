package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertTrue

class ContinueTest {
    @Test
    fun continuationCarriesThePreviousPartAndLevel() {
        val prompt = continueSystem(levelOf(500), listOf("chat"), listOf("maison"), "Il était une fois un chat.")
        assertTrue(prompt.contains("Il était une fois un chat."))
        assertTrue(prompt.contains("chat"))
        assertTrue(prompt.contains("JSON"))
    }
}
