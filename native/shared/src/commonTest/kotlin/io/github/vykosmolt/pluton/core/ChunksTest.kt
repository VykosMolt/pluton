package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChunksTest {
    @Test
    fun chunksAreUsableAndFindableFromTheirWords() {
        assertTrue(CHUNKS.size >= 20)
        assertEquals(CHUNKS.size, CHUNKS.map { it.text }.toSet().size)
        for (chunk in CHUNKS) {
            assertTrue(chunk.text.isNotBlank() && chunk.gloss.isNotBlank(), chunk.text)
            assertTrue(chunk.example.contains(chunk.text, ignoreCase = true), "example must contain the chunk: ${chunk.text}")
            assertTrue(chunk.lemmas.isNotEmpty(), chunk.text)
        }
        assertTrue(chunksFor("avoir").any { it.text == "avoir besoin de" })
        assertTrue(chunksFor("temps").any { it.text == "de temps en temps" })
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
