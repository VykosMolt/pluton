package io.github.vykosmolt.pluton.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// The same data/fr/build.py output the web app ships. Decoding lives in the shared core so Android and iOS load
// identical dictionaries.

@Serializable
data class Word(
    val lemma: String,
    val pos: String = "",
    val display: String = lemma,
    val gloss: String = "",
    val gender: String? = null,
    val sentence: String? = null,
    val translation: String? = null,
    val form: String? = null,
    @SerialName("audio_word") val audioWord: String? = null,
    @SerialName("audio_sentence") val audioSentence: String? = null,
)

@Serializable
data class StoryQuestion(
    @SerialName("q") val question: String,
    @SerialName("a") val options: List<String>,
    @SerialName("ok") val correct: Int,
    val why: String = "",
)

data class StorySentence(val start: Int, val end: Int, val audio: String)

// build.py writes [start, end, audioId] triples; decode them lazily so the mixed JSON doesn't need a custom serializer.
private fun audioRows(rows: List<List<JsonElement>>): List<StorySentence> = rows.mapNotNull { row ->
    val start = row.getOrNull(0)?.jsonPrimitive?.intOrNull
    val end = row.getOrNull(1)?.jsonPrimitive?.intOrNull
    val audio = row.getOrNull(2)?.jsonPrimitive?.contentOrNull
    if (start != null && end != null && audio != null) StorySentence(start, end, audio) else null
}

@Serializable
data class StoryChapter(
    val title: String,
    val body: String,
    val questions: List<StoryQuestion> = emptyList(),
    val sentences: List<List<JsonElement>> = emptyList(),
) {
    fun audioSentences(): List<StorySentence> = audioRows(sentences)
}

@Serializable
data class ShelfText(
    val title: String,
    val author: String = "",
    val year: Int = 0,
    val lang: String = "fr",
    val body: String,
    val source: String = "",
    val sentences: List<List<JsonElement>> = emptyList(),
) {
    fun audioSentences(): List<StorySentence> = audioRows(sentences)
}

private val json = Json { ignoreUnknownKeys = true }

fun decodeWords(text: String): List<Word> = json.decodeFromString(text)
fun decodeForms(text: String): Map<String, String> = json.decodeFromString(text)
fun decodeStory(text: String): List<StoryChapter> = json.decodeFromString(text)
fun decodeShelf(text: String): List<ShelfText> = json.decodeFromString(text)

/** French elisions the tokenizer expands, matching src/lib/lang.js. */
val FRENCH_ELISION: Map<String, String> = mapOf(
    "l" to "le", "d" to "de", "j" to "je", "m" to "me", "t" to "te", "s" to "se", "n" to "ne", "c" to "ce",
    "qu" to "que", "jusqu" to "jusque", "lorsqu" to "lorsque", "puisqu" to "puisque", "quoiqu" to "quoique",
)
