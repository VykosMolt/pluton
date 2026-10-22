package io.github.vykosmolt.pluton.core

// Writing corrections become short retrieval drills: see the original, type the fix, then read why.
// This is the "feed the error log back into practice" loop the P0 writing work left open.

data class ErrorDrill(
    val original: String,
    val fix: String,
    val why: String,
    val kind: String,
)

/** Apostrophe style and whitespace are ignored here, but case is not: a capitalization fix is real. */
private fun caseSensitiveNormal(s: String): String =
    s.replace('’', '\'').replace('‘', '\'').replace("œ", "oe").replace("Œ", "OE")
        .trim().replace(Regex("\\s+"), " ")

/** Recent corrections as drills: newest first, blank/unchanged pairs dropped, duplicates collapsed. */
fun errorDrills(corrections: List<CorrectionRecord>, limit: Int = 20): List<ErrorDrill> =
    corrections.asSequence()
        .filter {
            it.original.isNotBlank() && it.fix.isNotBlank() &&
                caseSensitiveNormal(it.original) != caseSensitiveNormal(it.fix)
        }
        .sortedByDescending { it.at }
        .distinctBy { norm(it.original) to norm(it.fix) }
        .take(limit.coerceAtLeast(0))
        .map { ErrorDrill(it.original.trim(), it.fix.trim(), it.why.trim(), it.kind.trim()) }
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
