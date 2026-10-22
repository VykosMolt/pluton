package io.github.vykosmolt.pluton.core

// Le Chemin: ten stages from A1 to C1, ported from src/lib/path.js. The path suggests and celebrates; it never locks
// anything. Targets: words learned, story chapters read, conjugation tables and grammar topics in review, listening
// minutes (cumulative), and optional talking/writing bonuses.

data class VerbGoal(val tense: String?, val n: Int)

data class Stage(
    val place: String,
    val en: String,
    val level: String,
    val words: Int,
    val verbs: VerbGoal? = null,
    val grammar: Int = 0,
    val read: List<String> = emptyList(),
    val listen: Int = 0,
    val talk: Int? = null,
    val speak: Int? = null,
    val write: Int? = null,
    val fr: String,
    val say: String,
)

val STAGES = listOf(
    Stage(
        "Le Mur", "The Wall", "A1", 75, read = listOf("story-0"), listen = 3,
        fr = "Il fait noir. Pluton se réveille derrière un mur.",
        say = "Pitch dark. Pluton wakes up behind a wall.",
    ),
    Stage(
        "La Cave", "The Cellar", "A1", 200, VerbGoal("ind:pre", 5), read = listOf("story-1"), listen = 10,
        fr = "Une cave froide, une porte fermée… et une petite fenêtre.",
        say = "A cold cellar, a locked door… and a small window.",
    ),
    Stage(
        "La Maison", "The House", "A2", 400, VerbGoal("ind:pre", 12), grammar = 1, read = listOf("story-2"),
        listen = 20, talk = 5, speak = 3, write = 1,
        fr = "Une vieille maison, du lait, et une petite fille qui n'a pas peur.",
        say = "An old house, some milk, and a girl who isn't afraid.",
    ),
    Stage(
        "Le Village", "The Village", "A2", 700, VerbGoal("pc", 8), grammar = 4, read = listOf("story-3"),
        listen = 40, talk = 10, speak = 5, write = 2,
        fr = "Le marché du village. Un chat noir, ça porte malheur ?",
        say = "Market day. Are black cats really bad luck?",
    ),
    Stage(
        "La Route", "The Road", "B1", 1100, VerbGoal("ind:imp", 8), grammar = 6, read = listOf("story-4"),
        listen = 75, talk = 20, speak = 10, write = 3,
        fr = "La route de Paris est longue. Heureusement, il y a Brutus.",
        say = "The road to Paris is long. Luckily there's Brutus.",
    ),
    Stage(
        "Le Fleuve", "The River", "B1", 1600, VerbGoal("ind:fut", 8), grammar = 8, read = listOf("story-5"),
        listen = 120, talk = 30, speak = 15, write = 5,
        fr = "Remonter la Seine à bord d'un vieux bateau.",
        say = "Up the Seine on an old boat.",
    ),
    Stage(
        "Paris", "Paris", "B2", 2300, VerbGoal("cnd:pre", 8), grammar = 10,
        read = listOf("story-6", "Les Étoiles"), listen = 240, talk = 50, speak = 25, write = 8,
        fr = "Les toits de Montmartre, et une chatte qui connaît les règles.",
        say = "The roofs of Montmartre, and a cat who knows the rules.",
    ),
    Stage(
        "Les Catacombes", "The Catacombs", "B2", 3200, VerbGoal("sub:pre", 8), grammar = 12,
        read = listOf("story-7", "La Parure"), listen = 420, talk = 80, speak = 40, write = 12,
        fr = "Sous Paris, une ville de morts. Il ne fallait pas descendre.",
        say = "Under Paris, a city of the dead. He shouldn't have gone down.",
    ),
    Stage(
        "La Bibliothèque", "The Library", "C1", 4500, VerbGoal(null, 150), grammar = 13,
        read = listOf("story-8", "Le Tour du monde en quatre-vingts jours, chapitre 1"),
        listen = 720, talk = 120, speak = 60, write = 16,
        fr = "Les notes de Baudelaire, et une lettre sans signature.",
        say = "Baudelaire's notes, and an unsigned letter.",
    ),
    Stage(
        "L'Opéra", "The Opéra", "C1", 6000, VerbGoal(null, 300), grammar = 15,
        read = listOf("story-9", "story-10", "story-11", "La Chèvre de monsieur Seguin"),
        listen = 1200, talk = 200, speak = 100, write = 20,
        fr = "Un bal masqué, un lac sous la scène, et quelqu'un qui attend.",
        say = "A masked ball, a lake under the stage, and someone waiting.",
    ),
)

private val TENSE_NAMES = mapOf(
    "ind:pre" to "présent",
    "pc" to "passé composé",
    "ind:imp" to "imparfait",
    "ind:fut" to "futur",
    "cnd:pre" to "conditionnel",
    "sub:pre" to "subjonctif",
)

