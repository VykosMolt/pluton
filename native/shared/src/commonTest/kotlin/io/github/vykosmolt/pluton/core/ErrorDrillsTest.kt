package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorDrillsTest {
    private fun correction(
        id: Long,
        original: String,
        fix: String,
        at: Long,
        why: String = "note",
        kind: String = "grammar",
    ) = CorrectionRecord(id, "writing", kind, original, fix, why, at, resolved = false)

    @Test
    fun newestUsefulCorrectionsBecomeDrills() {
        val drills = errorDrills(
            listOf(
                correction(1, "je suis alle", "je suis allé", 1000, why = "old note"),
                correction(2, "je suis alle", "je suis allé", 2000, why = "new note", kind = "spelling"),
                correction(3, "bonjour", "bonjour", 3000), // unchanged, not a drill
                correction(4, "  ", "quelque chose", 4000), // blank original
                correction(5, "les chats", "les chat", 5000),
            ),
        )
        assertEquals(listOf("les chats", "je suis alle"), drills.map { it.original })
        assertEquals(listOf("les chat", "je suis allé"), drills.map { it.fix })
        assertEquals("grammar", drills.first().kind)
        assertEquals("new note", drills[1].why)
        assertEquals("spelling", drills[1].kind)
        assertTrue(drills.all { it.why.isNotBlank() })
    }

    @Test
    fun aCapitalizationOnlyFixIsStillADrillAndIsGraded() {
        val drills = errorDrills(listOf(correction(1, "bonjour", "Bonjour", 1000)))
        assertEquals(1, drills.size)
        assertEquals("Bonjour", drills.single().fix)
        val graded = gradeError("bonjour", drills.single().fix)
        assertEquals("almost", graded.result)
        assertEquals("case", graded.why)
        assertEquals("right", gradeError("d'abord", "d’abord").result)
        assertEquals("right", gradeError("coeur", "cœur").result)
        assertEquals("right", gradeError("cœur", "coeur").result)
        assertTrue(errorDrills(listOf(correction(2, "je  suis", "je suis", 2000))).isEmpty())
    }

    @Test
    fun limitAndEmptyInput() {
        val corrections = (1L..5L).map { correction(it, "faute $it", "corrigé $it", it) }
        assertEquals(listOf("faute 5", "faute 4"), errorDrills(corrections, limit = 2).map { it.original })
        assertEquals(emptyList(), errorDrills(emptyList()))
        assertEquals(emptyList(), errorDrills(corrections, limit = 0))
    }

    @Test
    fun gradingAcceptsAccentsAndOneTypoButNotAnotherWord() {
        assertEquals("right", gradeError("je suis allé", "je suis allé").result)
        assertEquals("almost", gradeError("je suis alle", "je suis allé").result)
        assertEquals("almost", gradeError("je suis alléé", "je suis allé").result)
        assertEquals("wrong", gradeError("je suis partie", "je suis allé").result)
    }
}
