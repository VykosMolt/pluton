package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunksTest {
    @Test
    fun chunksAreUsableAndFindableFromTheirWords() {
        assertEquals(36, CHUNKS.size)
        assertEquals(CHUNKS.size, CHUNKS.map { it.text }.toSet().size)
        for (chunk in CHUNKS) {
            assertTrue(chunk.text.isNotBlank() && chunk.gloss.isNotBlank(), chunk.text)
            assertTrue(chunk.example.contains(chunk.text, ignoreCase = true), "example must contain the chunk: ${chunk.text}")
            assertTrue(chunk.lemmas.isNotEmpty(), chunk.text)
        }
        assertTrue(chunksFor("avoir").any { it.text == "avoir besoin de" })
        assertTrue(chunksFor("temps").any { it.text == "de temps en temps" })
        assertTrue(chunksFor("rendez-vous").any { it.text == "prendre rendez-vous" })
        assertTrue(chunksFor("course").any { it.text == "faire les courses" })
        assertTrue(chunksFor("air").any { it.text == "avoir l'air" })
        assertTrue(chunksFor("monde").any { it.text == "tout le monde" })
        assertTrue(chunksFor("dès").any { it.text == "dès que" })
    }

    @Test
    fun everyChunkIsReachableFromOneOfItsLemmas() {
        val unreachable = CHUNKS.filter { chunk ->
            chunk.lemmas.none { lemma -> chunksFor(lemma).take(3).any { it.text == chunk.text } }
        }.map { it.text }
        assertEquals(emptyList(), unreachable)
    }

    @Test
    fun everyChunkBecomesAPlayableClozeCard() {
        for (chunk in CHUNKS) {
            val word = chunkWord(chunk)
            assertEquals("chunk:${chunk.text}", word.lemma)
            assertTrue(isChunk(word.lemma))
            for (reps in 0..9) {
                assertEquals("cloze", modeFor(createEmptyCard(1).copy(reps = reps), word), "${chunk.text} reps=$reps")
            }
            val parts = blank(word.sentence!!, word.form!!)
            assertTrue(word.form!!.equals(parts.second, ignoreCase = true), "expected ${word.form}, blanked ${parts.second}")
        }
    }

    @Test
    fun aChunkCardUsesTheNormalCardModelWithoutSilentDictation() {
        val word = chunkWord(CHUNKS.first())
        assertEquals("chunk:${CHUNKS.first().text}", word.lemma)
        assertEquals("cloze", modeFor(createEmptyCard(1).copy(reps = 1), word))
        val parts = blank(word.sentence!!, word.form!!)
        assertTrue(word.form!!.equals(parts.second, ignoreCase = true), "expected ${word.form}, blanked ${parts.second}")
    }
}
