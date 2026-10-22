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
                correction(1, "je suis alle", "je suis allé", 1000),
                correction(2, "je suis alle", "je suis allé", 2000), // newer duplicate wins
                correction(3, "bonjour", "bonjour", 3000), // unchanged, not a drill
                correction(4, "  ", "quelque chose", 4000), // blank original
                correction(5, "les chats", "les chat", 5000),
            ),
        )
        assertEquals(listOf("les chats", "je suis alle"), drills.map { it.original })
        assertEquals(listOf("les chat", "je suis allé"), drills.map { it.fix })
        assertEquals("grammar", drills.first().kind)
        assertTrue(drills.all { it.why.isNotBlank() })
    }

    @Test
    fun aCapitalizationOnlyFixIsStillADrill() {
        val drills = errorDrills(listOf(correction(1, "bonjour", "Bonjour", 1000)))
        assertEquals(1, drills.size)
        assertEquals("Bonjour", drills.single().fix)
    }

    @Test
    fun limitAndEmptyInput() {
        val corrections = (1L..5L).map { correction(it, "faute $it", "corrigé $it", it) }
        assertEquals(2, errorDrills(corrections, limit = 2).size)
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
