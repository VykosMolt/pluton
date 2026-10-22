import Foundation
import SwiftUI
import UniformTypeIdentifiers
import PlutonCore

struct MoreView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            List {
                Section("Progress") {
                    NavigationLink { ProgressView() } label: { Label("Progress", systemImage: "chart.bar") }
                }
                Section("Reference") {
                    NavigationLink { PathView() } label: { Label("Path", systemImage: "map") }
                    NavigationLink { WordsView() } label: { Label("Words · \(model.cards.count)", systemImage: "text.book.closed") }
                    NavigationLink { VerbsView() } label: { Label("Verbs · \(model.conj.count)", systemImage: "character.book.closed") }
                    NavigationLink { GrammarView() } label: { Label("Grammar · \(model.grammar.count)", systemImage: "puzzlepiece") }
                }
                Section("App") {
                    NavigationLink { SettingsView() } label: { Label("Settings", systemImage: "gearshape") }
                }
                Section("About") {
                    Text("Pluton is the black cat from Edgar Allan Poe's “The Black Cat” (1843), named as in Baudelaire's French translation, “Le Chat noir”.")
                        .font(.footnote)
                    Text("Lexique 3.83 (CC BY-SA 4.0) · Tatoeba (CC BY 2.0 FR) · Wiktionary via kaikki.org (CC BY-SA 4.0) · Piper/SIWIS voice (CC BY 4.0) · public-domain classics · FSRS.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("More")
        }
    }
}

