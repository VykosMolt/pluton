import SwiftUI
import PlutonCore

/// French sounds: perception with minimal pairs, phrase listening, and a recording to compare.
struct SoundsView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var speaker = Speaker()
    @StateObject private var practice = SpeechPractice()
    @SwiftUI.State private var lesson: SoundLesson?
    @SwiftUI.State private var target: SoundPair?
    @SwiftUI.State private var spoken = ""
    @SwiftUI.State private var answer: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Sounds").font(.title2.bold())
                if let lesson {
                    detail(lesson)
                } else {
                    Text("Short, focused French sounds. Listen to the difference, choose what you heard, then say it yourself.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(model.bridge.soundLessons(), id: \.id) { item in
                        Button {
                            lesson = item
                            resetQuiz()
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.title).font(.headline).foregroundStyle(.primary)
                                Text(item.tip).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                }
                if !practice.error.isEmpty {
                    Text(practice.error).font(.footnote).foregroundStyle(.red)
                }
                if !speaker.error.isEmpty {
                    Text(speaker.error).font(.footnote).foregroundStyle(.red)
                }
            }
            .padding(20)
        }
        .task { await practice.requestPermission() }
        .task(id: practice.recording) {
            guard practice.recording else { return }
            try? await Task.sleep(nanoseconds: 15_000_000_000)
            if practice.recording { finishClip() }
        }
        .onChange(of: lesson?.id) { _, _ in
            if practice.recording { Task { _ = await practice.finishRecording() } }
        }
        .onDisappear {
            if practice.recording { Task { _ = await practice.finishRecording() } }
        }
    }

    private func detail(_ lesson: SoundLesson) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Button("← Sounds") {
                if practice.recording { finishClip() }
                self.lesson = nil
            }
                .buttonStyle(.plain)
                .foregroundStyle(.blue)
            Text(lesson.title).font(.title2.bold())
            Text(lesson.tip).font(.footnote).foregroundStyle(.secondary)

            if !lesson.pairs.isEmpty {
                quizCard(lesson)
            }
            if !lesson.phrases.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Listen and repeat").font(.headline)
                    ForEach(lesson.phrases, id: \.self) { phrase in
                        HStack {
                            Text(phrase).font(.title3)
                            Spacer()
                            Button("Hear") { speaker.speak(phrase) }
                                .buttonStyle(.bordered)
                                .disabled(practice.recording)
                        }
                    }
                    Text("Say each one aloud, then record yourself and compare.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Your voice").font(.headline)
                Text("Record the pair or phrase, play it back, and compare with the model. Don't chase a native accent; aim to be understood.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack {
                    if practice.recording {
                        Button("Stop") { finishClip() }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                    } else {
                        Button("Record") {
                            speaker.stop()
                            practice.start()
                        }
                            .buttonStyle(.borderedProminent)
                    }
                    Button("Play mine") { practice.play() }
                        .buttonStyle(.bordered)
                        .disabled(practice.recording)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
        }
    }

    private func quizCard(_ lesson: SoundLesson) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("What did you hear?").font(.headline)
            if let target {
                HStack {
                    Button(target.a) { answer = target.a }
                        .buttonStyle(.bordered)
                    Button(target.b) { answer = target.b }
                        .buttonStyle(.bordered)
                }
                if let answer {
                    let right = answer == spoken
                    Text(right ? "Yes — \(spoken)" : "It was “\(spoken)”.")
                        .font(.footnote.bold())
                        .foregroundStyle(right ? Color.green : Color.red)
                    if !target.hint.isEmpty {
                        Text(target.hint).font(.caption).foregroundStyle(.secondary)
                    }
                }
                HStack {
                    Button("Hear it again") { speaker.speak(spoken) }
                        .buttonStyle(.bordered)
                        .disabled(practice.recording)
                    Button("Next pair") { nextPair(lesson) }
                        .buttonStyle(.bordered)
                        .disabled(practice.recording)
                }
            } else {
                Text("Hear one word, then choose the one you heard.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Hear one") { nextPair(lesson) }
                    .buttonStyle(.borderedProminent)
                    .disabled(practice.recording)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private func nextPair(_ lesson: SoundLesson) {
        guard let pair = lesson.pairs.randomElement() else { return }
        target = pair
        spoken = Bool.random() ? pair.a : pair.b
        answer = nil
        speaker.speak(spoken)
    }

    private func resetQuiz() {
        target = nil
        spoken = ""
        answer = nil
    }

    private func finishClip() {
        Task {
            if await practice.finishRecording() != nil {
                model.addSpoken()
                model.markPlan("speak")
            }
        }
    }
}
