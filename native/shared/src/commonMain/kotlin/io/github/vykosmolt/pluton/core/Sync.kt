package io.github.vykosmolt.pluton.core

import kotlinx.serialization.Serializable

// Merge rules for account sync, ported from src/lib/sync.js. Pure: the Android client maps these domain objects to
// the Supabase record shapes, and Store applies the resulting ops. Cards and review logs replay deterministically,
// so every device that has the same union of reviews computes the same FSRS card.

@Serializable
data class SyncCard(
    val lemma: String,
    val updated: Long = 0,
    val known: Boolean = false,
    val added: Long = 0,
    val fsrs: Card = createEmptyCard(0),
    val mined: Mined? = null,
)

@Serializable
data class SyncText(
    val id: Long = 0, // local id; 0 when the text has not been stored locally yet
    val remoteKey: String? = null,
    val title: String,
    val body: String,
    val added: Long,
    val updated: Long = 0,
)

sealed interface PullRow {
    val key: String
    val updatedAt: Long

    data class CardRow(override val key: String, val card: SyncCard, val deleted: Boolean = false, override val updatedAt: Long) : PullRow
    data class TextRow(
        override val key: String,
        val title: String = "",
        val body: String = "",
        val added: Long = 0,
        val deleted: Boolean = false,
        override val updatedAt: Long,
    ) : PullRow
    data class LogRow(override val key: String, val entry: ReviewEntry, override val updatedAt: Long) : PullRow
}

data class SyncLocal(
    val device: String,
    val cards: Map<String, SyncCard> = emptyMap(),
    val texts: Map<Long, SyncText> = emptyMap(),
    val tombstones: Map<String, Long> = emptyMap(),
)

sealed interface SyncOp {
    data class PutCard(val card: SyncCard) : SyncOp
    data class DeleteCard(val lemma: String) : SyncOp
    data class PutText(val text: SyncText) : SyncOp
    data class DeleteText(val localId: Long?, val remoteKey: String?) : SyncOp
    data class PutLog(val remoteKey: String, val entry: ReviewEntry) : SyncOp
}

/** Remote rows → local writes. A local deletion newer than the remote edit still wins. */
fun plan(rows: List<PullRow>, local: SyncLocal): List<SyncOp> {
    val ops = mutableListOf<SyncOp>()
    val textsByRemote = local.texts.values.filter { it.remoteKey != null }.associateBy { it.remoteKey!! }
    val mine = "${local.device}:"
    for (row in rows) {
        when (row) {
            is PullRow.LogRow -> if (!row.key.startsWith(mine)) ops += SyncOp.PutLog(row.key, row.entry)
            is PullRow.CardRow -> {
                val current = local.cards[row.key]
                val tombAt = local.tombstones["cards\t${row.key}"] ?: 0
                if (row.deleted) {
                    if (current != null && current.updated <= row.updatedAt) ops += SyncOp.DeleteCard(row.key)
                } else if ((current == null || current.updated < row.updatedAt) && tombAt <= row.updatedAt) {
                    ops += SyncOp.PutCard(row.card.copy(updated = row.updatedAt))
                }
            }
            is PullRow.TextRow -> {
                val own = row.key.startsWith(mine)
                val localId = if (own) row.key.substring(mine.length).toLongOrNull() else null
                val current = if (own) local.texts[localId] else textsByRemote[row.key]
                val tombAt = listOfNotNull(
                    local.tombstones["texts\t${row.key}"],
                    if (own && localId != null) local.tombstones["texts\t$localId"] else null,
                ).maxOrNull() ?: 0
                if (row.deleted) {
                    if (current != null && current.updated <= row.updatedAt) {
                        ops += SyncOp.DeleteText(current.id.takeIf { it != 0L }, row.key)
                    }
                } else if ((current == null || current.updated < row.updatedAt) && tombAt <= row.updatedAt) {
                    ops += SyncOp.PutText(
                        SyncText(
                            id = current?.id ?: 0,
                            remoteKey = row.key,
                            title = row.title,
                            body = row.body,
                            added = row.added,
                            updated = row.updatedAt,
                        ),
                    )
                }
            }
        }
    }
    return ops
}

