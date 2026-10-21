import SwiftUI
import PlutonCore

struct HomeView: View {
    @EnvironmentObject var model: AppModel
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
    private var known: Int { model.cards.values.filter { model.status(for: $0.lemma) == WordStatus.known() }.count }
    private var learning: Int { model.cards.values.filter { model.status(for: $0.lemma) == WordStatus.learning() }.count }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Pluton").font(.system(size: 36, weight: .black))
                    Text("Native · \(model.words.count) words loaded").foregroundStyle(.secondary)

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
                    model.refresh()
                    running = false
                    self.run = nil
                }
            }
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
                Text("L'Opéra ✓").font(.title3.bold())
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
