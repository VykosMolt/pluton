package io.github.vykosmolt.pluton.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Conjugation and grammar reference data from data/fr/build.py. Card keys match the web app's lemmas:
// conj/<verb>/<tense> and gram/<topic>, so the same cards sync between both apps once sync exists.

const val CONJ = "conj/"
const val GRAM = "gram/"

fun conjKey(verb: String, tense: String) = "$CONJ$verb/$tense"
fun gramKey(id: String) = "$GRAM$id"

fun parseConj(key: String): Pair<String, String>? {
    val parts = key.split("/")
    return if (parts.size == 3 && parts[0] == "conj") parts[1] to parts[2] else null
}

@Serializable
data class ConjTable(
    val pp: String = "",
    val aux: String = "",
    val t: Map<String, List<String?>> = emptyMap(),
    val audio: Map<String, List<String?>> = emptyMap(),
)

@Serializable
data class GrammarItem(val q: String, val a: List<String> = emptyList(), val ask: String? = null)

@Serializable
data class GrammarTopic(
    val id: String,
    val level: String = "",
    val name: String,
    val en: String = "",
    val how: String = "",
    val items: List<GrammarItem> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

fun decodeConj(s: String): Map<String, ConjTable> = json.decodeFromString(s)
fun decodeGrammar(s: String): List<GrammarTopic> = json.decodeFromString(s)

data class Tense(val id: String, val name: String, val en: String, val how: String)

val FRENCH_PRONOUNS = listOf("je", "tu", "il", "nous", "vous", "ils")

// Mirrors LANG.tenses in src/lib/lang.js; keep the wording in sync.
val FRENCH_TENSES = listOf(
    Tense(
        "ind:pre", "présent", "present",
        "Regular -er verbs: -e, -es, -e, -ons, -ez, -ent. The most common verbs are irregular: learn them as tables.",
    ),
    Tense(
        "pc", "passé composé", "past: did, have done",
        "avoir or être in the present + past participle. Verbs of movement and change of state (aller, venir, partir, mourir…) take être, and then the participle agrees: elle est allée, ils sont venus.",
    ),
    Tense(
        "ind:imp", "imparfait", "past: was doing, used to",
        "Stem of the present nous form (nous parlons → parl-) + -ais, -ais, -ait, -ions, -iez, -aient. Only être is irregular: ét-.",
    ),
    Tense(
        "ind:fut", "futur", "future: will",
        "Infinitive (drop a final -e) + -ai, -as, -a, -ons, -ez, -ont. Irregular stems: ir- (aller), ser- (être), aur- (avoir), fer- (faire), pourr-, voudr-, viendr-, verr-, devr-, saur-.",
    ),
    Tense(
        "cnd:pre", "conditionnel", "would",
        "Future stem + imparfait endings: -ais, -ais, -ait, -ions, -iez, -aient. (je pourrais, nous irions)",
    ),
    Tense(
        "sub:pre", "subjonctif", "subjunctive: that … (after il faut que, vouloir que, bien que…)",
        "je, tu, il, ils: stem of the present ils form (ils prennent → prenn-) + -e, -es, -e, -ent. nous, vous: same as the imparfait. Irregular: sois, aie, aille, fasse, puisse, sache, veuille.",
    ),
)

/** What precedes the verb in a table row: "que je", "qu'il", "nous". */
fun subject(i: Int, tense: String): String =
    (if (tense == "sub:pre") (if (i == 2 || i == 5) "qu'" else "que ") else "") + FRENCH_PRONOUNS[i]
