package io.github.vykosmolt.pluton.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// The word-only part of the web app's run queue (src/lib/logic.js): due reviews first, new words spread through
// them, in-run requeue for cards due again soon. Conjugation/grammar cards come after the core is settled.

const val KNOWN_DAYS = 21.0
const val LEARN_AHEAD_MS = 20 * 60_000L
const val NEW_PER_RUN = 5
const val NEW_PER_DAY = 20
const val MAX_REVIEWS_PER_RUN = 40
const val MAX_SEEN_PER_RUN = 5
const val CONJ_PER_RUN = 2
const val UNLOCK_NEXT_TENSE = 8

@kotlinx.serialization.Serializable
data class Mined(val sentence: String, val form: String, val translation: String = "")

@kotlinx.serialization.Serializable
data class CardState(
    val lemma: String,
    val fsrs: Card,
    val known: Boolean = false,
    val added: Long = 0,
    val mined: Mined? = null,
    val updated: Long = 0, // last local write; sync's last-write-wins stamp
)

data class RunItem(
    val lemma: String,
    val card: Card,
    val phase: String, // "intro" or "quiz"
    val seen: Int = 0,
    val missed: Boolean = false,
)

fun isWord(card: CardState): Boolean = !card.lemma.contains('/')

fun graduated(c: CardState): Boolean =
    c.known || c.fsrs.state == State.Review || c.fsrs.state == State.Relearning

/** Whether a stored card can be shown with the current data (a rebuild can drop a sentence, table or topic). */
fun playable(
    card: CardState,
    words: Map<String, Word>,
    conj: Map<String, ConjTable>? = null,
    grammar: List<GrammarTopic>? = null,
): Boolean = when {
    isWord(card) -> card.mined != null || words[card.lemma]?.sentence != null
    card.lemma.startsWith(GRAM) -> grammar?.any { gramKey(it.id) == card.lemma } == true
    else -> parseConj(card.lemma)?.let { (verb, tense) -> conj?.get(verb)?.t?.get(tense) != null } == true
}

/** The sentence shown for a card: alternate the context it was mined in with the dictionary example. */
fun contextSentence(card: CardState?, word: Word?): String {
    val mined = card?.mined?.sentence?.takeIf { it.isNotBlank() }
    val builtIn = word?.sentence?.takeIf { it.isNotBlank() }
    if (mined == null) return builtIn.orEmpty()
    if (builtIn == null || builtIn == mined) return mined
    return if ((card?.fsrs?.reps ?: 0) % 2 == 0) mined else builtIn
}

/** The form to blank in the sentence returned by [contextSentence]. */
fun contextForm(card: CardState?, word: Word?): String {
    val mined = card?.mined
    val minedSentence = mined?.sentence?.takeIf { it.isNotBlank() }
    val builtIn = word?.sentence?.takeIf { it.isNotBlank() }
    val fallback = word?.form?.takeIf { it.isNotBlank() } ?: card?.lemma.orEmpty()
    if (mined == null || minedSentence == null) return fallback
    if (builtIn == null || builtIn == minedSentence) return mined.form.ifBlank { fallback }
    return if ((card?.fsrs?.reps ?: 0) % 2 == 0) mined.form.ifBlank { fallback } else fallback
}

fun status(card: CardState?, word: Word?): WordStatus {
    if (card?.known == true || (card?.fsrs?.stability ?: 0.0) >= KNOWN_DAYS || (card == null && word?.pos == "interj")) {
        return WordStatus.Known
    }
    if (card == null) return WordStatus.New
    return if (card.fsrs.state == State.New) WordStatus.Queued else WordStatus.Learning
}

private fun item(card: CardState, isNew: Boolean) = RunItem(card.lemma, card.fsrs, if (isNew) "intro" else "quiz")

// Grammar topics start once the vocabulary can carry them, one level at a time (matches GRAMMAR_FROM).
private val GRAMMAR_FROM = mapOf("A2" to 150, "B1" to 600, "B2" to 1500, "C1" to 3000)

