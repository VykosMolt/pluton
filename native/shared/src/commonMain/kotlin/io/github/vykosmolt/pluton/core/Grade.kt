package io.github.vykosmolt.pluton.core

// First slice of the shared core: the web app's grading rules, ported 1:1 from src/lib/logic.js and tested in
// commonTest. Tokenizer/queue/FSRS come next; keep this module free of Android/iOS APIs.

data class GradeResult(val result: String, val why: String? = null, val other: String? = null)
data class QuizQuestion(val options: List<String>, val correct: Int)
data class QuizResult(val right: Int, val total: Int, val pass: Boolean)

private val ACCENTS = mapOf(
    'à' to 'a', 'á' to 'a', 'â' to 'a', 'ä' to 'a', 'ã' to 'a', 'å' to 'a',
    'ç' to 'c',
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'ö' to 'o', 'õ' to 'o',
    'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
    'ý' to 'y', 'ÿ' to 'y', 'ñ' to 'n',
)

/** Lowercase + œ→oe; accents are kept (same as LANG.fold in the web app). */
fun fold(s: String): String = buildString(s.length) {
    for (ch in s.lowercase()) if (ch == 'œ') append("oe") else append(ch)
}

/** Normalized comparison form: curly apostrophe, trim, collapsed whitespace, folded. */
fun norm(s: String): String =
    fold(s.replace('’', '\'').trim().replace(Regex("\\s+"), " "))

/** Remove French accents (the web app's `strip`; no lowercasing). */
fun stripAccents(s: String): String = buildString(s.length) {
    for (ch in s) if (ch == 'œ') append("oe") else append(ACCENTS[ch] ?: ch)
}

fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        val swap = prev; prev = cur; cur = swap
    }
    return prev[b.length]
}

/**
 * {result: right|almost|wrong}. A real French word for a different lemma is wrong even when it is one accent away
 * (a/à, ou/où); typo leniency only applies to non-words, because French endings differ by one character.
 */
fun grade(typed: String, expected: String, lemma: String, forms: Map<String, String>): GradeResult {
    val t = norm(typed)
    val e = norm(expected)
    if (t == e) return GradeResult("right")
    val other = forms[t]
    if (other != null && other != lemma) return GradeResult("wrong", "other", other)
    if (t.isNotEmpty() && stripAccents(t) == stripAccents(e)) return GradeResult("almost", "accent")
    if (other == lemma) return GradeResult("almost", "form")
    if (e.length >= 4 && levenshtein(t, e) == 1) return GradeResult("almost", "typo")
    return GradeResult("wrong")
}

/** Comprehension quiz score; a pass is two thirds right (matches gradeQuiz in src/lib/logic.js). */
fun gradeQuiz(questions: List<QuizQuestion>, answers: List<Int>): QuizResult {
    val right = questions.indices.count { answers.getOrNull(it) == questions[it].correct }
    return QuizResult(right, questions.size, right * 3 >= questions.size * 2)
}

private val ARTICLE_GENDER = mapOf("le" to "m", "un" to "m", "la" to "f", "une" to "f")
private val ARTICLE = Regex("^(une |un |le |la |l')\\s*(.+)$")
private val PRODUCE_POS = setOf("noun", "verb", "adj", "adv")

/** What a production card expects: "la maison" for nouns, the lemma otherwise. */
fun produceAnswer(word: Word): String = if (word.pos == "noun" && word.gender != null) word.display else word.lemma

/** English → French recall: nouns must come with an article of the right gender (matches gradeProduce). */
fun gradeProduce(typed: String, word: Word, forms: Map<String, String>): GradeResult {
    if (word.pos != "noun" || word.gender == null) return grade(typed, word.lemma, word.lemma, forms)
    val m = ARTICLE.find(norm(typed))
    val g = grade(m?.groupValues?.get(2) ?: typed, word.lemma, word.lemma, forms)
    if (g.result == "wrong") return g
    if (m == null) return GradeResult("almost", "article")
    val article = m.groupValues[1].trim()
    val gender = if (article == "l'") null else ARTICLE_GENDER[article]
    if (gender != null && gender != word.gender) return GradeResult("almost", "gender")
    return g
}

