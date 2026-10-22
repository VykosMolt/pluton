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
    fun errorKindsAreCountedInTheWindowMostFrequentFirst() {
        fun row(id: Long, kind: String, at: Long) =
            CorrectionRecord(id, "writing", kind, "a$id", "b$id", "why", at, resolved = false)
        val rows = listOf(
            row(1, "spelling", 1000),
            row(2, "grammar", 2000),
            row(3, "Grammar", 3000),
            row(4, "vocabulary", 4000),
            row(5, "", 5000),
            row(6, "grammar", 100),
        )
        val counts = errorKindCounts(rows, since = 500)
        assertEquals(listOf(ErrorKindCount("grammar", 2), ErrorKindCount("spelling", 1), ErrorKindCount("vocabulary", 1)), counts)
        assertEquals(1, errorKindCounts(rows, since = 500, limit = 1).size)
    }

    @Test
    fun aCheckpointCompletesWhenEveryCanDoIsTicked() {
        val canDos = canDosFor("A2").map { it.id }.toSet()
        val report = progressReport(
            cards = emptyList(),
            words = emptyMap(),
            stats = Stats(),
            read = emptySet(),
            classicTitles = emptySet(),
            bridgeTitles = emptySet(),
            accuracy = null,
            week = WeekActivity(0, 0),
            canDoDone = canDos,
            stageLevel = "A2",
        )
        assertEquals(4, report.checkpoint.total)
        assertEquals(4, report.checkpoint.done)
        assertTrue(report.checkpoint.complete)
        assertEquals(-1.0, report.week.accuracy)
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
            week = WeekActivity(reviews = 12, good = 9),
            canDoDone = setOf("A1-1"),
            stageLevel = "A1",
            speechWpm = 118,
            snapshots = listOf(ProgressSnapshot("2026-10-22", 1, 1), ProgressSnapshot("2026-10-20", 1, 0)),
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
        assertEquals(Checkpoint("A1", 1, 4), report.checkpoint)
        assertTrue(!report.checkpoint.complete)
        assertEquals(0.75, report.week.accuracy)
        assertEquals(118, report.speechWpm)
        assertEquals(listOf("2026-10-20", "2026-10-22"), report.growth.map { it.day })
    }

    @Test
    fun speechRateNeedsEnoughWordsAndTime() {
        assertEquals(5, spokenWords("Il fait noir. Très noir."))
        assertEquals(4, spokenWords("Bonjour, je m'appelle Pluton."))
        assertEquals(80, speechRate("Bonjour, je m'appelle Pluton.", 3.0))
        assertEquals(-1, speechRate("Bonjour.", 3.0))
        assertEquals(-1, speechRate("Bonjour, je m'appelle Pluton.", 2.9))
        val tooFast = (1..50).joinToString(" ") { "mot" }
        assertEquals(-1, speechRate(tooFast, 3.0))
    }
}
