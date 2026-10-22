package io.github.vykosmolt.pluton.core

import io.github.vykosmolt.pluton.core.db.Cards
import io.github.vykosmolt.pluton.core.db.PlutonDb
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// SQLite persistence. Cards are derived state; the review table is append-only and will be unioned for sync.
// Writes are synchronous and tiny; move them to a writer coroutine if UI profiling ever shows jank.
// ponytail: synchronous SQLite writes on the caller's thread.

@Serializable
data class Stats(
    val day: String = "",
    val newCount: Int = 0,
    val read: List<String> = emptyList(),
    val listened: Int = 0,
    val spoken: Int = 0,
    val talked: Int = 0,
    val written: Int = 0,
)

@Serializable
data class ReviewEntry(
    val lemma: String,
    val rating: Int,
    val mode: String,
    val at: Long,
    val overridden: Boolean = false,
)

data class TextRecord(
    val id: Long,
    val title: String,
    val body: String,
    val added: Long,
    val updated: Long = added,
    val remoteKey: String? = null,
)

/** A saved writing attempt and its first correction. `revised` is the learner's second draft. */
data class WritingRecord(
    val id: Long,
    val prompt: String,
    val draft: String,
    val corrected: String,
    val feedback: String,
    val level: String,
    val errors: List<WritingError>,
    val revised: String?,
    val created: Long,
    val updated: Long,
)

/** One correction from writing or talk, kept locally so recurring errors can be surfaced. */
data class CorrectionRecord(
    val id: Long,
    val source: String,
    val kind: String,
    val original: String,
    val fix: String,
    val why: String,
    val at: Long,
    val resolved: Boolean,
)

/** New-word pace; the same JSON shape as the web app's settings meta. */
@Serializable
data class Pace(val perRun: Int = NEW_PER_RUN, val perDay: Int = NEW_PER_DAY)

/** Sync bookkeeping: one device id forever, plus the watermarks for pull/push. */
@Serializable
data class SyncState(
    val device: String = "",
    val pulledAt: Long = 0,
    val pushedAt: Long = 0,
    val logPushed: Long = 0,
)

private const val DEVICE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

private fun newDeviceId(): String = buildString {
    repeat(8) { append(DEVICE_ALPHABET[kotlin.random.Random.nextInt(DEVICE_ALPHABET.length)]) }
}

/** Backup file contents. No credentials are stored on this device, so none can leak into a backup. */
@Serializable
data class BackupText(val title: String, val body: String, val added: Long)

@Serializable
data class BackupWriting(
    val prompt: String,
    val draft: String,
    val corrected: String,
    val feedback: String,
    val level: String,
    val errors: List<WritingError> = emptyList(),
    val revised: String? = null,
    val created: Long,
    val updated: Long,
)

@Serializable
data class BackupCorrection(
    val source: String,
    val kind: String,
    val original: String,
    val fix: String,
    val why: String,
    val at: Long,
    val resolved: Boolean = false,
)

@Serializable
data class Backup(
    val version: Int = 1,
    val cards: List<CardState> = emptyList(),
    val reviews: List<ReviewEntry> = emptyList(),
    val texts: List<BackupText> = emptyList(),
    val writings: List<BackupWriting> = emptyList(),
    val corrections: List<BackupCorrection> = emptyList(),
    val stats: Stats = Stats(),
    val canDo: List<String> = emptyList(),
)

class Store(private val db: PlutonDb) {
    private val json = Json { ignoreUnknownKeys = true }

    fun cards(): List<CardState> = db.plutonQueries.selectAllCards().executeAsList().map { it.toState() }

    fun card(lemma: String): CardState? = db.plutonQueries.selectCard(lemma).executeAsOneOrNull()?.toState()

    /** Card state and its review log entry in one transaction, so they never disagree. */
    fun saveAnswer(card: CardState, entry: ReviewEntry) {
        db.transaction {
            writeCard(card, entry.at)
            db.plutonQueries.insertReview(entry.lemma, entry.rating.toLong(), entry.mode, entry.at, if (entry.overridden) 1L else 0L)
        }
    }

    /** A card change outside a run (add/mark known): no review log entry. */
    fun saveCard(card: CardState, now: Long) = writeCard(card, now)