/** Which exercise a word card gets on this rep (matches modeFor in src/lib/logic.js). */
fun modeFor(card: Card, word: Word): String {
    if (card.reps % 4 == 3 && card.stability >= KNOWN_DAYS && word.audioSentence != null) return "dictee"
    if (card.reps % 3 == 2 && card.state == State.Review && word.pos in PRODUCE_POS && word.gloss.isNotBlank()) return "produce"
    return if (card.reps % 2 == 1) "dictation" else "cloze"
}

private val PRONOUN = Regex("^(que |qu')?(je |j'|tu |il |elle |on |nous |vous |ils |elles )")

/** Conjugation cell: a leading pronoun is fine; être-verbs' participle agreement isn't tested (matches gradeConj). */
fun gradeConj(typed: String, expected: String, agrees: Boolean = false): GradeResult {
    fun clean(s: String): String {
        val x = norm(s).replace(PRONOUN, "")
        return if (agrees) x.replace(Regex("(es|e|s)$"), "") else x
    }
    val t = clean(typed)
    val e = clean(expected)
    if (t == e) return GradeResult("right")
    if (t.isNotEmpty() && stripAccents(t) == stripAccents(e)) return GradeResult("almost", "accent")
    return GradeResult("wrong")
}

/** Grammar gap or correction: any accepted answer is right; accents or a lost apostrophe are almost. */
fun gradeGram(typed: String, answers: List<String>): GradeResult {
    val t = norm(typed)
    fun loose(s: String) = stripAccents(s).replace("'", "")
    if (answers.any { norm(it) == t }) return GradeResult("right")
    if (t.isNotEmpty() && answers.any { loose(norm(it)) == loose(t) }) return GradeResult("almost", "accent")
    return GradeResult("wrong")
}

/**
 * A 0-100 recognizer match for the spoken target: full credit per word heard, 0.7 for accent-only slips, none for
 * missing/other words. This is word recognition, not phoneme scoring — the platform recognizer gives no phonemes.
 */
fun dicteeScore(result: DicteeResult): Int {
    if (result.marks.isEmpty()) return 0
    val points = result.marks.sumOf { when (it.mark) { "ok" -> 1.0; "accent" -> 0.7; else -> 0.0 } }
    return kotlin.math.round(points / result.marks.size * 100).toInt()
}

data class DicteeMark(val start: Int, val end: Int, val mark: String) // mark: ok | accent | bad
data class DicteeResult(val result: String, val why: String? = null, val marks: List<DicteeMark>)

private val DICTEE_WORDS = Regex("[\\p{L}\\p{N}'’-]+")

/**
 * Word-by-word alignment of a spoken/heard sentence against the target, matching gradeDictee in
 * src/lib/logic.js. Marks point into the target sentence; "accent" means the word was heard but accents differ.
 */
fun gradeDictee(typed: String, sentence: String): DicteeResult {
    val ref = DICTEE_WORDS.findAll(sentence).toList()
    val a = ref.map { norm(it.value) }
    val b = DICTEE_WORDS.findAll(norm(typed)).map { it.value }.toList()
    val d = Array(a.size + 1) { i -> IntArray(b.size + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    for (i in 1..a.size) {
        for (j in 1..b.size) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
    }
    val marks = Array(a.size) { "bad" }
    var i = a.size
    var j = b.size
    while (i > 0 && j >= 0) {
        if (j > 0 && d[i][j] == d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1) {
            marks[i - 1] = when {
                a[i - 1] == b[j - 1] -> "ok"
                stripAccents(a[i - 1]) == stripAccents(b[j - 1]) -> "accent"
                else -> "bad"
            }
            i--
            j--
        } else if (d[i][j] == d[i - 1][j] + 1) {
            i--
        } else {
            j--
        }
    }
    val errors = d[a.size][b.size]
    val accentsOnly = errors > 0 && marks.count { it == "accent" } == errors
    val result = when {
        errors == 0 -> "right"
        errors == 1 || accentsOnly -> "almost"
        else -> "wrong"
    }
    return DicteeResult(
        result,
        if (accentsOnly) "accent" else null,
        ref.mapIndexed { k, m -> DicteeMark(m.range.first, m.range.last + 1, marks[k]) },
    )
}