data class PathMetrics(
    val learned: Int = 0,
    val tables: Map<String, Int> = emptyMap(),
    val tablesTotal: Int = 0,
    val grammar: Int = 0,
    val read: Set<String> = emptySet(),
    val listenMin: Int = 0,
    val talked: Int = 0,
    val spoken: Int = 0,
    val written: Int = 0,
)

enum class GoalKind { Words, Verbs, Grammar, Read, Listen, Talk, Speak, Write }

data class Goal(
    val kind: GoalKind,
    val id: String?,
    val label: String,
    val have: Int,
    val need: Int,
    val bonus: Boolean,
    val done: Boolean,
    val pct: Double,
)

data class StageProgress(val stage: Stage, val index: Int, val goals: List<Goal>, val complete: Boolean, val pct: Double)

data class PathState(val stages: List<StageProgress>, val current: Int)

fun goalsOf(stage: Stage, m: PathMetrics, titles: Map<String, String> = emptyMap()): List<Goal> {
    val goals = mutableListOf(
        Goal(GoalKind.Words, null, "Learn ${stage.words} words", m.learned, stage.words, false, false, 0.0),
    )
    stage.verbs?.let { (tense, n) ->
        val label = if (tense != null) "$n verb tables in the ${TENSE_NAMES[tense]}" else "$n verb tables in all"
        val have = if (tense != null) m.tables[tense] ?: 0 else m.tablesTotal
        goals += Goal(GoalKind.Verbs, null, label, have, n, false, false, 0.0)
    }
    if (stage.grammar > 0) {
        val plural = if (stage.grammar > 1) "s" else ""
        goals += Goal(GoalKind.Grammar, null, "${stage.grammar} grammar topic$plural in review", m.grammar, stage.grammar, false, false, 0.0)
    }
    for (id in stage.read) {
        goals += Goal(GoalKind.Read, id, "Read “${titles[id] ?: id}”", if (id in m.read) 1 else 0, 1, false, false, 0.0)
    }
    goals += Goal(GoalKind.Listen, null, "Listen for ${stage.listen} minutes in all", m.listenMin, stage.listen, false, false, 0.0)
    stage.talk?.let {
        goals += Goal(GoalKind.Talk, null, "Talk with Pluton $it times", m.talked, it, true, false, 0.0)
    }
    stage.speak?.let {
        goals += Goal(GoalKind.Speak, null, "Speak French aloud $it times", m.spoken, it, true, false, 0.0)
    }
    stage.write?.let {
        goals += Goal(GoalKind.Write, null, "Have $it texts corrected", m.written, it, true, false, 0.0)
    }
    return goals.map { it.copy(done = it.have >= it.need, pct = minOf(1.0, it.have.toDouble() / it.need)) }
}

fun pathState(m: PathMetrics, titles: Map<String, String> = emptyMap()): PathState {
    val stages = STAGES.mapIndexed { i, s ->
        val goals = goalsOf(s, m, titles)
        val core = goals.filter { !it.bonus }
        StageProgress(s, i, goals, core.all { it.done }, core.sumOf { it.pct } / core.size)
    }
    val current = stages.indexOfFirst { !it.complete }.let { if (it < 0) stages.size else it }
    return PathState(stages, current)
}

/** The one thing to do next besides the daily run (the web's focus(); native has no AI, so writing is never offered). */
fun focus(stage: StageProgress?): Goal? {
    if (stage == null) return null
    val open = stage.goals.filter { !it.done }
    return open.firstOrNull { it.kind == GoalKind.Read }
        ?: open.firstOrNull { it.kind == GoalKind.Listen }
        ?: open.firstOrNull { it.kind == GoalKind.Grammar }
        ?: open.firstOrNull { it.bonus && it.kind == GoalKind.Talk }
}

/** Counts what the path needs from the card store and the cumulative stats (matches App.svelte's metrics). */
fun pathMetrics(
    cards: List<CardState>,
    read: Iterable<String>,
    listenedSeconds: Int,
    spoken: Int,
    talked: Int = 0,
    written: Int = 0,
): PathMetrics {
    var learned = 0
    var tablesTotal = 0
    var grammar = 0
    val tables = mutableMapOf<String, Int>()
    for (c in cards) {
        if (!graduated(c)) continue
        when {
            isWord(c) -> learned++
            c.lemma.startsWith(GRAM) -> grammar++
            c.lemma.startsWith(CONJ) && !c.known -> {
                parseConj(c.lemma)?.second?.let { tables[it] = (tables[it] ?: 0) + 1 }
                tablesTotal++
            }
        }
    }
    return PathMetrics(learned, tables, tablesTotal, grammar, read.toSet(), listenedSeconds / 60, talked, spoken, written)
}
