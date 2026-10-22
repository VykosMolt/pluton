package io.github.vykosmolt.pluton.core

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.vykosmolt.pluton.core.db.PlutonDb

/** One Swift-friendly facade over the shared core: keeps Swift free of top-level Kotlin function names. */
class IosBridge {
    fun openStore(): Store = Store(PlutonDb(NativeSqliteDriver(PlutonDb.Schema, "pluton.db")))

    fun words(json: String): List<Word> = decodeWords(json)
    fun forms(json: String): Map<String, String> = decodeForms(json)
    fun story(json: String): List<StoryChapter> = decodeStory(json)
    fun shelf(json: String): List<ShelfText> = decodeShelf(json).filter { it.lang == "fr" }

    /** English texts for the Norman Bridge. */
    fun bridgeShelf(json: String): List<ShelfText> = decodeShelf(json).filter { it.lang != "fr" }

    private var cognatesCache: Pair<String, Map<String, List<String>>>? = null

    fun bridgeTokens(text: String, cognatesJson: String): List<EnglishToken> {
        val map = cognatesCache?.takeIf { it.first == cognatesJson }?.second
            ?: io.github.vykosmolt.pluton.core.decodeCognates(cognatesJson).also { cognatesCache = cognatesJson to it }
        return io.github.vykosmolt.pluton.core.englishTokens(text, map)
    }

    fun bridgeNote(token: EnglishToken): String = io.github.vykosmolt.pluton.core.bridgeNote(token)
    fun conj(json: String): Map<String, ConjTable> = decodeConj(json)
    fun grammar(json: String): List<GrammarTopic> = decodeGrammar(json)

    fun buildRun(
        cards: List<CardState>,
        words: Map<String, Word>,
        now: Long,
        newToday: Int,
        pace: Pace,
        conj: Map<String, ConjTable>,
        grammar: List<GrammarTopic>,
    ): List<RunItem> = io.github.vykosmolt.pluton.core.buildRun(cards, words, now, newToday, conj, grammar, perRun = pace.perRun, perDay = pace.perDay)

    fun gotIt(queue: List<RunItem>, item: RunItem): List<RunItem> = io.github.vykosmolt.pluton.core.gotIt(queue, item)
    fun requeue(queue: List<RunItem>, item: RunItem, now: Long): List<RunItem> = io.github.vykosmolt.pluton.core.requeue(queue, item, now)
    fun next(card: Card, now: Long, rating: Int): Card =
        Scheduler().next(card, now, Rating.entries.first { it.value == rating })

    fun grade(typed: String, expected: String, lemma: String, forms: Map<String, String>): GradeResult = io.github.vykosmolt.pluton.core.grade(typed, expected, lemma, forms)
    fun gradeConj(typed: String, expected: String, agrees: Boolean): GradeResult = io.github.vykosmolt.pluton.core.gradeConj(typed, expected, agrees)
    fun gradeGram(typed: String, answers: List<String>): GradeResult = io.github.vykosmolt.pluton.core.gradeGram(typed, answers)
    fun gradeProduce(typed: String, word: Word, forms: Map<String, String>): GradeResult = io.github.vykosmolt.pluton.core.gradeProduce(typed, word, forms)
    fun gradeDictee(typed: String, sentence: String): DicteeResult = io.github.vykosmolt.pluton.core.gradeDictee(typed, sentence)
    fun errorDrills(corrections: List<CorrectionRecord>, limit: Int): List<ErrorDrill> = io.github.vykosmolt.pluton.core.errorDrills(corrections, limit)
    fun gradeError(typed: String, expected: String): GradeResult = io.github.vykosmolt.pluton.core.gradeError(typed, expected)
    fun dicteeScore(result: DicteeResult): Int = io.github.vykosmolt.pluton.core.dicteeScore(result)
    fun gradeQuiz(questions: List<QuizQuestion>, answers: List<Int>): QuizResult = io.github.vykosmolt.pluton.core.gradeQuiz(questions, answers)
    fun isCorrect(question: StoryQuestion, answer: Int): Boolean = question.correct == answer
    fun quizPasses(right: Int, total: Int): Boolean = right * 3 >= total * 2

    fun newRun(store: Store, words: Map<String, Word>, now: Long, newToday: Int, pace: Pace): IosRun =
        IosRun(store, words, now, newToday, pace)

    fun previewRun(store: Store, words: Map<String, Word>, now: Long, newToday: Int, pace: Pace): List<RunItem> =
        io.github.vykosmolt.pluton.core.buildRun(store.cards(), words, now, newToday, perRun = pace.perRun, perDay = pace.perDay)

