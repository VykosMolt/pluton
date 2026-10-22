import AVFoundation
import SwiftUI
import UIKit
import UniformTypeIdentifiers
import PlutonCore

/// Play any French audio/video the learner owns, with its subtitles, line controls and dictogloss.
struct ListenView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var player: AVPlayer?
    @SwiftUI.State private var timeObserver: Any?
    @SwiftUI.State private var endToken: NSObjectProtocol?
    @SwiftUI.State private var failToken: NSObjectProtocol?
    @SwiftUI.State private var isVideo = false
    @SwiftUI.State private var mediaName = ""
    @SwiftUI.State private var subsName = ""
    @SwiftUI.State private var cues: [Cue] = []
    @SwiftUI.State private var playing = false
    @SwiftUI.State private var position: Double = 0
    @SwiftUI.State private var duration: Double = 0
    @SwiftUI.State private var rate: Double = 1
    @SwiftUI.State private var autoPause = false
    @SwiftUI.State private var showMediaPicker = false
    @SwiftUI.State private var showSubsPicker = false
    @SwiftUI.State private var selected: String?
    @SwiftUI.State private var selectedSentence = ""
    @SwiftUI.State private var listenStarted: Date?
    @SwiftUI.State private var lastTick: Double = 0
    @SwiftUI.State private var dictCue: Cue?
    @SwiftUI.State private var dictation = ""
    @SwiftUI.State private var dictResult: DicteeResult?
    @SwiftUI.State private var error = ""

    private var cueIndex: Int {
        cues.isEmpty ? -1 : model.cueIndex(cues, at: position)
    }

    private var cue: Cue? {
        cues.indices.contains(cueIndex) ? cues[cueIndex] : nil
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Listen").font(.largeTitle.bold())
                    Text("Play any French audio or video you have with its French subtitles. Tap a word to look it up or add it. Time spent here counts toward your listening hours.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Button(mediaName.isEmpty ? "Choose audio or video" : mediaName) { showMediaPicker = true }
                        .buttonStyle(.bordered)
                    Button(subsName.isEmpty ? "Choose subtitles (.srt / .vtt)" : subsName) { showSubsPicker = true }
                        .buttonStyle(.bordered)
                    if !error.isEmpty {
                        Text(error).font(.footnote).foregroundStyle(.red)
                    }
                    if player != nil {
                        mediaControls
                    }
                    subtitleCard
                    if let cue {
                        dictoglossCard(cue)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .navigationTitle("Listen")
            .navigationBarTitleDisplayMode(.inline)
        }
        .fileImporter(isPresented: $showMediaPicker, allowedContentTypes: [.audio, .movie]) { result in
            importMedia(result)
        }
        .fileImporter(isPresented: $showSubsPicker, allowedContentTypes: [.item]) { result in
            importSubs(result)
        }
        .sheet(item: Binding(get: { selected.map(IdentifiedString.init) }, set: { selected = $0?.value })) { wrapper in
            WordDetailView(lemma: wrapper.value, sentence: selectedSentence)
                .environmentObject(model)
        }
        .onDisappear { stop() }
    }

    @ViewBuilder
    private var mediaControls: some View {
        if isVideo, let player {
            PlayerLayerView(player: player)
                .frame(height: 200)
                .background(.black)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        HStack(spacing: 12) {
            Button(playing ? "Pause" : "Play") { playing ? pause() : play() }
                .buttonStyle(.borderedProminent)
            Text("\(clock(position)) / \(clock(duration))")
                .font(.callout.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        Slider(value: Binding(
            get: { duration > 0 ? min(max(position / duration, 0), 1) : 0 },
            set: { seek(to: $0 * duration) }
        ), in: 0...1)
        if !cues.isEmpty {
            HStack(spacing: 8) {
                Button("Previous") { if let prev = cues[safe: cueIndex - 1] { seek(to: prev.start) } }
                    .disabled(cueIndex <= 0)
                Button("Replay") { if let cue { seek(to: cue.start) } }
                    .disabled(cue == nil)
                Button("Next") { if let next = cues[safe: cueIndex + 1] { seek(to: next.start) } }
                    .disabled(cueIndex + 1 >= cues.count)
            }
            .buttonStyle(.bordered)
            HStack(spacing: 8) {
                ForEach([0.75, 1.0, 1.25], id: \.self) { value in
                    Button(value == 1 ? "1×" : String(format: "%g×", value)) { setRate(value) }
                        .buttonStyle(.bordered)
                        .tint(rate == value ? Color.blue : Color.gray)
                }
                Toggle("Pause after each line", isOn: $autoPause)
                    .font(.footnote)
            }
        }
    }

    private var subtitleCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            if cue != nil {
                Text(annotated(cue?.text ?? ""))
                    .font(.system(size: 22))
                    .lineSpacing(6)
                    .environment(\.openURL, OpenURLAction { url in
                        if url.scheme == "pluton" {
                            selected = url.lastPathComponent.removingPercentEncoding
                            selectedSentence = cue?.text ?? ""
                            return .handled
                        }
                        return .systemAction
                    })
            } else {
                Text(cues.isEmpty ? "Choose subtitles to see and tap each line." : "…")
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func dictoglossCard(_ cue: Cue) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Dictogloss").font(.headline)
            Text("Listen to one line, then type what you heard. Replay it as often as you need; this is listening practice, not a test.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button("Use this line") {
                dictCue = cue
                dictation = ""
                dictResult = nil
                seek(to: cue.start)
                play()
            }
            .buttonStyle(.bordered)
            if let chosen = dictCue {
                TextField("Type the line…", text: $dictation, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(2...5)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                HStack {
                    Button("Check") {
                        dictResult = model.gradeDictee(dictation, chosen.text)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(dictation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Button("Hear the line again") {
                        seek(to: chosen.start)
                        play()
                    }
                    .buttonStyle(.bordered)
                }
                if let result = dictResult {
                    Text(outcome(result))
                        .font(.body.bold())
                        .foregroundStyle(result.result == "right" ? Color.green : result.result == "almost" ? Color.orange : Color.red)
                    Text("Word match: \(model.dicteeScore(result))%")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    MarkedLineView(sentence: chosen.text, marks: result.marks)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func outcome(_ result: DicteeResult) -> String {
        switch result.result {
        case "right": return "Tout est là !"
        case "almost": return result.why == "accent" ? "Almost — only accents slipped." : "One word off."
        default: return "Not quite; listen once more."
        }
    }

    private func annotated(_ text: String) -> AttributedString {
        let tokens = model.tokens(text)
        let ns = text as NSString
        var result = AttributedString()
        var cursor = 0
        for token in tokens where !token.name {
            let start = max(Int(token.start), cursor)
            let end = Int(token.end)
            guard end > start, end <= ns.length else { continue }
            if start > cursor {
                result += AttributedString(ns.substring(with: NSRange(location: cursor, length: start - cursor)))
            }
            var piece = AttributedString(ns.substring(with: NSRange(location: start, length: end - start)))
            let escaped = token.lemma.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? token.lemma
            piece.link = URL(string: "pluton://word/\(escaped)")
            let status = model.statusCode(for: token.lemma)
            if status == 1 {
                piece.backgroundColor = Color.orange.opacity(0.22)
            } else if status == 2 {
                piece.backgroundColor = Color.blue.opacity(0.14)
            } else if status == 3 {
                piece.backgroundColor = Color.red.opacity(0.16)
            }
            result += piece
            cursor = end
        }
        if cursor < ns.length {
            result += AttributedString(ns.substring(from: cursor))
        }
        return result
    }

    private func importMedia(_ result: Result<URL, Error>) {
        do {
            let url = try result.get()
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            stop()
            let ext = url.pathExtension.isEmpty ? "m4a" : url.pathExtension
            let dest = FileManager.default.temporaryDirectory
                .appendingPathComponent("pluton-listen-\(UUID().uuidString).\(ext)")
            try FileManager.default.copyItem(at: url, to: dest)
            isVideo = (try? url.resourceValues(forKeys: [.contentTypeKey]).contentType)?.conforms(to: .movie) ?? false
            let item = AVPlayerItem(url: dest)
            let p = AVPlayer(playerItem: item)
            player = p
            mediaName = url.lastPathComponent
            position = 0
            duration = 0
            lastTick = 0
            error = ""
            observe(p, item: item)
            Task {
                if let d = try? await item.asset.load(.duration), d.seconds.isFinite, d.seconds > 0 {
                    duration = d.seconds
                }
            }
        } catch {
            self.error = "Couldn't open that file."
        }
    }

    private func importSubs(_ result: Result<URL, Error>) {
        do {
            let url = try result.get()
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            let text: String
            if let utf8 = try? String(contentsOf: url, encoding: .utf8) {
                text = utf8
            } else {
                text = try String(contentsOf: url, encoding: .isoLatin1)
            }
            let parsed = model.parseCues(text)
            cues = parsed
            lastTick = position
            dictCue = nil
            dictation = ""
            dictResult = nil
            subsName = "\(url.lastPathComponent) · \(parsed.count) lines"
            error = parsed.isEmpty ? "No timed lines found in that file." : ""
        } catch {
            self.error = "Couldn't read those subtitles."
        }
    }

    private func observe(_ p: AVPlayer, item: AVPlayerItem) {
        timeObserver = p.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.2, preferredTimescale: 600),
            queue: .main
        ) { time in
            guard player === p, time.seconds.isFinite else { return }
            let now = time.seconds
            let previous = lastTick
            position = now
            lastTick = now
            // Pause at the end of any line, including back-to-back cues; ignore seek jumps.
            if playing, autoPause, now > previous, now - previous <= 0.6,
               cues.contains(where: { $0.end > previous && $0.end <= now }) {
                pause()
            }
        }
        endToken = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.didPlayToEndTimeNotification,
            object: item,
            queue: .main
        ) { _ in
            guard player?.currentItem === item else { return }
            playing = false
            countListen()
            if duration > 0 { position = duration }
        }
        failToken = NotificationCenter.default.addObserver(
            forName: AVPlayerItem.failedToPlayToEndTimeNotification,
            object: item,
            queue: .main
        ) { _ in
            guard player?.currentItem === item else { return }
            playing = false
            countListen()
            error = "Playback failed."
        }
    }

    private func removeObserver() {
        if let timeObserver, let player {
            player.removeTimeObserver(timeObserver)
        }
        timeObserver = nil
        if let endToken { NotificationCenter.default.removeObserver(endToken) }
        endToken = nil
        if let failToken { NotificationCenter.default.removeObserver(failToken) }
        failToken = nil
    }

    private func play() {
        guard let p = player else { return }
        if autoPause, let cue, position >= cue.end {
            // Resume on the line after the one that just auto-paused, or replay the last one.
            seek(to: cues[safe: cueIndex + 1]?.start ?? cue.start)
        } else if duration > 0, position >= duration - 0.1 {
            seek(to: 0)
        }
        activateAudio()
        p.rate = Float(rate)
        playing = true
        lastTick = position
        if listenStarted == nil { listenStarted = Date() }
    }

    private func pause() {
        player?.pause()
        playing = false
        countListen()
    }

    private func stop() {
        pause()
        removeObserver()
        player?.replaceCurrentItem(with: nil)
        player = nil
        isVideo = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func seek(to seconds: Double) {
        guard let player else { return }
        let actual = max(0, seconds)
        player.seek(to: CMTime(seconds: actual, preferredTimescale: 600))
        position = actual
        lastTick = actual
    }

    private func setRate(_ value: Double) {
        rate = value
        if playing { player?.rate = Float(value) }
    }

    private func activateAudio() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio)
        try? session.setActive(true)
    }

    private func countListen() {
        guard let started = listenStarted else { return }
        let seconds = Int(Date().timeIntervalSince(started))
        if seconds > 0 { model.addListened(seconds) }
        listenStarted = nil
    }

    private func clock(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let total = Int(seconds)
        return "\(total / 60):" + String(format: "%02d", total % 60)
    }
}

private struct MarkedLineView: View {
    let sentence: String
    let marks: [DicteeMark]

    var body: some View {
        Text(annotated).font(.system(size: 18))
    }

    private var annotated: AttributedString {
        let ns = sentence as NSString
        var result = AttributedString()
        var cursor = 0
        for mark in marks.sorted(by: { $0.start < $1.start }) {
            let start = max(Int(mark.start), cursor)
            let end = Int(mark.end)
            guard end > start, end <= ns.length else { continue }
            if start > cursor {
                result += AttributedString(ns.substring(with: NSRange(location: cursor, length: start - cursor)))
            }
            var piece = AttributedString(ns.substring(with: NSRange(location: start, length: end - start)))
            if mark.mark == "accent" {
                piece.backgroundColor = Color.orange.opacity(0.35)
            } else if mark.mark == "bad" {
                piece.backgroundColor = Color.red.opacity(0.28)
            }
            result += piece
            cursor = end
        }
        if cursor < ns.length {
            result += AttributedString(ns.substring(from: cursor))
        }
        return result
    }
}

/// A bare AVPlayerLayer: Listen owns the controls, so there is no second, out-of-sync control bar.
private struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerView {
        let view = PlayerView()
        view.player = player
        return view
    }

    func updateUIView(_ uiView: PlayerView, context: Context) {
        uiView.player = player
    }

    final class PlayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }

        var player: AVPlayer? {
            get { (layer as? AVPlayerLayer)?.player }
            set { (layer as? AVPlayerLayer)?.player = newValue }
        }
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
