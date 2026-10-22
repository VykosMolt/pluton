package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanTest {
    @Test
    fun noHistoryKeepsTheLearnersPace() {
        assertEquals(5, suggestedNewWords(5, null))
        assertEquals(10, suggestedNewWords(10, null))
    }

    @Test
    fun accuracyMovesThePaceWithinBounds() {
        assertEquals(3, suggestedNewWords(5, 0.70))
        assertEquals(4, suggestedNewWords(5, 0.80))
        assertEquals(5, suggestedNewWords(5, 0.90))
        assertEquals(6, suggestedNewWords(5, 0.93))
        assertEquals(7, suggestedNewWords(5, 0.97))
        assertEquals(3, suggestedNewWords(3, 0.60))
        assertEquals(10, suggestedNewWords(10, 0.99))
    }
}