    /** Queue a reader word; words without a built-in sentence keep the sentence it was tapped in. */
    fun addCard(word: Word, form: String, sentence: String?, now: Long): CardState {
        // Never replace a card that already exists: a stale reader sheet must not erase FSRS progress or "known".
        card(word.lemma)?.let { return it }
        val mined = if (!sentence.isNullOrBlank() && sentence != word.sentence) Mined(sentence, form) else null
        val card = CardState(word.lemma, createEmptyCard(now), added = now, mined = mined)
        saveCard(card, now)
        return card
    }

    fun markKnown(lemma: String, now: Long): CardState {
        val card = (card(lemma) ?: CardState(lemma, createEmptyCard(now), added = now)).copy(known = true)
        saveCard(card, now)
        return card
    }

    /** Undo markKnown; a never-reviewed card disappears, a reviewed one goes back to learning. */
    fun unmark(lemma: String, now: Long): CardState? {
        val card = card(lemma) ?: return null
        return if (card.fsrs.state == State.New) {
            deleteCard(lemma)
            null
        } else {
            val next = card.copy(known = false)
            saveCard(next, now)
            next
        }
    }

    fun deleteCard(lemma: String, now: Long = nowMillis()) {
        db.transaction {
            db.plutonQueries.insertTombstone("cards", lemma, now)
            db.plutonQueries.deleteCard(lemma)
        }
    }

    private fun writeCard(card: CardState, updated: Long) {
        db.plutonQueries.upsertCard(
            lemma = card.lemma,
            due = card.fsrs.due,
            stability = card.fsrs.stability,
            difficulty = card.fsrs.difficulty,
            elapsed_days = card.fsrs.elapsedDays.toLong(),
            scheduled_days = card.fsrs.scheduledDays.toLong(),
            reps = card.fsrs.reps.toLong(),
            lapses = card.fsrs.lapses.toLong(),
            learning_steps = card.fsrs.learningSteps.toLong(),
            state = card.fsrs.state.ordinal.toLong(),
            last_review = card.fsrs.lastReview,
            known = if (card.known) 1L else 0L,
            added = card.added,
            mined_sentence = card.mined?.sentence,
            mined_form = card.mined?.form,
            mined_translation = card.mined?.translation,
            updated = updated,
        )
    }

    fun reviews(): List<ReviewEntry> = db.plutonQueries.selectReviews().executeAsList().map {
        ReviewEntry(it.lemma, it.rating.toInt(), it.mode, it.at, it.overridden != 0L)
    }

    /** Share of the last [n] reviews rated Good or Easy; null until there is history. */
    /** Review count and Good/Easy count over the last seven days. */
    fun weekActivity(now: Long): WeekActivity {
        val since = now - 7L * 24 * 60 * 60 * 1000
        val ratings = db.plutonQueries.reviewsSince(since).executeAsList()
        return WeekActivity(ratings.size, ratings.count { it >= 3L })
    }

    fun recentAccuracy(n: Int = 30): Double? {
        val ratings = db.plutonQueries.recentRatings(n.toLong()).executeAsList()
        if (ratings.isEmpty()) return null
        return ratings.count { it >= 3L }.toDouble() / ratings.size
    }

    fun meta(key: String): String? = db.plutonQueries.getMeta(key).executeAsOneOrNull()

    fun putMeta(key: String, value: String) = db.plutonQueries.putMeta(key, value)

    /** Daily onboarding answer (minutes); null until the learner has chosen. */
    fun onboardingMinutes(): Int? = meta("onboarding")?.toIntOrNull()

    fun saveOnboarding(minutes: Int) = putMeta("onboarding", minutes.toString())

    /**
     * True when onboarding is done or the store already has progress from before onboarding existed.
     * Existing installs must not be forced through the first-run screen or have their pace overwritten.
     */
    fun onboardedOrExisting(): Boolean {
        if (onboardingMinutes() != null) return true
        val existing = cards().isNotEmpty() || reviews().isNotEmpty() || texts().isNotEmpty() ||
            meta("settings") != null || meta("stats") != null
        if (existing) putMeta("onboarding", "0")
        return existing
    }

    fun canDoDone(): Set<String> =
        meta("cando")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun setCanDo(id: String, done: Boolean): Set<String> {
        val next = if (done) canDoDone() + id else canDoDone() - id
        putMeta("cando", next.joinToString(","))
        return next
    }

    fun lastTab(): String? = meta("lastTab")

