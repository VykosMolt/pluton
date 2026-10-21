import SwiftUI
import PlutonCore

struct SpeakView: View {
    @EnvironmentObject var model: AppModel
    @State private var mode = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Picker("Mode", selection: $mode) {
                    Text("Practice").tag(0)
                    Text("Talk").tag(1)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 20)
                if mode == 0 {
                    PracticeView()
                } else {
                    TalkView()
                }
            }
            .navigationTitle("Speak")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct PracticeView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var speaker = Speaker()
    @StateObject private var practice = SpeechPractice()
    @State private var pool: [String] = []
    @State private var index = 0
    @State private var transcript = ""
    @State private var result: DicteeResult?
    @State private var busy = false

    private var sentence: String { pool.isEmpty ? "Bonjour !" : pool[index % pool.count] }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Read the sentence aloud. Record yourself, compare with Pluton's voice, and let the device check which words it heard.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 10) {
                    Text(sentence).font(.title2.bold())
                    HStack {
                        Button { speaker.speak(sentence) } label: { Label("Hear it", systemImage: "speaker.wave.2") }
                            .buttonStyle(.bordered)
                        if practice.recording {
                            Button { Task { await stop() } } label: { Label("Stop", systemImage: "stop.circle") }
                                .buttonStyle(.borderedProminent)
                                .tint(.red)
                        } else {
                            Button { practice.start() } label: { Label("Record", systemImage: "mic") }
                                .buttonStyle(.borderedProminent)
                                .disabled(busy)
                        }
                        Button { practice.play() } label: { Label("Yours", systemImage: "play") }
                            .buttonStyle(.bordered)
                    }
                    if !practice.error.isEmpty { Text(practice.error).foregroundStyle(.red) }
                    if !transcript.isEmpty { Text("Heard: “\(transcript)”").foregroundStyle(.secondary) }
                    if let result {
                        Text(result.result == "right" ? "Tout est là !" : (result.result == "almost" ? "Almost." : "The recognizer heard something different."))
                            .bold()
                            .foregroundStyle(result.result == "right" ? Color.green : (result.result == "almost" ? Color.orange : Color.red))
                        Text("Recognizer match: \(model.bridge.dicteeScore(result: result))% — word recognition, not a phoneme score")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        Text(sentence)
                            .padding(8)
                            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                Button("Next sentence →") {
                    index += 1
                    transcript = ""
                    result = nil
                }
            }
            .padding(20)
        }
        .onAppear(perform: loadPool)
        .task { await practice.requestPermission() }
    }

    private func loadPool() {
        guard pool.isEmpty else { return }
        let cards = model.cards.values.compactMap { card -> String? in
            card.mined?.sentence ?? model.words[card.lemma]?.sentence
        }
        let fallback = model.words.values.filter { $0.sentence != nil }.prefix(80).compactMap { $0.sentence }
        pool = Array((cards.isEmpty ? fallback : cards).shuffled().prefix(12))
    }

    private func stop() async {
        busy = true
        transcript = await practice.stop()
        if !transcript.isEmpty {
            result = model.bridge.gradeDictee(typed: transcript, sentence: sentence)
            model.addSpoken()
        }
        busy = false
    }

}

private struct TalkView: View {
    @EnvironmentObject var model: AppModel
    @State private var turns: [ChatTurn] = []
    @State private var draft = ""
    @State private var busy = false
    @State private var error = ""
    @StateObject private var speaker = Speaker()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if !AIClient.ready() {
                    Text("Talk with Pluton uses an AI model with your own API key. Add one in More → Settings. The key stays in the Keychain on this device.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Level \(model.levelCode()) · \(model.vocab()) words known or learning")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    ForEach(Array(turns.enumerated()), id: \.offset) { _, turn in
                        VStack(alignment: .leading, spacing: 4) {
                            if turn.role == "assistant" {
                                let correction = model.bridge.splitCorrection(text: turn.content)
                                if let fix = correction.fix {
                                    Text("✏️ \(fix)" + (correction.why.isEmpty ? "" : " — \(correction.why)"))
                                        .font(.footnote.bold())
                                        .foregroundStyle(.orange)
                                }
                                Text(correction.body)
                            } else {
                                Text(turn.content)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(turn.role == "user" ? Color.blue.opacity(0.12) : Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
                    }
                    if busy { ProgressView().padding(.vertical, 4) }
                    if !error.isEmpty { Text(error).foregroundStyle(.red) }
                    HStack {
                        TextField("Écris à Pluton…", text: $draft, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                        Button("Send") { send() }
                            .buttonStyle(.borderedProminent)
                            .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || busy)
                    }
                }
            }
            .padding(20)
        }
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !busy else { return }
        draft = ""
        error = ""
        turns.append(ChatTurn(role: "user", content: text))
        busy = true
        Task {
            do {
                let system = model.bridge.chatSystem(words: Int32(model.vocab()), learning: model.learningWords())
                let reply = try await AIClient.chat(system: system, turns: turns)
                turns.append(ChatTurn(role: "assistant", content: reply))
                speaker.speak(model.bridge.splitCorrection(text: reply).body)
            } catch {
                self.error = error.localizedDescription
                if turns.last?.role == "user" { turns.removeLast() }
                draft = text
            }
            busy = false
        }
    }
}
