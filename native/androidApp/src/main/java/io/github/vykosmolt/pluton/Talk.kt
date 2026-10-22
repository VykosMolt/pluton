package io.github.vykosmolt.pluton

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.isChunk
import io.github.vykosmolt.pluton.core.chatSystem
import io.github.vykosmolt.pluton.core.levelOf
import io.github.vykosmolt.pluton.core.speechRate
import io.github.vykosmolt.pluton.core.splitCorrection
import io.github.vykosmolt.pluton.core.status
import kotlinx.coroutines.launch

/** Three modes under the Speak tab: read-aloud practice, conversation, and corrected writing. */
@Composable
internal fun SpeakTabs(mode: Int, onMode: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((value, label) in listOf(0 to "Practice", 1 to "Talk", 2 to "Write", 3 to "Fluency", 4 to "Sounds")) {
            TextButton(onClick = { onMode(value) }) {
                Text(
                    label,
                    color = if (mode == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (mode == value) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun NoKeyCard(blurb: String, onSettings: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(blurb, color = scheme.onSurfaceVariant)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pluton needs a key first", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Add one in More → Settings → AI tutor. Claude, OpenAI, DeepSeek, OpenRouter, a local Ollama or any OpenAI-compatible server works.",
                color = scheme.onSurfaceVariant,
            )
            Button(onClick = onSettings) { Text("Open Settings") }
        }
    }
}

@Composable
internal fun TalkScreen(
    ai: AiConfig?,
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    store: Store,
    focus: String? = null,
    onSettings: () -> Unit,
    onTalked: () -> Unit,
    onPractice: () -> Unit,
    onWrite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = rememberFrenchTts()
    val voice = rememberVoiceInput()
    var voiceOn by remember { mutableStateOf(true) }
    var permitted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf("") }
    var pendingVoice by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) {
            pendingVoice = true
        } else {
            error = "Microphone permission is needed for voice chat."
        }
    }
    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val log = remember { mutableStateListOf<ChatMessage>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    val vocab = cards.values.count {
        status(it, words[it.lemma]).let { s -> s == WordStatus.Known || s == WordStatus.Learning }
    }
    val level = remember(vocab) { levelOf(vocab) }
    val learning = remember(cards.size) {
        cards.values
            .filter { !isChunk(it.lemma) && status(it, words[it.lemma]) == WordStatus.Learning }
            .sortedByDescending { it.fsrs.lastReview ?: 0 }
            .take(40)
            .map { it.lemma }
    }

    LaunchedEffect(log.size, busy) {
        if (log.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    fun sendText(raw: String) {
        val config = ai ?: return
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        draft = ""
        error = ""
        log += ChatMessage("user", text)
        busy = true
        scope.launch {
            runCatching { aiChat(config, chatSystem(level, learning, focus), log.toList()) }
                .onSuccess {
                    log += ChatMessage("assistant", it)
                    store.addTalked()
                    onTalked()
                    if (voiceOn) tts.speak(splitCorrection(it).body)
                }
                .onFailure {
                    error = it.message ?: "The AI request failed."
                    if (log.lastOrNull()?.role == "user") log.removeAt(log.lastIndex)
                    draft = text
                }
            busy = false
        }
    }

    fun send() = sendText(draft)

    fun listen() {
        tts.stop()
        voice.start { text ->
            val seconds = if (voice.speechStartedAt > 0) {
                (SystemClock.elapsedRealtime() - voice.speechStartedAt) / 1000.0
            } else {
                0.0
            }
            val wpm = speechRate(text, seconds)
            if (wpm > 0) store.addSpeechRate(wpm)
            sendText(text)
        }
    }

    fun startVoice() {
        if (!permitted) {
            permission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        listen()
    }

    LaunchedEffect(pendingVoice) {
        if (pendingVoice) {
            pendingVoice = false
            listen()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speak", fontSize = 30.sp, fontWeight = FontWeight.Black)
        SpeakTabs(mode = 1, onMode = { if (it == 0) onPractice() else if (it == 2) onWrite() })
        if (!ready(ai)) {
            NoKeyCard(
                "Talk with Pluton uses an AI model with your own API key. The key stays on this device and goes only to the provider you pick.",
                onSettings,
            )
            return
        }

        Text("Level ${level.code} · ${level.words} words known or learning", color = scheme.onSurfaceVariant, fontSize = 13.sp)
        if (log.isEmpty()) {
            Text("Dis bonjour à Pluton. Il répond en français à ton niveau et corrige tes phrases.", color = scheme.onSurfaceVariant)
        }
        for (message in log) {
            val user = message.role == "user"
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (user) scheme.primaryContainer else scheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    val correction = if (user) null else splitCorrection(message.content)
                    if (correction?.fix != null) {
                        Text(
                            "Correction · ${correction.fix}" + if (correction.why.isNotBlank()) " — ${correction.why}" else "",
                            color = scheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(correction.body, fontSize = 17.sp, lineHeight = 24.sp)
                    } else {
                        Text(message.content, fontSize = 17.sp, lineHeight = 24.sp)
                    }
                }
            }
        }
        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.padding(top = 4.dp))
                Text("Pluton écrit…", color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (error.isNotBlank()) Text(error, color = scheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Écris à Pluton…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Button(onClick = { send() }, enabled = draft.isNotBlank() && !busy) { Text("Send") }
        }
        if (recognitionAvailable) {
            TextButton(
                onClick = { if (voice.listening) voice.stop() else startVoice() },
                enabled = !busy,
            ) {
                Text(if (voice.listening) "Listening… tap to stop" else "Speak instead of typing")
            }
        } else {
            Text("Voice input isn't available on this device.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Text("Voice input uses your device's speech service; only the transcript goes to the AI.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
        TextButton(onClick = { voiceOn = !voiceOn }) {
            Text(if (voiceOn) "Voice replies on" else "Voice replies off", color = scheme.onSurfaceVariant, fontSize = 13.sp)
        }
        if (voice.error.isNotBlank()) Text(voice.error, color = scheme.error)
        if (tts.error.isNotBlank()) Text(tts.error, color = scheme.error)
    }
}