    fun saveLastTab(tab: String) = putMeta("lastTab", tab)

    fun planDone(day: String): Set<String> =
        meta("plan:$day")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun markPlanDone(day: String, step: String): Set<String> {
        val next = planDone(day) + step
        putMeta("plan:$day", next.joinToString(","))
        markPractice(day)
        return next
    }

    /** Days with at least one completed plan step; used for the gentle weekly consistency row. */
    fun practiceDays(): Set<String> =
        meta("practice")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun markPractice(day: String): Set<String> {
        val next = practiceDays() + day
        putMeta("practice", next.joinToString(","))
        return next
    }

    fun unmarkPlanDone(day: String, step: String): Set<String> {
        val next = planDone(day) - step
        putMeta("plan:$day", next.joinToString(","))
        return next
    }

    fun writings(): List<WritingRecord> = db.plutonQueries.selectAllWriting().executeAsList().map {
        WritingRecord(
            id = it.id,
            prompt = it.prompt,
            draft = it.draft,
            corrected = it.corrected,
            feedback = it.feedback,
            level = it.level,
            errors = runCatching { json.decodeFromString<List<WritingError>>(it.errors) }.getOrDefault(emptyList()),
            revised = it.revised,
            created = it.created,
            updated = it.updated,
        )
    }

    fun saveWriting(prompt: String, draft: String, result: WritingResult, now: Long): WritingRecord =
        db.transactionWithResult {
            db.plutonQueries.insertWriting(
                prompt,
                draft,
                result.corrected,
                result.feedback,
                result.level,
                json.encodeToString(result.errors),
                null,
                now,
                now,
            )
            val id = db.plutonQueries.lastWritingId().executeAsOne()
            for (e in result.errors) {
                db.plutonQueries.insertCorrection("writing", e.kind, e.original, e.fix, e.why, now, 0)
            }
            WritingRecord(id, prompt, draft, result.corrected, result.feedback, result.level, result.errors, null, now, now)
        }

    fun updateWritingRevision(id: Long, revised: String, now: Long) =
        db.plutonQueries.updateWritingRevision(revised, now, id)

    fun addCorrections(source: String, errors: List<WritingError>, now: Long) {
        db.transaction {
            for (e in errors) {
                db.plutonQueries.insertCorrection(source, e.kind, e.original, e.fix, e.why, now, 0)
            }
        }
    }

    fun corrections(): List<CorrectionRecord> = db.plutonQueries.selectAllCorrections().executeAsList().map {
        CorrectionRecord(it.id, it.source, it.kind, it.original, it.fix, it.why, it.at, it.resolved != 0L)
    }

    fun texts(): List<TextRecord> = db.plutonQueries.selectAllTexts().executeAsList().map {
        TextRecord(it.id, it.title, it.body, it.added, it.updated, it.remote_key)
    }

    fun addText(title: String, body: String, now: Long): TextRecord {
        val clean = body.trim()
        require(clean.isNotEmpty()) { "A text needs a body" }
        val name = title.trim().ifBlank { clean.take(40) }
        return db.transactionWithResult {
            db.plutonQueries.insertText(name, clean, now, now)
            val id = db.plutonQueries.lastTextId().executeAsOne()
            TextRecord(id, name, clean, now, now)
        }
    }

    fun deleteText(id: Long, now: Long = nowMillis()) {
        val text = db.plutonQueries.selectTextById(id).executeAsOneOrNull()
        db.transaction {
            db.plutonQueries.insertTombstone("texts", text?.remote_key ?: id.toString(), now)
            db.plutonQueries.deleteText(id)
        }
    }

    fun pace(): Pace {
        val raw = db.plutonQueries.getMeta("settings").executeAsOneOrNull()
        return raw?.let { runCatching { json.decodeFromString<Pace>(it) }.getOrNull() } ?: Pace()
    }

    fun savePace(pace: Pace) = db.plutonQueries.putMeta("settings", json.encodeToString(pace))

    /** Everything that matters on this device, as JSON. */
    fun export(): String = json.encodeToString(
        Backup(
            version = 3,
            cards = cards(),
            reviews = reviews(),
            texts = texts().map { BackupText(it.title, it.body, it.added) },
            writings = writings().map {
                BackupWriting(it.prompt, it.draft, it.corrected, it.feedback, it.level, it.errors, it.revised, it.created, it.updated)
            },
            corrections = corrections().map {
                BackupCorrection(it.source, it.kind, it.original, it.fix, it.why, it.at, it.resolved)
            },
            stats = rawStats(),
            canDo = canDoDone().toList(),
        ),
    )