    fun tokenize(text: String, forms: Map<String, String>): List<Token> = io.github.vykosmolt.pluton.core.tokenize(text, forms, FRENCH_ELISION)
    fun bridgeReadId(title: String): String = io.github.vykosmolt.pluton.core.bridgeReadId(title)
    fun parseCues(text: String): List<Cue> = io.github.vykosmolt.pluton.core.parseCues(text)
    fun cueAt(cues: List<Cue>, t: Double): Int = io.github.vykosmolt.pluton.core.cueAt(cues, t)
    fun coverage(tokens: List<Token>, cards: Map<String, CardState>, words: Map<String, Word>): Coverage =
        io.github.vykosmolt.pluton.core.coverage(tokens) { io.github.vykosmolt.pluton.core.status(cards[it], words[it]) }
    fun unlockList(tokens: List<Token>, cards: Map<String, CardState>, words: Map<String, Word>): List<String> =
        io.github.vykosmolt.pluton.core.unlockList(tokens, { io.github.vykosmolt.pluton.core.status(cards[it], words[it]) })

    fun unlockList(tokens: List<Token>, cards: Map<String, CardState>, words: Map<String, Word>, target: Double): List<String> =
        io.github.vykosmolt.pluton.core.unlockList(tokens, { io.github.vykosmolt.pluton.core.status(cards[it], words[it]) }, target)
    fun status(card: CardState?, word: Word?): WordStatus = io.github.vykosmolt.pluton.core.status(card, word)

    /** Swift-friendly status: 0 known, 1 learning, 2 queued, 3 new (avoids Kotlin enum interop). */
    fun statusCode(card: CardState?, word: Word?): Int = when (io.github.vykosmolt.pluton.core.status(card, word)) {
        WordStatus.Known -> 0
        WordStatus.Learning -> 1
        WordStatus.Queued -> 2
        WordStatus.New -> 3
    }
    fun isWord(card: CardState): Boolean = io.github.vykosmolt.pluton.core.isWord(card)
    fun blank(sentence: String, form: String): List<String> = io.github.vykosmolt.pluton.core.blank(sentence, form).toList()
    fun sentenceAt(text: String, at: Int): String = io.github.vykosmolt.pluton.core.sentenceAt(text, at)
    fun wordOf(lemma: String, words: Map<String, Word>): Word = words[lemma] ?: Word(lemma, display = lemma)
    fun runWord(card: CardState, words: Map<String, Word>): Word = wordOf(card.lemma, words)
    fun runSentence(card: CardState?, words: Map<String, Word>): String =
        io.github.vykosmolt.pluton.core.contextSentence(card, card?.lemma?.let { words[it] })
    fun runForm(card: CardState?, words: Map<String, Word>): String =
        io.github.vykosmolt.pluton.core.contextForm(card, card?.lemma?.let { words[it] })
    fun conjForms(table: ConjTable, tense: String): List<String> = table.t[tense]?.map { it ?: "" } ?: emptyList()
    fun conjAudioIds(table: ConjTable, tense: String): List<String> = table.audio[tense]?.map { it ?: "" } ?: emptyList()
    fun modeFor(card: Card, word: Word): String = io.github.vykosmolt.pluton.core.modeFor(card, word)
    fun produceAnswer(word: Word): String = io.github.vykosmolt.pluton.core.produceAnswer(word)
    fun parseConj(key: String): List<String> = io.github.vykosmolt.pluton.core.parseConj(key)?.let { listOf(it.first, it.second) } ?: emptyList()
    fun conjKey(verb: String, tense: String): String = io.github.vykosmolt.pluton.core.conjKey(verb, tense)
    fun gramKey(id: String): String = io.github.vykosmolt.pluton.core.gramKey(id)
    fun subject(index: Int, tense: String): String = io.github.vykosmolt.pluton.core.subject(index, tense)
    fun tenses(): List<Tense> = FRENCH_TENSES
    fun soundLessons(): List<SoundLesson> = SOUND_LESSONS
    fun chunks(): List<Chunk> = CHUNKS
    fun chunksFor(lemma: String): List<Chunk> = io.github.vykosmolt.pluton.core.chunksFor(lemma)
    fun chunkWord(chunk: Chunk): Word = io.github.vykosmolt.pluton.core.chunkWord(chunk)

    fun pathState(
        cards: List<CardState>,
        read: List<String>,
        listenedSeconds: Int,
        spoken: Int,
        talked: Int,
        written: Int,
        titles: Map<String, String>,
    ): PathState = io.github.vykosmolt.pluton.core.pathState(
        io.github.vykosmolt.pluton.core.pathMetrics(cards, read, listenedSeconds, spoken, talked, written),
        titles,
    )
    fun stages(): List<Stage> = STAGES
    fun focus(stage: StageProgress): Goal? = io.github.vykosmolt.pluton.core.focus(stage)

