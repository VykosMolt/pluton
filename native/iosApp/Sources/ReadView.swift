import SwiftUI
import PlutonCore

private struct Reading: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let body: String
    let questions: [StoryQuestion]
    let userTextId: Int64?

    init(id: String, title: String, subtitle: String = "", body: String, questions: [StoryQuestion] = [], userTextId: Int64? = nil) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.body = body
        self.questions = questions
        self.userTextId = userTextId
    }
}

struct ReadView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var sheet: ReadSheet?

    private enum ReadSheet: Identifiable {
        case reading(Reading)
        case add
        case bridge(ShelfText)

        var id: String {
            switch self {
            case .reading(let reading): return "reading-\(reading.id)"
            case .add: return "add"
            case .bridge(let text): return "bridge-\(text.title)"
            }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Library").font(.largeTitle.bold())
                    Text("Everything is open. The colours show the words you know.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    if let focus = model.grammarFocusLabel() {
                        Text("While you read, notice \(focus).")
                            .font(.caption.bold())
                            .foregroundStyle(.orange)
                    }
                    Text("Norman Bridge").font(.title2.bold()).padding(.top, 8)
                    Text("English texts read through the French they inherited after 1066. The words you know can turn back into French.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(model.bridgeShelf, id: \.title) { text in
                        bridgeCard(text)
                    }
                    Text("Pluton's story").font(.title2.bold()).padding(.top, 8)
                    ForEach(Array(model.story.enumerated()), id: \.offset) { index, chapter in
                        card(
                            id: "story-\(index)",
                            title: "Chapitre \(index + 1) · \(chapter.title)",
                            subtitle: "",
                            body: chapter.body
                        ) {
                            sheet = .reading(Reading(id: "story-\(index)", title: "Chapitre \(index + 1) · \(chapter.title)", body: chapter.body, questions: chapter.questions))
                        }
                    }
                    Text("Classics").font(.title2.bold()).padding(.top, 8)
                    ForEach(Array(model.shelf.enumerated()), id: \.offset) { _, text in
                        card(id: text.title, title: text.title, subtitle: [text.author, text.year > 0 ? String(text.year) : ""].filter { !$0.isEmpty }.joined(separator: ", "), body: text.body) {
                            sheet = .reading(Reading(id: text.title, title: text.title, subtitle: text.author, body: text.body))
                        }
                    }
                    HStack {
                        Text("Your texts").font(.title2.bold())
                        Spacer()
                        Button { sheet = .add } label: { Label("Add", systemImage: "plus") }
                    }
                    .padding(.top, 8)
                    ForEach(model.texts, id: \.id) { text in
                        card(id: "text-\(text.id)", title: text.title, subtitle: "Your text", body: text.body) {
                            sheet = .reading(Reading(id: "text-\(text.id)", title: text.title, subtitle: "Your text", body: text.body, userTextId: text.id))
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .navigationBarHidden(true)
        }
        .sheet(item: $sheet) { selection in
            switch selection {
            case .reading(let reading):
                ReadingView(reading: reading)
                    .environmentObject(model)
            case .add:
                AddTextView { title, body in
                    model.addText(title: title, body: body)
                    sheet = nil
                }
                .environmentObject(model)
            case .bridge(let text):
                BridgeView(text: text)
                    .environmentObject(model)
            }
        }
    }

    private func bridgeCard(_ text: ShelfText) -> some View {
        let tokens = model.bridgeTokens(text.body)
        let french = tokens.filter { $0.fr != nil && !$0.faux }
        let words = Set(french.compactMap { $0.fr })
        let ready = words.filter { model.statusCode(for: $0) != 3 }.count
        let faux = tokens.filter { $0.faux }.count
        return Button {
            sheet = .bridge(text)
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(text.title).font(.headline).foregroundStyle(.primary)
                if !text.author.isEmpty {
                    Text(text.author).font(.caption).foregroundStyle(.secondary)
                }
                Text("\(french.count) French-derived words · \(ready) in your deck" + (faux > 0 ? " · \(faux) false friends" : ""))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }

    private func comfortLabel(_ code: Int) -> (String, Color) {
        switch code {
        case 0: return ("Comfortable", .green)
        case 1: return ("Stretch", .orange)
        default: return ("Challenge", .red)
        }
    }

    private func card(id: String, title: String, subtitle: String, body: String, action: @escaping () -> Void) -> some View {
        let coverage = model.coverage(body)
        let labeled = comfortLabel(model.comfortCode(coverage.pct))
        let isRead = model.read.contains(id)
        return Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(title).font(.headline).foregroundStyle(.primary)
                    Spacer()
                    if isRead { Text("Read").font(.caption.bold()).foregroundStyle(.green) }
                }
                if !subtitle.isEmpty { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
                Text("\(Int(coverage.pct * 100))% understood · \(labeled.0) · \(coverage.total) words")
                    .font(.caption)
                    .foregroundStyle(labeled.1)
                SwiftUI.ProgressView(value: coverage.pct)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}

private struct ReadingView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var speaker = Speaker()
    @SwiftUI.State private var selected: String?
    @SwiftUI.State private var selectedSentence = ""
    @SwiftUI.State private var selectedForm = ""
    @SwiftUI.State private var confirmDelete = false
    @SwiftUI.State private var addable: [String] = []
    @SwiftUI.State private var contBusy = false
    @SwiftUI.State private var contError = ""
    let reading: Reading

    private var coverage: Coverage { model.coverage(reading.body) }
    private var comfortInfo: (String, Color) {
        let code = model.comfortCode(coverage.pct)
        switch code {
        case 0: return ("Comfortable", .green)
        case 1: return ("Stretch", .orange)
        default: return ("Challenge", .red)
        }
    }

    private var annotated: AttributedString {
        let tokens = model.tokens(reading.body)
        let ns = reading.body as NSString
        var result = AttributedString()
        var cursor = 0
        for token in tokens where !token.name {
            let start = max(Int(token.start), cursor)
            let end = Int(token.end)
            guard end > start, end <= ns.length else { continue }
            if start > cursor {
                result += AttributedString(ns.substring(with: NSRange(location: cursor, length: start - cursor)))
            }
            var piece = AttributedString(ns.substring(with: NSRange(location: start, length: end - start)))
            piece.link = URL(string: "pluton://word/\(token.start)")
            let status = model.statusCode(for: token.lemma)
            if status == 1 {
                piece.backgroundColor = Color.orange.opacity(0.22)
            } else if status == 2 {
                piece.backgroundColor = Color.blue.opacity(0.14)
            } else if status == 3 {
                piece.backgroundColor = Color.red.opacity(0.16)
            }
            result += piece
            cursor = end
        }
        if cursor < ns.length {
            result += AttributedString(ns.substring(from: cursor))
        }
        return result
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if !reading.subtitle.isEmpty {
                        Text(reading.subtitle).foregroundStyle(.secondary)
                    }
                    if let focus = model.grammarFocusLabel() {
                        Text("Notice: \(focus).")
                            .font(.caption.bold())
                            .foregroundStyle(.orange)
                    }
                    Text("\(Int(coverage.pct * 100))% understood · \(comfortInfo.0) · \(coverage.total) words")
                        .font(.footnote)
                        .foregroundStyle(comfortInfo.1)
                    if !addable.isEmpty {
                        Button("Queue \(addable.count) words to reach 98%") { queueWords() }
                            .buttonStyle(.bordered)
                    }
                    if reading.userTextId != nil && AIClient.ready() {
                        Button(contBusy ? "Pluton écrit la suite…" : "Continue this text") {
                            continueText()
                        }
                        .buttonStyle(.bordered)
                        .disabled(contBusy)
                        if !contError.isEmpty {
                            Text(contError).font(.footnote).foregroundStyle(.red)
                        }
                    }
                    Text(annotated)
                        .font(.system(size: 20))
                        .lineSpacing(6)
                        .environment(\.openURL, OpenURLAction { url in
                            if url.scheme == "pluton" {
                                if let raw = Int(url.lastPathComponent),
                                   let token = model.tokens(reading.body).first(where: { !$0.name && Int($0.start) == raw }) {
                                    selected = token.lemma
                                    selectedForm = token.text
                                    selectedSentence = model.sentence(at: token.start, in: reading.body)
                                } else {
                                    selected = url.lastPathComponent.removingPercentEncoding
                                    selectedForm = selected ?? ""
                                    selectedSentence = reading.body
                                }
                                return .handled
                            }
                            return .systemAction
                        })
                    if !reading.questions.isEmpty {
                        QuizView(questions: reading.questions) {
                            model.addRead(reading.id)
                        }
                    } else if model.read.contains(reading.id) {
                        Text("You read this one").bold().foregroundStyle(.green)
                    } else {
                        Button("Mark as read") { model.addRead(reading.id) }
                            .buttonStyle(.bordered)
                    }
                    if reading.userTextId != nil {
                        Button("Delete this text", role: .destructive) { confirmDelete = true }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .navigationTitle(reading.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { speaker.speak(reading.body) } label: { Image(systemName: "speaker.wave.2") }
                }
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .sheet(item: Binding(get: { selected.map(IdentifiedString.init) }, set: { selected = $0?.value })) { wrapper in
            WordDetailView(lemma: wrapper.value, sentence: selectedSentence, form: selectedForm)
                .environmentObject(model)
        }
        .alert("Delete this text?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) {
                if let id = reading.userTextId { model.deleteText(id) }
                dismiss()
            }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("The words you queued from it stay in your deck.")
        }
        .onAppear(perform: refreshAddable)
        .onChange(of: model.cardRevision) { _, _ in refreshAddable() }
    }

    private func refreshAddable() {
        addable = model.unlockList(reading.body).filter { model.statusCode(for: $0) == 3 }
    }

    private func continueText() {
        guard !contBusy else { return }
        contBusy = true
        contError = ""
        Task {
            do {
                let system = model.bridge.continueSystem(
                    words: Int32(model.vocab()),
                    known: model.knownWords(),
                    targets: model.targetWords(),
                    previous: reading.body
                )
                let reply = try await AIClient.chat(system: system, turns: [ChatTurn(role: "user", content: "Continue the text.")])
                guard let story = model.bridge.parseStory(reply: reply) else {
                    throw AIError.message("The model returned an unexpected continuation. Try again.")
                }
                let base = reading.title.hasPrefix("Suite · ") ? String(reading.title.dropFirst(8)) : reading.title
                model.addText(title: "Suite · \(base)", body: story.body)
                dismiss()
            } catch {
                contError = error.localizedDescription
            }
            contBusy = false
        }
    }

    private func queueWords() {
        let tokens = model.tokens(reading.body)
        let chosen = addable.compactMap { lemma in
            tokens.first(where: { !$0.name && $0.lemma == lemma })
        }
        model.addWords(tokens: chosen, body: reading.body)
        addable = []
    }
}

struct IdentifiedString: Identifiable {
    let value: String
    var id: String { value }
}

private struct QuizView: View {
    @EnvironmentObject var model: AppModel
    let questions: [StoryQuestion]
    let onPass: () -> Void
    @SwiftUI.State private var answers: [Int: Int] = [:]
    @SwiftUI.State private var result: (right: Int, total: Int, pass: Bool)?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Check understanding").font(.headline)
            if let result {
                Text(result.pass ? "Compris ! \(result.right) / \(result.total)" : "Pas encore… \(result.right) / \(result.total)")
                    .bold()
                    .foregroundStyle(result.pass ? .green : .red)
            } else {
                ForEach(Array(questions.enumerated()), id: \.offset) { qi, question in
                    Text("\(qi + 1). \(question.question)").font(.subheadline.bold())
                    ForEach(Array(question.options.enumerated()), id: \.offset) { oi, option in
                        Button {
                            answers[qi] = oi
                        } label: {
                            HStack {
                                Image(systemName: answers[qi] == oi ? "largecircle.fill.circle" : "circle")
                                Text(option)
                            }
                            .foregroundStyle(.primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
                Button("Check answers") {
                    let right = questions.enumerated().filter { pair in
                        model.bridge.isCorrect(question: pair.element, answer: Int32(answers[pair.offset] ?? -1))
                    }.count
                    let pass = model.bridge.quizPasses(right: Int32(right), total: Int32(questions.count))
                    result = (right, questions.count, pass)
                    if pass { onPass() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(answers.count != questions.count)
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}

struct WordDetailView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let lemma: String
    let sentence: String
    let form: String
    @SwiftUI.State private var tappedSentence = ""

    var body: some View {
        let word = model.words[lemma]
        let card = model.cards[lemma]
        let status = model.statusCode(for: lemma)
        VStack(alignment: .leading, spacing: 10) {
            Text(word?.display ?? lemma)
                .font(.system(size: 28, weight: .black))
                .foregroundStyle(word?.gender == "f" ? Color.red : Color.blue)
            Text([word?.pos, word?.gender.map { $0 == "f" ? "feminine" : "masculine" }].compactMap { $0 }.joined(separator: " · "))
                .foregroundStyle(.secondary)
            Text(word?.gloss ?? "No gloss for this one. The sentence will have to teach it.")
            Text(sentence).italic().foregroundStyle(.secondary)
            let chunks = model.chunks(for: lemma).prefix(3)
            if !chunks.isEmpty {
                Text("Chunks with this word").font(.headline)
                ForEach(Array(chunks), id: \.text) { chunk in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(chunk.text).font(.subheadline.bold())
                        Text(chunk.gloss).font(.caption).foregroundStyle(.secondary)
                        Text(chunk.example).font(.caption.italic()).foregroundStyle(.secondary)
                        Button("Add chunk") {
                            model.addChunk(chunk)
                            dismiss()
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            if status == 3 {
                HStack {
                    Button("+ Add to deck") {
                        let text = form.isEmpty ? (word?.form ?? lemma) : form
                        let token = Token(start: 0, end: Int32(text.count), text: text, lemma: lemma, name: false)
                        model.addWord(token: token, word: word, sentence: sentence)
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                    Button("I know it") {
                        model.markKnown(lemma)
                        dismiss()
                    }
                    .buttonStyle(.bordered)
                }
            } else if card?.known == true {
                Button("Undo marked known") {
                    model.unmark(lemma)
                    dismiss()
                }
            } else if status == 1 {
                Text("In your deck").foregroundStyle(.green)
            } else {
                Text("Queued for your next runs").foregroundStyle(.blue)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .presentationDetents([.medium])
    }
}

private struct AddTextView: View {
    @Environment(\.dismiss) private var dismiss
    let onSave: (String, String) -> Void
    @SwiftUI.State private var title = ""
    @SwiftUI.State private var text = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Title (optional)", text: $title)
                TextEditor(text: $text)
                    .frame(minHeight: 180)
                    .overlay(alignment: .topLeading) {
                        if text.isEmpty {
                            Text("Text in French").foregroundStyle(.secondary).padding(.top, 8)
                        }
                    }
            }
            .navigationTitle("Add a text")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(title, text) }
                        .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