private struct PathView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        let path = model.path()
        List {
            Section {
                Text("Pluton's journey from Poe's wall to the Paris Opéra, A1 to C1. Nothing is locked: the goals just show the next useful step. Bonus goals are extra.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(path.stages.enumerated()), id: \.offset) { index, stage in
                DisclosureGroup {
                    Text(stage.stage.fr).italic()
                    Text(stage.stage.say).font(.footnote).foregroundStyle(.secondary)
                    ForEach(Array(stage.goals.enumerated()), id: \.offset) { _, goal in
                        HStack {
                            Text(goal.done ? "Done" : (goal.bonus ? "Bonus" : "•"))
                                .font(.caption.bold())
                                .foregroundStyle(goal.done ? .green : .secondary)
                                .frame(minWidth: 42, alignment: .leading)
                            Text(goal.label)
                            Spacer()
                            if !goal.done {
                                Text("\(goal.have) / \(goal.need)").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .font(.subheadline)
                    }
                } label: {
                    VStack(alignment: .leading) {
                        Text("\(index + 1) / \(path.stages.count) · \(stage.stage.level)\(stage.complete ? " · complete" : "")")
                            .font(.caption.bold())
                            .foregroundStyle(stage.complete ? .green : .orange)
                        Text("\(stage.stage.place) · \(stage.stage.en)").font(.headline)
                        Text("\(Int(stage.pct * 100))%").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("Le Chemin")
    }
}

private struct WordsView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var query = ""

    private var rows: [CardState] {
        model.cards.values
            .filter { model.bridge.isWord(card: $0) }
            .filter { card in
                query.isEmpty ||
                    card.lemma.localizedCaseInsensitiveContains(query) ||
                    (model.words[card.lemma]?.gloss.localizedCaseInsensitiveContains(query) ?? false)
            }
            .sorted { $0.added > $1.added }
    }

    var body: some View {
        List {
            ForEach(rows, id: \.lemma) { card in
            let word = model.words[card.lemma]
            let status = model.statusCode(for: card.lemma)
            HStack {
                VStack(alignment: .leading) {
                    Text(word?.display ?? card.lemma).font(.headline)
                    if let gloss = word?.gloss, !gloss.isEmpty {
                        Text(gloss).font(.caption).foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(statusLabel(status))
                    .font(.caption.bold())
                    .foregroundStyle(status == 0 ? Color.green : (status == 1 ? Color.orange : Color.blue))
            }
            }
        }
        .searchable(text: $query, prompt: "Search French or English…")
        .navigationTitle("Words")
    }

    private func statusLabel(_ status: Int32) -> String {
        if status == 0 { return "known" }
        if status == 1 { return "learning" }
        return "queued"
    }
}

private struct VerbsView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        List(model.conj.keys.sorted(), id: \.self) { verb in
            NavigationLink { VerbView(verb: verb) } label: { Text(verb) }
        }
        .navigationTitle("Verbs")
    }
}

private struct VerbView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var speaker = Speaker()
    let verb: String

    var body: some View {
        List {
            if let table = model.conj[verb] {
                Section {
                    Text("past participle \(table.pp) · with \(table.aux)")
                        .foregroundStyle(.secondary)
                }
                ForEach(model.bridge.tenses(), id: \.id) { tense in
                    let forms = model.bridge.conjForms(table: table, tense: tense.id)
                    if !forms.isEmpty {
                        Section {
                            Text(tense.en).font(.caption).foregroundStyle(.secondary)
                            ForEach(Array(forms.enumerated()), id: \.offset) { index, form in
                                if !form.isEmpty {
                                    Button {
                                        speaker.speak("\(model.bridge.subject(index: Int32(index), tense: tense.id)) \(form)")
                                    } label: {
                                        HStack {
                                            Text(model.bridge.subject(index: Int32(index), tense: tense.id))
                                                .foregroundStyle(.secondary)
                                            Text(form).bold().foregroundStyle(.primary)
                                        }
                                    }
                                }
                            }
                            Text(tense.how).font(.caption).foregroundStyle(.secondary)
                        } header: {
                            Text(tense.name)
                        }
                    }
                }
            }
        }
        .navigationTitle(verb)
    }
}

private struct GrammarView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        List {
            ForEach(Array(model.grammar.enumerated()), id: \.offset) { _, topic in
                DisclosureGroup {
                    Text(topic.how)
                    ForEach(Array(topic.items.enumerated()), id: \.offset) { _, item in
                        let parts = item.q.components(separatedBy: "___")
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(parts.first ?? "") ____ \(parts.count > 1 ? parts[1] : "")")
                            Text(item.a.joined(separator: " / ")).font(.footnote.bold()).foregroundStyle(.green)
                        }
                    }
                } label: {
                    VStack(alignment: .leading) {
                        Text(topic.level).font(.caption.bold()).foregroundStyle(.orange)
                        Text(topic.name).font(.headline)
                        Text(topic.en).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("Grammar")
    }
}

private struct SettingsView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var config = AIClient.config()
    @SwiftUI.State private var key = AIClient.key
    @SwiftUI.State private var note = ""
    @SwiftUI.State private var backup = ""
    @SwiftUI.State private var exportDocument: BackupDocument?
    @SwiftUI.State private var exporting = false
    @SwiftUI.State private var importing = false
    @SwiftUI.State private var confirmImport = false
    @SwiftUI.State private var backupNote = ""


    var body: some View {
        Form {
            Section("Pace") {
                Picker("New words per run", selection: Binding(
                    get: { Int(model.pace.perRun) },
                    set: { model.savePace(perRun: Int32($0), perDay: model.pace.perDay) }
                )) {
                    ForEach([3, 5, 8, 10], id: \.self) { Text("\($0)").tag($0) }
                }
                Picker("New words per day", selection: Binding(
                    get: { Int(model.pace.perDay) },
                    set: { model.savePace(perRun: model.pace.perRun, perDay: Int32($0)) }
                )) {
                    ForEach([10, 15, 20, 30, 40], id: \.self) { Text("\($0)").tag($0) }
                }
                Text("Every new word comes back for reviews, so a higher pace means more reviews in the weeks after.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("Backup") {
                Button("Export backup") {
                    backup = model.exportBackup()
                    exportDocument = BackupDocument(text: backup)
                    exporting = true
                }
                Button("Import backup") { confirmImport = true }
                if !backupNote.isEmpty {
                    Text(backupNote).font(.footnote).foregroundStyle(.secondary)
                }
                Text("Cards, reviews, texts, stats, writing history and the error log. The AI key is never included.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("AI tutor") {
                Picker("Provider", selection: $config.provider) {
                    ForEach(AIClient.providers) { provider in
                        Text(provider.name).tag(provider.id)
                    }
                }
                SecureField("API key", text: $key)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Model", text: $config.model)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Server URL (optional)", text: $config.base)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Save") {
                    AIClient.key = key
                    AIClient.save(config: config)
                    note = "Saved. The key is in the Keychain."
                }
                if !note.isEmpty { Text(note).font(.footnote).foregroundStyle(.secondary) }
                Text("Talking practice uses your own API key. It is stored in the Keychain, sent only to the provider you pick, and never included in backups.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
        .fileExporter(
            isPresented: $exporting,
            document: exportDocument,
            contentType: .json,
            defaultFilename: "pluton-backup"
        ) { result in
            switch result {
            case .success: backupNote = "Backup saved."
            case .failure(let error): backupNote = "Backup failed: \(error.localizedDescription)"
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                do {
                    let data = try String(contentsOf: url, encoding: .utf8)
                    if let error = model.importBackup(data) {
                        backupNote = "Import failed: \(error)"
                    } else {
                        backupNote = "Backup restored."
                    }
                } catch {
                    backupNote = "Could not read that file: \(error.localizedDescription)"
                }
            case .failure(let error):
                backupNote = "Import failed: \(error.localizedDescription)"
            }
        }
        .alert("Replace all progress?", isPresented: $confirmImport) {
            Button("Choose backup", role: .destructive) { importing = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Cards, reviews, texts, stats and writing history are replaced by the backup's. This cannot be undone.")
        }
    }
}

private struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var text: String

    init(text: String) { self.text = text }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}