    fun levelCode(words: Int): String = io.github.vykosmolt.pluton.core.levelOf(words).code
    fun levelWords(words: Int): Int = io.github.vykosmolt.pluton.core.levelOf(words).words
    fun chatSystem(words: Int, learning: List<String>): String = io.github.vykosmolt.pluton.core.chatSystem(io.github.vykosmolt.pluton.core.levelOf(words), learning)
    fun chatSystemFocused(words: Int, learning: List<String>, focus: String?): String =
        io.github.vykosmolt.pluton.core.chatSystem(io.github.vykosmolt.pluton.core.levelOf(words), learning, focus)
    fun writeSystem(words: Int, prompt: String): String = io.github.vykosmolt.pluton.core.writeSystem(io.github.vykosmolt.pluton.core.levelOf(words), prompt)
    fun writeSystemFocused(words: Int, prompt: String, focus: String?): String =
        io.github.vykosmolt.pluton.core.writeSystem(io.github.vykosmolt.pluton.core.levelOf(words), prompt, focus)
    fun grammarFocus(cards: List<CardState>, grammar: List<GrammarTopic>, conj: Map<String, ConjTable>): String? =
        io.github.vykosmolt.pluton.core.grammarFocus(cards, grammar, conj)
    fun continueSystem(words: Int, known: List<String>, targets: List<String>, previous: String): String =
        io.github.vykosmolt.pluton.core.continueSystem(io.github.vykosmolt.pluton.core.levelOf(words), known, targets, previous)
    fun readerSystem(words: Int, known: List<String>, targets: List<String>): String =
        io.github.vykosmolt.pluton.core.readerSystem(io.github.vykosmolt.pluton.core.levelOf(words), known, targets)
    fun retellPrompt(): String = io.github.vykosmolt.pluton.core.RETELL
    fun writingPrompts(words: Int): List<WritingPrompt> = io.github.vykosmolt.pluton.core.suggestedWritingPrompts(io.github.vykosmolt.pluton.core.levelOf(words))
    fun parseWriting(reply: String): WritingResult? = io.github.vykosmolt.pluton.core.parseWriting(reply)
    fun parseStory(reply: String): StoryDraft? = io.github.vykosmolt.pluton.core.parseStory(reply)
    fun parseRetell(reply: String): RetellFeedback? = io.github.vykosmolt.pluton.core.parseRetell(reply)
    fun splitCorrection(text: String): Correction = io.github.vykosmolt.pluton.core.splitCorrection(text)

    fun cards(store: Store): List<CardState> = store.cards()
    fun isOnboarded(store: Store): Boolean = store.onboardedOrExisting()
    fun onboardingMinutes(store: Store): Int = store.onboardingMinutes()?.takeIf { it > 0 } ?: 10
    fun saveOnboarding(store: Store, minutes: Int) = store.saveOnboarding(minutes)
    fun lastTab(store: Store): String? = store.lastTab()
    fun saveLastTab(store: Store, tab: String) = store.saveLastTab(tab)
    fun planDone(store: Store, day: String): List<String> = store.planDone(day).toList()
    fun canDoDone(store: Store): List<String> = store.canDoDone().toList()
    fun setCanDo(store: Store, id: String, done: Boolean): List<String> = store.setCanDo(id, done).toList()
    fun weekActivity(store: Store, now: Long): WeekActivity = store.weekActivity(now)
    fun progressReport(
        cards: List<CardState>,
        words: Map<String, Word>,
        stats: Stats,
        read: List<String>,
        classicTitles: List<String>,
        bridgeTitles: List<String>,
        accuracy: Double,
        week: WeekActivity,
        canDoDone: List<String>,
        stageLevel: String,
    ): ProgressReport = io.github.vykosmolt.pluton.core.progressReport(
        cards,
        words,
        stats,
        read.toSet(),
        classicTitles.toSet(),
        bridgeTitles.toSet(),
        accuracy.takeIf { it >= 0.0 },
        week,
        canDoDone.toSet(),
        stageLevel,
    )
    fun practiceDays(store: Store): List<String> = store.practiceDays().toList()
    fun markPlanDone(store: Store, day: String, step: String): List<String> = store.markPlanDone(day, step).toList()
    fun unmarkPlanDone(store: Store, day: String, step: String): List<String> = store.unmarkPlanDone(day, step).toList()
    /** -1 when there is no review history, so Swift does not deal with a nullable primitive. */
    fun recentAccuracy(store: Store): Double = store.recentAccuracy() ?: -1.0
    fun suggestedNewWords(current: Int, accuracy: Double): Int =
        io.github.vykosmolt.pluton.core.suggestedNewWords(current, accuracy.takeIf { it >= 0.0 })
    fun coverageComfortCode(pct: Double): Int = when (io.github.vykosmolt.pluton.core.coverageComfort(pct)) {
        CoverageComfort.Comfortable -> 0
        CoverageComfort.Stretch -> 1
        CoverageComfort.Challenge -> 2
    }
    fun writings(store: Store): List<WritingRecord> = store.writings()
    fun saveWriting(store: Store, prompt: String, draft: String, result: WritingResult, now: Long): WritingRecord =
        store.saveWriting(prompt, draft, result, now)
    fun updateWritingRevision(store: Store, id: Long, revised: String, now: Long) = store.updateWritingRevision(id, revised, now)
    fun addCorrections(store: Store, source: String, errors: List<WritingError>, now: Long) = store.addCorrections(source, errors, now)
    fun corrections(store: Store): List<CorrectionRecord> = store.corrections()
    fun meta(store: Store, key: String): String? = store.meta(key)
    fun putMeta(store: Store, key: String, value: String) = store.putMeta(key, value)
    fun addWritten(store: Store): Stats = store.addWritten()
    fun exportBackup(store: Store): String = store.export()
    fun importBackup(store: Store, data: String): String? =
        runCatching { store.import(data) }.exceptionOrNull()?.message

