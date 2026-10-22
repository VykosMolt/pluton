import Foundation
import SwiftUI
import PlutonCore

/// Fluency development: shadowing a model line, then 4/3/2 with the same prompt.
struct FluencyView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var speaker = Speaker()
    @StateObject private var practice = SpeechPractice()
    @SwiftUI.State private var sub = 0
    @SwiftUI.State private var pool: [String] = []
    @SwiftUI.State private var index = 0
    @SwiftUI.State private var prompts: [WritingPrompt] = []
    @SwiftUI.State private var promptIndex = 0
    @SwiftUI.State private var round = 0
    @SwiftUI.State private var seconds = 240
    @SwiftUI.State private var active = false
    @SwiftUI.State private var timerTask: Task<Void, Never>?

    private let durations = [240, 180, 120]

    private var sentence: String { pool.isEmpty ? "Bonjour !" : pool[index % pool.count] }
    private var prompt: String {
        guard !prompts.isEmpty else { return "Parle de ta journée." }
        return prompts[promptIndex % prompts.count].fr
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Fluency")
                    .font(.title2.bold())
                Text("Use language you already know. The goal is smoother speech — not new words or perfect grammar.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    modeButton("Shadow", 0)
                    modeButton("4 / 3 / 2", 1)
                }
                if sub == 0 {
                    shadowCard
                } else {
                    fourThreeTwoCard
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
        .onAppear(perform: load)
        .task { await practice.requestPermission() }
        .onDisappear {
            timerTask?.cancel()
            timerTask = nil
            active = false
            if practice.recording {
                Task { _ = await practice.finishRecording() }
            }
        }
        .onChange(of: sub) { _, _ in
            timerTask?.cancel()
            timerTask = nil
            active = false
            if practice.recording {
                Task { _ = await practice.finishRecording() }
            }
        }
        .task(id: practice.recording) {
            guard practice.recording && sub == 0 else { return }
            try? await Task.sleep(nanoseconds: 20_000_000_000)
            if practice.recording && sub == 0 { finishClip() }
        }
    }

    private func modeButton(_ title: String, _ value: Int) -> some View {
        Button {
            sub = value
        } label: {
            Text(title)
                .fontWeight(sub == value ? .bold : .regular)
                .foregroundStyle(sub == value ? Color.blue : Color.secondary)
        }
        .buttonStyle(.plain)
    }

    private var shadowCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Shadowing").font(.headline)
            Text("Listen to the model, then say it at the same time — don't wait for the end. Record, compare, repeat.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Text(sentence).font(.title2.bold())
            HStack {
                Button("Hear the model") { speaker.speak(sentence) }
                    .buttonStyle(.bordered)
                    .disabled(practice.recording)
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
            Button("Next line") {
                index += 1
                if practice.recording { finishClip() }
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var fourThreeTwoCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("4 / 3 / 2").font(.headline)
            Text("Say the same thing three times, in 4, then 3, then 2 minutes. Don't add new ideas; make the same talk smoother.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Text("Round \(round + 1) · \(durations[round] / 60) minute\(durations[round] == 60 ? "" : "s")")
                .font(.caption.bold())
                .foregroundStyle(.orange)
            Text(prompt).font(.title3.bold())
            Text(active ? clock(seconds) : (round == 2 && !active ? "Round finished" : "Ready"))
                .font(.system(size: 28, weight: .black))
                .foregroundStyle(active ? Color.blue : Color.secondary)
            HStack {
                if active {
                    Button("Stop") { Task { await endRound() } }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                } else {
                    Button(round == 2 ? "Repeat round 3" : (round == 0 ? "Start round 1" : "Start round \(round + 1)")) { startRound() }
                        .buttonStyle(.borderedProminent)
                }
                Button("Play mine") { practice.play() }
                    .buttonStyle(.bordered)
                    .disabled(practice.recording)
            }
            HStack {
                if !active && round < 2 {
                    Button("Next round") {
                        round += 1
                        seconds = durations[round]
                    }
                    .buttonStyle(.bordered)
                }
                if !active && round == 2 {
                    Button("Start over") {
                        round = 0
                        seconds = durations[0]
                    }
                    .buttonStyle(.bordered)
                }
                Button("New prompt") {
                    promptIndex += 1
                    round = 0
                    seconds = durations[0]
                }
                .buttonStyle(.bordered)
                .disabled(active)
            }
            Button("Hear the prompt") { speaker.speak(prompt) }
                .buttonStyle(.bordered)
                .disabled(practice.recording || active)
            Text("No score here: record, listen, and notice one thing to change next round.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private func load() {
        guard pool.isEmpty else { return }
        let cards = model.cards.values.compactMap { card -> String? in
            card.mined?.sentence ?? model.words[card.lemma]?.sentence
        }
        let fallback = model.words.values.filter { $0.sentence != nil }.prefix(100).compactMap { $0.sentence }
        pool = Array((cards.isEmpty ? fallback : cards).shuffled().prefix(12))
        prompts = model.bridge.writingPrompts(words: Int32(model.vocab()))
    }

    private func startRound() {
        speaker.stop()
        practice.start()
        guard practice.recording else { return }
        seconds = durations[round]
        active = true
        timerTask?.cancel()
        timerTask = Task { @MainActor in
            while !Task.isCancelled && active && seconds > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if !Task.isCancelled && active {
                    seconds -= 1
                }
            }
            if !Task.isCancelled && active && seconds <= 0 {
                await endRound()
            }
        }
    }

    private func endRound() async {
        guard active else { return }
        timerTask?.cancel()
        timerTask = nil
        active = false
        if await practice.finishRecording() != nil {
            model.addSpoken()
            model.markPlan("speak")
        }
    }

    private func finishClip() {
        Task {
            if await practice.finishRecording() != nil {
                model.addSpoken()
                model.markPlan("speak")
            }
        }
    }

    private func clock(_ total: Int) -> String {
        String(format: "%d:%02d", total / 60, total % 60)
    }
}
