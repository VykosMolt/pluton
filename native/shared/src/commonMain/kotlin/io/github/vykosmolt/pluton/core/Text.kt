package io.github.vykosmolt.pluton.core

import kotlin.math.floor

data class SentenceSpan(val start: Int, val end: Int)

private val SENTENCE = Regex("[^.!?…\\n]+(?:[.!?…]+)?")
private val LETTER = Regex("\\p{L}")
private val LEAD = Regex("^[ «»—\"“”]+")
private val SPEAKABLE = Regex("[«»“\"]|^—\\s*")

/** Sentence offsets for texts with no recorded clips (TTS reads them); mirrors sentencesOf in src/lib/logic.js. */
fun sentencesOf(body: String): List<SentenceSpan> {
    val out = mutableListOf<SentenceSpan>()
    for (m in SENTENCE.findAll(body)) {
        val s = m.value
        if (!LETTER.containsMatchIn(s)) continue
        val lead = s.length - s.replace(LEAD, "").length
        val start = m.range.first + lead
        val end = m.range.first + s.trimEnd().length
        val spoken = body.substring(start, end).replace(SPEAKABLE, "").trim()
        if (spoken.isNotEmpty()) out += SentenceSpan(start, end)
    }
    return out
}

// Tokenizer/coverage ported from src/lib/logic.js. Keep the form/elision maps and the French tokenizer rules in sync
// with data/fr/build.py and the web app.

data class Token(val start: Int, val end: Int, val text: String, val lemma: String, val name: Boolean)

enum class WordStatus { Known, Learning, Queued, New }
data class Coverage(val known: Int, val learning: Int, val queued: Int, val newWords: Int, val total: Int, val pct: Double)

private val WORD = Regex("""\p{L}+(?:['-]\p{L}+)*'?""")
private val PART = Regex("""[^'-]+'?""")
private val SENTENCE_START = Regex("""(?:[.!?…:«»"“”—\n]\s*|^\s*)$""")

private fun isUpper(s: String): Boolean = s.firstOrNull()?.isUpperCase() == true

/** Words with offsets. Compounds Lexique knows stay whole; others split on ' and -. A mid-sentence capital is a name. */
fun tokenize(text: String, forms: Map<String, String>, elision: Map<String, String>): List<Token> {
    val out = mutableListOf<Token>()
    fun tok(start: Int, word: String, lemma: String?, name: Boolean = lemma == null && isUpper(word)) {
        out += Token(start, start + word.length, word, lemma ?: fold(word), name)
    }
    val plain = text.replace('’', '\'') // same length, so offsets match the original
    for (m in WORD.findAll(plain)) {
        val word = m.value
        val start = m.range.first
        if (isUpper(word) && !SENTENCE_START.containsMatchIn(plain.substring(maxOf(0, start - 3), start))) {
            tok(start, word, null, true)
            continue
        }
        val whole = forms[fold(word)]
        if (whole != null && !word.endsWith("'")) {
            tok(start, word, whole)
            continue
        }
        for (p in PART.findAll(word)) {
            val part = p.value
            val at = start + p.range.first
            when {
                p.range.first > 0 && isUpper(part) -> tok(at, part, null, true) // L'Italie
                p.range.first > 0 && fold(part) == "t" -> Unit // the euphonic -t- in a-t-il
                else -> tok(at, part, if (part.endsWith("'")) elision[fold(part.dropLast(1))] else forms[fold(part)])
            }
        }
    }
    return out
}

/** Coverage of a token list; names don't count. Only known + learning count as understood: queuing isn't learning. */
fun coverage(tokens: List<Token>, statusOf: (String) -> WordStatus): Coverage {
    var known = 0
    var learning = 0
    var queued = 0
    var newWords = 0
    for (t in tokens) {
        if (t.name) continue
        when (statusOf(t.lemma)) {
            WordStatus.Known -> known++
            WordStatus.Learning -> learning++
            WordStatus.Queued -> queued++
            WordStatus.New -> newWords++
        }
    }
    val total = known + learning + queued + newWords
    return Coverage(known, learning, queued, newWords, total, if (total == 0) 0.0 else (known + learning).toDouble() / total)
}

/** The lemmas to learn (most frequent in the text first) to reach `target` coverage. */
fun unlockList(tokens: List<Token>, statusOf: (String) -> WordStatus, target: Double = 0.95): List<String> {
    val c = coverage(tokens, statusOf)
    var gap = c.queued + c.newWords - floor(c.total * (1 - target)).toInt()
    val counts = LinkedHashMap<String, Int>()
    for (t in tokens) {
        if (t.name) continue
        val s = statusOf(t.lemma)
        if (s == WordStatus.New || s == WordStatus.Queued) counts[t.lemma] = (counts[t.lemma] ?: 0) + 1
    }
    val out = mutableListOf<String>()
    for ((lemma, n) in counts.entries.sortedByDescending { it.value }) {
        if (gap <= 0) break
        gap -= n
        out += lemma
    }
    return out
}

/** The sentence around offset `at`, cropped to ~160 chars so a mined card stays readable. */
fun sentenceAt(text: String, at: Int): String {
    for (m in Regex("[^.!?…\\n]+[.!?…]*").findAll(text)) {
        if (at < m.range.first || at >= m.range.last + 1) continue
        val s = m.value.trim()
        if (s.length <= 160) return s
        val off = at - m.range.first - (m.value.length - m.value.trimStart().length)
        return "…" + s.substring(maxOf(0, off - 80), minOf(s.length, off + 80)).trim() + "…"
    }
    return ""
}

/** [before, match, after] around the first whole-word occurrence of `form` (case-insensitive). */
fun blank(sentence: String, form: String): Triple<String, String, String> {
    if (form.isEmpty()) return Triple(sentence, "", "")
    var from = 0
    while (true) {
        val i = sentence.indexOf(form, from, ignoreCase = true)
        if (i < 0) return Triple(sentence, "", "")
        val before = if (i == 0) null else sentence[i - 1]
        val after = if (i + form.length >= sentence.length) null else sentence[i + form.length]
        if ((before == null || !before.isLetter()) && (after == null || !after.isLetter())) {
            return Triple(sentence.substring(0, i), sentence.substring(i, i + form.length), sentence.substring(i + form.length))
        }
        from = i + 1
    }
}
