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
    @SwiftUI.State private var reading: Reading?
    @SwiftUI.State private var addOpen = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Library").font(.largeTitle.bold())
                    Text("Everything is open. The colours show the words you know.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(Array(model.story.enumerated()), id: \.offset) { index, chapter in
                        card(
                            id: "story-\(index)",
                            title: "Chapitre \(index + 1) · \(chapter.title)",
                            subtitle: "",
                            body: chapter.body
                        ) {
                            reading = Reading(id: "story-\(index)", title: "Chapitre \(index + 1) · \(chapter.title)", body: chapter.body, questions: chapter.questions)
                        }
                    }
                    Text("Classics").font(.title2.bold()).padding(.top, 8)
                    ForEach(Array(model.shelf.enumerated()), id: \.offset) { _, text in
                        card(id: text.title, title: text.title, subtitle: [text.author, text.year > 0 ? String(text.year) : ""].filter { !$0.isEmpty }.joined(separator: ", "), body: text.body) {
                            reading = Reading(id: text.title, title: text.title, subtitle: text.author, body: text.body)
                        }
                    }
                    HStack {
                        Text("Your texts").font(.title2.bold())
                        Spacer()
                        Button { addOpen = true } label: { Label("Add", systemImage: "plus") }
                    }
                    .padding(.top, 8)
                    ForEach(model.texts, id: \.id) { text in
                        card(id: "text-\(text.id)", title: text.title, subtitle: "Your text", body: text.body) {
                            reading = Reading(id: "text-\(text.id)", title: text.title, subtitle: "Your text", body: text.body, userTextId: text.id)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .navigationBarHidden(true)
        }
        .sheet(item: $reading) { reading in
            ReadingView(reading: reading)
                .environmentObject(model)
        }
        .sheet(isPresented: $addOpen) {
            AddTextView { title, body in
                model.addText(title: title, body: body)
                addOpen = false
            }
            .environmentObject(model)
        }
    }

    private func card(id: String, title: String, subtitle: String, body: String, action: @escaping () -> Void) -> some View {
        let coverage = model.coverage(body)
        let isRead = model.read.contains(id)
        return Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(title).font(.headline).foregroundStyle(.primary)
                    Spacer()
                    if isRead { Text("✓ read").font(.caption.bold()).foregroundStyle(.green) }
                }
                if !subtitle.isEmpty { Text(subtitle).font(.caption).foregroundStyle(.secondary) }
                Text("\(Int(coverage.pct * 100))% understood · \(coverage.total) words")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ProgressView(value: coverage.pct)
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
    @SwiftUI.State private var confirmDelete = false
    let reading: Reading

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
            let escaped = token.lemma.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? token.lemma
            piece.link = URL(string: "pluton://word/\(escaped)")
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
                    Text(annotated)
                        .font(.system(size: 20))
                        .lineSpacing(6)
                        .environment(\.openURL, OpenURLAction { url in
                            if url.scheme == "pluton" {
                                selected = url.lastPathComponent.removingPercentEncoding
                                return .handled
                            }
                            return .systemAction
                        })
                    if !reading.questions.isEmpty {
                        QuizView(questions: reading.questions) {
                            model.addRead(reading.id)
                        }
                    } else if model.read.contains(reading.id) {
                        Text("✓ You read this one").bold().foregroundStyle(.green)
                    } else {
                        Button("✓ I read it") { model.addRead(reading.id) }
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
            WordDetailView(lemma: wrapper.value, sentence: reading.body)
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
    }
}

private struct IdentifiedString: Identifiable {
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
                Text(result.pass ? "✓ Compris ! \(result.right) / \(result.total)" : "Pas encore… \(result.right) / \(result.total)")
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

private struct WordDetailView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let lemma: String
    let sentence: String
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
            if status == 3 {
                HStack {
                    Button("+ Add to deck") {
                        let token = Token(start: 0, end: Int32(lemma.count), text: word?.form ?? lemma, lemma: lemma, name: false)
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
                Text("In your deck ✓").foregroundStyle(.green)
            } else {
                Text("Queued for your next runs ✓").foregroundStyle(.blue)
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
