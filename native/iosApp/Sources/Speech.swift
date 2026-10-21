import AVFoundation
import Speech

/// French TTS, used for examples, read-along and listening exercises.
final class Speaker: ObservableObject {
    private let synth = AVSpeechSynthesizer()

    func speak(_ text: String) {
        guard !text.isEmpty else { return }
        synth.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "fr-FR")
        utterance.rate = 0.48
        synth.speak(utterance)
    }

    func stop() {
        synth.stopSpeaking(at: .immediate)
    }
}

/// Record + transcribe one sentence, then let the shared gradeDictee compare it.
@MainActor
final class SpeechPractice: ObservableObject {
    @Published var transcript = ""
    @Published var recording = false
    @Published var error = ""
    @Published var authorized = false

    private var recognition: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let engine = AVAudioEngine()
    private var file: URL?
    private var audioFile: AVAudioFile?

    init() {
        recognition = SFSpeechRecognizer(locale: Locale(identifier: "fr-FR"))
    }

    func requestPermission() async {
        let speech = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in continuation.resume(returning: status == .authorized) }
        }
        let mic = await AVAudioApplication.requestRecordPermission()
        authorized = speech && mic
        if !authorized { error = "Microphone and speech recognition permissions are needed." }
    }

    func start() {
        guard authorized else { return }
        reset()
        transcript = ""
        error = ""
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("pluton-speak.caf")
        file = url
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .measurement, options: .duckOthers)
            try session.setActive(true)
            let inputFormat = engine.inputNode.outputFormat(forBus: 0)
            let file = try AVAudioFile(forWriting: url, settings: inputFormat.settings)
            audioFile = file
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            self.request = request
            engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { buffer, _ in
                try? file.write(from: buffer)
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
            recording = true
            if let recognition {
                task = recognition.recognitionTask(with: request) { [weak self] result, _ in
                    guard let self, let result else { return }
                    Task { @MainActor in self.transcript = result.bestTranscription.formattedString }
                }
            }
        } catch {
            self.error = "Recording failed: \(error.localizedDescription)"
            recording = false
        }
    }

    private func reset() {
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
        audioFile = nil
        task?.cancel()
        task = nil
        request?.endAudio()
        request = nil
    }

    /// Stops recording and returns the recognized transcript once the recognizer settles.
    func stop() async -> String {
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
        recording = false
        request?.endAudio()
        try? await Task.sleep(nanoseconds: 700_000_000)
        task?.cancel()
        task = nil
        request = nil
        audioFile = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return transcript
    }

    func play() {
        guard let file else { return }
        let player = try? AVAudioPlayer(contentsOf: file)
        player?.play()
        self.player = player
    }

    private var player: AVAudioPlayer?
}
