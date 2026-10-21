package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunTest {
    private val now = 1_000_000L

    private fun word(lemma: String, pos: String = "noun") = Word(lemma = lemma, pos = pos, sentence = "Le $lemma dort.", form = lemma)
    private fun card(lemma: String, state: State = State.Review, due: Long = now - 1) =
        CardState(lemma, createEmptyCard(now).copy(state = state, due = due, stability = 3.0, difficulty = 5.0))

    @Test
    fun newRunTakesWordsInDeckOrder() {
        val words = listOf(word("de", "prep"), word("chat"), word("chien"), word("maison")).associateBy { it.lemma }
        val q = buildRun(emptyList(), words, now, 0, perRun = 2)
        assertEquals(listOf("de", "chat"), q.map { it.lemma })
        assertEquals(listOf("intro", "intro"), q.map { it.phase })
    }

    @Test
    fun dueCardsComeFirstAndNewWordsAreSpread() {
        val words = listOf(word("chat"), word("chien")).associateBy { it.lemma }
        val q = buildRun(listOf(card("chat")), words, now, 0, perRun = 1)
        assertEquals(listOf("chien", "chat"), q.map { it.lemma })
        assertEquals(listOf("intro", "quiz"), q.map { it.phase })
    }

    @Test
    fun dailyBudgetCanStopNewWords() {
        val words = listOf(word("chat")).associateBy { it.lemma }
        assertEquals(emptyList(), buildRun(emptyList(), words, now, 20, perRun = 5))
    }

    @Test
    fun requeuesSoonDueCardsOnly() {
        val item = RunItem("chat", createEmptyCard(now).copy(due = now + 10 * 60_000), "quiz")
        val q = listOf(RunItem("a", createEmptyCard(now), "quiz"), RunItem("b", createEmptyCard(now), "quiz"),
            RunItem("c", createEmptyCard(now), "quiz"), RunItem("d", createEmptyCard(now), "quiz"))
        assertEquals(listOf("a", "b", "c", "chat", "d"), requeue(q, item, now) { 0.0 }.map { it.lemma })
        val later = item.copy(card = item.card.copy(due = now + 30 * 60_000))
        assertEquals(q, requeue(q, later, now))
        assertEquals(q, requeue(q, item.copy(seen = MAX_SEEN_PER_RUN), now))
    }

    @Test
    fun aNewWordRunIntroducesAndGraduatesBothWords() {
        val words = listOf(word("chat"), word("chien")).associateBy { it.lemma }
        val scheduler = Scheduler()
        var queue = buildRun(emptyList(), words, now, 0, perRun = 2)
        var t = now
        var introduced = 0
        var answers = 0
        while (queue.isNotEmpty() && answers < 20) {
            val item = queue.first()
            if (item.phase == "intro") {
                queue = gotIt(queue, item)
                introduced++
                continue
            }
            val next = scheduler.next(item.card, t, Rating.Good)
            queue = requeue(queue.drop(1), item.copy(card = next, seen = item.seen + 1), t) { 0.0 }
            answers++
            t += 60_000
        }
        assertEquals(2, introduced)
        assertEquals(4, answers) // each word: one learning step, then a review
        assertTrue(queue.isEmpty())
    }

    private fun table(vararg tenses: String) = ConjTable(
        pp = "été",
        aux = "avoir",
        t = tenses.associateWith { listOf("suis", "es", "est", "sommes", "êtes", "sont") },
    )

    @Test
    fun conjugationTablesWaitForLearnedVerbsAndTenseUnlocks() {
        val conj = mapOf("être" to table("ind:pre", "pc"), "aller" to table("ind:pre", "pc"))
        val learned = listOf(card("être", State.Learning), card("aller", State.Learning))
        assertEquals(listOf("conj/être/ind:pre", "conj/aller/ind:pre"), newConj(learned, conj, listOf("ind:pre", "pc"), 5))
        // The next tense needs 8 graduated tables of the previous one (the current ones come back first).
        val seven = (1..7).map { card("conj/v$it/ind:pre") }
        val eight = seven + card("conj/v8/ind:pre")
        assertEquals(
            listOf("conj/être/ind:pre", "conj/aller/ind:pre"),
            newConj(learned + seven, conj, listOf("ind:pre", "pc"), 5),
        )
        assertEquals(
            listOf("conj/être/ind:pre", "conj/aller/ind:pre", "conj/être/pc", "conj/aller/pc"),
            newConj(learned + eight, conj, listOf("ind:pre", "pc"), 5),
        )
        assertEquals(emptyList(), newConj(emptyList(), conj, listOf("ind:pre"), 5)) // no learned verbs yet
    }

    @Test
    fun grammarTopicsWaitForVocabularyAndThePreviousTopic() {
        val grammar = listOf(GrammarTopic("a", level = "A2", name = "A"), GrammarTopic("b", level = "A2", name = "B"))
        val justUnder = (1..149).map { card("w$it") }
        assertEquals(emptyList(), newGrammar(justUnder, grammar, 2))
        val enough = (1..150).map { card("w$it") }
        assertEquals(listOf("gram/a"), newGrammar(enough, grammar, 2))
        assertEquals(emptyList(), newGrammar(enough + card("gram/a", State.New), grammar, 2)) // previous not graduated
        assertEquals(listOf("gram/b"), newGrammar(enough + card("gram/a"), grammar, 2))
    }

    @Test
    fun aRunCarriesNewTablesAndGrammarTopics() {
        val conj = mapOf("être" to table("ind:pre"))
        val words = mapOf("être" to word("être"))
        val known = CardState("être", createEmptyCard(now).copy(state = State.Review, stability = 30.0), known = true)
        val withTable = buildRun(listOf(known), words, now, 0, conj = conj)
        assertEquals(listOf("conj/être/ind:pre"), withTable.map { it.lemma })
        assertEquals("intro", withTable.single().phase)

        val grammar = listOf(GrammarTopic("objets", level = "A2", name = "objets"))
        val later = now + 86_400_000
        val graduated = (1..150).map { card("w$it", due = later).copy(mined = Mined("Le w$it.", "w$it")) }
        val withGrammar = buildRun(graduated, emptyMap(), now, 0, grammar = grammar)
        assertEquals(listOf("gram/objets"), withGrammar.map { it.lemma })
        // A playable check rejects cards whose data disappeared.
        assertTrue(playable(CardState("conj/être/ind:pre", createEmptyCard(now)), emptyMap(), conj))
        assertTrue(!playable(CardState("conj/être/pc", createEmptyCard(now)), emptyMap(), conj))
        assertTrue(!playable(CardState("gram/objets", createEmptyCard(now)), emptyMap(), grammar = null))
    }

    @Test
    fun statusFollowsTheWebRules() {
        assertEquals(WordStatus.New, status(null, Word("x", pos = "noun")))
        assertEquals(WordStatus.Known, status(null, Word("x", pos = "interj")))
        assertEquals(WordStatus.Queued, status(CardState("x", createEmptyCard(now)), Word("x")))
        assertEquals(WordStatus.Learning, status(CardState("x", createEmptyCard(now).copy(state = State.Review, stability = 3.0)), Word("x")))
        assertEquals(WordStatus.Known, status(CardState("x", createEmptyCard(now).copy(stability = 30.0)), Word("x")))
        assertEquals(WordStatus.Known, status(CardState("x", createEmptyCard(now), known = true), Word("x")))
    }
}
