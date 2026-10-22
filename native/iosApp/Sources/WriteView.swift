import Foundation
import SwiftUI
import PlutonCore

/// Writing with Pluton: a prompt, an AI correction, a local history, a revision, and an error log.
struct WriteView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var promptIndex = 0
    @SwiftUI.State private var essay = ""
    @SwiftUI.State private var result: WritingResult?
    @SwiftUI.State private var current: WritingRecord?
    @SwiftUI.State private var revising = false
    @SwiftUI.State private var busy = false
    @SwiftUI.State private var error = ""
    @SwiftUI.State private var note = ""
    @SwiftUI.State private var loadedDraft = false

    private var prompts: [WritingPrompt] {
        model.bridge.writingPrompts(words: Int32(model.vocab()))
    }

    private var prompt: WritingPrompt? {
        guard !prompts.isEmpty else { return nil }
        return prompts[promptIndex % prompts.count]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if !AIClient.ready() {
                    Text("Writing correction uses an AI model with your own API key. Add one in More → Settings. The key stays in the Keychain on this device.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Level \(model.levelCode()) · \(model.vocab()) words known or learning")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    if revising, let current {
                        savedCorrection(current)
                    }

                    if result == nil {
                        if !revising, let prompt {
                            promptCard(prompt)
                        } else {
                            Text("Rewrite it with the feedback in view. The first draft stays saved.")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                        TextEditor(text: $essay)
                            .frame(minHeight: 170)
                            .padding(8)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 10))
                            .overlay(alignment: .topLeading) {
                                if essay.isEmpty {
                                    Text(revising ? "Ta révision…" : "Écris en français…")
                                        .foregroundStyle(.secondary)
                                        .padding(.top, 16)
                                        .padding(.leading, 13)
                                        .allowsHitTesting(false)
                                }
                            }
                        Button {
                            check()
                        } label: {
                            Text(revising ? "Check my revision" : "Check my writing")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(essay.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
                        if busy {
                            HStack { ProgressView(); Text("Pluton lit…").foregroundStyle(.secondary) }
                        }
                        if !note.isEmpty { Text(note).font(.footnote).foregroundStyle(.green) }
                        if !error.isEmpty { Text(error).foregroundStyle(.red) }
                    } else if let result {
                        resultCard(result)
                        if !note.isEmpty { Text(note).font(.footnote).foregroundStyle(.green) }
                        HStack {
                            Button(current != nil ? "Revise this one" : "Write another") {
                                if let current { revise(current) } else { another() }
                            }
                            .buttonStyle(.borderedProminent)
                            Button("Start fresh") { another() }
                                .buttonStyle(.bordered)
                            if current != nil {
                                Button("My draft") { backToDraft() }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                }

                if !model.writings.isEmpty {
                    historyCard
                }
                if !model.corrections.isEmpty {
                    errorCard
                }
            }
            .padding(20)
        }
        .onAppear {
            guard !loadedDraft else { return }
            loadedDraft = true
            let revisionId = model.metaValue("revision_id").flatMap { Int64($0) }
            if let revisionId, let record = model.writings.first(where: { $0.id == revisionId }) {
                current = record
                revising = true
                essay = model.metaValue("revision_draft") ?? ""
            } else {
                essay = model.draftText()
                promptIndex = model.draftPrompt()
                model.setMeta("revision_id", "")
                model.setMeta("revision_draft", "")
            }
        }
        .task(id: "\(essay)|\(revising)|\(current?.id ?? 0)|\(result == nil)") {
            try? await Task.sleep(nanoseconds: 400_000_000)
            guard !Task.isCancelled else { return }
            if revising, let current {
                model.setMeta("revision_draft", essay)
                model.setMeta("revision_id", String(current.id))
            } else if current == nil && result == nil {
                model.saveDraft(essay, prompt: promptIndex)
            }
        }
    }

    private func promptCard(_ prompt: WritingPrompt) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("WRITING PROMPT · \(prompt.level)")
                    .font(.caption2.bold())
                    .foregroundStyle(.orange)
                Spacer()
                Button("Another prompt") { promptIndex = (promptIndex + 1) % max(prompts.count, 1) }
                    .font(.caption)
            }
            Text(prompt.fr).font(.title3.bold())
            Text(prompt.en).font(.footnote.italic()).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private func savedCorrection(_ record: WritingRecord) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("YOUR CORRECTION")
                .font(.caption2.bold())
                .foregroundStyle(.orange)
            Text(record.corrected)
            if !record.feedback.isEmpty {
                Text(record.feedback).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private func resultCard(_ shown: WritingResult) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("CORRECTED").font(.caption2.bold()).foregroundStyle(.orange)
            Text(shown.corrected).font(.body)
            if !shown.level.isEmpty {
                Text("Estimated level: \(shown.level)").font(.footnote).foregroundStyle(.secondary)
            }
            if !shown.errors.isEmpty {
                Text("What changed").font(.headline)
                ForEach(Array(shown.errors.enumerated()), id: \.offset) { _, item in
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(item.original).strikethrough().foregroundStyle(.red)
                            Text("→").foregroundStyle(.secondary)
                            Text(item.fix).bold()
                        }
                        if !item.why.isEmpty {
                            Text(item.why).font(.footnote).foregroundStyle(.secondary)
                        }
                        if !item.kind.isEmpty {
                            Text(item.kind).font(.caption2.bold()).foregroundStyle(.orange)
                        }
                    }
                }
            }
            if !shown.feedback.isEmpty {
                Text("Feedback").font(.headline)
                Text(shown.feedback)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private var historyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Your writing").font(.headline)
            Text("Saved on this device; nothing leaves it except the AI request.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            ForEach(Array(model.writings.prefix(6)), id: \.id) { record in
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        Text(Date(timeIntervalSince1970: Double(record.created) / 1000), style: .relative)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        if record.revised != nil {
                            Text("revised").font(.caption2.bold()).foregroundStyle(.green)
                        }
                    }
                    Text(record.prompt).font(.subheadline.bold())
                    Text(record.corrected).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                    HStack {
                        Button("Open") { open(record) }.font(.caption)
                        Button("Revise") { revise(record) }.font(.caption)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private var errorCard: some View {
        let groups = Dictionary(grouping: model.corrections) { $0.kind.isEmpty ? "other" : $0.kind }
            .map { ErrorGroup(kind: $0.key, items: $0.value) }
            .sorted { $0.items.count > $1.items.count }
            .prefix(6)
        return VStack(alignment: .leading, spacing: 8) {
            Text("Your errors").font(.headline)
            Text("\(model.corrections.count) corrections saved. Recurring kinds are the ones to watch.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            ForEach(Array(groups)) { group in
                HStack {
                    Text(group.kind).font(.subheadline.bold())
                    Spacer()
                    Text("\(group.items.count)").bold().foregroundStyle(.orange)
                }
                if let example = group.items.first {
                    Text("\(example.original) → \(example.fix)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private func check() {
        guard let prompt, !busy else { return }
        let text = essay.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        busy = true
        error = ""
        note = ""
        Task {
            do {
                let asked = revising ? (current?.prompt ?? prompt.fr) : prompt.fr
                let system = model.writeSystem(prompt: asked, focus: model.grammarFocusLabel())
                let reply = try await AIClient.chat(system: system, turns: [ChatTurn(role: "user", content: text)])
                guard let parsed = model.bridge.parseWriting(reply: reply) else {
                    throw AIError.message("The model did not return the expected format. Try again.")
                }
                if revising, let editing = current {
                    model.reviseWriting(id: editing.id, revised: text, result: parsed)
                    model.setMeta("revision_draft", "")
                    model.setMeta("revision_id", "")
                    current = model.writings.first { $0.id == editing.id }
                    note = "Revision saved on this device."
                } else {
                    current = model.saveWriting(prompt: prompt.fr, draft: text, result: parsed)
                    model.saveDraft("", prompt: promptIndex)
                    model.setMeta("revision_draft", "")
                    model.setMeta("revision_id", "")
                    note = "Saved on this device."
                }
                result = parsed
                revising = false
                essay = ""
                model.markPlan("write")
            } catch {
                self.error = error.localizedDescription
            }
            busy = false
        }
    }

    private func preserveDraft() {
        if !revising && current == nil && result == nil {
            model.saveDraft(essay, prompt: promptIndex)
        }
    }

    private func revise(_ record: WritingRecord) {
        preserveDraft()
        current = record
        essay = record.revised ?? record.draft
        model.setMeta("revision_draft", essay)
        model.setMeta("revision_id", String(record.id))
        result = nil
        revising = true
        error = ""
        note = ""
    }

    private func open(_ record: WritingRecord) {
        preserveDraft()
        current = record
        essay = record.revised ?? record.draft
        result = WritingResult(corrected: record.corrected, errors: record.errors, feedback: record.feedback, level: record.level)
        revising = false
        error = ""
    }

    private func backToDraft() {
        model.setMeta("revision_draft", "")
        model.setMeta("revision_id", "")
        current = nil
        essay = model.draftText()
        promptIndex = model.draftPrompt()
        result = nil
        revising = false
        error = ""
        note = ""
    }

    private func another() {
        model.saveDraft("", prompt: promptIndex)
        model.setMeta("revision_draft", "")
        model.setMeta("revision_id", "")
        current = nil
        essay = ""
        result = nil
        revising = false
        error = ""
        note = ""
        promptIndex = (promptIndex + 1) % max(prompts.count, 1)
    }
}

private struct ErrorGroup: Identifiable {
    let kind: String
    let items: [CorrectionRecord]
    var id: String { kind }
}
