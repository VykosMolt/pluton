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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.SOUND_LESSONS
import io.github.vykosmolt.pluton.core.SoundLesson
import io.github.vykosmolt.pluton.core.SoundPair
import io.github.vykosmolt.pluton.core.Store
import kotlinx.coroutines.delay
import kotlin.random.Random

/** French sounds: perception first (minimal pairs), then a phrase list and a recording to compare. */
@Composable
internal fun SoundsScreen(
    store: Store,
    onMode: (Int) -> Unit,
    onSpoken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val tts = rememberFrenchTts()
    val recorder = rememberClipRecorder()
    var lesson by remember { mutableStateOf<SoundLesson?>(null) }
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

    fun finishedClip() {
        if (recorder.path != null) {
            store.addSpoken(1)
            onSpoken()
        }
    }

    LaunchedEffect(recorder.recording) {
        if (recorder.recording) {
            delay(15_000)
            if (recorder.stop()) finishedClip()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speak", fontSize = 30.sp, fontWeight = FontWeight.Black)
        SpeakTabs(mode = 4, onMode = onMode)
        Text("Sounds", fontSize = 24.sp, fontWeight = FontWeight.Black)

        val chosen = lesson
        if (chosen == null) {
            Text(
                "Short, focused French sounds. Listen to the difference, choose what you heard, then say it yourself.",
                color = scheme.onSurfaceVariant,
            )
            for (item in SOUND_LESSONS) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(item.tip, color = scheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2)
                        TextButton(onClick = { lesson = item }) { Text("Open") }
                    }
                }
            }
        } else {
            TextButton(onClick = {
                if (recorder.stop()) finishedClip()
                lesson = null
            }) { Text("← Sounds") }
            Text(chosen.title, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(chosen.tip, color = scheme.onSurfaceVariant)

            if (chosen.pairs.isNotEmpty()) {
                PairQuiz(chosen, tts, recorder.recording)
            }
            if (chosen.phrases.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Listen and repeat", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        for (phrase in chosen.phrases) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(phrase, fontSize = 18.sp)
                                TextButton(onClick = { tts.speak(phrase) }, enabled = tts.ready && !recorder.recording) { Text("Hear") }
                            }
                        }
                        Text("Say each one aloud, then record yourself and compare.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your voice", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Record the pair or phrase, play it back, and compare with the model. Don't chase a native accent; aim to be understood.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (recorder.recording) {
                            Button(onClick = { if (recorder.stop()) finishedClip() }) { Text("Stop") }
                        } else {
                            Button(onClick = { ensureMic { tts.stop(); recorder.start() } }) { Text("Record") }
                        }
                        Button(onClick = { recorder.play() }, enabled = recorder.path != null && !recorder.recording) { Text("Play mine") }
                    }
                }
            }
        }

        if (micError.isNotBlank()) Text(micError, color = scheme.error)
        if (tts.error.isNotBlank()) Text(tts.error, color = scheme.error)
        if (recorder.error.isNotBlank()) Text(recorder.error, color = scheme.error)
    }
}

@Composable
private fun PairQuiz(lesson: SoundLesson, tts: FrenchTts, recording: Boolean) {
    val scheme = MaterialTheme.colorScheme
    var target by remember(lesson.id) { mutableStateOf<SoundPair?>(null) }
    var spoken by remember(lesson.id) { mutableStateOf("") }
    var answer by remember(lesson.id) { mutableStateOf<String?>(null) }

    fun next() {
        val pair = lesson.pairs.random()
        target = pair
        spoken = if (Random.nextBoolean()) pair.a else pair.b
        answer = null
        tts.speak(spoken)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What did you hear?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            val pair = target
            if (pair == null) {
                Text("Hear one word, then choose the one you heard.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                Button(onClick = { next() }, enabled = tts.ready && !recording) { Text("Hear one") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (option in listOf(pair.a, pair.b)) {
                        Button(onClick = { answer = option }) {
                            Text(
                                option,
                                fontWeight = if (answer == option) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                if (answer != null) {
                    val right = answer == spoken
                    Text(
                        if (right) "Yes — $spoken" else "It was “$spoken”.",
                        color = if (right) scheme.secondary else scheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                    if (pair.hint.isNotBlank()) Text(pair.hint, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { tts.speak(spoken) }, enabled = tts.ready && !recording) { Text("Hear it again") }
                    TextButton(onClick = { next() }, enabled = !recording) { Text("Next pair") }
                }
            }
        }
    }
}
