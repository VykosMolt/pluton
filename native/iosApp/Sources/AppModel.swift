import Foundation
import PlutonCore

/// Loads the generated data and SQLite store once, then keeps SwiftUI in sync with the shared core.
@MainActor
final class AppModel: ObservableObject {
    let bridge: IosBridge
    let store: Store

    @Published var words: [String: Word] = [:]
    @Published var forms: [String: String] = [:]
    @Published var story: [StoryChapter] = []
    @Published var shelf: [ShelfText] = []
    @Published var bridgeShelf: [ShelfText] = []
    @Published var cognatesJson: String = ""
    @Published var conj: [String: ConjTable] = [:]
    @Published var grammar: [GrammarTopic] = []
    @Published var cards: [String: CardState] = [:]
    @Published var texts: [TextRecord] = []
    @Published var stats: Stats
    @Published var pace: Pace
    @Published var read: Set<String> = []
    @Published var newToday: Int32 = 0
    @Published var onboarded = false
    @Published var dailyMinutes = 10
    @Published var planDone: Set<String> = []
    @Published var practiceDays: Set<String> = []
    @Published var accuracy: Double = -1
    @Published var cardRevision = 0
    @Published var day = AppModel.today()
    @Published var writings: [WritingRecord] = []
    @Published var corrections: [CorrectionRecord] = []
    @Published var error: String?

    init() {
        let bridge = IosBridge()
        self.bridge = bridge
        self.store = bridge.openStore()
        self.stats = bridge.stats(store: store, today: Self.today())
        self.pace = bridge.pace(store: store)
        load()
        self.onboarded = bridge.isOnboarded(store: store)
        self.dailyMinutes = Int(bridge.onboardingMinutes(store: store))
        self.planDone = Set(bridge.planDone(store: store, day: Self.today()))
        self.practiceDays = Set(bridge.practiceDays(store: store))
        self.accuracy = bridge.recentAccuracy(store: store)
        self.writings = bridge.writings(store: store)
        self.corrections = bridge.corrections(store: store)
    }