/** Conjugation tables to introduce next, in tense order; a tense opens after UNLOCK_NEXT_TENSE tables graduate. */
fun newConj(cards: List<CardState>, conj: Map<String, ConjTable>, order: List<String>, n: Int): List<String> {
    val have = cards.associateBy { it.lemma }
    val out = mutableListOf<String>()
    for (k in order.indices) {
        if (out.size >= n) break
        if (k > 0) {
            val ready = cards.count {
                it.lemma.startsWith(CONJ) && parseConj(it.lemma)?.second == order[k - 1] && it.fsrs.state == State.Review
            }
            if (ready < UNLOCK_NEXT_TENSE) break
        }
        for (verb in conj.keys) {
            if (out.size >= n) break
            val word = have[verb]
            val learned = word != null && (word.known || word.fsrs.state != State.New)
            val key = conjKey(verb, order[k])
            if (learned && conj[verb]?.t?.get(order[k]) != null && key !in have) out += key
        }
    }
    return out
}

/** The next grammar topics (in order): its level's vocabulary reached, and the previous topic graduated. */
fun newGrammar(cards: List<CardState>, grammar: List<GrammarTopic>, n: Int): List<String> {
    val have = cards.associateBy { it.lemma }
    val learned = cards.count { isWord(it) && graduated(it) }
    val out = mutableListOf<String>()
    for ((i, t) in grammar.withIndex()) {
        if (out.size >= n || learned < (GRAMMAR_FROM[t.level] ?: Int.MAX_VALUE)) break
        val prev = if (i > 0) have[gramKey(grammar[i - 1].id)] else null
        if (i > 0 && !(prev != null && graduated(prev))) break
        if (gramKey(t.id) !in have) out += gramKey(t.id)
    }
    return out
}

/** Due reviews (oldest first) with new words spread through them, plus up to CONJ_PER_RUN new tables and one topic. */
fun buildRun(
    cards: List<CardState>,
    words: Map<String, Word>,
    now: Long,
    newToday: Int,
    conj: Map<String, ConjTable>? = null,
    grammar: List<GrammarTopic>? = null,
    tenses: List<String> = FRENCH_TENSES.map { it.id },
    perRun: Int = NEW_PER_RUN,
    perDay: Int = NEW_PER_DAY,
): List<RunItem> {
    val playableCards = cards.filter { playable(it, words, conj, grammar) }
    val due = playableCards
        .filter { !it.known && it.fsrs.state != State.New && it.fsrs.due <= now }
        .sortedBy { it.fsrs.due }
        .take(MAX_REVIEWS_PER_RUN)
        .map { item(it, false) }
    val have = playableCards.map { it.lemma }.toSet()
    val n = max(0, min(perRun, perDay - newToday))
    val fresh = playableCards.filter { !it.known && it.fsrs.state == State.New }.toMutableList()
    for (w in words.values) {
        if (fresh.size >= n) break
        if (w.sentence != null && w.lemma !in have) fresh += CardState(w.lemma, createEmptyCard(now), added = now)
    }
    val queue = due.toMutableList()
    fresh.take(n).forEachIndexed { i, c -> queue.add(min(queue.size, i * 5), item(c, true)) }
    val room = max(0, min(CONJ_PER_RUN, perDay - newToday - min(n, fresh.size)))
    val tables = if (conj != null) newConj(playableCards, conj, tenses, room) else emptyList()
    // A table the learner just queued from Verbs starts the next run, after any due reviews.
    tables.forEachIndexed { i, key ->
        queue.add(min(queue.size, due.size + i * 6), item(CardState(key, createEmptyCard(now), added = now), true))
    }
    if (grammar != null) {
        val gramRoom = max(0, min(1, perDay - newToday - min(n, fresh.size) - tables.size))
        newGrammar(playableCards, grammar, gramRoom).forEach { key ->
            queue.add(min(queue.size, 8), item(CardState(key, createEmptyCard(now), added = now), true))
        }
    }
    return queue
}

/** After an intro, move the same card to the quiz position a couple of cards later (the first item is `item`). */
fun gotIt(queue: List<RunItem>, item: RunItem): List<RunItem> {
    val rest = queue.drop(1).toMutableList()
    rest.add(min(rest.size, 2), item.copy(phase = "quiz"))
    return rest
}

/** Reinsert a card 3-5 places later when it is due again within 20 minutes; otherwise leave the queue alone. */
fun requeue(queue: List<RunItem>, it: RunItem, now: Long, rnd: () -> Double = { Random.nextDouble() }): List<RunItem> {
    if (it.card.due - now >= LEARN_AHEAD_MS || it.seen >= MAX_SEEN_PER_RUN) return queue
    val at = min(queue.size, 3 + floor(rnd() * 3).toInt())
    return queue.toMutableList().apply { add(at, it) }
}
