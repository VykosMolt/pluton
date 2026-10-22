import AVFoundation
import Speech

/// French TTS, used for examples, read-along and listening exercises.
final class Speaker: ObservableObject {
    @Published var error = ""
    private let synth = AVSpeechSynthesizer()

    func speak(_ text: String) {
        guard !text.isEmpty else { return }
        guard let voice = AVSpeechSynthesisVoice(language: "fr-FR") else {
            error = "No French voice is installed on this device."
            return
        }
        error = ""
        synth.stopSpeaking(at: .immediate)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = voice
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
    private var tapInstalled = false
    private var generation = 0
    private var player: AVAudioPlayer?

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
        guard let recognition, recognition.isAvailable else {
            error = "French speech recognition isn't available on this device."
            return
        }
        reset()
        transcript = ""
        error = ""
        let inputFormat = engine.inputNode.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
            error = "No usable microphone input on this device."
            return
        }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("pluton-speak.caf")
        file = url
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .measurement, options: .duckOthers)
            try session.setActive(true)
            let file = try AVAudioFile(forWriting: url, settings: inputFormat.settings)
            audioFile = file
            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            self.request = request
            engine.inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { buffer, _ in
                try? file.write(from: buffer)
                request.append(buffer)
            }
            tapInstalled = true
            engine.prepare()
            try engine.start()
            recording = true
            generation += 1
            let current = generation
            task = recognition.recognitionTask(with: request) { [weak self] result, error in
                guard let self else { return }
                Task { @MainActor in
                    guard self.generation == current else { return }
                    if let result {
                        self.transcript = result.bestTranscription.formattedString
                    }
                    if let error, result == nil {
                        self.error = "Speech recognition failed: \(error.localizedDescription)"
                        self.recording = false
                        self.reset()
                    }
                }
            }
        } catch {
            self.error = "Recording failed: \(error.localizedDescription)"
            recording = false
            reset()
        }
    }

    private func reset() {
        if tapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        if engine.isRunning {
            engine.stop()
        }
        audioFile = nil
        task?.cancel()
        task = nil
        request?.endAudio()
        request = nil
        player?.stop()
        player = nil
    }

    /// Stops once and returns the transcript, or nil when nothing was recording. Concurrent calls are ignored.
    func finishRecording() async -> String? {
        guard recording else { return nil }
        recording = false
        let current = generation
        if tapInstalled {
            engine.inputNode.removeTap(onBus: 0)
            tapInstalled = false
        }
        if engine.isRunning {
            engine.stop()
        }
        request?.endAudio()
        let myRequest = request
        let myTask = task
        try? await Task.sleep(nanoseconds: 700_000_000)
        guard generation == current else { return "" }
        myTask?.cancel()
        myRequest?.endAudio()
        task = nil
        request = nil
        audioFile = nil
        player?.stop()
        player = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return transcript
    }

    func play() {
        guard !recording, let file else { return }
        do {
            let player = try AVAudioPlayer(contentsOf: file)
            player.play()
            self.player = player
        } catch {
            self.error = "Could not play the recording."
        }
    }
}
