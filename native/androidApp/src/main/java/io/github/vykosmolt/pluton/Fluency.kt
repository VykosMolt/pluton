package io.github.vykosmolt.pluton

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import io.github.vykosmolt.pluton.core.levelOf
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.suggestedWritingPrompts
import kotlinx.coroutines.delay

/** Fluency development: shadowing a model line, then the 4/3/2 talk with the same prompt. */
@Composable
internal fun FluencyScreen(
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    store: Store,
    onMode: (Int) -> Unit,
    onSpoken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val tts = rememberFrenchTts()
    val recorder = rememberClipRecorder()
    var sub by rememberSaveable { mutableStateOf(0) }
    var permitted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var micError by remember { mutableStateOf("") }
    var pendingMic by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        if (granted) {
            pendingMic?.invoke()
        } else {
            micError = "Microphone permission is needed to record yourself."
        }
        pendingMic = null
    }
    fun ensureMic(action: () -> Unit) {
        if (permitted) action() else {
            pendingMic = action
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val pool = remember(cards.size) {
        val learned = cards.values.mapNotNull { c ->
            c.mined?.sentence ?: words[c.lemma]?.sentence
        }
        val fallback = words.values.asSequence().filter { it.sentence != null }.take(100).mapNotNull { it.sentence }
        (learned.ifEmpty { fallback.toList() }).shuffled().take(12).ifEmpty { listOf("Bonjour !") }
    }

    fun finishedClip() {
        if (recorder.path != null) {
            store.addSpoken(1)
            onSpoken()
        }
    }

    LaunchedEffect(sub) {
        if (recorder.stop()) finishedClip()
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speak", fontSize = 30.sp, fontWeight = FontWeight.Black)
        SpeakTabs(mode = 3, onMode = onMode)
        Text("Fluency", fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(
            "Fluency practice uses language you already know. The goal is smoother speech — not new words or perfect grammar.",
            color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { sub = 0 }) {
                Text("Shadow", color = if (sub == 0) scheme.primary else scheme.onSurfaceVariant, fontWeight = if (sub == 0) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { sub = 1 }) {
                Text("4 / 3 / 2", color = if (sub == 1) scheme.primary else scheme.onSurfaceVariant, fontWeight = if (sub == 1) FontWeight.Bold else FontWeight.Normal)
            }
        }
        if (sub == 0) {
            ShadowCard(pool, tts, recorder, { action -> ensureMic(action) }, { finishedClip() })
        } else {
            FourThreeTwo(words, cards, tts, recorder, { action -> ensureMic(action) }, { finishedClip() })
        }
        if (micError.isNotBlank()) Text(micError, color = scheme.error)
        if (tts.error.isNotBlank()) Text(tts.error, color = scheme.error)
        if (recorder.error.isNotBlank()) Text(recorder.error, color = scheme.error)
    }
}

@Composable
private fun ShadowCard(
    pool: List<String>,
    tts: FrenchTts,
    recorder: ClipRecorder,
    ensureMic: (() -> Unit) -> Unit,
    finishedClip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var index by remember { mutableStateOf(0) }
    val sentence = pool.getOrElse(index) { "Bonjour !" }

    LaunchedEffect(recorder.recording) {
        if (recorder.recording) {
            delay(20_000)
            if (recorder.stop()) finishedClip()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Shadowing", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Listen to the model, then say it at the same time — don't wait for the end. Record, compare, repeat.",
                color = scheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Text(sentence, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { tts.speak(sentence) }, enabled = tts.ready && !recorder.recording) { Text("Hear the model") }
                if (recorder.recording) {
                    Button(onClick = { if (recorder.stop()) finishedClip() }) { Text("Stop") }
                } else {
                    Button(onClick = { ensureMic { tts.stop(); recorder.start() } }) { Text("Record") }
                }
                Button(onClick = { recorder.play() }, enabled = recorder.path != null && !recorder.recording) { Text("Play mine") }
            }
            TextButton(onClick = { index = (index + 1) % pool.size }) { Text("Next line") }
        }
    }
}

@Composable
private fun FourThreeTwo(
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    tts: FrenchTts,
    recorder: ClipRecorder,
    ensureMic: (() -> Unit) -> Unit,
    finishedClip: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val vocab = cards.values.count {
        status(it, words[it.lemma]).let { s -> s == WordStatus.Known || s == WordStatus.Learning }
    }
    val prompts = remember(vocab) { suggestedWritingPrompts(levelOf(vocab)).map { it.fr } }
    val durations = listOf(240, 180, 120)
    var promptIndex by remember { mutableStateOf(0) }
    var round by remember { mutableStateOf(0) }
    var seconds by remember { mutableStateOf(durations[0]) }
    var active by remember { mutableStateOf(false) }
    val prompt = prompts.getOrElse(promptIndex % prompts.size) { "Parle de ta journée." }

    fun stopRound() {
        if (!active) return
        active = false
        if (recorder.stop()) finishedClip()
    }

    LaunchedEffect(active) {
        if (active) {
            while (active && seconds > 0) {
                delay(1000)
                seconds -= 1
            }
            if (active && seconds <= 0) stopRound()
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("4 / 3 / 2", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Say the same thing three times, in 4, then 3, then 2 minutes. Don't add new ideas; make the same talk smoother.",
                color = scheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
            Text(
                "Round ${round + 1} · ${durations[round] / 60} minute" + if (durations[round] == 60) "" else "s",
                color = scheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            Text(prompt, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
            Text(
                if (active) clock(seconds) else if (round == 2 && !active) "Round finished" else "Ready",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (active) scheme.primary else scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) {
                    Button(onClick = { stopRound() }) { Text("Stop") }
                } else {
                    Button(onClick = {
                        ensureMic {
                            tts.stop()
                            recorder.start()
                            if (recorder.recording) {
                                seconds = durations[round]
                                active = true
                            }
                        }
                    }) { Text(if (round == 2) "Repeat round 3" else if (round == 0) "Start round 1" else "Start round ${round + 1}") }
                }
                Button(onClick = { recorder.play() }, enabled = recorder.path != null && !recorder.recording) { Text("Play mine") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!active && round < 2) {
                    TextButton(onClick = { round += 1; seconds = durations[round] }) { Text("Next round") }
                }
                if (!active && round == 2) {
                    TextButton(onClick = { round = 0; seconds = durations[0] }) { Text("Start over") }
                }
                TextButton(
                    onClick = { promptIndex = (promptIndex + 1) % prompts.size; round = 0; seconds = durations[0] },
                    enabled = !active,
                ) { Text("New prompt") }
            }
            TextButton(onClick = { tts.speak(prompt) }, enabled = tts.ready && !recorder.recording && !active) { Text("Hear the prompt") }
            Text(
                "No score here: record, listen, and notice one thing to change next round.",
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

private fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