    private func load() {
        do {
            var loadedWords = Dictionary(uniqueKeysWithValues: try bridge.words(json: json("words")).map { ($0.lemma, $0) })
            for chunk in bridge.chunks() {
                let word = bridge.chunkWord(chunk: chunk)
                loadedWords[word.lemma] = word
            }
            words = loadedWords
            forms = try bridge.forms(json: json("forms"))
            story = try bridge.story(json: json("story"))
            shelf = try bridge.shelf(json: json("shelf"))
            bridgeShelf = try bridge.bridgeShelf(json: json("shelf"))
            cognatesJson = try json("cognates")
            conj = try bridge.conj(json: json("conj"))
            grammar = try bridge.grammar(json: json("grammar"))
            refresh()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func json(_ name: String) throws -> String {
        // A top-level "Resources" directory makes CoreFoundation treat the bundle as a macOS layout and
        // simctl/installd reject it as "Missing bundle ID"; the data lives under Data/fr instead.
        let url = Bundle.main.url(forResource: name, withExtension: "json", subdirectory: "Data/fr")
            ?? Bundle.main.url(forResource: name, withExtension: "json", subdirectory: "fr")
        guard let url else {
            throw NSError(domain: "Pluton", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing fr/\(name).json"])
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    func rolloverDay() {
        let now = Self.today()
        guard now != day else { return }
        day = now
        refresh()
    }

    func refresh() {
        cards = Dictionary(uniqueKeysWithValues: bridge.cards(store: store).map { ($0.lemma, $0) })
        texts = bridge.texts(store: store)
        stats = bridge.stats(store: store, today: Self.today())
        read = Set(stats.read)
        newToday = stats.newCount
        cardRevision += 1
        planDone = Set(bridge.planDone(store: store, day: Self.today()))
        practiceDays = Set(bridge.practiceDays(store: store))
        accuracy = bridge.recentAccuracy(store: store)
        writings = bridge.writings(store: store)
        corrections = bridge.corrections(store: store)
    }

    static func today() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    static func now() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    func statusCode(for lemma: String) -> Int32 {
        bridge.statusCode(card: cards[lemma], word: words[lemma])
    }

    func coverage(_ body: String) -> Coverage {
        bridge.coverage(tokens: tokens(body), cards: cards, words: words)
    }

    func unlockList(_ body: String, target: Double = 0.98) -> [String] {
        bridge.unlockList(tokens: tokens(body), cards: cards, words: words, target: target)
    }

    func tokens(_ body: String) -> [Token] {
        bridge.tokenize(text: body, forms: forms)
    }

    func bridgeReadId(_ title: String) -> String {
        bridge.bridgeReadId(title: title)
    }

    func parseCues(_ text: String) -> [Cue] {
        bridge.parseCues(text: text)
    }

    func cueIndex(_ cues: [Cue], at time: Double) -> Int {
        Int(bridge.cueAt(cues: cues, t: time))
    }

    func gradeDictee(_ typed: String, _ sentence: String) -> DicteeResult {
        bridge.gradeDictee(typed: typed, sentence: sentence)
    }

    func dicteeScore(_ result: DicteeResult) -> Int {
        Int(bridge.dicteeScore(result: result))
    }

    private var bridgeCache: [String: [EnglishToken]] = [:]

    func bridgeTokens(_ body: String) -> [EnglishToken] {
        if let hit = bridgeCache[body] { return hit }
        let tokens = bridge.bridgeTokens(text: body, cognatesJson: cognatesJson)
        bridgeCache[body] = tokens
        return tokens
    }

    func bridgeNote(_ token: EnglishToken) -> String {
        bridge.bridgeNote(token: token)
    }

    func vocab() -> Int {
        cards.values.filter {
            let status = statusCode(for: $0.lemma)
            return status == 0 || status == 1
        }.count
    }

    func levelCode() -> String { bridge.levelCode(words: Int32(vocab())) }

    func learningWords() -> [String] {
        cards.values
            .filter { !$0.lemma.hasPrefix("chunk:") && statusCode(for: $0.lemma) == 1 }
            .sorted { (($0.fsrs.lastReview?.int64Value ?? 0) > ($1.fsrs.lastReview?.int64Value ?? 0)) }
            .prefix(40)
            .map { $0.lemma }
    }

    func targetWords() -> [String] {
        cards.values
            .filter { !$0.lemma.hasPrefix("chunk:") && statusCode(for: $0.lemma) == 1 && !$0.known }
            .prefix(8)
            .map { $0.lemma }
    }

    func path() -> PathState {
        let titles = Dictionary(uniqueKeysWithValues: story.enumerated().map {
            ("story-\($0.offset)", "Chapitre \($0.offset + 1) · \($0.element.title)")
        })
        return bridge.pathState(
            cards: Array(cards.values),
            read: Array(read),
            listenedSeconds: stats.listened,
            spoken: stats.spoken,
            talked: stats.talked,
            written: stats.written,
            titles: titles
        )
    }

    func newRun() -> IosRun {
        bridge.doNewRun(store: store, words: words, now: Self.now(), newToday: newToday, pace: pace)
    }

    func addWord(token: Token, word: Word?, sentence: String) {
        // A stale sheet must never replace a known/reviewed card with a fresh one.
        guard statusCode(for: token.lemma) == 3 else { return }
        let chosen = word ?? bridge.wordOf(lemma: token.lemma, words: words)
        _ = bridge.addCard(store: store, word: chosen, form: token.text, sentence: sentence, now: Self.now())
        refresh()
    }

    func addWords(tokens: [Token], body: String) {
        for token in tokens {
            guard statusCode(for: token.lemma) == 3 else { continue }
            let chosen = words[token.lemma] ?? bridge.wordOf(lemma: token.lemma, words: words)
            let sentence = bridge.sentenceAt(text: body, at: token.start)
            _ = bridge.addCard(store: store, word: chosen, form: token.text, sentence: sentence, now: Self.now())
        }
        refresh()
    }

    func sentence(at offset: Int32, in body: String) -> String {
        bridge.sentenceAt(text: body, at: offset)
    }

    func markKnown(_ lemma: String) {
        _ = bridge.markKnown(store: store, lemma: lemma, now: Self.now())
        refresh()
    }

    func unmark(_ lemma: String) {
        _ = bridge.unmark(store: store, lemma: lemma, now: Self.now())
        refresh()
    }

    func addRead(_ id: String) {
        _ = bridge.addRead(store: store, today: Self.today(), id: id)
        refresh()
        markPlan("read")
    }

    func addListened(_ seconds: Int) {
        _ = bridge.addListened(store: store, seconds: Int32(seconds))
        refresh()
    }

    func addSpoken() {
        _ = bridge.addSpoken(store: store, n: 1)
        refresh()
    }

    func addTalked() {
        _ = bridge.addTalked(store: store)
        refresh()
    }

    func addText(title: String, body: String) {
        _ = bridge.addText(store: store, title: title, body: body, now: Self.now())
        refresh()
    }

    func deleteText(_ id: Int64) {
        bridge.deleteText(store: store, id: id, now: Self.now())
        refresh()
    }

    func savePace(perRun: Int32, perDay: Int32) {
        bridge.savePace(store: store, perRun: perRun, perDay: perDay)
        pace = bridge.pace(store: store)
    }

    func knownWords() -> [String] {
        cards.values
            .filter {
                !$0.lemma.hasPrefix("chunk:") && statusCode(for: $0.lemma) != 3 &&
                    (words[$0.lemma]?.gloss.trimmingCharacters(in: .whitespaces).isEmpty == false)
            }
            .prefix(40)
            .map { $0.lemma }
    }

    func progressReport() -> ProgressReport {
        let path = self.path()
        let stageLevel = Int(path.current) < path.stages.count ? path.stages[Int(path.current)].stage.level : "C1"
        return bridge.progressReport(
            cards: Array(cards.values),
            words: words,
            stats: stats,
            read: Array(read),
            classicTitles: shelf.filter { $0.lang == "fr" }.map { $0.title },
            // `shelf` is already filtered to French; the English Bridge texts live in `bridgeShelf`.
            bridgeTitles: bridgeShelf.map { bridgeReadId($0.title) },
            accuracy: accuracy,
            week: bridge.weekActivity(store: store, now: Self.now()),
            canDoDone: Array(Set(bridge.canDoDone(store: store))),
            stageLevel: stageLevel
        )
    }

    func setCanDo(_ id: String, _ done: Bool) -> [String] {
        bridge.setCanDo(store: store, id: id, done: done)
    }

    func grammarFocusLabel() -> String? {
        bridge.grammarFocus(cards: Array(cards.values), grammar: grammar, conj: conj)
    }

    func writeSystem(prompt: String, focus: String?) -> String {
        bridge.writeSystemFocused(words: Int32(vocab()), prompt: prompt, focus: focus)
    }

    func chatSystem(learning: [String], focus: String?) -> String {
        bridge.chatSystemFocused(words: Int32(vocab()), learning: learning, focus: focus)
    }

    func chunks(for lemma: String) -> [Chunk] { bridge.chunksFor(lemma: lemma) }

    func errorDrills(limit: Int32 = 20) -> [ErrorDrill] {
        bridge.errorDrills(corrections: corrections, now: Self.now(), limit: limit)
    }

    func gradeError(_ typed: String, _ expected: String) -> GradeResult {
        bridge.gradeError(typed: typed, expected: expected)
    }

    func reviewError(id: Int64, result: String) {
        _ = bridge.reviewError(store: store, id: id, result: result, now: Self.now())
        refresh()
    }

    func addChunk(_ chunk: Chunk) {
        let word = bridge.chunkWord(chunk: chunk)
        _ = bridge.addCard(store: store, word: word, form: chunk.text, sentence: chunk.example, now: Self.now())
        refresh()
    }

    func sentence(for lemma: String) -> String { bridge.runSentence(card: cards[lemma], words: words) }
    func form(for lemma: String) -> String { bridge.runForm(card: cards[lemma], words: words) }

    func saveOnboarding(minutes: Int) {
        bridge.saveOnboarding(store: store, minutes: Int32(minutes))
        dailyMinutes = minutes
        onboarded = true
        let chosen: (Int32, Int32) = minutes <= 5 ? (3, 10) : (minutes <= 10 ? (5, 20) : (minutes <= 20 ? (8, 30) : (10, 40)))
        bridge.savePace(store: store, perRun: chosen.0, perDay: chosen.1)
        pace = bridge.pace(store: store)
        bridge.saveLastTab(store: store, tab: "Home")
    }

    func togglePlan(_ step: String) {
        if planDone.contains(step) {
            planDone = Set(bridge.unmarkPlanDone(store: store, day: Self.today(), step: step))
        } else {
            planDone = Set(bridge.markPlanDone(store: store, day: Self.today(), step: step))
        }
        practiceDays = Set(bridge.practiceDays(store: store))
    }

    func markPlan(_ step: String) {
        planDone = Set(bridge.markPlanDone(store: store, day: Self.today(), step: step))
        practiceDays = Set(bridge.practiceDays(store: store))
    }

    func suggestedWords() -> Int {
        Int(bridge.suggestedNewWords(current: pace.perRun, accuracy: accuracy))
    }

    func useSuggestedWords() {
        let next = bridge.suggestedNewWords(current: pace.perRun, accuracy: accuracy)
        bridge.savePace(store: store, perRun: next, perDay: pace.perDay)
        pace = bridge.pace(store: store)
    }

    func comfortCode(_ pct: Double) -> Int { Int(bridge.coverageComfortCode(pct: pct)) }

    func saveWriting(prompt: String, draft: String, result: WritingResult) -> WritingRecord {
        let record = bridge.saveWriting(store: store, prompt: prompt, draft: draft, result: result, now: Self.now())
        bridge.addWritten(store: store)
        refresh()
        return record
    }

    func reviseWriting(id: Int64, revised: String, result: WritingResult) {
        bridge.updateWritingRevision(store: store, id: id, revised: revised, now: Self.now())
        bridge.addCorrections(store: store, source: "revision", errors: result.errors, now: Self.now())
        refresh()
    }

    func metaValue(_ key: String) -> String? { bridge.meta(store: store, key: key) }
    func setMeta(_ key: String, _ value: String) { bridge.putMeta(store: store, key: key, value: value) }
    func draftText() -> String { bridge.meta(store: store, key: "writing_draft") ?? "" }
    func draftPrompt() -> Int { Int(bridge.meta(store: store, key: "writing_prompt") ?? "0") ?? 0 }
    func saveDraft(_ text: String, prompt: Int) {
        bridge.putMeta(store: store, key: "writing_draft", value: text)
        bridge.putMeta(store: store, key: "writing_prompt", value: String(prompt))
    }

    func exportBackup() -> String { bridge.exportBackup(store: store) }

    func importBackup(_ data: String) -> String? {
        if let error = bridge.importBackup(store: store, data: data) { return error }
        refresh()
        onboarded = bridge.isOnboarded(store: store)
        dailyMinutes = Int(bridge.onboardingMinutes(store: store))
        planDone = Set(bridge.planDone(store: store, day: Self.today()))
        practiceDays = Set(bridge.practiceDays(store: store))
        accuracy = bridge.recentAccuracy(store: store)
        writings = bridge.writings(store: store)
        corrections = bridge.corrections(store: store)
        return nil
    }

    func lastTab() -> String? { bridge.lastTab(store: store) }
    func saveLastTab(_ tab: String) { bridge.saveLastTab(store: store, tab: tab) }
}