    /** Replace all progress with a backup, in one transaction: a bad file leaves the store untouched. */
    fun import(data: String) {
        val backup = json.decodeFromString<Backup>(data)
        db.transaction {
            db.plutonQueries.deleteAllCards()
            db.plutonQueries.deleteAllReviews()
            db.plutonQueries.deleteAllTexts()
            if (backup.version >= 2) {
                db.plutonQueries.deleteAllWriting()
                db.plutonQueries.deleteAllCorrections()
            }
            for (c in backup.cards) writeCard(c, c.updated.takeIf { it != 0L } ?: c.added)
            for (r in backup.reviews) {
                db.plutonQueries.insertReview(r.lemma, r.rating.toLong(), r.mode, r.at, if (r.overridden) 1L else 0L)
            }
            for (t in backup.texts) db.plutonQueries.insertText(t.title, t.body, t.added, t.added)
            if (backup.version >= 2) {
                for (w in backup.writings) {
                    db.plutonQueries.insertWriting(
                        w.prompt,
                        w.draft,
                        w.corrected,
                        w.feedback,
                        w.level,
                        json.encodeToString(w.errors),
                        w.revised,
                        w.created,
                        w.updated,
                    )
                }
                for (c in backup.corrections) {
                    db.plutonQueries.insertCorrection(c.source, c.kind, c.original, c.fix, c.why, c.at, if (c.resolved) 1L else 0L)
                }
                db.plutonQueries.putMeta("writing_draft", "")
                db.plutonQueries.putMeta("writing_prompt", "0")
                db.plutonQueries.putMeta("revision_draft", "")
                db.plutonQueries.putMeta("revision_id", "")
            }
            db.plutonQueries.putMeta("stats", json.encodeToString(backup.stats))
            // A v1/v2 file predates the can-do self-assessment, so it must not erase the local ticks.
            if (backup.version >= 3) db.plutonQueries.putMeta("cando", backup.canDo.joinToString(","))
            // A restored device starts from scratch: old deletions must not wipe the account, and pulled records
            // must be re-fetched. The device identity is kept.
            db.plutonQueries.deleteAllTombstones()
            val state = syncState()
            db.plutonQueries.putMeta("_sync", json.encodeToString(state.copy(pulledAt = 0, pushedAt = 0, logPushed = 0)))
        }
    }

    fun syncState(): SyncState {
        val raw = db.plutonQueries.getMeta("_sync").executeAsOneOrNull()
        val parsed = raw?.let { runCatching { json.decodeFromString<SyncState>(it) }.getOrNull() }
        if (parsed != null && parsed.device.isNotBlank()) return parsed
        val fresh = SyncState(device = newDeviceId())
        db.plutonQueries.putMeta("_sync", json.encodeToString(fresh))
        return fresh
    }

    fun saveSyncState(state: SyncState) = db.plutonQueries.putMeta("_sync", json.encodeToString(state))

    fun syncCards(): List<SyncCard> = cards().map {
        SyncCard(it.lemma, it.updated, it.known, it.added, it.fsrs, it.mined)
    }

    fun syncTexts(): List<SyncText> = texts().map {
        SyncText(it.id, it.remoteKey, it.title, it.body, it.added, it.updated)
    }

    fun syncTombstones(): Map<String, Long> = db.plutonQueries.selectAllTombstones().executeAsList()
        .associate { "${it.store}\t${it.key}" to it.at }

    /** This device's own review rows (remote reviews are not pushed back). */
    fun localLog(): List<Pair<Long, ReviewEntry>> = db.plutonQueries.selectLocalReviews().executeAsList().map {
        it.id to ReviewEntry(it.lemma, it.rating.toInt(), it.mode, it.at, it.overridden != 0L)
    }

    /** Every review with its sync key: own rows by local id, pulled rows by their remote key. */
    fun allLog(): List<Pair<String, ReviewEntry>> = db.plutonQueries.selectReviews().executeAsList().map {
        val key = it.remote_key ?: it.id.toString()
        key to ReviewEntry(it.lemma, it.rating.toInt(), it.mode, it.at, it.overridden != 0L)
    }

