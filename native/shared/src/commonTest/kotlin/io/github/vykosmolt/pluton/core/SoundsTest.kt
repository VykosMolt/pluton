package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundsTest {
    @Test
    fun everyLessonIsUsable() {
        assertEquals(9, SOUND_LESSONS.size)
        assertEquals(
            setOf("nasal", "yu", "r", "sz", "shj", "finals", "liaison", "silent", "caduc"),
            SOUND_LESSONS.map { it.id }.toSet(),
        )
        for (lesson in SOUND_LESSONS) {
            assertTrue(lesson.title.isNotBlank(), lesson.id)
            assertTrue(lesson.tip.isNotBlank(), lesson.id)
            assertTrue(lesson.pairs.isNotEmpty() || lesson.phrases.isNotEmpty(), lesson.id)
            for (pair in lesson.pairs) {
                assertTrue(pair.a.isNotBlank() && pair.b.isNotBlank(), lesson.id)
                assertTrue(pair.a != pair.b, lesson.id)
            }
            assertTrue(lesson.phrases.all { it.isNotBlank() }, lesson.id)
        }
    }
}
