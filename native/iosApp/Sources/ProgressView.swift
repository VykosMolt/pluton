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
                    Text("Last 7 days").font(.headline)
                    if report.week.reviews == 0 {
                        Text("No reviews yet this week. A short run starts the record.")
                            .foregroundStyle(.secondary)
                    } else {
                        Text("\(report.week.reviews) reviews · \(Int(report.week.accuracy * 100))% Good/Easy")
                            .font(.system(size: 22, weight: .black))
                            .foregroundStyle(.green)
                        Text("Only the last seven days; the 30-review accuracy is above.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                card {
                    Text("Vocabulary growth").font(.headline)
                    let growth = report.growth
                    if growth.count < 2 {
                        Text("Come back in a few days; Pluton is counting.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        let first = growth[0]
                        let last = growth[growth.count - 1]
                        Text("\(first.known) → \(last.known) known · \(first.day) to \(last.day)")
                            .font(.subheadline)
                        let maxKnown = max(growth.map { Int($0.known) }.max() ?? 1, 1)
                        HStack(alignment: .bottom, spacing: 2) {
                            ForEach(0..<growth.count, id: \.self) { i in
                                let known = Int(growth[i].known)
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(Color.blue.opacity(0.55))
                                    .frame(maxWidth: .infinity)
                                    .frame(height: max(2, CGFloat(known) / CGFloat(maxKnown) * 40))
                            }
                        }
                        .frame(height: 40)
                    }
                }

                card {
                    Text("Error log · 30 days").font(.headline)
                    let total = model.errorTotal()
                    if total == 0 {
                        Text("No corrections yet. Write a paragraph and ask for a correction.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("\(total) correction\(total == 1 ? "" : "s") · most common first")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        ForEach(model.errorKinds(), id: \.kind) { item in
                            HStack {
                                Text(item.kind).font(.subheadline)
                                Spacer()
                                Text("\(item.count)").font(.subheadline.bold())
                            }
                        }
                    }
                }

                card {
                    Text("Can you…? · \(report.stageLevel)").font(.headline)
                    Text("Self-assessment for your current stage. Be honest; a tick is not a test result.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    let ticked = report.canDos.count { canDoDone.contains($0.id) }
                    let complete = !report.canDos.isEmpty && ticked == report.canDos.count
                    Text(complete
                         ? "All \(report.canDos.count) ticked — see Le Chemin when you're ready for the next stage."
                         : "\(ticked) of \(report.canDos.count) ticked.")
                        .font(.caption)
                        .foregroundStyle(complete ? Color.green : Color.secondary)
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
                    Text("Cabinet").font(.headline)
                    if finishedTexts.isEmpty {
                        Text("Nothing collected yet — finish a story, a classic or a Bridge text.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("\(finishedTexts.count) finished")
                            .font(.system(size: 22, weight: .black))
                            .foregroundStyle(.blue)
                        ForEach(0..<min(finishedTexts.count, 8), id: \.self) { i in
                            Text("• \(finishedTexts[i])").font(.subheadline)
                        }
                        if finishedTexts.count > 8 {
                            Text("+ \(finishedTexts.count - 8) more")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text("Pluton curls up beside \(finishedTexts.count) finished text\(finishedTexts.count == 1 ? "" : "s").")
                            .font(.caption)
                            .foregroundStyle(.secondary)
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

    private var finishedTexts: [String] {
        var out: [String] = []
        for (i, chapter) in model.story.enumerated() where model.read.contains("story-\(i)") {
            out.append("Chapitre \(i + 1) · \(chapter.title)")
        }
        for text in model.shelf where model.read.contains(text.title) {
            out.append(text.title)
        }
        for text in model.bridgeShelf where model.read.contains(model.bridgeReadId(text.title)) {
            out.append(text.title)
        }
        for text in model.texts where model.read.contains("text-\(text.id)") {
            out.append(text.title)
        }
        return out
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6, content: content)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }
}
