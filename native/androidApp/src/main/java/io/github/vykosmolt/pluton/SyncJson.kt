package io.github.vykosmolt.pluton

import io.github.vykosmolt.pluton.core.Card
import io.github.vykosmolt.pluton.core.Mined
import io.github.vykosmolt.pluton.core.PullRow
import io.github.vykosmolt.pluton.core.PushRow
import io.github.vykosmolt.pluton.core.ReviewEntry
import io.github.vykosmolt.pluton.core.State
import io.github.vykosmolt.pluton.core.SyncCard
import io.github.vykosmolt.pluton.core.SyncText
import io.github.vykosmolt.pluton.core.createEmptyCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.OffsetDateTime

// The web app's record shapes (src/lib/sync.js + db.js), so both apps can share one Supabase account.
// Cards use ts-fsrs's snake_case fields and numeric states; dates cross the wire as ISO-8601 strings.

internal fun iso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

internal fun parseIso(text: String): Long? =
    runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(text).toEpochMilli() }
        .getOrNull()

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.content?.toDoubleOrNull()
private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun cardToJson(card: SyncCard): JsonObject = buildJsonObject {
    put("lemma", card.lemma)
    put("updated", card.updated)
    put("known", card.known)
    put("added", card.added)
    putJsonObject("fsrs") {
        put("due", iso(card.fsrs.due))
        put("stability", card.fsrs.stability)
        put("difficulty", card.fsrs.difficulty)
        put("elapsed_days", card.fsrs.elapsedDays)
        put("scheduled_days", card.fsrs.scheduledDays)
        put("reps", card.fsrs.reps)
        put("lapses", card.fsrs.lapses)
        put("learning_steps", card.fsrs.learningSteps)
        put("state", card.fsrs.state.ordinal)
        val last = card.fsrs.lastReview
        if (last != null) put("last_review", iso(last)) else put("last_review", JsonNull)
    }
    card.mined?.let {
        putJsonObject("mined") {
            put("sentence", it.sentence)
            put("form", it.form)
            put("translation", it.translation)
        }
    }
}

internal fun cardFromJson(lemma: String, data: JsonObject, updatedAt: Long): SyncCard {
    val f = data.obj("fsrs")
    val fsrs = if (f == null) createEmptyCard(0) else Card(
        due = f.str("due")?.let(::parseIso) ?: f.long("due") ?: 0,
        stability = f.double("stability") ?: 0.0,
        difficulty = f.double("difficulty") ?: 0.0,
        elapsedDays = f.int("elapsed_days") ?: 0,
        scheduledDays = f.int("scheduled_days") ?: 0,
        reps = f.int("reps") ?: 0,
        lapses = f.int("lapses") ?: 0,
        learningSteps = f.int("learning_steps") ?: 0,
        state = State.entries.getOrElse(f.int("state") ?: 0) { State.New },
        lastReview = f.str("last_review")?.let(::parseIso) ?: f.long("last_review"),
    )
    val mined = data.obj("mined")?.let {
        Mined(it.str("sentence").orEmpty(), it.str("form").orEmpty(), it.str("translation").orEmpty())
    }
    return SyncCard(
        lemma = data.str("lemma") ?: lemma,
        updated = data.long("updated") ?: updatedAt,
        known = data.bool("known") ?: false,
        added = data.long("added") ?: 0,
        fsrs = fsrs,
        mined = mined,
    )
}

internal fun pullRowOf(store: String, key: String, data: JsonObject, updatedAt: Long): PullRow? = when (store) {
    "cards" -> if (data.bool("deleted") == true) {
        PullRow.CardRow(key, createEmptyCard(0).let { SyncCard(key, updatedAt, fsrs = it) }, deleted = true, updatedAt = updatedAt)
    } else {
        PullRow.CardRow(key, cardFromJson(key, data, updatedAt), updatedAt = updatedAt)
    }
    "texts" -> PullRow.TextRow(
        key = key,
        title = data.str("title").orEmpty(),
        body = data.str("body").orEmpty(),
        added = data.long("added") ?: 0,
        deleted = data.bool("deleted") == true,
        updatedAt = updatedAt,
    )
    "log" -> {
        val lemma = data.str("lemma")
        val rating = data.int("rating")
        val at = data.long("at")
        if (lemma == null || rating == null || at == null) null
        else PullRow.LogRow(
            key = key,
            entry = ReviewEntry(lemma, rating, data.str("mode").orEmpty(), at, data.bool("overridden") == true),
            updatedAt = updatedAt,
        )
    }
    else -> null
}

internal fun pushRowJson(row: PushRow): JsonObject = buildJsonObject {
    put("store", row.store)
    put("key", row.key)
    put("updated_at", iso(row.updatedAt))
    putJsonObject("data") {
        when (row.store) {
            "cards" -> row.card?.let { card -> cardToJson(card).forEach { (k, v) -> put(k, v) } }
            "texts" -> {
                put("id", row.key)
                if (row.deleted) {
                    put("deleted", true)
                } else row.text?.let { t ->
                    put("title", t.title)
                    put("body", t.body)
                    put("added", t.added)
                    put("updated", t.updated)
                }
            }
            "log" -> if (row.deleted) put("deleted", true) else row.log?.let { e ->
                put("lemma", e.lemma)
                put("rating", e.rating)
                put("mode", e.mode)
                put("at", e.at)
                if (e.overridden) put("overridden", true)
            }
            else -> put("deleted", true)
        }
    }
}

/** The `{data, updated_at, ...}` rows returned by PostgREST, parsed into the shared pull model. */
internal fun parsePullResponse(body: String): List<PullRow> {
    val array = runCatching { Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: return emptyList()
    val rows = mutableListOf<PullRow>()
    for (item in array) {
        val obj = item as? JsonObject ?: continue
        val store = obj.str("store") ?: continue
        val key = obj.str("key") ?: continue
        val data = obj.obj("data") ?: continue
        val updatedAt = obj.str("updated_at")?.let(::parseIso) ?: 0
        pullRowOf(store, key, data, updatedAt)?.let(rows::add)
    }
    return rows
}

internal fun maxSyncedAtOf(body: String): Long {
    val array = runCatching { Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonArray }.getOrNull() ?: return 0
    return array.mapNotNull { (it as? JsonObject)?.str("synced_at")?.let(::parseIso) }.maxOrNull() ?: 0
}

internal fun textToJson(text: SyncText, key: String): JsonObject = buildJsonObject {
    put("id", key)
    put("title", text.title)
    put("body", text.body)
    put("added", text.added)
    put("updated", text.updated)
}
