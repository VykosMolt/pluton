package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CuesTest {
    @Test
    fun parsesSrtAndStripsTagsAndFormatting() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            <i>Bonjour</i> {b}tout le monde

            2
            00:00:04,000 --> 00:00:06,250
            Comment ça va ?
        """.trimIndent()
        val cues = parseCues(srt)
        assertEquals(2, cues.size)
        assertEquals(Cue(1.0, 3.5, "Bonjour tout le monde"), cues[0])
        assertEquals("Comment ça va ?", cues[1].text)
    }

    @Test
    fun parsesVttHourlessAndSorts() {
        val vtt = """
            WEBVTT

            01:02.000 --> 01:03.000
            Deuxième

            00:01.000 --> 00:02.000
            Première
        """.trimIndent()
        val cues = parseCues(vtt)
        assertEquals(listOf("Première", "Deuxième"), cues.map { it.text })
        assertEquals(1.0, cues[0].start)
        assertEquals(62.0, cues[1].start)
    }

    @Test
    fun cueAtKeepsTheLastLineThroughGaps() {
        val cues = listOf(Cue(1.0, 2.0, "a"), Cue(4.0, 5.0, "b"))
        assertEquals(-1, cueAt(cues, 0.5))
        assertEquals(0, cueAt(cues, 1.0))
        assertEquals(0, cueAt(cues, 3.0))
        assertEquals(1, cueAt(cues, 9.0))
        assertEquals(-1, cueAt(emptyList(), 1.0))
    }
}
