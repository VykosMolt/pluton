package io.github.vykosmolt.pluton.core

// What Pluton asks the AI to do, ported from src/lib/tutor.js. Prompts are product logic, so they live in the shared
// core and stay identical across platforms; only the HTTP/SDK layer is platform-specific.

data class Level(val code: String, val how: String, val words: Int)

private val LEVELS = listOf(
    Triple(150, "A1", "a beginner: very short sentences, present tense, the most common words; put a hard word's English meaning in parentheses"),
    Triple(600, "A2", "elementary: simple sentences, présent and passé composé, everyday vocabulary"),
    Triple(2000, "B1", "intermediate: natural everyday French, the common tenses, an occasional idiom"),
    Triple(4000, "B2", "upper-intermediate: natural French at normal complexity, idioms welcome"),
    Triple(Int.MAX_VALUE, "C1", "advanced: talk as with an educated native speaker, nuanced vocabulary and register"),
)

/** Rough level from the size of the learner's vocabulary (words known or being learned). A waypoint, not a test. */
fun levelOf(words: Int): Level = LEVELS.first { words < it.first }.let { Level(it.second, it.third, words) }

fun chatSystem(level: Level, learning: List<String>): String = buildString {
    append(
        "You are Pluton, the black cat from Edgar Allan Poe's \"The Black Cat\" (named as in Baudelaire's French " +
            "translation), now a French conversation partner in a language-learning app. You are witty, a little " +
            "gothic, warm, and never mean.\n\nThe learner is an English speaker learning French. They know roughly " +
            "${level.words} French words, so treat them as ${level.code}, ${level.how}.",
    )
    if (learning.isNotEmpty()) append("\nWords they are learning right now (reuse a few naturally): ${learning.joinToString(", ")}.")
    append(
        "\n\nHow to reply:\n" +
            "- Write in French they can follow at that level. Keep it to 1-4 short sentences and end with a question that keeps the conversation going.\n" +
            "- If their last message has French mistakes, start with exactly one line: \"✏️ \" + their sentence corrected + \" — \" + a very short explanation in English. Then continue in French on a new line. No mistakes: no ✏️ line.\n" +
            "- If they write in English, use the ✏️ line to show how to say it in French, then continue in French.\n" +
            "- Plain sentences only: no lists, no headings, no translation of your own reply unless they ask.",
    )
}

data class WritingPrompt(val level: String, val fr: String, val en: String)

/** Writing prompts, easiest first (mirrors PROMPTS in src/lib/tutor.js). */
val WRITING_PROMPTS = listOf(
    WritingPrompt("A1", "Présente-toi : qui es-tu, où habites-tu, qu'aimes-tu faire ?", "Introduce yourself: who you are, where you live, what you like doing."),
    WritingPrompt("A1", "Décris ta chambre.", "Describe your bedroom."),
    WritingPrompt("A2", "Raconte ton dernier week-end.", "Tell me about your last weekend. (passé composé)"),
    WritingPrompt("A2", "Quel est ton plat préféré ? Pourquoi ?", "What is your favourite dish, and why?"),
    WritingPrompt("A2", "Écris la suite : « Il était une fois un chat noir qui… »", "Continue the story: \"Once upon a time there was a black cat who…\""),
    WritingPrompt("B1", "Raconte un souvenir d'enfance.", "Tell a childhood memory. (imparfait vs passé composé)"),
    WritingPrompt("B1", "Si tu pouvais vivre dans n'importe quelle ville, laquelle choisirais-tu ?", "If you could live in any city, which would you choose? (conditionnel)"),
    WritingPrompt("B2", "Les réseaux sociaux rendent-ils les gens plus seuls ? Donne ton avis.", "Do social networks make people lonelier? Give your opinion."),
    WritingPrompt("B2", "Écris une lettre à toi-même dans dix ans.", "Write a letter to yourself ten years from now."),
    WritingPrompt("C1", "« Le Chat noir » de Poe : la culpabilité peut-elle rendre fou ? Argumente.", "Poe's \"The Black Cat\": can guilt drive someone mad? Argue your case."),
    WritingPrompt("C1", "Faut-il préserver les langues régionales, comme le normand ? Argumente.", "Should regional languages such as Norman be preserved? Argue your case."),
)

