package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncTest {
    private fun card(lemma: String, updated: Long, added: Long = 500, known: Boolean = false) =
        SyncCard(lemma, updated, known, added, createEmptyCard(0))

    private fun describe(op: SyncOp): String = when (op) {
        is SyncOp.PutCard -> "put card ${op.card.lemma}"
        is SyncOp.DeleteCard -> "del card ${op.lemma}"
        is SyncOp.PutText -> "put text ${op.text.remoteKey}"
        is SyncOp.DeleteText -> "del text ${op.localId ?: op.remoteKey}"
        is SyncOp.PutLog -> "put log ${op.remoteKey}"
    }

    @Test
    fun pullNewerRemoteWinsAndOlderOnesAndOwnEchoesAreIgnored() {
        val local = SyncLocal(
            device = "dev1",
            cards = mapOf("chat" to card("chat", 2000), "chien" to card("chien", 9000)),
            texts = mapOf(3L to SyncText(3, null, "mine", "body", 1000, 9000)),
        )
        val rows = listOf(
            PullRow.CardRow("chat", card("chat", 5000), updatedAt = 5000),
            PullRow.CardRow("chien", card("chien", 5000), updatedAt = 5000),
            PullRow.CardRow("maison", card("maison", 5000), updatedAt = 5000),
            PullRow.TextRow("dev1:3", title = "mine", body = "body", added = 1000, updatedAt = 9000),
            PullRow.TextRow("dev2:7", title = "theirs", body = "b", added = 0, updatedAt = 4000),
            PullRow.LogRow("dev1:5", ReviewEntry("chat", 3, "cloze", 1), 1),
            PullRow.LogRow("dev2:5", ReviewEntry("chat", 3, "cloze", 2), 1),
        )
        val ops = plan(rows, local)
        assertEquals(listOf("put card chat", "put card maison", "put text dev2:7", "put log dev2:5"), ops.map(::describe))
        assertEquals(5000, (ops[0] as SyncOp.PutCard).card.updated)
        assertEquals(4000, (ops[2] as SyncOp.PutText).text.updated)
    }

    @Test
    fun anotherDeviceCanEditOrDeleteTheTextThisDeviceCreated() {
        val local = SyncLocal(device = "dev1", texts = mapOf(3L to SyncText(3, null, "mine", "b", 1000, 1000)))
        val edited = plan(listOf(PullRow.TextRow("dev1:3", title = "edited", body = "b", added = 1000, updatedAt = 5000)), local)
        val put = edited.single() as SyncOp.PutText
        assertEquals(3L, put.text.id)
        assertEquals("edited", put.text.title)
        assertEquals("dev1:3", put.text.remoteKey)
        val deleted = plan(listOf(PullRow.TextRow("dev1:3", deleted = true, updatedAt = 5000)), local)
        assertEquals(listOf("del text 3"), deleted.map(::describe))
    }

    @Test
    fun aNewerLocalDeletionIsNotUndoneByAnOlderRemoteEdit() {
        val rows = listOf(PullRow.CardRow("chat", card("chat", 5000), updatedAt = 5000))
        val base = SyncLocal(device = "dev1")
        assertEquals(emptyList(), plan(rows, base.copy(tombstones = mapOf("cards\tchat" to 6000))))
        assertEquals(1, plan(rows, base.copy(tombstones = mapOf("cards\tchat" to 4000))).size)
    }

    @Test
    fun pulledCardIsRebuiltFromBothDevicesReviewsWithoutDoubleCounting() {
        val op = SyncOp.PutCard(SyncCard("chat", 3000, added = 500, fsrs = createEmptyCard(0)))
        val mine = listOf("1" to ReviewEntry("chat", 3, "cloze", 2000))
        val rows = listOf(PullRow.LogRow("dev2:1", ReviewEntry("chat", 3, "cloze", 1000), 1000))
        val out = replayPulled(listOf(op), mine, rows, "dev1").single() as SyncOp.PutCard
        assertEquals(2, out.card.fsrs.reps)
        // The same remote key as a local one is not applied twice.
        val own = listOf(PullRow.LogRow("dev1:1", ReviewEntry("chat", 3, "cloze", 2000), 2000))
        assertEquals(1, (replayPulled(listOf(op), mine, own, "dev1").single() as SyncOp.PutCard).card.fsrs.reps)
        // A marked-known card keeps its state.
        val known = SyncOp.PutCard(SyncCard("chat", 3000, known = true, added = 500))
        assertEquals(known, replayPulled(listOf(known), mine, emptyList(), "dev1").single())
        // The LWW winner still absorbs the other device's review.
        val cards = mapOf("chat" to SyncCard("chat", 3000, added = 500, fsrs = createEmptyCard(0).copy(reps = 1)))
        val rebuilt = replayPulled(emptyList(), mine, rows, "dev1", cards).single() as SyncOp.PutCard
        assertEquals(2, rebuilt.card.fsrs.reps)
        // ...but a pulled deletion is not resurrected by an old review.
        assertEquals(
            listOf("del card chat"),
            replayPulled(listOf(SyncOp.DeleteCard("chat")), mine, rows, "dev1", cards).map(::describe),
        )
    }

    @Test
    fun deletionsApplyUnlessTheLocalCopyChangedLater() {
        val local = SyncLocal(
            device = "dev1",
            cards = mapOf("a" to card("a", 1000), "b" to card("b", 9000)),
            texts = mapOf(4L to SyncText(4, null, "x", "y", 1000, 1000)),
        )
        val rows = listOf(
            PullRow.CardRow("a", card("a", 5000), deleted = true, updatedAt = 5000),
            PullRow.CardRow("b", card("b", 5000), deleted = true, updatedAt = 5000),
            PullRow.TextRow("dev1:4", deleted = true, updatedAt = 5000),
        )
        assertEquals(listOf("del card a", "del text 4"), plan(rows, local).map(::describe))
    }

    @Test
    fun pushOnlyWhatChangedHereSinceTheLastPush() {
        val cards = listOf(card("old", 1000), card("new", 5000), card("pulled", 6000))
        val texts = listOf(SyncText(7, null, "t", "b", 0, 4000), SyncText(0, "dev2:1", "x", "y", 0, 9000))
        val log = listOf(1L to ReviewEntry("a", 3, "cloze", 1), 2L to ReviewEntry("a", 3, "cloze", 2), 3L to ReviewEntry("a", 3, "cloze", 3))
        val tomb = mapOf("texts\t8" to 4500L, "cards\tchat" to 100L)
        val result = collect("dev1", 3000, 2, cards, texts, log, tomb)
        // Pulled records are echoed once (a server no-op: its updated_at is not newer); native doesn't track `pulled`.
        assertEquals(
            listOf("cards new", "cards pulled", "texts dev1:7", "texts dev2:1", "log dev1:3", "texts dev1:8"),
            result.rows.map { "${it.store} ${it.key}" },
        )
        assertEquals(3, result.maxLog)
        assertTrue(result.rows.last().deleted)
        assertEquals("dev1:7", result.rows[2].key)
    }
}