    fun card(store: Store, lemma: String): CardState? = store.card(lemma)
    fun saveAnswer(store: Store, card: CardState, rating: Int, mode: String, at: Long) =
        store.saveAnswer(card, ReviewEntry(card.lemma, rating, mode, at))
    fun saveCard(store: Store, card: CardState, now: Long) = store.saveCard(card, now)
    fun addCard(store: Store, word: Word, form: String, sentence: String?, now: Long): CardState =
        store.addCard(word, form, sentence, now)
    fun markKnown(store: Store, lemma: String, now: Long): CardState = store.markKnown(lemma, now)
    fun unmark(store: Store, lemma: String, now: Long): CardState? = store.unmark(lemma, now)
    fun deleteCard(store: Store, lemma: String, now: Long) = store.deleteCard(lemma, now)
    fun stats(store: Store, today: String): Stats = store.stats(today)
    fun addIntroduced(store: Store, today: String, n: Int): Stats = store.addIntroduced(today, n)
    fun addRead(store: Store, today: String, id: String): Stats = store.addRead(today, id)
    fun addListened(store: Store, seconds: Int): Stats = store.addListened(seconds)
    fun addSpoken(store: Store, n: Int): Stats = store.addSpoken(n)
    fun addTalked(store: Store): Stats = store.addTalked()
    fun pace(store: Store): Pace = store.pace()
    fun savePace(store: Store, pace: Pace) = store.savePace(pace)
    fun savePace(store: Store, perRun: Int, perDay: Int) = store.savePace(Pace(perRun, perDay))
    fun texts(store: Store): List<TextRecord> = store.texts()
    fun addText(store: Store, title: String, body: String, now: Long): TextRecord = store.addText(title, body, now)
    fun deleteText(store: Store, id: Long, now: Long) = store.deleteText(id, now)
}

/** A run queue driven from Swift: words only (conjugation/grammar drills stay Android-first for now). */
class IosRun(
    private val store: Store,
    private val words: Map<String, Word>,
    now: Long,
    newToday: Int,
    pace: Pace,
) {
    private val scheduler = Scheduler()
    private val queue = buildRun(store.cards(), words, now, newToday, perRun = pace.perRun, perDay = pace.perDay).toMutableList()

    var introduced: Int = 0
        private set

    fun current(): RunItem? = queue.firstOrNull()
    fun remaining(): Int = queue.size

    fun gotIt() {
        val item = queue.firstOrNull() ?: return
        val rest = gotIt(queue.toList(), item)
        queue.clear()
        queue.addAll(rest)
        introduced++
    }

    /** Save the answer (rating 1–4), then reinsert the card if it is due again soon. */
    fun advance(rating: Int, mode: String, now: Long) {
        val item = queue.firstOrNull() ?: return
        val next = scheduler.next(item.card, now, Rating.entries.first { it.value == rating })
        val prior = store.card(item.lemma) ?: CardState(item.lemma, next, added = now)
        store.saveAnswer(prior.copy(fsrs = next), ReviewEntry(item.lemma, rating, mode, now))
        val pending = item.copy(card = next, seen = item.seen + 1, missed = item.missed || rating == 1)
        queue.removeAt(0)
        val rest = requeue(queue.toList(), pending, now)
        queue.clear()
        queue.addAll(rest)
    }
}
