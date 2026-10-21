package io.github.vykosmolt.pluton.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.vykosmolt.pluton.core.db.PlutonDb
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEngineTest {
    private fun store(): Store {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlutonDb.Schema.create(driver).value
        return Store(PlutonDb(driver))
    }

    /** In-memory PostgREST + push(): last write wins per record, pull by server sync stamp. */
    private class FakeServer : SyncApi {
        private data class Rec(val row: PullRow, val updatedAt: Long, val syncedAt: Long)
        private val records = mutableMapOf<String, Rec>()
        private var clock = 0L
        private fun key(store: String, key: String) = "$store\t$key"

        override suspend fun pull(since: Long): PullResult {
            val rows = records.values.filter { it.syncedAt > since }.sortedBy { it.syncedAt }.map { it.row }
            return PullResult(rows, records.values.maxOfOrNull { it.syncedAt } ?: 0, rows.size)
        }

        override suspend fun push(rows: List<PushRow>) {
            for (r in rows) {
                val k = key(r.store, r.key)
                val current = records[k]
                if (current != null && current.updatedAt >= r.updatedAt) continue
                clock += 1000
                val row = when (r.store) {
                    "cards" -> PullRow.CardRow(
                        r.key,
                        r.card ?: SyncCard(r.key, r.updatedAt),
                        deleted = r.deleted,
                        updatedAt = r.updatedAt,
                    )
                    "texts" -> PullRow.TextRow(
                        key = r.key,
                        title = r.text?.title.orEmpty(),
                        body = r.text?.body.orEmpty(),
                        added = r.text?.added ?: 0,
                        deleted = r.deleted,
                        updatedAt = r.updatedAt,
                    )
                    else -> PullRow.LogRow(r.key, r.log!!, r.updatedAt)
                }
                records[k] = Rec(row, r.updatedAt, clock)
            }
        }

        fun count(store: String) = records.keys.count { it.startsWith("$store\t") }
    }

    @Test
    fun twoDevicesConvergeCardsTextsAndReviews() = runBlocking {
        val server = FakeServer()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val a = store()
        val word = Word("chat", pos = "noun", sentence = "Le chat dort.", form = "chat")
        a.addCard(word, "chat", null, now)
        val reviewed = Scheduler().next(createEmptyCard(now), now, Rating.Good)
        a.saveAnswer(CardState("chat", reviewed, added = now), ReviewEntry("chat", 3, "cloze", now))
        a.addText("Titre", "Bonjour", now)
        SyncEngine(a, server).sync()
        assertEquals(1, server.count("cards"))
        assertEquals(1, server.count("log"))
        assertEquals(1, server.count("texts"))

        val b = store()
        SyncEngine(b, server).sync()
        val first = b.card("chat")!!
        assertEquals(1, first.fsrs.reps)
        assertEquals(reviewed.due, first.fsrs.due) // deterministic replay agrees
        assertEquals("Titre", b.texts().single().title)
        assertTrue(b.texts().single().remoteKey!!.startsWith("${a.syncState().device}:"))

        // B answers; both devices end up with both reviews.
        val second = Scheduler().next(first.fsrs, now + 2 * day, Rating.Good)
        b.saveAnswer(first.copy(fsrs = second), ReviewEntry("chat", 3, "cloze", now + 2 * day))
        SyncEngine(b, server).sync()
        SyncEngine(a, server).sync()
        val merged = a.card("chat")!!
        assertEquals(2, merged.fsrs.reps)
        assertEquals(second.due, merged.fsrs.due)
        assertEquals(2, a.allLog().size)
        assertEquals(1, a.localLog().size) // only A's own review is pushed from A
        // A's own card was rebuilt from the union, so a third sync changes nothing.
        SyncEngine(a, server).sync()
        assertEquals(2, a.allLog().size)
        assertEquals(2, a.card("chat")!!.fsrs.reps)
    }

    @Test
    fun aDeletionTravelsAndSurvivesReplay() = runBlocking {
        val server = FakeServer()
        val now = System.currentTimeMillis()
        val a = store()
        a.addCard(Word("chien", pos = "noun", sentence = "Le chien dort.", form = "chien"), "chien", null, now)
        a.saveAnswer(
            CardState("chien", createEmptyCard(now).copy(state = State.Review), added = now),
            ReviewEntry("chien", 3, "cloze", now),
        )
        SyncEngine(a, server).sync()
        val b = store()
        SyncEngine(b, server).sync()
        assertEquals(1, b.cards().size)
        a.deleteCard("chien", System.currentTimeMillis() + 1000)
        SyncEngine(a, server).sync()
        SyncEngine(b, server).sync()
        assertEquals(emptyList(), b.cards())
    }
}
