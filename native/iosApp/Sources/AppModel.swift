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
    @Published var conj: [String: ConjTable] = [:]
    @Published var grammar: [GrammarTopic] = []
    @Published var cards: [String: CardState] = [:]
    @Published var texts: [TextRecord] = []
    @Published var stats: Stats
    @Published var pace: Pace
    @Published var read: Set<String> = []
    @Published var newToday: Int32 = 0
    @Published var error: String?

    init() {
        let bridge = IosBridge()
        self.bridge = bridge
        self.store = bridge.openStore()
        self.stats = bridge.stats(store: store, today: Self.today())
        self.pace = bridge.pace(store: store)
        load()
    }

    private func load() {
        do {
            words = Dictionary(uniqueKeysWithValues: try bridge.words(json: json("words")).map { ($0.lemma, $0) })
            forms = try bridge.forms(json: json("forms"))
            story = try bridge.story(json: json("story"))
            shelf = try bridge.shelf(json: json("shelf"))
            conj = try bridge.conj(json: json("conj"))
            grammar = try bridge.grammar(json: json("grammar"))
            refresh()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func json(_ name: String) throws -> String {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json", subdirectory: "fr") else {
            throw NSError(domain: "Pluton", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing fr/\(name).json"])
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    func refresh() {
        cards = Dictionary(uniqueKeysWithValues: bridge.cards(store: store).map { ($0.lemma, $0) })
        texts = bridge.texts(store: store)
        stats = bridge.stats(store: store, today: Self.today())
        read = Set(stats.read)
        newToday = stats.newCount
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

    func tokens(_ body: String) -> [Token] {
        bridge.tokenize(text: body, forms: forms)
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
            .filter { statusCode(for: $0.lemma) == 1 }
            .sorted { (($0.fsrs.lastReview?.int64Value ?? 0) > ($1.fsrs.lastReview?.int64Value ?? 0)) }
            .prefix(40)
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
        let chosen = word ?? bridge.wordOf(lemma: token.lemma, words: words)
        _ = bridge.addCard(store: store, word: chosen, form: token.text, sentence: sentence, now: Self.now())
        refresh()
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
    }

    func addListened(_ seconds: Int) {
        _ = bridge.addListened(store: store, seconds: Int32(seconds))
        refresh()
    }

    func addSpoken() {
        _ = bridge.addSpoken(store: store, n: 1)
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

    func sentence(for card: CardState?) -> String { bridge.runSentence(card: card, words: words) }
    func form(for card: CardState?) -> String { bridge.runForm(card: card, words: words) }
}