private val LEVEL_ORDER = listOf("A1", "A2", "B1", "B2", "C1")

/** Prompts within one level of the learner's, like the web app's suggestion filter. */
fun suggestedWritingPrompts(level: Level): List<WritingPrompt> {
    val at = LEVEL_ORDER.indexOf(level.code).coerceAtLeast(0)
    return WRITING_PROMPTS.filter { kotlin.math.abs(LEVEL_ORDER.indexOf(it.level) - at) <= 1 }
}

/**
 * The grammar or conjugation form the learner is currently working on, as a short English/French label.
 * Used to point AI conversation/writing and the reading notice at one useful form.
 */
fun grammarFocus(cards: List<CardState>, grammar: List<GrammarTopic>, conj: Map<String, ConjTable>? = null): String? {
    fun topicLabel(topic: GrammarTopic): String =
        if (topic.en.isBlank()) "the grammar topic “${topic.name}”" else "the grammar topic “${topic.name}” (${topic.en})"

    val learningTopic = cards.firstOrNull {
        it.lemma.startsWith(GRAM) && it.fsrs.state != State.Review && !it.known
    }?.let { card -> grammar.firstOrNull { gramKey(it.id) == card.lemma } }
    if (learningTopic != null) return topicLabel(learningTopic)

    val nextTopic = newGrammar(cards, grammar, 1).firstOrNull()
        ?.let { key -> grammar.firstOrNull { gramKey(it.id) == key } }
    if (nextTopic != null) return topicLabel(nextTopic)

    val learningTable = cards.firstOrNull {
        it.lemma.startsWith(CONJ) && it.fsrs.state != State.Review && !it.known
    }
    parseConj(learningTable?.lemma ?: "")?.let { (verb, tense) ->
        val name = FRENCH_TENSES.firstOrNull { it.id == tense }?.name ?: tense
        return "the $name forms of “$verb”"
    }

    val nextTable = newConj(cards, conj ?: emptyMap(), FRENCH_TENSES.map { it.id }, 1).firstOrNull()
    parseConj(nextTable ?: "")?.let { (verb, tense) ->
        val name = FRENCH_TENSES.firstOrNull { it.id == tense }?.name ?: tense
        return "the $name forms of “$verb”"
    }
    return null
}

fun chatSystem(level: Level, learning: List<String>, focus: String?): String {
    val base = chatSystem(level, learning)
    if (focus.isNullOrBlank()) return base
    return base + "\n\nLearner focus: $focus. Use this form naturally in your own replies; when you correct French mistakes, correct this form first."
}

fun writeSystem(level: Level, prompt: String, focus: String?): String {
    val base = writeSystem(level, prompt)
    if (focus.isNullOrBlank()) return base
    return base + "\n\nAlso pay particular attention to $focus. Correct errors in that form first, and mention it in the feedback."
}

fun writeSystem(level: Level, prompt: String): String =
    "You are a meticulous, encouraging French writing teacher. The learner (about ${level.code}) wrote a short " +
        "text in answer to: \"$prompt\".\n\nReturn ONLY a JSON object, with nothing before or after it:\n" +
        "{\"corrected\": \"<their text corrected: keep their meaning and voice, change only what is wrong or clearly unnatural>\",\n" +
        " \"errors\": [{\"original\": \"<exact wrong fragment from their text>\", \"fix\": \"<corrected fragment>\", " +
        "\"why\": \"<one short sentence in English>\", \"kind\": \"grammar|spelling|agreement|conjugation|vocabulary|style\"}],\n" +
        " \"feedback\": \"<2-3 sentences in English: what went well, and the single most useful thing to work on next>\",\n" +
        " \"level\": \"<A1|A2|B1|B2|C1|C2, your estimate for this text>\"}"

@kotlinx.serialization.Serializable
data class WritingError(
    val original: String = "",
    val fix: String = "",
    val why: String = "",
    val kind: String = "",
)

@kotlinx.serialization.Serializable
data class WritingResult(
    val corrected: String = "",
    val errors: List<WritingError> = emptyList(),
    val feedback: String = "",
    val level: String = "",
)

private val tutorJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

/** The first {...} object in a reply, tolerating prose or code fences around it; null when it isn't usable. */
private fun firstJsonObject(reply: String): String? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    return if (start < 0 || end <= start) null else reply.substring(start, end + 1)
}

