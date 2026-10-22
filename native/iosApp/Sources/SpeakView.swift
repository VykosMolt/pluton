import SwiftUI
import PlutonCore

struct SpeakView: View {
    @EnvironmentObject var model: AppModel
    @Binding var mode: Int

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 14) {
                        ForEach(SpeakMode.all) { item in
                            Button(item.label) { mode = item.id }
                                .fontWeight(mode == item.id ? .bold : .regular)
                                .foregroundStyle(mode == item.id ? Color.blue : Color.secondary)
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                if mode == 0 {
                    PracticeView()
                } else if mode == 1 {
                    TalkView()
                } else if mode == 2 {
                    WriteView()
                } else if mode == 3 {
                    FluencyView()
                } else {
                    SoundsView()
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
    @SwiftUI.State private var pool: [String] = []
    @SwiftUI.State private var index = 0
    @SwiftUI.State private var transcript = ""
    @SwiftUI.State private var result: DicteeResult?
    @SwiftUI.State private var busy = false

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
                            .disabled(practice.recording || busy)
                        if practice.recording {
                            Button { Task { await stop() } } label: { Label("Stop", systemImage: "stop.circle") }
                                .buttonStyle(.borderedProminent)
                                .tint(.red)
                        } else {
                            Button {
                                speaker.stop()
                                practice.start()
                            } label: { Label("Record", systemImage: "mic") }
                                .buttonStyle(.borderedProminent)
                                .disabled(busy)
                        }
                        Button { practice.play() } label: { Label("Yours", systemImage: "play") }
                            .buttonStyle(.bordered)
                            .disabled(practice.recording)
                    }
                    if !practice.error.isEmpty { Text(practice.error).foregroundStyle(.red) }
                    if !speaker.error.isEmpty { Text(speaker.error).font(.footnote).foregroundStyle(.red) }
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
        .onDisappear {
            if practice.recording { Task { _ = await practice.finishRecording() } }
        }
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
        guard let text = await practice.finishRecording() else {
            busy = false
            return
        }
        transcript = text
        if !text.isEmpty {
            result = model.bridge.gradeDictee(typed: text, sentence: sentence)
        }
        model.addSpoken()
        model.markPlan("speak")
        busy = false
    }

}

private struct TalkView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var turns: [ChatTurn] = []
    @SwiftUI.State private var draft = ""
    @SwiftUI.State private var busy = false
    @SwiftUI.State private var error = ""
    @StateObject private var speaker = Speaker()
    @StateObject private var practice = SpeechPractice()

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
                                    Text("Correction · \(fix)" + (correction.why.isEmpty ? "" : " — \(correction.why)"))
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
                    if busy { SwiftUI.ProgressView().padding(.vertical, 4) }
                    if !error.isEmpty { Text(error).foregroundStyle(.red) }
                    HStack {
                        TextField("Écris à Pluton…", text: $draft, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                        Button("Send") { send() }
                            .buttonStyle(.borderedProminent)
                            .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || busy)
                        Button {
                            if practice.recording {
                                stopAndSend()
                            } else {
                                Task {
                                    speaker.stop()
                                    await practice.requestPermission()
                                    if practice.authorized { practice.start() }
                                }
                            }
                        } label: {
                            Image(systemName: practice.recording ? "stop.circle.fill" : "mic.fill")
                        }
                        .buttonStyle(.bordered)
                        .tint(practice.recording ? .red : .blue)
                        .disabled(busy)
                    }
                    if practice.recording {
                        Text("Listening… tap the stop button when you finish.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text("Voice input uses your device's speech service; only the transcript goes to the AI.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    if !practice.error.isEmpty {
                        Text(practice.error).font(.footnote).foregroundStyle(.red)
                    }
                    if !speaker.error.isEmpty {
                        Text(speaker.error).font(.footnote).foregroundStyle(.red)
                    }
                }
            }
            .padding(20)
        }
        .onDisappear {
            if practice.recording { Task { _ = await practice.finishRecording() } }
        }
    }

    private func send() {
        sendText(draft)
    }

    private func sendText(_ raw: String) {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !busy else { return }
        draft = ""
        error = ""
        turns.append(ChatTurn(role: "user", content: text))
        busy = true
        Task {
            do {
                let system = model.chatSystem(learning: model.learningWords(), focus: model.grammarFocusLabel())
                let reply = try await AIClient.chat(system: system, turns: turns)
                turns.append(ChatTurn(role: "assistant", content: reply))
                model.addTalked()
                model.markPlan("speak")
                speaker.speak(model.bridge.splitCorrection(text: reply).body)
            } catch {
                self.error = error.localizedDescription
                if turns.last?.role == "user" { turns.removeLast() }
                draft = text
            }
            busy = false
        }
    }

    private func stopAndSend() {
        let seconds = practice.speechSeconds
        Task {
            if let text = await practice.finishRecording(), !text.isEmpty {
                let wpm = model.bridge.speechRate(text: text, seconds: seconds)
                if wpm > 0 { model.addSpeechRate(wpm) }
                sendText(text)
            }
        }
    }
}

private struct SpeakMode: Identifiable {
    let id: Int
    let label: String
    static let all = [
        SpeakMode(id: 0, label: "Practice"),
        SpeakMode(id: 1, label: "Talk"),
        SpeakMode(id: 2, label: "Write"),
        SpeakMode(id: 3, label: "Fluency"),
        SpeakMode(id: 4, label: "Sounds"),
    ]
}
