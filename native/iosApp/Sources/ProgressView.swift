import SwiftUI
import PlutonCore

/// A rough vocabulary estimate, self-assessment and concrete milestones. Not a test.
struct ProgressView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var canDoDone: Set<String> = []

    var body: some View {
        let report = model.progressReport()
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("A waypoint, not a test. The estimate is based on the words you have actually graduated.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                card {
                    Text("Vocabulary estimate").font(.headline)
                    Text(report.cefr).font(.system(size: 30, weight: .black)).foregroundStyle(.blue)
                    Text("\(report.knownWords) known · \(report.learningWords) learning")
                        .foregroundStyle(.secondary)
                    Text("A rough receptive range from word count, not a CEFR exam.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                card {
                    Text("Review accuracy").font(.headline)
                    if report.accuracy >= 0 {
                        Text("\(Int(report.accuracy * 100))%").font(.system(size: 28, weight: .black)).foregroundStyle(.green)
                        Text("Good or Easy answers in your last 30 reviews.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("No reviews yet. Finish a run and this appears.")
                            .foregroundStyle(.secondary)
                    }
                }

                card {
                    Text("Can you…? · \(report.stageLevel)").font(.headline)
                    Text("Self-assessment for your current stage. Be honest; a tick is not a test result.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    ForEach(report.canDos, id: \.id) { item in
                        Button {
                            canDoDone = Set(model.setCanDo(item.id, !canDoDone.contains(item.id)))
                        } label: {
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: canDoDone.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(canDoDone.contains(item.id) ? Color.green : Color.secondary)
                                Text(item.text).foregroundStyle(.primary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }

                card {
                    Text("Milestones").font(.headline)
                    ForEach(report.milestones, id: \.id) { milestone in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(milestone.label)
                                    .foregroundStyle(milestone.done ? Color.secondary : Color.primary)
                                Spacer()
                                Text(milestone.done ? "Done" : "\(min(Int(milestone.have), Int(milestone.need))) / \(Int(milestone.need))")
                                    .font(.caption)
                                    .foregroundStyle(milestone.done ? Color.green : Color.secondary)
                            }
                            if !milestone.done {
                                SwiftUI.ProgressView(value: min(Double(milestone.have) / Double(milestone.need), 1))
                            }
                        }
                    }
                }
            }
            .padding(20)
        }
        .navigationTitle("Progress")
        .onAppear { canDoDone = Set(report.canDoDone) }
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}
