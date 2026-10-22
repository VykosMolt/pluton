import SwiftUI
import PlutonCore

/// Retrieval practice for the learner's own writing corrections: see the mistake, type the fix, read why.
struct ErrorReviewView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var drills: [ErrorDrill]
    @SwiftUI.State private var index = 0
    @SwiftUI.State private var typed = ""
    @SwiftUI.State private var result: GradeResult?
    @SwiftUI.State private var right = 0

    init(drills: [ErrorDrill]) {
        // Seed once: grading refreshes `corrections`, and a rebuilt destination must not swap the drill.
        _drills = State(initialValue: drills)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Type the corrected version of your own sentence. The fix and the reason appear after you check.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if drills.isEmpty {
                    card {
                        Text(model.corrections.isEmpty ? "Nothing to review yet" : "No drills due right now")
                            .font(.headline)
                        Text(model.corrections.isEmpty
                             ? "Write a paragraph in Speak → Write and get it corrected; the fixes will land here."
                             : "New corrections appear here; this list only shows what is due.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    let current = min(max(index, 0), drills.count - 1)
                    let drill = drills[current]
                    Text("\(current + 1) / \(drills.count) · \(right) right")
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
                    card {
                        Text("YOU WROTE").font(.caption2.bold()).foregroundStyle(.secondary)
                        Text(drill.original).font(.title3)
                        if !drill.kind.isEmpty {
                            Text(drill.kind).font(.caption).foregroundStyle(.secondary)
                        }
                        TextField("The fix", text: $typed, axis: .vertical)
                            .textFieldStyle(.roundedBorder)
                            .lineLimit(2...5)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                            .disabled(result != nil)
                        HStack {
                            Button("Check") {
                                let graded = model.gradeError(typed, drill.fix)
                                result = graded
                                if graded.result == "right" { right += 1 }
                                model.reviewError(id: drill.id, result: graded.result)
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(typed.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || result != nil)
                            if result != nil {
                                Button(current + 1 >= drills.count ? "Start over" : "Next") {
                                    if current + 1 >= drills.count { right = 0 }
                                    index = (current + 1) % drills.count
                                    typed = ""
                                    result = nil
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                    if let result {
                        card {
                            Text(outcome(result))
                                .font(.body.bold())
                                .foregroundStyle(result.result == "right" ? Color.green
                                                 : result.result == "almost" ? Color.orange : Color.red)
                            Text("The fix: \(drill.fix)").font(.headline)
                            if !drill.why.isEmpty {
                                Text(drill.why).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
        }
        .navigationTitle("Error review")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func outcome(_ result: GradeResult) -> String {
        switch result.result {
        case "right": return "Exactly."
        case "almost":
            switch result.why {
            case "accent": return "Almost — only the accents differ."
            case "case": return "Almost — check the capitals."
            default: return "Almost — one small typo."
            }
        default: return "Not quite. Compare with the fix."
        }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}
