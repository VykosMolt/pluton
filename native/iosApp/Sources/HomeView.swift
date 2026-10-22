import SwiftUI
import PlutonCore

struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @Binding var tab: Int
    @Binding var speakMode: Int
    @SwiftUI.State private var running = false
    @SwiftUI.State private var run: IosRun?
    @SwiftUI.State private var summary: String?

    private var preview: [RunItem] {
        model.bridge.previewRun(
            store: model.store,
            words: model.words,
            now: AppModel.now(),
            newToday: model.newToday,
            pace: model.pace
        )
    }

    private var due: Int { preview.filter { $0.phase == "quiz" }.count }
    private var fresh: Int { preview.filter { $0.phase == "intro" }.count }
    private var known: Int { model.cards.values.filter { model.statusCode(for: $0.lemma) == 0 }.count }
    private var learning: Int { model.cards.values.filter { model.statusCode(for: $0.lemma) == 1 }.count }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Pluton").font(.system(size: 36, weight: .black))
                    Text("\(model.words.count) words, all on this device").foregroundStyle(.secondary)

                    todayCard

                    pathCard

                    if let summary {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Run complete").font(.headline)
                            Text(summary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Your deck").font(.headline)
                        Text("\(known) known · \(learning) learning · \(model.cards.count) cards saved")
                        Text("Progress is saved on this device.").font(.footnote).foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))

                    Button {
                        run = model.newRun()
                        running = true
                    } label: {
                        Text(preview.isEmpty ? "Nothing due today" : "Start a run · \(due) reviews · \(fresh) new")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(preview.isEmpty)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .navigationBarHidden(true)
        }
        .fullScreenCover(isPresented: $running) {
            if let run {
                RunView(run: run) { introduced, done in
                    summary = "\(introduced) new words introduced · \(done) answers"
                    if done > 0 { model.markPlan("review") }
                    model.refresh()
                    running = false
                    self.run = nil
                }
            }
        }
    }

    private var todayCard: some View {
        let suggested = model.suggestedWords()
        let total = 4
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Today").font(.title3.bold())
                Spacer()
                Text("\(model.planDone.count) / \(total)").foregroundStyle(.secondary).bold()
            }
            Text("About \(model.dailyMinutes) minutes · four small steps")
                .font(.caption)
                .foregroundStyle(.secondary)
            ProgressView(value: Double(model.planDone.count) / Double(total))
            HStack(spacing: 6) {
                Text("This week").font(.caption2).foregroundStyle(.secondary)
                ForEach(lastSevenDays, id: \.self) { day in
                    Circle()
                        .fill(model.practiceDays.contains(day) ? Color.green : Color.secondary.opacity(0.2))
                        .frame(width: 9, height: 9)
                }
                Text("\(lastSevenDays.filter { model.practiceDays.contains($0) }.count) / 7")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            step("review", "Warm up", reviewDetail) {
                run = model.newRun()
                running = true
            }
            step("read", "Read something", "A story, a classic or a Bridge text") { tab = 1 }
            step("speak", "Speak aloud", "Read a sentence aloud or talk with Pluton") {
                speakMode = 0
                tab = 2
            }
            step("write", "Write a little", "One paragraph, then one correction") {
                speakMode = 2
                tab = 2
            }
            if model.accuracy >= 0 && suggested != Int(model.pace.perRun) {
                Text("Recent reviews: \(Int(model.accuracy * 100))%. Try \(suggested) new words per run.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Use \(suggested)") { model.useSuggestedWords() }
                    .buttonStyle(.bordered)
            }
            if model.planDone.count == total {
                Text("Pluton purrs. Today is done — anything else is a bonus.")
                    .font(.footnote.bold())
                    .foregroundStyle(.green)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }

    private var lastSevenDays: [String] {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return (0..<7).reversed().map { offset in
            let date = Calendar.current.date(byAdding: .day, value: -offset, to: Date()) ?? Date()
            return formatter.string(from: date)
        }
    }

    private var reviewDetail: String {
        if due + fresh == 0 { return "Nothing due; a bonus review is fine" }
        return "\(due) due · \(fresh) new"
    }

    private func step(_ id: String, _ title: String, _ detail: String, action: @escaping () -> Void) -> some View {
        HStack(spacing: 8) {
            Button {
                model.togglePlan(id)
            } label: {
                Image(systemName: model.planDone.contains(id) ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(model.planDone.contains(id) ? Color.green : Color.secondary)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.bold())
                Text(detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(model.planDone.contains(id) ? "Again" : "Open", action: action)
                .buttonStyle(.bordered)
        }
    }

    private var pathCard: some View {
        let path = model.path()
        let stage = Int(path.current) < path.stages.count ? path.stages[Int(path.current)] : nil
        return VStack(alignment: .leading, spacing: 6) {
            if let stage {
                Text("ÉTAPE \(Int(path.current) + 1) / \(model.bridge.stages().count) · \(stage.stage.level)")
                    .font(.caption.bold())
                    .foregroundStyle(.orange)
                Text("\(stage.stage.place) · \(stage.stage.en)").font(.title3.bold())
                ProgressView(value: stage.pct)
                if let next = model.bridge.focus(stage: stage) {
                    Text("Next: \(next.label)").font(.footnote).foregroundStyle(.secondary)
                }
            } else {
                Text("LE CHEMIN · DONE").font(.caption.bold()).foregroundStyle(.orange)
                Text("L'Opéra · complete").font(.title3.bold())
                Text("Keep reading, listening and talking: C1 is a habit, not a finish line.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}
