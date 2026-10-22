package io.github.vykosmolt.pluton.core

// Writing corrections become short retrieval drills: see the original, type the fix, then read why.
// This is the "feed the error log back into practice" loop the P0 writing work left open.

data class ErrorDrill(
    val id: Long,
    val original: String,
    val fix: String,
    val why: String,
    val kind: String,
    val nextDue: Long,
    val reps: Int,
)

/** Apostrophe style and whitespace are ignored here, but case is not: a capitalization fix is real. */
private fun caseSensitiveNormal(s: String): String =
    s.replace('’', '\'').replace('‘', '\'').replace("œ", "oe").replace("Œ", "OE")
        .trim().replace(Regex("\\s+"), " ")

/** Corrections that are due now: unresolved, changed, de-duplicated by pair, earliest due first. */
fun errorDrills(corrections: List<CorrectionRecord>, now: Long, limit: Int = 20): List<ErrorDrill> =
    corrections.asSequence()
        .filter {
            !it.resolved && it.nextDue <= now && it.original.isNotBlank() && it.fix.isNotBlank() &&
                caseSensitiveNormal(it.original) != caseSensitiveNormal(it.fix)
        }
        .sortedWith(compareBy({ it.nextDue }, { -it.at }))
        .distinctBy { norm(it.original) to norm(it.fix) }
        .take(limit.coerceAtLeast(0))
        .map {
            ErrorDrill(
                id = it.id,
                original = it.original.trim(),
                fix = it.fix.trim(),
                why = it.why.trim(),
                kind = it.kind.trim(),
                nextDue = it.nextDue,
                reps = it.reps,
            )
        }
        .toList()

/** Grade a typed fix: exact, accent/typo/case "almost", or wrong. */
fun gradeError(typed: String, expected: String): GradeResult {
    val graded = grade(typed, expected, lemma = "", forms = emptyMap())
    if (graded.result != "right") return graded
    return if (caseSensitiveNormal(typed) != caseSensitiveNormal(expected)) {
        GradeResult("almost", "case")
    } else {
        graded
    }
}
