package io.github.vykosmolt.pluton.core

// Writing corrections become short retrieval drills: see the original, type the fix, then read why.
// This is the "feed the error log back into practice" loop the P0 writing work left open.

data class ErrorDrill(
    val original: String,
    val fix: String,
    val why: String,
    val kind: String,
)

/** Recent corrections as drills: newest first, blank/unchanged pairs dropped, duplicates collapsed. */
fun errorDrills(corrections: List<CorrectionRecord>, limit: Int = 20): List<ErrorDrill> =
    corrections.asSequence()
        .filter { it.original.isNotBlank() && it.fix.isNotBlank() && it.original.trim() != it.fix.trim() }
        .sortedByDescending { it.at }
        .distinctBy { norm(it.original) to norm(it.fix) }
        .take(limit.coerceAtLeast(0))
        .map { ErrorDrill(it.original.trim(), it.fix.trim(), it.why.trim(), it.kind.trim()) }
        .toList()

/** Grade a typed fix: exact, accent/typo "almost", or wrong. */
fun gradeError(typed: String, expected: String): GradeResult =
    grade(typed, expected, lemma = "", forms = emptyMap())
