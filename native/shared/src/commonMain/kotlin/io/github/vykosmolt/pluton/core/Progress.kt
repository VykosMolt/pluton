package io.github.vykosmolt.pluton.core

import kotlinx.serialization.Serializable

// A small, honest progress report: vocabulary is a rough CEFR waypoint, not a test; can-do statements are
// self-assessment; milestones celebrate concrete things the learner has done.

data class CanDo(val id: String, val text: String)

@Serializable
data class ProgressSnapshot(val day: String, val known: Int, val learning: Int)

data class StudyCounts(val known: Int, val learning: Int)

data class Checkpoint(val level: String, val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done >= total
}

data class ErrorKindCount(val kind: String, val count: Int)

/** Corrections by kind in a window, most frequent first; blank kinds are ignored. */
fun errorKindCounts(corrections: List<CorrectionRecord>, since: Long, limit: Int = 3): List<ErrorKindCount> =
    corrections.asSequence()
        .filter { it.at >= since && it.kind.isNotBlank() }
        .groupingBy { it.kind.trim().lowercase() }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit.coerceAtLeast(0))
        .map { ErrorKindCount(it.key, it.value) }
        .toList()

/** Reviews in the last seven days; accuracy is -1 until there is one. */
data class WeekActivity(val reviews: Int, val good: Int) {
    val accuracy: Double get() = if (reviews == 0) -1.0 else good.toDouble() / reviews
}

data class Milestone(
    val id: String,
    val label: String,
    val done: Boolean,
    val have: Int,
    val need: Int,
)

data class ProgressReport(
    val knownWords: Int,
    val learningWords: Int,
    val cefr: String,
    val stageLevel: String,
    /** -1 when there are no reviews yet; avoids a nullable primitive in the Swift bridge. */
    val accuracy: Double,
    val week: WeekActivity,
    val checkpoint: Checkpoint,
    val growth: List<ProgressSnapshot>,
    val canDos: List<CanDo>,
    val canDoDone: Set<String>,
    val milestones: List<Milestone>,
)

/** The id a Bridge text is marked read under; keeps the reader, the milestone and both apps in sync. */
fun bridgeReadId(title: String): String = "bridge:$title"

/** Known and learning single words (chunks excluded); the same counts the CEFR estimate and snapshots use. */
fun studyCounts(cards: List<CardState>, words: Map<String, Word>): StudyCounts {
    var known = 0
    var learning = 0
    for (card in cards) {
        if (!isWord(card) || isChunk(card.lemma)) continue
        when (status(card, words[card.lemma])) {
            WordStatus.Known -> known++
            WordStatus.Learning -> learning++
            else -> Unit
        }
    }
    return StudyCounts(known, learning)
}

/** A deliberately broad receptive vocabulary estimate; the app labels it as an estimate, never a CEFR test. */
fun cefrEstimate(knownWords: Int): String = when {
    knownWords < 500 -> "A1"
    knownWords < 1500 -> "A1–A2"
    knownWords < 3000 -> "A2–B1"
    knownWords < 5000 -> "B1–B2"
    else -> "B2–C1"
}

fun canDosFor(level: String): List<CanDo> {
    val items = when (level) {
        "A2" -> listOf(
            "Introduce yourself and ask simple questions",
            "Describe your day and your plans",
            "Handle a shop, café or ticket counter",
            "Read a short text with help and get the main idea",
        )
        "B1" -> listOf(
            "Tell a short story in the past",
            "Follow a familiar podcast or video with subtitles",
            "Write a paragraph and improve it after feedback",
            "Give your opinion on a familiar topic",
        )
        "B2" -> listOf(
            "Argue a point and respond to a counter-argument",
            "Read a novel or long article with occasional lookups",
            "Follow a film or series without full subtitles",
            "Write a structured text with a clear position",
        )
        "C1" -> listOf(
            "Read literature and follow style and irony",
            "Follow natural speech, including fast conversation",
            "Speak fluently on abstract or unfamiliar topics",
            "Write with nuance, register and idiomatic phrasing",
        )
        else -> listOf(
            "Greet someone and say who you are",
            "Order food or ask for a price",
            "Read a very short story with support",
            "Understand slow, clear speech about everyday things",
        )
    }
    return items.mapIndexed { i, text -> CanDo("$level-${i + 1}", text) }
}

fun progressReport(
    cards: List<CardState>,
    words: Map<String, Word>,
    stats: Stats,
    read: Set<String>,
    classicTitles: Set<String>,
    bridgeTitles: Set<String>,
    accuracy: Double?,
    week: WeekActivity,
    canDoDone: Set<String>,
    stageLevel: String,
    snapshots: List<ProgressSnapshot> = emptyList(),
): ProgressReport {
    val counts = studyCounts(cards, words)
    val known = counts.known
    val learning = counts.learning
    fun milestone(id: String, label: String, have: Int, need: Int) =
        Milestone(id, label, have >= need, have, need)
    val milestones = listOf(
        milestone("words-100", "Learn 100 words", known, 100),
        milestone("words-500", "Learn 500 words", known, 500),
        milestone("words-1000", "Learn 1,000 words", known, 1000),
        milestone("words-2000", "Learn 2,000 words", known, 2000),
        milestone("words-3000", "Learn 3,000 words", known, 3000),
        milestone("read-1", "Finish your first text", read.size, 1),
        milestone("classic", "Finish a French classic", read.count { it in classicTitles }, 1),
        milestone("bridge", "Finish a Norman Bridge text", read.count { it in bridgeTitles }, 1),
        milestone("listen-1h", "Listen for an hour", stats.listened, 3600),
        milestone("listen-10h", "Listen for ten hours", stats.listened, 36000),
        milestone("speak-1", "Record yourself once", stats.spoken, 1),
        milestone("speak-50", "Record yourself 50 times", stats.spoken, 50),
        milestone("talk-10", "Talk with Pluton 10 times", stats.talked, 10),
        milestone("write-1", "Get your first text corrected", stats.written, 1),
    )
    val canDos = canDosFor(stageLevel)
    return ProgressReport(
        knownWords = known,
        learningWords = learning,
        cefr = cefrEstimate(known),
        stageLevel = stageLevel,
        accuracy = accuracy ?: -1.0,
        week = week,
        checkpoint = Checkpoint(stageLevel, canDos.count { it.id in canDoDone }, canDos.size),
        growth = snapshots.sortedBy { it.day }.takeLast(14),
        canDos = canDos,
        canDoDone = canDoDone,
        milestones = milestones,
    )
}
