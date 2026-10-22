package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressTest {
    @Test
    fun cefrIsAWideEstimate() {
        assertEquals("A1", cefrEstimate(0))
        assertEquals("A1–A2", cefrEstimate(500))
        assertEquals("A2–B1", cefrEstimate(1500))
        assertEquals("B1–B2", cefrEstimate(3000))
        assertEquals("B2–C1", cefrEstimate(5000))
    }

    @Test
    fun reportCountsWordsAndMilestones() {
        val known = CardState("chat", createEmptyCard(1).copy(state = State.Review), known = true)
        val learning = CardState("chien", createEmptyCard(1).copy(state = State.Learning))
        val chunk = CardState("chunk:il y a", createEmptyCard(1).copy(state = State.Review), known = true)
        val report = progressReport(
            cards = listOf(known, learning, chunk),
            words = emptyMap(),
            stats = Stats(read = listOf("La Parure"), listened = 3600, spoken = 1, talked = 10, written = 1),
            read = setOf("La Parure", bridgeReadId("Beowulf")),
            classicTitles = setOf("La Parure"),
            bridgeTitles = setOf(bridgeReadId("Beowulf")),
            accuracy = 0.9,
            canDoDone = setOf("A1-1"),
            stageLevel = "A1",
        )
        assertEquals(1, report.knownWords)
        assertEquals(1, report.learningWords)
        assertEquals("A1", report.cefr)
        assertTrue(report.milestones.single { it.id == "classic" }.done)
        assertTrue(report.milestones.single { it.id == "bridge" }.done)
        assertTrue(report.milestones.single { it.id == "listen-1h" }.done)
        assertTrue(report.milestones.single { it.id == "words-100" }.done.not())
        assertEquals(4, report.canDos.size)
        assertTrue("A1-1" in report.canDoDone)
    }
}
