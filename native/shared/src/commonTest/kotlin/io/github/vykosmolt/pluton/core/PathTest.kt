package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathTest {
    @Test
    fun anEmptyStoreStartsAtTheWall() {
        val p = pathState(PathMetrics())
        assertEquals(10, STAGES.size)
        assertEquals(0, p.current)
        val wall = p.stages[0]
        assertEquals("Le Mur", wall.stage.place)
        assertEquals("A1", wall.stage.level)
        assertEquals(listOf("Learn 75 words", "Read “story-0”", "Listen for 3 minutes in all"), wall.goals.map { it.label })
        assertEquals(0.0, wall.pct)
        assertTrue(!wall.complete)
    }

    @Test
    fun theWallCompletesAtWordsStoryAndListening() {
        val m = PathMetrics(learned = 75, read = setOf("story-0"), listenMin = 3)
        val p = pathState(m, mapOf("story-0" to "Chapitre 1 · Le Mur"))
        assertEquals("Read “Chapitre 1 · Le Mur”", p.stages[0].goals[1].label)
        assertTrue(p.stages[0].complete)
        assertEquals(1, p.current)
    }

    @Test
    fun bonusGoalsNeverBlockAStage() {
        val m = PathMetrics(
            learned = 400,
            tables = mapOf("ind:pre" to 12),
            tablesTotal = 12,
            grammar = 1,
            read = setOf("story-0", "story-1", "story-2"),
            listenMin = 20,
        )
        val p = pathState(m)
        assertTrue(p.stages[2].complete) // talk/write are bonus
        assertTrue(p.stages[2].goals.any { it.kind == GoalKind.Talk && !it.done })
        assertEquals(3, p.current)
    }

    @Test
    fun focusPrefersReadingThenListening() {
        val p = pathState(PathMetrics(learned = 75, listenMin = 3))
        assertEquals(GoalKind.Read, focus(p.stages[0])?.kind)
        val read = pathState(PathMetrics(learned = 75, read = setOf("story-0")))
        assertEquals(GoalKind.Listen, focus(read.stages[0])?.kind)
        val everything = PathMetrics(
            learned = 6000,
            tables = mapOf("ind:pre" to 12, "pc" to 8, "ind:imp" to 8, "ind:fut" to 8, "cnd:pre" to 8, "sub:pre" to 8),
            tablesTotal = 300,
            grammar = 15,
            read = STAGES.flatMap { it.read }.toSet(),
            listenMin = 1200,
            spoken = 200,
            talked = 200,
        )
        val done = pathState(everything)
        assertEquals(10, done.current)
        assertNull(focus(done.stages[9]))
    }

    @Test
    fun metricsCountGraduatedCardsOnly() {
        val cards = listOf(
            CardState("chat", createEmptyCard(1).copy(state = State.Review)),
            CardState("chien", createEmptyCard(1).copy(state = State.Learning)),
            CardState("gram/objets", createEmptyCard(1).copy(state = State.Review)),
            CardState("conj/être/ind:pre", createEmptyCard(1).copy(state = State.Review)),
            CardState("conj/être/pc", createEmptyCard(1).copy(state = State.New)),
        )
        val m = pathMetrics(cards, listOf("story-0"), listenedSeconds = 90, spoken = 4)
        assertEquals(1, m.learned)
        assertEquals(1, m.grammar)
        assertEquals(1, m.tablesTotal)
        assertEquals(mapOf("ind:pre" to 1), m.tables)
        assertEquals(1, m.listenMin)
        assertEquals(4, m.spoken)
        assertEquals(setOf("story-0"), m.read)
    }

    @Test
    fun speakingIsItsOwnBonusGoal() {
        val m = PathMetrics(learned = 400, spoken = 3)
        val p = pathState(m)
        val goal = p.stages[2].goals.single { it.kind == GoalKind.Speak }
        assertEquals("Speak French aloud 3 times", goal.label)
        assertTrue(goal.done)
        assertTrue(goal.bonus)
    }
}
