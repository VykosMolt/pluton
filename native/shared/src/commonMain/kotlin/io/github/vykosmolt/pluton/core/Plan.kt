package io.github.vykosmolt.pluton.core

// The daily loop and adaptive pace. Small, testable rules; the UI decides how to present them.

/** New words per run suggested by recent review accuracy; null history keeps the learner's pace. */
fun suggestedNewWords(currentPerRun: Int, recentAccuracy: Double?): Int {
    val base = currentPerRun.coerceIn(3, 10)
    if (recentAccuracy == null) return base
    val delta = when {
        recentAccuracy < 0.75 -> -2
        recentAccuracy < 0.85 -> -1
        recentAccuracy > 0.95 -> 2
        recentAccuracy > 0.90 -> 1
        else -> 0
    }
    return (base + delta).coerceIn(3, 10)
}

/** A short, concrete plan for the day; ids let the store remember which steps are done. */
enum class PlanStep(val id: String, val title: String, val detail: String) {
    Review("review", "Warm up", "Review what is due"),
    Read("read", "Read something", "A text at your level"),
    Speak("speak", "Speak aloud", "Read aloud or talk with Pluton"),
    Write("write", "Write a little", "One paragraph, one correction"),
}
