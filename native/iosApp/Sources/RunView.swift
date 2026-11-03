import SwiftUI
import PlutonCore

struct RunView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var speaker = Speaker()

    let run: IosRun
    let onDone: (Int, Int) -> Void

    init(run: IosRun, onDone: @escaping (Int, Int) -> Void) {
        self.run = run
        self.onDone = onDone
    }

    @SwiftUI.State private var item: RunItem?
    @SwiftUI.State private var typed = ""
    @SwiftUI.State private var detail: Detail?
    @SwiftUI.State private var done = 0
    @SwiftUI.State private var finished = false
    @SwiftUI.State private var didReport = false
    @SwiftUI.State private var person = 0
    @SwiftUI.State private var grammarPick = 0

    private struct Detail {
        let mode: String
        let rating: Int32
        let result: String
        let correct: String
    }

    private func word(for item: RunItem) -> Word {
        model.words[item.lemma] ?? model.bridge.wordOf(lemma: item.lemma, words: model.words)
    }

    private func sentence(for item: RunItem) -> String {
        model.sentence(for: item.lemma)
    }

    private func form(for item: RunItem) -> String {
        model.form(for: item.lemma)
    }

    private func mode(for item: RunItem) -> String {
        if conjPair(item) != nil { return "conj" }
        if grammarTopic(item) != nil { return "gram" }
        return model.bridge.modeFor(card: item.card, word: word(for: item))
    }

    private func conjPair(_ item: RunItem) -> (String, String)? {
        let parts = model.bridge.parseConj(key: item.lemma)
        return parts.count == 2 ? (parts[0], parts[1]) : nil
    }

    private func grammarTopic(_ item: RunItem) -> GrammarTopic? {
        model.grammar.first { model.bridge.gramKey(id: $0.id) == item.lemma }
    }

    private func tenseName(_ id: String) -> String {
        model.bridge.tenses().first { $0.id == id }?.name ?? id
    }

    private var progressText: String {
        guard item != nil else { return "" }
        return "\(done + 1) / \(done + Int(run.remaining()))"
    }

    private var completeView: some View {
        VStack(spacing: 12) {
            Text("Run complete").font(.title.bold())
            Text("\(run.introduced) new cards introduced")
            Button("Close") { finish() }.buttonStyle(.borderedProminent)
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if finished || item == nil {
                    completeView
                } else if let item {
                    content(for: item)
                }
            }
            .padding(20)
            .navigationTitle("Run")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { finish() } label: { Image(systemName: "xmark") }
                }
                ToolbarItem(placement: .principal) {
                    Text(progressText)
                        .font(.footnote.bold())
                        .foregroundStyle(.secondary)
                }
            }
        }
        .onAppear { if item == nil && !finished { loadCurrent() } }
        .interactiveDismissDisabled() // a swipe must go through finish(), so the daily budget is recorded
    }

    @ViewBuilder
    private func content(for item: RunItem) -> some View {
        if let pair = conjPair(item), let table = model.conj[pair.0] {
            conjContent(item: item, verb: pair.0, tense: pair.1, table: table)
        } else if let topic = grammarTopic(item) {
            grammarContent(item: item, topic: topic)
        } else {
            wordContent(for: item)
        }
    }

    @ViewBuilder
    private func wordContent(for item: RunItem) -> some View {
        let word = word(for: item)
        let sentence = sentence(for: item)
        let form = form(for: item)
        let parts = model.bridge.blank(sentence: sentence, form: form)
        VStack(alignment: .leading, spacing: 14) {
            if item.phase == "intro" {
                introCard(item: item, word: word, sentence: sentence, parts: parts)
            } else {
                quizCard(item: item, word: word, sentence: sentence, parts: parts)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func conjContent(item: RunItem, verb: String, tense: String, table: ConjTable) -> some View {
        let forms = model.bridge.conjForms(table: table, tense: tense)
        let info = model.bridge.tenses().first { $0.id == tense }
        VStack(alignment: .leading, spacing: 10) {
            if item.phase == "intro" {
                Text("CONJUGATION · \(tenseName(tense))").font(.caption.bold()).foregroundStyle(.orange)
                Text("\(verb) · \(tenseName(tense))").font(.system(size: 30, weight: .black))
                Text([info?.en, model.words[verb]?.gloss].compactMap { $0 }.joined(separator: " · "))
                    .foregroundStyle(.secondary)
                Text(info?.how ?? "").font(.footnote).foregroundStyle(.secondary)
                ForEach(0..<forms.count, id: \.self) { index in
                    if !forms[index].isEmpty {
                        Button {
                            speaker.speak("\(model.bridge.subject(index: Int32(index), tense: tense)) \(forms[index])")
                        } label: {
                            HStack {
                                Text(model.bridge.subject(index: Int32(index), tense: tense))
                                    .foregroundStyle(.secondary)
                                Text(forms[index]).bold()
                            }
                        }
                    }
                }
                Button {
                    run.gotIt()
                    loadCurrent()
                } label: {
                    Text("Got it →").font(.headline).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            } else {
                Text("CONJUGATE").font(.caption.bold()).foregroundStyle(.secondary)
                Text("\(verb) · \(tenseName(tense))").font(.title3.bold())
                if detail == nil {
                    Text("\(model.bridge.subject(index: Int32(person), tense: tense)) ______").font(.title3)
                    TextField("Your answer", text: $typed)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .onSubmit { submit(item: item, parts: []) }
                    Button { submit(item: item, parts: []) } label: {
                        Text("Check").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
                } else if let detail {
                    Text(message(detail.result))
                        .font(.headline)
                        .foregroundStyle(detail.result == "right" ? .green : detail.result == "almost" ? .orange : .red)
                    Text("→ \(model.bridge.subject(index: Int32(person), tense: tense)) \(detail.correct)")
                        .font(.headline)
                    Button { next() } label: {
                        Text("Next →").font(.headline).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func grammarContent(item: RunItem, topic: GrammarTopic) -> some View {
        let exercise: GrammarItem? = topic.items.isEmpty ? nil : topic.items[min(grammarPick, topic.items.count - 1)]
        let parts = exercise?.q.components(separatedBy: "___") ?? []
        VStack(alignment: .leading, spacing: 10) {
            if item.phase == "intro" {
                Text("GRAMMAR · \(topic.level)").font(.caption.bold()).foregroundStyle(.orange)
                Text(topic.name).font(.system(size: 30, weight: .black))
                if !topic.en.isEmpty { Text(topic.en).foregroundStyle(.secondary) }
                Text(topic.how).font(.footnote)
                ForEach(Array(topic.items.prefix(2).enumerated()), id: \.offset) { _, example in
                    let exampleParts = example.q.components(separatedBy: "___")
                    Text("\(exampleParts.first ?? "") \(example.a.first ?? "") \(exampleParts.count > 1 ? exampleParts[1] : "")")
                        .font(.body.weight(.semibold))
                }
                Button {
                    run.gotIt()
                    loadCurrent()
                } label: {
                    Text("Got it →").font(.headline).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            } else {
                Text("GRAMMAR").font(.caption.bold()).foregroundStyle(.secondary)
                Text(topic.name).font(.title3.bold())
                if detail == nil {
                    Text("\(parts.first ?? "") ______ \(parts.count > 1 ? parts[1] : "")").font(.title3)
                    TextField("Your answer", text: $typed)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .onSubmit { submit(item: item, parts: []) }
                    Button { submit(item: item, parts: []) } label: {
                        Text("Check").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
                } else if let detail {
                    Text(message(detail.result))
                        .font(.headline)
                        .foregroundStyle(detail.result == "right" ? .green : detail.result == "almost" ? .orange : .red)
                    Text("→ \(detail.correct)").font(.headline)
                    Text(topic.how).font(.footnote).foregroundStyle(.secondary)
                    Button { next() } label: {
                        Text("Next →").font(.headline).frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func introCard(item: RunItem, word: Word, sentence: String, parts: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("NEW WORD").font(.caption.bold()).foregroundStyle(.orange)
            Text(word.display)
                .font(.system(size: 40, weight: .black))
                .foregroundStyle(word.gender == "f" ? Color.red : Color.blue)
            Text([word.pos, word.gender.map { $0 == "f" ? "feminine" : "masculine" }].compactMap { $0 }.joined(separator: " · "))
                .foregroundStyle(.secondary)
            if !word.gloss.isEmpty { Text(word.gloss).font(.title3.bold()) }
            if !sentence.isEmpty { Text(sentence).font(.title3) }
            if let translation = word.translation, !translation.isEmpty {
                Text(translation).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        Button {
            run.gotIt()
            loadCurrent()
        } label: {
            Text("Got it →").font(.headline).frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
    }

    @ViewBuilder
    private func quizCard(item: RunItem, word: Word, sentence: String, parts: [String]) -> some View {
        let mode = mode(for: item)
        return VStack(alignment: .leading, spacing: 12) {
            Text(tag(mode)).font(.caption.bold()).foregroundStyle(.secondary)
            switch mode {
            case "produce":
                Text("Comment dit-on « \(word.gloss.isEmpty ? word.display : word.gloss) » ?").font(.title3)
            case "dictee":
                Text("Écris tout ce que tu entends.").font(.title3)
            default:
                Text(parts.count == 3 ? "\(parts[0])______\(parts[2])" : sentence).font(.title3)
            }
            if mode == "dictation" || mode == "dictee" || mode == "cloze" {
                Button {
                    speaker.speak(sentence.isEmpty ? word.display : sentence)
                } label: {
                    Label("Hear it", systemImage: "speaker.wave.2")
                }
                .buttonStyle(.bordered)
            }
            if detail == nil {
                TextField("Your answer", text: $typed)
                    .textFieldStyle(.roundedBorder)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .onSubmit { submit(item: item, parts: parts) }
                Button { submit(item: item, parts: parts) } label: {
                    Text("Check").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(typed.trimmingCharacters(in: .whitespaces).isEmpty)
            } else if let detail {
                Text(message(detail.result))
                    .font(.headline)
                    .foregroundStyle(detail.result == "right" ? .green : detail.result == "almost" ? .orange : .red)
                Text("→ \(detail.correct)").font(.headline)
                if mode == "produce" { Text("\(word.display) — \(word.gloss)").font(.subheadline) }
                Button { next() } label: {
                    Text("Next →").font(.headline).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private func tag(_ mode: String) -> String {
        switch mode {
        case "conj": return "CONJUGATE"
        case "gram": return "GRAMMAR"
        case "dictation": return "LISTEN, TYPE THE MISSING WORD"
        case "dictee": return "DICTÉE: WRITE THE WHOLE SENTENCE"
        case "produce": return "SAY IT IN FRENCH"
        default: return "FILL THE GAP"
        }
    }

    private func message(_ result: String) -> String {
        switch result {
        case "right": return "Parfait !"
        case "almost": return "Almost."
        default: return "Not quite."
        }
    }

    private func submit(item: RunItem, parts: [String]) {
        guard detail == nil else { return }
        let trimmed = typed.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let word = word(for: item)
        let result: String
        let correct: String
        let mode: String
        if let pair = conjPair(item), let table = model.conj[pair.0] {
            let forms = model.bridge.conjForms(table: table, tense: pair.1)
            let expected = forms.indices.contains(person) ? forms[person] : ""
            let graded = model.bridge.gradeConj(typed: trimmed, expected: expected, agrees: pair.1 == "pc" && table.aux == "être")
            result = graded.result
            correct = expected
            mode = "conj"
        } else if let topic = grammarTopic(item) {
            let exercise: GrammarItem? = topic.items.isEmpty ? nil : topic.items[min(grammarPick, topic.items.count - 1)]
            let graded = model.bridge.gradeGram(typed: trimmed, answers: exercise?.a ?? [])
            result = graded.result
            correct = exercise?.a.joined(separator: " / ") ?? ""
            mode = "gram"
        } else {
            let wordMode = mode(for: item)
            switch wordMode {
            case "produce":
                let graded = model.bridge.gradeProduce(typed: trimmed, word: word, forms: model.forms)
                result = graded.result
                correct = model.bridge.produceAnswer(word: word)
            case "dictee":
                let graded = model.bridge.gradeDictee(typed: trimmed, sentence: sentence(for: item))
                result = graded.result
                correct = sentence(for: item)
            default:
                let graded = model.bridge.grade(typed: trimmed, expected: parts.count == 3 ? parts[1] : "", lemma: item.lemma, forms: model.forms)
                result = graded.result
                correct = parts.count == 3 ? parts[1] : ""
            }
            mode = wordMode
        }
        let rating: Int32 = result == "right" ? 3 : result == "almost" ? 2 : 1
        detail = Detail(mode: mode, rating: rating, result: result, correct: correct)
        speaker.speak(mode == "dictee" ? sentence(for: item) : correct)
    }

    private func next() {
        guard let detail else { return }
        run.advance(rating: detail.rating, mode: detail.mode, now: AppModel.now())
        model.refresh()
        done += 1
        self.detail = nil
        loadCurrent()
    }

    private func loadCurrent() {
        typed = ""
        detail = nil
        item = run.current()
        guard let item else {
            finished = true
            return
        }
        if let pair = conjPair(item), let table = model.conj[pair.0] {
            let forms = model.bridge.conjForms(table: table, tense: pair.1)
            let open = forms.indices.filter { !forms[$0].isEmpty }
            person = open.randomElement() ?? 0
        } else if let topic = grammarTopic(item), !topic.items.isEmpty {
            grammarPick = Int.random(in: 0..<topic.items.count)
        }
        if item.phase == "quiz" {
            let mode = mode(for: item)
            if mode == "dictation" || mode == "dictee" {
                speaker.speak(sentence(for: item))
            }
        }
    }

    private func finish() {
        guard !didReport else { return }
        didReport = true
        onDone(Int(run.introduced), done)
        dismiss()
    }
}
