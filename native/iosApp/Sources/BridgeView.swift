import SwiftUI
import PlutonCore

/// An English text seen through the French words it inherited after 1066. In a weaving mode the words you
/// already know are shown in French, so the English slowly turns back into French as your deck grows.
struct BridgeView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let text: ShelfText

    @SwiftUI.State private var mode = 0
    @SwiftUI.State private var pick: BridgePick?

    private var tokens: [EnglishToken] { model.bridgeTokens(text.body) }
    private var frenchTokens: [EnglishToken] { tokens.filter { $0.fr != nil && !$0.faux } }
    private var frenchWords: Set<String> { Set(frenchTokens.compactMap { $0.fr }) }
    private var ready: Int { frenchWords.filter { model.statusCode(for: $0) != 3 }.count }
    private var faux: Int { tokens.filter { $0.faux }.count }

    private func woven(_ token: EnglishToken) -> Bool {
        guard token.fr != nil, !token.faux else { return false }
        return mode == 2 || (mode == 1 && model.statusCode(for: token.fr!) != 3)
    }

    private func piece(_ token: EnglishToken) -> String {
        guard let fr = token.fr, woven(token) else { return token.text }
        if let first = token.text.first, first.isUppercase {
            return String(fr.prefix(1)).uppercased() + String(fr.dropFirst())
        }
        return fr
    }

    private func color(_ token: EnglishToken) -> Color {
        if token.faux { return .red }
        switch model.statusCode(for: token.fr ?? "") {
        case 0: return .green
        case 1: return .orange
        default: return .blue
        }
    }

    private var annotated: AttributedString {
        let ns = text.body as NSString
        var out = AttributedString()
        var cursor = 0
        for token in tokens {
            let start = Int(token.start)
            let end = Int(token.end)
            guard end > start, end <= ns.length else { continue }
            if start > cursor {
                out += AttributedString(ns.substring(with: NSRange(location: cursor, length: start - cursor)))
            }
            var span = AttributedString(piece(token))
            if token.fr != nil {
                span.link = URL(string: "pluton://bridge/\(start)")
                span.underlineStyle = Text.LineStyle.single
                span.foregroundColor = color(token)
                if woven(token) {
                    span.font = .system(size: 19, weight: .bold).italic()
                }
            }
            out += span
            cursor = end
        }
        if cursor < ns.length {
            out += AttributedString(ns.substring(from: cursor))
        }
        return out
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(text.title).font(.title2.bold())
                    if !text.author.isEmpty {
                        Text(text.author).foregroundStyle(.secondary)
                    }
                    Text(
                        "After 1066, Norman French poured into English. \(frenchTokens.count) words here "
                        + "(\(tokens.isEmpty ? 0 : frenchTokens.count * 100 / tokens.count)%) came that way: "
                        + "\(frenchWords.count) different French words, \(ready) already in your deck."
                        + (faux > 0 ? " \(faux) false friends are flagged in red." : "")
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    Picker("Display", selection: $mode) {
                        Text("English").tag(0)
                        Text("What I know").tag(1)
                        Text("Everything").tag(2)
                    }
                    .pickerStyle(.segmented)
                    Text("Tap any underlined word to see the French it came from.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(annotated)
                        .font(.system(size: 19))
                        .lineSpacing(6)
                        .environment(\.openURL, OpenURLAction { url in
                            guard url.scheme == "pluton", let raw = Int(url.lastPathComponent),
                                  let token = tokens.first(where: { Int($0.start) == raw }) else {
                                return .systemAction
                            }
                            pick = BridgePick(token: token, piece: piece(token))
                            return .handled
                        })
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .navigationTitle("Norman Bridge")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .sheet(item: $pick) { pick in
            BridgeWordSheet(token: pick.token, piece: pick.piece)
                .environmentObject(model)
        }
    }
}

private struct BridgePick: Identifiable {
    let token: EnglishToken
    let piece: String
    var id: Int32 { token.start }
}

private struct BridgeWordSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss
    let token: EnglishToken
    let piece: String

    var body: some View {
        let fr = token.fr ?? ""
        let status = model.statusCode(for: fr)
        let note = model.bridgeNote(token)
        VStack(alignment: .leading, spacing: 10) {
            Text(fr).font(.system(size: 28, weight: .black)).foregroundStyle(.blue)
            Text("English: \(token.text)\(token.faux ? " · false friend" : "")")
                .foregroundStyle(.secondary)
            Text(note)
            Text("In this text: \(piece)").italic().foregroundStyle(.secondary)
            if status == 3 {
                HStack {
                    Button("+ Add to deck") {
                        let form = Token(start: 0, end: Int32(fr.count), text: fr, lemma: fr, name: false)
                        model.addWord(token: form, word: model.words[fr], sentence: note)
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                    Button("I know it") {
                        model.markKnown(fr)
                        dismiss()
                    }
                    .buttonStyle(.bordered)
                }
            } else if model.cards[fr]?.known == true {
                Button("Undo marked known") {
                    model.unmark(fr)
                    dismiss()
                }
            } else if status == 1 {
                Text("In your deck").foregroundStyle(.green)
            } else {
                Text("Queued for your next runs").foregroundStyle(.blue)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .presentationDetents([.medium])
    }
}