/** Replay the union of local and pulled reviews so a pulled card state matches what every device has seen. */
fun replayPulled(
    ops: List<SyncOp>,
    allLog: List<Pair<String, ReviewEntry>>,
    rows: List<PullRow>,
    device: String,
    cards: Map<String, SyncCard> = emptyMap(),
): List<SyncOp> {
    val byLemma = mutableMapOf<String, MutableMap<String, ReviewEntry>>()
    val localKeys = mutableSetOf<String>()

    fun add(key: String, entry: ReviewEntry) {
        if (entry.lemma.isBlank() || entry.at <= 0 || entry.rating !in 1..4) return
        byLemma.getOrPut(entry.lemma) { mutableMapOf() }[key] = entry
    }

    for ((key, entry) in allLog) {
        val k = if (key.toLongOrNull() != null) "$device:$key" else key
        localKeys += k
        add(k, entry)
    }
    val fresh = mutableSetOf<String>()
    for (row in rows) {
        if (row !is PullRow.LogRow || row.key.startsWith("$device:")) continue
        if (row.key !in localKeys) fresh += row.entry.lemma
        if (row.key !in localKeys) add(row.key, row.entry)
    }

    val out = ops.map { op ->
        if (op is SyncOp.PutCard && !op.card.known) {
            val entries = byLemma[op.card.lemma]
            if (entries.isNullOrEmpty()) op else op.copy(card = op.card.copy(fsrs = replayCard(entries, op.card.added)))
        } else {
            op
        }
    }.toMutableList()

    // A pulled review can arrive without a card op when the other device's card state lost LWW. Rebuild the local
    // card so that review still counts, unless the card was deleted or marked known.
    for (lemma in fresh) {
        val entries = byLemma[lemma] ?: continue
        if (out.any { (it is SyncOp.PutCard && it.card.lemma == lemma) || (it is SyncOp.DeleteCard && it.lemma == lemma) }) continue
        val card = cards[lemma] ?: continue
        if (card.known) continue
        out += SyncOp.PutCard(card.copy(fsrs = replayCard(entries, card.added)))
    }
    return out
}

/** Deterministic FSRS replay (no fuzz), ordered by review time then key so devices agree. */
fun replayCard(entries: Map<String, ReviewEntry>, added: Long?): Card {
    val sorted = entries.entries.sortedWith(compareBy({ it.value.at }, { it.key }))
    var card = createEmptyCard(added ?: sorted.firstOrNull()?.value?.at ?: 0)
    val scheduler = Scheduler()
    for ((_, entry) in sorted) {
        val rating = Rating.entries.find { it.value == entry.rating } ?: continue
        card = scheduler.next(card, entry.at, rating)
    }
    return card
}

data class PushRow(
    val store: String,
    val key: String,
    val updatedAt: Long,
    val card: SyncCard? = null,
    val text: SyncText? = null,
    val log: ReviewEntry? = null,
    val deleted: Boolean = false,
)

data class PushRows(val rows: List<PushRow>, val maxLog: Long)

data class PullResult(val rows: List<PullRow>, val maxSyncedAt: Long, val count: Int)
data class SyncResult(val pulled: Int, val pushed: Int)

/** Transport boundary: the Supabase client on Android, a fake in tests. */
interface SyncApi {
    suspend fun pull(since: Long): PullResult
    suspend fun push(rows: List<PushRow>)
}

/** Pull, replay, apply, then push; one sync at a time is the caller's job. */
class SyncEngine(private val store: Store, private val api: SyncApi) {
    suspend fun sync(): SyncResult {
        val state = store.syncState()
        val since = maxOf(0, state.pulledAt - 60_000) // one minute of overlap for slow concurrent pushes
        val pulled = api.pull(since)
        val local = SyncLocal(
            device = state.device,
            cards = store.syncCards().associateBy { it.lemma },
            texts = store.syncTexts().associateBy { it.id },
            tombstones = store.syncTombstones(),
        )
        var ops = plan(pulled.rows, local)
        val remoteLog = pulled.rows.any { it is PullRow.LogRow && !it.key.startsWith("${state.device}:") }
        if (remoteLog || ops.any { it is SyncOp.PutCard }) {
            // ponytail: the whole log is read once per sync; index it if a very long history makes this slow.
            ops = replayPulled(ops, store.allLog(), pulled.rows, state.device, local.cards)
        }
        store.applySync(ops)

        val started = System.currentTimeMillis()
        val collected = collect(
            state.device,
            state.pushedAt,
            state.logPushed,
            store.syncCards(),
            store.syncTexts(),
            store.localLog(),
            store.syncTombstones(),
        )
        api.push(collected.rows)
        store.saveSyncState(
            state.copy(
                pulledAt = maxOf(state.pulledAt, pulled.maxSyncedAt),
                pushedAt = started,
                logPushed = collected.maxLog,
            ),
        )
        return SyncResult(ops.size, collected.rows.size)
    }
}

/** Local changes since the last push, as records for the server. */
fun collect(
    device: String,
    since: Long,
    logPushed: Long,
    cards: List<SyncCard>,
    texts: List<SyncText>,
    log: List<Pair<Long, ReviewEntry>>,
    tombstones: Map<String, Long>,
): PushRows {
    val rows = mutableListOf<PushRow>()
    for (card in cards) if (card.updated > since) rows += PushRow("cards", card.lemma, card.updated, card = card)
    for (text in texts) {
        if (text.updated <= since) continue
        val key = text.remoteKey ?: "$device:${text.id}"
        rows += PushRow("texts", key, text.updated, text = text)
    }
    var maxLog = logPushed
    for ((id, entry) in log) {
        if (id <= logPushed) continue
        rows += PushRow("log", "$device:$id", entry.at, log = entry)
        maxLog = maxOf(maxLog, id)
    }
    for ((storedKey, at) in tombstones) {
        if (at <= since) continue
        val parts = storedKey.split("\t", limit = 2)
        if (parts.size != 2) continue
        val (store, key) = parts
        val remoteKey = if (store == "texts" && key.toLongOrNull() != null) "$device:$key" else key
        rows += PushRow(store, remoteKey, at, deleted = true)
    }
    return PushRows(rows, maxLog)
}
