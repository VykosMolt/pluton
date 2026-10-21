package io.github.vykosmolt.pluton

import io.github.vykosmolt.pluton.core.Card
import io.github.vykosmolt.pluton.core.Mined
import io.github.vykosmolt.pluton.core.PullRow
import io.github.vykosmolt.pluton.core.PushRow
import io.github.vykosmolt.pluton.core.ReviewEntry
import io.github.vykosmolt.pluton.core.State
import io.github.vykosmolt.pluton.core.SyncCard
import io.github.vykosmolt.pluton.core.SyncText
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncJsonTest {
    private val due = Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()

    @Test
    fun cardJsonRoundTripsInTheWebShape() {
        val card = SyncCard(
            lemma = "chat",
            updated = 5000,
            known = false,
            added = 1000,
            fsrs = Card(
                due = due,
                stability = 3.5,
                difficulty = 5.25,
                elapsedDays = 2,
                scheduledDays = 3,
                reps = 4,
                lapses = 1,
                learningSteps = 1,
                state = State.Review,
                lastReview = due - 1000,
            ),
            mined = Mined("Le chat dort.", "chat", "The cat sleeps."),
        )
        val json = cardToJson(card)
        assertEquals("chat", json["lemma"]!!.jsonPrimitive.content)
        assertEquals(2, json["fsrs"]!!.jsonObject["state"]!!.jsonPrimitive.int)
        assertTrue(json["fsrs"]!!.jsonObject.containsKey("elapsed_days"))
        assertEquals(card, cardFromJson("chat", json, 5000))
    }

    @Test
    fun postgrestRowsParseIntoThePullModel() {
        val body = """
            [
              {"store":"cards","key":"chat","updated_at":"2026-10-21T10:00:00.123456+00:00","synced_at":"2026-10-21T10:00:01+00:00",
               "data":{"lemma":"chat","fsrs":{"due":"2026-10-21T12:00:00.000Z","stability":3,"difficulty":5,"elapsed_days":0,
               "scheduled_days":0,"reps":0,"lapses":0,"learning_steps":0,"state":2,"last_review":null},"known":false,"added":1000}},
              {"store":"texts","key":"dev2:7","updated_at":"2026-10-21T10:00:00Z","synced_at":"2026-10-21T10:00:01Z",
               "data":{"id":"dev2:7","title":"T","body":"B","added":1}},
              {"store":"log","key":"dev2:1","updated_at":"2026-10-21T10:00:00Z","synced_at":"2026-10-21T10:00:01Z",
               "data":{"lemma":"chat","rating":3,"mode":"cloze","at":42}},
              {"store":"cards","key":"chien","updated_at":"2026-10-21T10:00:00Z","synced_at":"2026-10-21T10:00:01Z",
               "data":{"deleted":true}},
              {"store":"meta","key":"talk","updated_at":"2026-10-21T10:00:00Z","synced_at":"2026-10-21T10:00:01Z","data":{}}
            ]
        """.trimIndent()
        val rows = parsePullResponse(body)
        assertEquals(4, rows.size) // meta is not part of this sync
        val card = rows[0] as PullRow.CardRow
        assertEquals(due, card.card.fsrs.due)
        assertEquals(State.Review, card.card.fsrs.state)
        assertEquals(1000, card.card.added)
        val text = rows[1] as PullRow.TextRow
        assertEquals("T", text.title)
        assertEquals(1, text.added)
        val log = rows[2] as PullRow.LogRow
        assertEquals(ReviewEntry("chat", 3, "cloze", 42), log.entry)
        assertTrue((rows[3] as PullRow.CardRow).deleted)
        assertTrue(maxSyncedAtOf(body) > 0)
    }

    @Test
    fun pushRowsCarryTheWebFields() {
        val card = SyncCard("chat", 5000, added = 1000)
        val cardRow = pushRowJson(PushRow("cards", "chat", 5000, card = card))
        assertEquals("2026-10-21T12:00:00Z", parseIso("2026-10-21T12:00:00Z")?.let(::iso))
        assertEquals("cards", cardRow["store"]!!.jsonPrimitive.content)
        assertEquals("chat", cardRow["data"]!!.jsonObject["lemma"]!!.jsonPrimitive.content)

        val text = SyncText(7, null, "Titre", "Corps", 1000, 4000)
        val textRow = pushRowJson(PushRow("texts", "dev1:7", 4000, text = text))
        assertEquals("dev1:7", textRow["data"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Titre", textRow["data"]!!.jsonObject["title"]!!.jsonPrimitive.content)

        val deleted = pushRowJson(PushRow("texts", "dev1:8", 4500, deleted = true))
        assertTrue(deleted["data"]!!.jsonObject["deleted"]!!.jsonPrimitive.content.toBoolean())

        val logRow = pushRowJson(PushRow("log", "dev1:3", 3000, log = ReviewEntry("chat", 3, "cloze", 3000)))
        assertEquals(3, logRow["data"]!!.jsonObject["rating"]!!.jsonPrimitive.int)
        assertEquals(3000, logRow["data"]!!.jsonObject["at"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun isoParsesZAndPostgresOffsets() {
        assertEquals(due, parseIso("2026-10-21T12:00:00Z"))
        assertEquals(due, parseIso("2026-10-21T14:00:00+02:00"))
        assertEquals(due + 123, parseIso("2026-10-21T12:00:00.123Z"))
        assertEquals(null, parseIso("yesterday"))
    }
}
