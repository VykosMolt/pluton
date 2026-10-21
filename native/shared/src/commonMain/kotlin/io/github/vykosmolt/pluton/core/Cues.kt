package io.github.vykosmolt.pluton.core

// Subtitle parsing, ported from the web app's src/lib/logic.js. SRT and VTT both use the same cue shapes.

data class Cue(val start: Double, val end: Double, val text: String)

private val TIME = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{3})""")
private val TAGS = Regex("""<[^>]+>|\{[^}]+\}""")
private val BLANK_LINES = Regex("""\n{2,}""")
private val SPACES = Regex("""\s+""")

/** Timed lines from .srt / .vtt, sorted by start. Tags and cue settings are dropped. */
fun parseCues(s: String): List<Cue> = s.replace("\r", "").split(BLANK_LINES).mapNotNull { block ->
    val lines = block.lines()
    val i = lines.indexOfFirst { "-->" in it }
    if (i < 0) return@mapNotNull null
    val sides = lines[i].split("-->")
    val a = sides.getOrNull(0)?.let(TIME::find)?.groupValues
    val b = sides.getOrNull(1)?.let(TIME::find)?.groupValues
    val text = lines.drop(i + 1).joinToString(" ").replace(TAGS, "").replace(SPACES, " ").trim()
    if (a == null || b == null || text.isEmpty()) null else Cue(secs(a), secs(b), text)
}.sortedBy { it.start }

private fun secs(g: List<String>): Double =
    (g.getOrNull(1)?.toIntOrNull() ?: 0) * 3600 + g[2].toInt() * 60 + g[3].toInt() + g[4].toInt() / 1000.0

/** Index of the cue on screen at time t: the last one started (kept through gaps), or -1 before the first. */
fun cueAt(cues: List<Cue>, t: Double): Int {
    var lo = 0
    var hi = cues.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (cues[mid].start <= t) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}
