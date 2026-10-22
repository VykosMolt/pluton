package io.github.vykosmolt.pluton.core

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.vykosmolt.pluton.core.db.PlutonDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreTest {
    private fun store(): Store {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlutonDb.Schema.create(driver).value
        return Store(PlutonDb(driver))
    }

    private fun v1Driver(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val v1 = listOf(
            """CREATE TABLE cards (
                lemma TEXT NOT NULL PRIMARY KEY, due INTEGER NOT NULL, stability REAL NOT NULL,
                difficulty REAL NOT NULL, elapsed_days INTEGER NOT NULL, scheduled_days INTEGER NOT NULL,
                reps INTEGER NOT NULL, lapses INTEGER NOT NULL, learning_steps INTEGER NOT NULL,
                state INTEGER NOT NULL, last_review INTEGER, known INTEGER NOT NULL DEFAULT 0,
                added INTEGER NOT NULL, mined_sentence TEXT, mined_form TEXT, mined_translation TEXT,
                updated INTEGER NOT NULL)""",
            "CREATE TABLE reviews (id INTEGER PRIMARY KEY AUTOINCREMENT, lemma TEXT NOT NULL, rating INTEGER NOT NULL, mode TEXT NOT NULL, at INTEGER NOT NULL, overridden INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX reviews_lemma ON reviews(lemma)",
            "CREATE TABLE meta (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
        )
        for (sql in v1) driver.execute(null, sql, 0)
        return driver
    }

    @Test
    fun savesCardAndReviewTogetherAndReloads() {
        val store = store()
        val now = 1_000_000L
        val card = CardState(
            lemma = "chat",
            fsrs = createEmptyCard(now).copy(state = State.Review, stability = 3.0, difficulty = 5.0, reps = 2, due = now + 86_400_000),
            added = now - 10_000,
        )
        store.saveAnswer(card, ReviewEntry("chat", 3, "cloze", now))

        val loaded = store.card("chat")
        assertEquals(card.lemma, loaded?.lemma)
        assertEquals(card.fsrs.state, loaded?.fsrs?.state)
        assertEquals(card.fsrs.due, loaded?.fsrs?.due)
        assertEquals(card.fsrs.stability, loaded?.fsrs?.stability)
        assertEquals(card.added, loaded?.added)
        assertEquals(listOf(ReviewEntry("chat", 3, "cloze", now, false)), store.reviews())
        assertNull(store.card("chien"))
    }

    @Test
    fun upsertKeepsOneRowAndAppendsReviews() {
        val store = store()
        val first = CardState("chat", createEmptyCard(1000).copy(due = 2000))
        store.saveAnswer(first, ReviewEntry("chat", 3, "cloze", 1000))
        val second = first.copy(fsrs = first.fsrs.copy(due = 5000, reps = 1))
        store.saveAnswer(second, ReviewEntry("chat", 2, "cloze", 2000, overridden = true))
        assertEquals(1, store.cards().size)
        assertEquals(5000, store.card("chat")?.fsrs?.due)
        assertEquals(2, store.reviews().size)
        assertEquals(true, store.reviews().last().overridden)
    }

    @Test
    fun newWordBudgetResetsOnANewDayButReadHistoryDoesNot() {
        val store = store()
        assertEquals(Stats("2026-10-21", 5), store.addIntroduced("2026-10-21", 5))
        assertEquals(Stats("2026-10-21", 8), store.addIntroduced("2026-10-21", 3))
        assertEquals(Stats("2026-10-21", 8, listOf("story-0")), store.addRead("2026-10-21", "story-0"))
        assertEquals(Stats("2026-10-22", 0, listOf("story-0")), store.stats("2026-10-22"))
        assertEquals(Stats("2026-10-22", 2, listOf("story-0")), store.addIntroduced("2026-10-22", 2))
        assertEquals(Stats("2026-10-22", 2, listOf("story-0")), store.addRead("2026-10-22", "story-0")) // once
    }

    @Test
    fun readerCardsCanBeAddedKnownAndUndone() {
        val store = store()
        val word = Word("maison", pos = "noun", display = "la maison", sentence = "La maison est grande.", form = "maison")
        store.addCard(word, "maison", null, 1000)
        assertEquals(WordStatus.Queued, status(store.card("maison"), word))
        val known = store.markKnown("maison", 2000)
        assertTrue(known.known)
        assertTrue(store.card("maison")!!.known)
        val undone = store.unmark("maison", 3000)
        assertNull(undone) // never reviewed -> deleted
        assertNull(store.card("maison"))
        // The review log is append-only and independent of card deletion.
        store.addCard(word, "maison", null, 4000)
        val reviewedCard = store.card("maison")!!.copy(fsrs = store.card("maison")!!.fsrs.copy(state = State.Review, stability = 3.0))
        store.saveAnswer(reviewedCard, ReviewEntry("maison", 3, "cloze", 5000))
        val reviewed = store.unmark("maison", 6000)
        assertTrue(reviewed != null && !reviewed.known && store.card("maison") != null)
        assertEquals(1, store.reviews().size)
    }

    @Test
    fun textsRoundTripAndDelete() {
        val store = store()
        val first = store.addText("Titre", "Un corps", 1000)
        val second = store.addText("", "Un texte sans titre", 2000)
        assertEquals("Un texte sans titre", second.title) // falls back to the body
        assertEquals(listOf(second.id, first.id), store.texts().map { it.id }) // newest first
        assertEquals("Un corps", store.texts().last().body)
        store.deleteText(first.id)
        assertEquals(listOf(second.id), store.texts().map { it.id })
    }

    @Test
    fun paceDefaultsAndPersists() {
        val store = store()
        assertEquals(Pace(5, 20), store.pace())
        store.savePace(Pace(perRun = 8, perDay = 30))
        assertEquals(Pace(8, 30), store.pace())
    }

    @Test
    fun backupRoundTripsCardsReviewsTextsAndStats() {
        val store = store()
        val now = 1_000_000L
        val card = CardState(
            "chat",
            createEmptyCard(now).copy(state = State.Review, stability = 3.0, difficulty = 5.0, due = now + 86_400_000),
            added = now - 5000,
        )
        store.saveAnswer(card, ReviewEntry("chat", 3, "cloze", now))
        store.addText("Titre", "Bonjour", now)
        store.addIntroduced("2026-10-21", 5)
        store.addListened(30)
        store.setCanDo("A1-1", true)
        val copy = store()
        copy.import(store.export())
        assertEquals(store.cards(), copy.cards())
        assertEquals(store.reviews(), copy.reviews())
        assertEquals(store.texts().map { it.title to it.body }, copy.texts().map { it.title to it.body })
        assertEquals(store.stats("2026-10-21"), copy.stats("2026-10-21"))
        assertEquals(setOf("A1-1"), copy.canDoDone())
    }

    @Test
    fun restoreReplacesEverythingAndABadFileChangesNothing() {
        val store = store()
        store.addText("Keep", "Corps", 1)
        assertTrue(runCatching { store.import("{oops") }.isFailure)
        assertEquals(listOf("Keep"), store.texts().map { it.title })
        val source = store()
        source.addText("New", "Nouveau", 2)
        store.import(source.export())
        assertEquals(listOf("New"), store.texts().map { it.title })
    }

    @Test
    fun version4CorrectionsGetScheduleDefaults() {
        val driver = v1Driver()
        PlutonDb.Schema.migrate(driver, 1, 4).value
        driver.execute(
            null,
            "INSERT INTO corrections(source, kind, original, fix, why, at, resolved) " +
                "VALUES ('writing', 'spelling', 'a', 'b', 'why', 1000, 0)",
            0,
        )
        PlutonDb.Schema.migrate(driver, 4, 5).value
        val store = Store(PlutonDb(driver))
        val drill = store.corrections().single()
        assertEquals(0L, drill.nextDue)
        assertEquals(0, drill.reps)
        assertEquals(1, store.reviewError(drill.id, "right", 2000)?.reps)
    }

    @Test
    fun version1DatabaseMigratesToTheCurrentSchema() {
        val driver = v1Driver()
        PlutonDb.Schema.migrate(driver, 1, 5).value
        val store = Store(PlutonDb(driver))
        store.markKnown("chat", 2000) // old tables still work
        store.addText("Titre", "Bonjour", 1000)
        assertEquals(listOf("Titre"), store.texts().map { it.title })
        store.saveAnswer(
            CardState("chat", createEmptyCard(3000).copy(state = State.Review)),
            ReviewEntry("chat", 3, "cloze", 3000),
        )
        assertEquals(1, store.localLog().size) // the new remote_key column is usable
        store.deleteText(store.texts().single().id, 4000)
        assertEquals(setOf("texts\t1"), store.syncTombstones().keys)
        store.saveWriting(
            "Raconte",
            "je suis alle",
            WritingResult("Je suis allé.", listOf(WritingError("je suis alle", "Je suis allé", "accent", "spelling")), "Bien", "A2"),
            5000,
        )
        assertEquals(1, store.writings().size)
        assertEquals(1, store.corrections().size)
        assertEquals(1, store.reviewError(store.corrections().single().id, "right", 6000)?.reps)
    }

    @Test
    fun syncTombstonesLogsAndStateRoundTrip() {
        val store = store()
        val state = store.syncState()
        assertEquals(8, state.device.length)
        assertEquals(state.device, store.syncState().device) // stable across calls
        store.saveAnswer(
            CardState("chat", createEmptyCard(1000).copy(state = State.Review), added = 1000),
            ReviewEntry("chat", 3, "cloze", 1000),
        )
        store.addCard(Word("chien", pos = "noun", sentence = "Le chien dort.", form = "chien"), "chien", null, 2000)
        val text = store.addText("Titre", "Corps", 3000)
        store.deleteCard("chien", 4000)
        store.deleteText(text.id, 5000)
        assertEquals(mapOf("cards\tchien" to 4000L, "texts\t${text.id}" to 5000L), store.syncTombstones())
        assertEquals(listOf("1"), store.localLog().map { it.first.toString() })
        assertEquals(1, store.allLog().size)
        assertEquals("1", store.allLog().single().first)
    }

    @Test
    fun pulledOpsApplyWithoutLocalTombstones() {
        val store = store()
        store.applySync(
            listOf(
                SyncOp.PutCard(SyncCard("chat", 5000, added = 1000, fsrs = createEmptyCard(0).copy(reps = 2))),
                SyncOp.PutText(SyncText(0, "dev2:7", "Theirs", "Texte", 1000, 7000)),
                SyncOp.PutLog("dev2:1", ReviewEntry("chat", 3, "cloze", 6000)),
            ),
        )
        assertEquals(2, store.card("chat")?.fsrs?.reps)
        assertEquals(5000, store.card("chat")?.updated)
        assertEquals(listOf("Theirs"), store.texts().map { it.title })
        assertEquals("dev2:7", store.texts().single().remoteKey)
        assertEquals(1, store.allLog().size)
        // Pulling the same log again is ignored (unique remote key), and the remote text updates in place.
        store.applySync(
            listOf(
                SyncOp.PutLog("dev2:1", ReviewEntry("chat", 3, "cloze", 6000)),
                SyncOp.PutText(SyncText(0, "dev2:7", "Edited", "Texte", 1000, 8000)),
            ),
        )
        assertEquals(1, store.allLog().size)
        assertEquals(listOf("Edited"), store.texts().map { it.title })
        assertEquals(1, store.texts().size)
        // Remote deletions do not create tombstones of our own.
        store.applySync(listOf(SyncOp.DeleteText(null, "dev2:7")))
        assertEquals(emptyMap(), store.syncTombstones())
        assertEquals(emptyList(), store.texts())
    }

    @Test
    fun backupImportResetsSyncWatermarksButKeepsTheDevice() {
        val store = store()
        val state = store.syncState().copy(pulledAt = 1000, pushedAt = 2000, logPushed = 3)
        store.saveSyncState(state)
        store.addText("Keep", "Corps", 1)
        store.import(store.export())
        val reset = store.syncState()
        assertEquals(state.device, reset.device)
        assertEquals(0, reset.pulledAt)
        assertEquals(0, reset.pushedAt)
        assertEquals(0, reset.logPushed)
    }

    @Test
    fun listeningTotalsAccumulateAcrossDays() {
        val store = store()
        store.addIntroduced("2026-10-21", 5)
        store.addRead("2026-10-21", "story-0")
        assertEquals(Stats("2026-10-21", 5, listOf("story-0"), 30), store.addListened(30))
        assertEquals(Stats("2026-10-21", 5, listOf("story-0"), 45), store.addListened(15)) // cumulative, day-agnostic
        assertEquals(Stats("2026-10-21", 5, listOf("story-0"), 45, 2), store.addSpoken(2))
        assertEquals(Stats("2026-10-21", 5, listOf("story-0"), 45, 2, 3), store.addTalked(3))
        assertEquals(Stats("2026-10-21", 5, listOf("story-0"), 45, 2, 3, 1), store.addWritten())
        assertEquals(Stats("2026-10-22", 0, listOf("story-0"), 45, 2, 3, 1), store.stats("2026-10-22"))
    }

    @Test
    fun minedWordsKeepTheirSentence() {
        val store = store()
        val word = Word("chat", pos = "noun", display = "le chat", gloss = "cat") // no built-in sentence
        val card = store.addCard(word, "chat", "Le chat dort.", 1000)
        assertEquals(Mined("Le chat dort.", "chat"), store.card("chat")?.mined)
        assertEquals("chat", card.lemma)
    }

    @Test
    fun writingAndCorrectionsSurviveAReload() {
        val store = store()
        val result = WritingResult(
            corrected = "Je suis allé au marché.",
            errors = listOf(WritingError("je suis alle", "je suis allé", "accent manquant", "spelling")),
            feedback = "Bien, mais attention aux accents.",
            level = "A2",
        )
        val saved = store.saveWriting("Raconte ton week-end", "je suis alle au marché", result, 1000)
        assertEquals(1, saved.id)
        val writing = store.writings().single()
        assertEquals("Je suis allé au marché.", writing.corrected)
        assertEquals("spelling", writing.errors.single().kind)
        assertNull(writing.revised)
        val correction = store.corrections().single()
        assertEquals("writing", correction.source)
        assertEquals("accent manquant", correction.why)

        store.updateWritingRevision(saved.id, "Je suis allé au marché !", 2000)
        assertEquals("Je suis allé au marché !", store.writings().single().revised)
    }

    @Test
    fun backupRoundTripsWritingAndCorrections() {
        val store = store()
        val result = WritingResult(
            corrected = "Bonjour.",
            errors = listOf(WritingError("bonjours", "bonjour", "pas de s", "spelling")),
            feedback = "Simple et juste.",
            level = "A1",
        )
        store.saveWriting("Dis bonjour", "bonjours", result, 1000)
        val scheduled = store.reviewError(store.corrections().single().id, "right", 2000)!!
        val copy = store()
        copy.import(store.export())
        assertEquals(store.writings().map { it.corrected }, copy.writings().map { it.corrected })
        assertEquals(store.corrections().map { it.original }, copy.corrections().map { it.original })
        assertEquals(scheduled.nextDue, copy.corrections().single().nextDue)
        assertEquals(1, copy.corrections().single().reps)
    }

    @Test
    fun onboardingPlanAndLastTabAreLocalMeta() {
        val store = store()
        assertNull(store.onboardingMinutes())
        store.saveOnboarding(10)
        assertEquals(10, store.onboardingMinutes())
        assertEquals(emptySet(), store.planDone("2026-10-21"))
        assertEquals(setOf("review"), store.markPlanDone("2026-10-21", "review"))
        assertEquals(setOf("review", "speak"), store.markPlanDone("2026-10-21", "speak"))
        assertEquals(setOf("speak"), store.unmarkPlanDone("2026-10-21", "review"))
        assertEquals(setOf("2026-10-21"), store.practiceDays()) // unchecking a step does not erase the day
        store.saveLastTab("Read")
        assertEquals("Read", store.lastTab())
    }

    @Test
    fun canDoTicksPersistLocally() {
        val store = store()
        assertEquals(emptySet(), store.canDoDone())
        assertEquals(setOf("A1-1"), store.setCanDo("A1-1", true))
        assertEquals(setOf("A1-1", "A1-2"), store.setCanDo("A1-2", true))
        assertEquals(setOf("A1-2"), store.setCanDo("A1-1", false))
    }

    @Test
    fun recentAccuracyCountsGoodAndEasy() {
        val store = store()
        assertNull(store.recentAccuracy())
        val base = CardState("chat", createEmptyCard(1000).copy(state = State.Review))
        store.saveAnswer(base, ReviewEntry("chat", 1, "cloze", 1000))
        store.saveAnswer(base, ReviewEntry("chat", 3, "cloze", 2000))
        store.saveAnswer(base, ReviewEntry("chat", 4, "cloze", 3000))
        assertEquals(2.0 / 3.0, store.recentAccuracy())
    }

    @Test
    fun errorReviewsAdvanceScheduleAndRetire() {
        val store = store()
        val result = WritingResult(
            corrected = "Je suis allé.",
            errors = listOf(WritingError("je suis alle", "je suis allé", "accent", "spelling")),
            feedback = "Bien",
            level = "A2",
        )
        store.saveWriting("Raconte", "je suis alle", result, 1000)
        val id = store.corrections().single().id
        val start = 10_000L
        val reset = store.reviewError(id, "wrong", start)!!
        assertEquals(0, reset.reps)
        assertEquals(start, reset.nextDue)

        val first = store.reviewError(id, "right", start)!!
        assertEquals(1, first.reps)
        assertEquals(start + 86_400_000L, first.nextDue)
        assertTrue(!first.resolved)
        assertEquals(1, store.reviewError(id, "right", start)!!.reps) // not due yet: no farming

        val gaps = mutableListOf(1L)
        var last = first
        var guard = 0
        while (!last.resolved && guard++ < 10) {
            val due = store.corrections().single().nextDue
            last = store.reviewError(id, "right", due)!!
            if (!last.resolved) gaps += (last.nextDue - due) / 86_400_000L
        }
        assertEquals(listOf(1L, 3L, 7L, 16L, 35L), gaps)
        assertTrue(last.resolved)
        assertEquals(6, last.reps)
        assertTrue(store.reviewError(id, "wrong", start)!!.resolved) // retired stays retired
        assertTrue(errorDrills(store.corrections(), start).isEmpty())
    }

    @Test
    fun duplicateCorrectionsShareOneSchedule() {
        val store = store()
        val result = WritingResult(
            corrected = "Je suis allé.",
            errors = listOf(WritingError("je suis alle", "je suis allé", "accent", "spelling")),
            feedback = "Bien",
            level = "A2",
        )
        store.saveWriting("Un", "je suis alle", result, 1000)
        store.saveWriting("Deux", "je suis alle", result, 2000)
        assertEquals(2, store.corrections().size)
        val start = 10_000L
        val reviewed = store.reviewError(store.corrections().first().id, "right", start)!!
        assertEquals(1, reviewed.reps)
        assertTrue(store.corrections().all { it.reps == 1 })
        assertTrue(store.corrections().all { it.nextDue == start + 86_400_000L })
        assertTrue(errorDrills(store.corrections(), start + 1).isEmpty())
    }

    @Test
    fun almostComesBackTomorrowWithoutAdvancing() {
        val store = store()
        val result = WritingResult(
            corrected = "Je suis allé.",
            errors = listOf(WritingError("je suis alle", "je suis allé", "accent", "spelling")),
            feedback = "Bien",
            level = "A2",
        )
        store.saveWriting("Raconte", "je suis alle", result, 1000)
        val id = store.corrections().single().id
        val start = 10_000L
        val almost = store.reviewError(id, "almost", start)!!
        assertEquals(0, almost.reps)
        assertEquals(start + 86_400_000L, almost.nextDue)
        assertEquals(0, store.reviewError(id, "right", start)!!.reps) // not due yet
        assertEquals(1, errorDrills(store.corrections(), start + 86_400_000L).size)
    }

    @Test
    fun weekActivityCountsOnlyTheLastSevenDays() {
        val store = store()
        val now = 10L * 86_400_000L
        val base = CardState("chat", createEmptyCard(1000).copy(state = State.Review))
        store.saveAnswer(base, ReviewEntry("chat", 4, "cloze", now - 86_400_000L))
        store.saveAnswer(base, ReviewEntry("chat", 2, "cloze", now - 2L * 86_400_000L))
        store.saveAnswer(base, ReviewEntry("chat", 3, "cloze", now - 8L * 86_400_000L))
        val week = store.weekActivity(now)
        assertEquals(2, week.reviews)
        assertEquals(1, week.good)
        assertEquals(0.5, week.accuracy)
        val empty = store().weekActivity(now)
        assertEquals(-1.0, empty.accuracy)
    }

    @Test
    fun saveWritingReturnsAUsableIdForEveryRecord() {
        val store = store()
        val result = WritingResult(corrected = "Bonjour.", feedback = "Bien", level = "A1")
        val first = store.saveWriting("Un", "bonjour", result, 1000)
        val second = store.saveWriting("Deux", "bonjour", result, 2000)
        assertEquals(1, first.id)
        assertEquals(2, second.id)
        assertEquals(listOf(2L, 1L), store.writings().map { it.id })
        assertEquals(0, store.stats("2026-10-21").written) // saving a writing is not a UI "written" count
        store.addWritten()
        val written = store.stats("2026-10-21").written
        store.updateWritingRevision(first.id, "Bonjour !", 3000)
        assertEquals(written, store.stats("2026-10-21").written)
    }

    @Test
    fun version1BackupKeepsLocalWritingsAndCorrections() {
        val store = store()
        val result = WritingResult(
            corrected = "Je suis allé.",
            errors = listOf(WritingError("je suis alle", "je suis allé", "accent", "spelling")),
            feedback = "Bien",
            level = "A2",
        )
        store.saveWriting("Raconte", "je suis alle", result, 1000)
        store.setCanDo("A2-1", true)
        val legacy = kotlinx.serialization.json.Json.encodeToString(Backup(version = 1))
        store.import(legacy)
        assertEquals(1, store.writings().size)
        assertEquals(1, store.corrections().size)
        assertEquals("Je suis allé.", store.writings().single().corrected)
        assertEquals(setOf("A2-1"), store.canDoDone())
    }

    @Test
    fun onboardingSkipsExistingProgressAndKeepsThePace() {
        val store = store()
        assertTrue(!store.onboardedOrExisting())
        store.savePace(Pace(8, 30))
        store.markKnown("chat", 1000)
        assertTrue(store.onboardedOrExisting())
        assertEquals(0, store.onboardingMinutes())
        assertEquals(Pace(8, 30), store.pace())
    }

    @Test
    fun planAndPracticeAreIndependentPerDay() {
        val store = store()
        store.markPlanDone("2026-10-20", "review")
        assertEquals(setOf("review"), store.planDone("2026-10-20"))
        assertEquals(emptySet(), store.planDone("2026-10-21"))
        assertEquals(setOf("2026-10-20"), store.practiceDays())
    }

    @Test
    fun addingAnExistingCardNeverErasesProgress() {
        val store = store()
        val word = Word("chat", pos = "noun", display = "le chat", gloss = "cat")
        store.addCard(word, "chat", "Le chat dort.", 1000)
        store.markKnown("chat", 2000)
        store.addCard(word, "chat", "Un autre chat.", 3000)
        assertEquals(true, store.card("chat")?.known)
        assertEquals("Le chat dort.", store.card("chat")?.mined?.sentence)
    }
}
