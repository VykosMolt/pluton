package io.github.vykosmolt.pluton

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.DicteeMark
import io.github.vykosmolt.pluton.core.DicteeResult
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.dicteeScore
import io.github.vykosmolt.pluton.core.gradeDictee
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private data class Practice(val sentence: String, val translation: String)

/** Read-aloud practice: TTS, record/playback, and optional Android speech recognition with word-level marks. */
@Composable
internal fun SpeakScreen(
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    store: Store,
    ai: AiConfig?,
    onSettings: () -> Unit,
    onTalked: () -> Unit,
    onWritten: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(0) }
    if (mode == 1) {
        TalkScreen(
            ai = ai,
            words = words,
            cards = cards,
            store = store,
            onSettings = onSettings,
            onTalked = onTalked,
            onPractice = { mode = 0 },
            onWrite = { mode = 2 },
            modifier = modifier,
        )
        return
    }
    if (mode == 2) {
        WritingScreen(
            ai = ai,
            words = words,
            cards = cards,
            store = store,
            onSettings = onSettings,
            onWritten = onWritten,
            onPractice = { mode = 0 },
            onTalk = { mode = 1 },
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    val pool = remember {
        val learned = cards.values.mapNotNull { c ->
            val w = words[c.lemma]
            val sentence = c.mined?.sentence ?: w?.sentence ?: return@mapNotNull null
            val translation = c.mined?.translation?.takeIf { it.isNotBlank() } ?: w?.translation ?: ""
            Practice(sentence, translation)
        }
        val fallback = words.values.asSequence().filter { it.sentence != null }.take(80)
            .map { Practice(it.sentence!!, it.translation ?: "") }.toList()
        (learned.ifEmpty { fallback }).shuffled().take(12).ifEmpty { listOf(Practice("Bonjour !", "")) }
    }
    var i by remember { mutableStateOf(0) }
    val practice = pool[i % pool.size]
    var showEn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var clipPath by remember { mutableStateOf<String?>(null) }
    var pendingPath by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var dictee by remember { mutableStateOf<DicteeResult?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    var permitted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (!granted) error = "Microphone permission is needed for speaking practice."
    }
    fun ensureMic(action: () -> Unit) {
        if (permitted) action() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsError by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            val e = engine
            if (status == TextToSpeech.SUCCESS && e != null) {
                val result = e.setLanguage(Locale.forLanguageTag("fr-FR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsError = "No French voice is installed on this device."
                } else {
                    ttsReady = true
                }
            } else if (status != TextToSpeech.SUCCESS) {
                ttsError = "Text-to-speech failed to start."
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    fun hear() {
        if (!ttsReady) return
        tts?.speak(practice.sentence, TextToSpeech.QUEUE_FLUSH, null, "pluton-speak")
    }

    fun stopPlayer() {
        player?.release()
        player = null
    }

    fun startRecording() {
        stopPlayer()
        clipPath?.let { File(it).delete() }
        clipPath = null
        pendingPath = null
        error = ""
        val file = File(context.cacheDir, "pluton-speak-${System.currentTimeMillis()}.m4a")
        try {
            @Suppress("DEPRECATION")
            val r = MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            pendingPath = file.absolutePath
            recording = true
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            recording = false
            error = "Could not start recording: ${e.message}"
        }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
            clipPath = pendingPath?.takeIf { File(it).length() > 0 }
        } catch (e: Exception) {
            clipPath = null
            error = "Recording failed: ${e.message}"
        }
        recorder?.release()
        recorder = null
        recording = false
        if (clipPath != null) store.addSpoken(1)
    }

    fun toggleRecording() {
        if (recording) stopRecording() else ensureMic { startRecording() }
    }

    fun playClip() {
        val path = clipPath ?: return
        stopPlayer()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            mp.start()
            player = mp
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
        } catch (_: Exception) {
            error = "Could not play the recording."
        }
    }

    fun stopRecognition() {
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    fun checkSpeech() {
        if (listening || recording) return
        ensureMic {
            stopPlayer()
            tts?.stop()
            transcript = ""
            dictee = null
            error = ""
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(errorCode: Int) {
                    listening = false
                    error = "Speech recognition failed ($errorCode)."
                    r.destroy()
                    recognizer = null
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    transcript = text
                    dictee = gradeDictee(text, practice.sentence)
                    store.addSpoken(1)
                    listening = false
                    r.destroy()
                    recognizer = null
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
                recognizer = null
            }
        }
    }

    fun nextSentence() {
        stopPlayer()
        if (recording) stopRecording()
        stopRecognition()
        transcript = ""
        dictee = null
        error = ""
        showEn = false
        i = (i + 1) % pool.size
    }

    LaunchedEffect(recording) {
        if (recording) {
            delay(15_000)
            if (recording) stopRecording()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.release()
            player?.release()
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    val scheme = MaterialTheme.colorScheme
    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speak", fontSize = 30.sp, fontWeight = FontWeight.Black)
        SpeakTabs(mode = 0, onMode = { mode = it })
        Text(
            "Read the sentence aloud. Record yourself, compare with Pluton's voice, and — where the device can listen — see which words it heard.",
            color = scheme.onSurfaceVariant,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${i + 1} / ${pool.size}", color = scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    if (practice.translation.isNotBlank()) {
                        TextButton(onClick = { showEn = !showEn }) {
                            Text(if (showEn) "Hide English" else "Show English")
                        }
                    }
                }
                Text(practice.sentence, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
                if (showEn && practice.translation.isNotBlank()) {
                    Text(practice.translation, color = scheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { hear() }, enabled = ttsReady && !recording && !listening) { Text("🔊 Hear it") }
                    if (recording) {
                        Button(
                            onClick = { stopRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.error),
                        ) { Text("⏹ Stop") }
                    } else {
                        Button(onClick = { toggleRecording() }, enabled = !listening) { Text("🎤 Record") }
                    }
                    Button(onClick = { playClip() }, enabled = clipPath != null && !listening) { Text("▶ Yours") }
                }
                if (recognitionAvailable) {
                    TextButton(
                        onClick = { checkSpeech() },
                        enabled = !listening && !recording,
                    ) { Text(if (listening) "Listening…" else "👂 Check what I said") }
                } else {
                    Text("Speech recognition isn't available on this device; record and compare by ear.", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
                if (ttsError.isNotBlank()) Text(ttsError, color = scheme.error)
                if (error.isNotBlank()) Text(error, color = scheme.error)
                dictee?.let { result ->
                    Text(
                        when (result.result) {
                            "right" -> "Tout est là !"
                            "almost" -> if (result.why == "accent") "Almost — only accents slipped." else "One word off, so close."
                            else -> "The recognizer heard something different."
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = when (result.result) {
                            "right" -> scheme.secondary
                            "almost" -> scheme.tertiary
                            else -> scheme.error
                        },
                    )
                    if (transcript.isNotBlank()) Text("Heard: “$transcript”", color = scheme.onSurfaceVariant)
                    Text(
                        "Recognizer match: ${dicteeScore(result)}% — word recognition, not a phoneme score",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    MarkedDictee(practice.sentence, result.marks)
                }
                TextButton(onClick = { nextSentence() }, enabled = !recording && !listening) {
                    Text("Next sentence →")
                }
            }
        }
    }
}

@Composable
private fun MarkedDictee(sentence: String, marks: List<DicteeMark>) {
    val scheme = MaterialTheme.colorScheme
    val text = buildAnnotatedString {
        append(sentence)
        for (m in marks) {
            val bg = when (m.mark) {
                "accent" -> scheme.tertiary.copy(alpha = 0.35f)
                "bad" -> scheme.error.copy(alpha = 0.28f)
                else -> Color.Transparent
            }
            if (bg != Color.Transparent) addStyle(SpanStyle(background = bg), m.start, m.end)
        }
    }
    Text(text, fontSize = 20.sp, lineHeight = 28.sp)
}