    /** Apply pulled ops in one transaction; deletions here are remote, so they don't create local tombstones. */
    fun applySync(ops: List<SyncOp>) {
        if (ops.isEmpty()) return
        db.transaction {
            for (op in ops) {
                when (op) {
                    is SyncOp.PutCard -> writeCard(
                        CardState(op.card.lemma, op.card.fsrs, op.card.known, op.card.added, op.card.mined, op.card.updated),
                        op.card.updated,
                    )
                    is SyncOp.DeleteCard -> db.plutonQueries.deleteCardSilent(op.lemma)
                    is SyncOp.PutText -> {
                        val t = op.text
                        val existing = if (t.id != 0L) {
                            db.plutonQueries.selectTextById(t.id).executeAsOneOrNull()
                        } else {
                            t.remoteKey?.let { db.plutonQueries.selectTextByRemote(it).executeAsOneOrNull() }
                        }
                        if (existing != null) {
                            db.plutonQueries.updateRemoteText(t.title, t.body, t.updated, t.remoteKey, existing.id)
                        } else {
                            db.plutonQueries.insertRemoteText(t.title, t.body, t.added, t.updated, t.remoteKey)
                        }
                    }
                    is SyncOp.DeleteText -> {
                        if (op.localId != null) db.plutonQueries.deleteTextSilent(op.localId)
                        else op.remoteKey?.let { db.plutonQueries.deleteTextByRemote(it) }
                    }
                    is SyncOp.PutLog -> db.plutonQueries.insertPulledReview(
                        op.entry.lemma,
                        op.entry.rating.toLong(),
                        op.entry.mode,
                        op.entry.at,
                        if (op.entry.overridden) 1L else 0L,
                        op.remoteKey,
                    )
                }
            }
        }
    }

    private fun rawStats(): Stats {
        val raw = db.plutonQueries.getMeta("stats").executeAsOneOrNull()
        return raw?.let { runCatching { json.decodeFromString<Stats>(it) }.getOrNull() } ?: Stats()
    }

    /** New-word budget for a local day; yesterday's count does not carry over. Read/listening history is not daily. */
    fun stats(today: String): Stats {
        val s = rawStats()
        return if (s.day == today) s else s.copy(day = today, newCount = 0)
    }

    /** Cumulative listening seconds, independent of the day boundary. */
    fun addListened(seconds: Int): Stats {
        val current = rawStats()
        val next = current.copy(listened = current.listened + seconds)
        putStats(next)
        return next
    }

    /** Cumulative read-aloud attempts, independent of the day boundary. */
    fun addSpoken(n: Int = 1): Stats {
        val current = rawStats()
        val next = current.copy(spoken = current.spoken + n)
        putStats(next)
        return next
    }

    /** Cumulative AI conversation messages, independent of the day boundary. */
    fun addTalked(n: Int = 1): Stats {
        val current = rawStats()
        val next = current.copy(talked = current.talked + n)
        putStats(next)
        return next
    }

    /** Cumulative AI-corrected texts, independent of the day boundary. */
    fun addWritten(n: Int = 1): Stats {
        val current = rawStats()
        val next = current.copy(written = current.written + n)
        putStats(next)
        return next
    }

    fun addIntroduced(today: String, n: Int): Stats {
        val current = stats(today)
        val next = current.copy(newCount = current.newCount + n)
        putStats(next)
        return next
    }

    fun addRead(today: String, id: String): Stats {
        val current = stats(today)
        if (id in current.read) return current
        val next = current.copy(read = current.read + id)
        putStats(next)
        return next
    }

    private fun putStats(s: Stats) = db.plutonQueries.putMeta("stats", json.encodeToString(s))
}

private fun Cards.toState() = CardState(
    lemma = lemma,
    fsrs = Card(
        due = due,
        stability = stability,
        difficulty = difficulty,
        elapsedDays = elapsed_days.toInt(),
        scheduledDays = scheduled_days.toInt(),
        reps = reps.toInt(),
        lapses = lapses.toInt(),
        learningSteps = learning_steps.toInt(),
        state = State.entries[state.toInt()],
        lastReview = last_review,
    ),
    known = known != 0L,
    added = added,
    mined = mined_sentence?.let { Mined(it, mined_form.orEmpty(), mined_translation.orEmpty()) },
    updated = updated,
)