fun parseWriting(reply: String): WritingResult? = firstJsonObject(reply)?.let { body ->
    runCatching { tutorJson.decodeFromString<WritingResult>(body) }.getOrNull()
}?.takeIf { it.corrected.isNotBlank() }

@kotlinx.serialization.Serializable
data class StoryDraft(val title: String = "", val body: String = "")

fun parseStory(reply: String): StoryDraft? = firstJsonObject(reply)?.let { body ->
    runCatching { tutorJson.decodeFromString<StoryDraft>(body) }.getOrNull()
}?.takeIf { it.title.isNotBlank() && it.body.trim().length >= 80 }

/** A short story at the learner's level, mostly from words they know (mirrors readerSystem in tutor.js). */
fun readerSystem(level: Level, known: List<String> = emptyList(), targets: List<String> = emptyList()): String =
    buildString {
        append("You write short French stories for a language learner. The learner is ${level.code} (${level.how}).")
        append(if (known.isEmpty()) "\nThey are just starting." else "\nWords they already know: ${known.joinToString(", ")}.")
        if (targets.isNotEmpty()) append("\nWords they are learning right now (use a few naturally): ${targets.joinToString(", ")}.")
        append(
            "\n\nWrite a new, self-contained story of 150-250 words. Aim for about 95% words the learner already knows " +
                "(recycle the known list), with short sentences and clear connections. Keep it concrete and enjoyable; " +
                "no lists, no grammar explanations. For A1-A2 use the present and passé composé; from B1 you can use " +
                "the full range of common tenses.\n\nReturn ONLY a JSON object, with nothing before or after it:\n" +
                "{\"title\": \"<a short French title>\", \"body\": \"<the story, with \\n between paragraphs>\"}",
        )
    }

/** Continue an existing story at the learner's level; used by the serialised reading action. */
fun continueSystem(level: Level, known: List<String>, targets: List<String>, previous: String): String = buildString {
    append("You write short French stories for a language learner. The learner is ${level.code} (${level.how}).")
    if (known.isNotEmpty()) append("\nWords they already know: ${known.joinToString(", ")}.")
    if (targets.isNotEmpty()) append("\nWords they are learning right now (use a few naturally): ${targets.joinToString(", ")}.")
    append(
        "\n\nContinue the text below. If it is a story, keep the same characters, setting and tone; otherwise continue in the same style and subject. Write 150-250 words. " +
            "Recycle the known words and end on a small hook. Return ONLY a JSON object, with nothing before or after it:\n" +
            "{\"title\": \"<a short French title for this part>\", \"body\": \"<the continuation, with \\n between paragraphs>\"}\n\n" +
            "Previous part:\n" + previous.take(2500),
    )
}

/** Comprehension check for a text the learner read: judges meaning, not grammar (mirrors RETELL in tutor.js). */
const val RETELL = "You check whether a French learner understood a text they just read. They write a short " +
    "retelling in French or English. Judge comprehension only: ignore grammar, spelling and accent mistakes unless " +
    "they change the meaning.\n\nReturn ONLY a JSON object, with nothing before or after it:\n" +
    "{\"score\": <1-5>, \"feedback\": \"<two sentences in English: what they got right, and the one important thing " +
    "they missed or misunderstood>\"}\n" +
    "Score 1 if they missed the point, 3 if they got the main idea, 5 if they got the main idea and the important details."

@kotlinx.serialization.Serializable
data class RetellFeedback(val score: Int = 0, val feedback: String = "")

fun parseRetell(reply: String): RetellFeedback? = firstJsonObject(reply)?.let { body ->
    runCatching { tutorJson.decodeFromString<RetellFeedback>(body) }.getOrNull()
}?.takeIf { it.score in 1..5 && it.feedback.isNotBlank() }

data class Correction(val fix: String?, val why: String, val body: String)

private val CORRECTION = Regex("""^\s*✏️\s*(.+?)(?:\s+[—–-]\s+(.+))?\n+([\s\S]*)$""")

/** Splits a reply into its optional correction line and the rest. */
fun splitCorrection(text: String): Correction {
    val m = CORRECTION.find(text) ?: return Correction(null, "", text.trim())
    return Correction(m.groupValues[1].trim(), m.groupValues[2].trim(), m.groupValues[3].trim())
}
