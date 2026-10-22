package io.github.vykosmolt.pluton

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.Locale

/** One French TTS engine for the extra speaking screens. */
internal class FrenchTts(context: Context) {
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context) { status ->
            val e = engine
            if (status == TextToSpeech.SUCCESS && e != null) {
                val result = e.setLanguage(Locale.forLanguageTag("fr-FR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    error = "No French voice is installed on this device."
                } else {
                    ready = true
                }
            } else {
                error = "Text-to-speech failed to start."
            }
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank()) engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pluton-tts")
    }

    fun stop() = engine?.stop()

    fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}

@Composable
internal fun rememberFrenchTts(): FrenchTts {
    val context = LocalContext.current
    val tts = remember { FrenchTts(context) }
    DisposableEffect(Unit) { onDispose { tts.release() } }
    return tts
}

/** A tiny record/playback clip, used by shadowing, 4/3/2 and the sounds lesson. */
internal class ClipRecorder(private val context: Context) {
    var recording by mutableStateOf(false)
        private set
    var path by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var pendingPath: String? = null

    fun start() {
        if (recording) return
        stopPlayback()
        path?.let { File(it).delete() }
        path = null
        pendingPath = null
        error = ""
        val file = File(context.cacheDir, "pluton-clip-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = MediaRecorder()
        recorder = r
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            pendingPath = file.absolutePath
            recording = true
        } catch (e: Exception) {
            r.release()
            recorder = null
            recording = false
            pendingPath = null
            error = "Could not start recording: ${e.message}"
        }
    }

    /** Returns true only for the call that actually stopped a live recording. */
    fun stop(): Boolean {
        if (!recording) return false
        try {
            recorder?.stop()
            path = pendingPath?.takeIf { File(it).length() > 0 }
        } catch (e: Exception) {
            path = null
            error = "Recording failed: ${e.message}"
        }
        recorder?.release()
        recorder = null
        recording = false
        pendingPath = null
        return true
    }

    fun play() {
        if (recording) return
        val file = path ?: return
        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file)
            mp.prepare()
            mp.start()
            player = mp
            playing = true
            mp.setOnCompletionListener {
                it.release()
                if (player === it) {
                    player = null
                    playing = false
                }
            }
        } catch (_: Exception) {
            error = "Could not play the recording."
        }
    }

    fun stopPlayback() {
        player?.release()
        player = null
        playing = false
    }

    fun release() {
        recorder?.release()
        recorder = null
        stopPlayback()
    }
}

@Composable
internal fun rememberClipRecorder(): ClipRecorder {
    val context = LocalContext.current
    val recorder = remember { ClipRecorder(context) }
    DisposableEffect(Unit) { onDispose { recorder.release() } }
    return recorder
}

/** Continuous speech recognition that hands one transcript to the caller. */
internal class VoiceInput(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    /** Elapsed-realtime millis when the recognizer first heard speech, or 0. */
    var speechStartedAt by mutableStateOf(0L)
        private set
    private var recognizer: SpeechRecognizer? = null

    fun start(onResult: (String) -> Unit) {
        if (listening) return
        error = ""
        speechStartedAt = 0
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {
                speechStartedAt = SystemClock.elapsedRealtime()
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(errorCode: Int) {
                listening = false
                error = "Speech recognition failed ($errorCode)."
                r.destroy()
                if (recognizer === r) recognizer = null
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                listening = false
                r.destroy()
                if (recognizer === r) recognizer = null
                if (text.isNotBlank()) onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            listening = false
            error = "Could not start speech recognition."
            r.destroy()
            if (recognizer === r) recognizer = null
        }
    }

    /** Ask the recognizer to finish; onResults still delivers the transcript. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        listening = false
    }
}

@Composable
internal fun rememberVoiceInput(): VoiceInput {
    val context = LocalContext.current
    val input = remember { VoiceInput(context) }
    DisposableEffect(Unit) { onDispose { input.release() } }
    return input
}
