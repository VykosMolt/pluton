import SwiftUI
import PlutonCore

struct RunView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @StateObject private var speaker = Speaker()

    let run: IosRun
    let onDone: (Int, Int) -> Void

    @State private var item: RunItem?
    @State private var typed = ""
    @State private var detail: Detail?
    @State private var done = 0
    @State private var finished = false

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
        model.cards[item.lemma]?.mined?.sentence ?? word(for: item).sentence ?? ""
    }

    private func form(for item: RunItem) -> String {
        model.cards[item.lemma]?.mined?.form ?? word(for: item).form ?? item.lemma
    }

    private func mode(for item: RunItem) -> String {
        model.bridge.modeFor(card: item.card, word: word(for: item))
    }

    var body: some View {
        NavigationStack {
            Group {
                if finished || item == nil {
                    VStack(spacing: 12) {
                        Text("Run complete").font(.title.bold())
                        Text("\(run.introduced) new words introduced")
                        Button("Close") { finish() }.buttonStyle(.borderedProminent)
                    }
                } else if let item {
                    content(for: item)
                }
            }
            .padding(20)
            .navigationTitle("Run")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("✕") { finish() }
                }
                ToolbarItem(placement: .principal) {
                    Text(item.map { "\(done + 1) / \(done + run.remaining())" } ?? "")
                        .font(.footnote.bold())
                        .foregroundStyle(.secondary)
                }
            }
        }
        .onAppear { if item == nil && !finished { loadCurrent() } }
    }

    @ViewBuilder
    private func content(for item: RunItem) -> some View {
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
    private func introCard(item: RunItem, word: Word, sentence: String, parts: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("✨ NEW WORD").font(.caption.bold()).foregroundStyle(.orange)
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
        case "dictation": return "🎧 LISTEN, TYPE THE MISSING WORD"
        case "dictee": return "📝 DICTÉE: WRITE THE WHOLE SENTENCE"
        case "produce": return "💬 SAY IT IN FRENCH"
        default: return "✍️ FILL THE GAP"
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
        let mode = mode(for: item)
        let result: String
        let correct: String
        switch mode {
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
        if item == nil {
            finished = true
            return
        }
        if let item, item.phase == "quiz" {
            let mode = mode(for: item)
            if mode == "dictation" || mode == "dictee" {
                speaker.speak(sentence(for: item))
            }
        }
    }

    private func finish() {
        onDone(run.introduced, done)
        dismiss()
    }
}
