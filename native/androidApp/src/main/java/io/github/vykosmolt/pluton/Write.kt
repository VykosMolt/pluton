package io.github.vykosmolt.pluton

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.WritingResult
import io.github.vykosmolt.pluton.core.levelOf
import io.github.vykosmolt.pluton.core.parseWriting
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.suggestedWritingPrompts
import io.github.vykosmolt.pluton.core.writeSystem
import kotlinx.coroutines.launch

/** A short text in French, corrected by the AI: corrected version, each error, feedback and an estimated level. */
@Composable
internal fun WritingScreen(
    ai: AiConfig?,
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    store: Store,
    onSettings: () -> Unit,
    onWritten: () -> Unit,
    onPractice: () -> Unit,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val vocab = cards.values.count {
        status(it, words[it.lemma]).let { s -> s == WordStatus.Known || s == WordStatus.Learning }
    }
    val level = remember(vocab) { levelOf(vocab) }
    val prompts = remember(level) { suggestedWritingPrompts(level) }
    var promptIndex by remember { mutableStateOf(0) }
    val prompt = prompts[promptIndex % prompts.size]
    var essay by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WritingResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun check() {
        val config = ai ?: return
        if (essay.isBlank() || busy) return
        busy = true
        error = ""
        scope.launch {
            runCatching {
                val reply = aiChat(config, writeSystem(level, prompt.fr), listOf(ChatMessage("user", essay.trim())))
                parseWriting(reply) ?: throw AiError("The model did not return the expected format. Try again.")
            }.onSuccess {
                result = it
                store.addWritten()
                onWritten()
            }.onFailure { error = it.message ?: "The AI request failed." }
            busy = false
        }
    }

    fun another() {
        essay = ""
        result = null
        error = ""
        promptIndex = (promptIndex + 1) % prompts.size
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Speak", fontSize = 30.sp, fontWeight = FontWeight.Black)
        SpeakTabs(mode = 2, onMode = { if (it == 0) onPractice() else if (it == 1) onTalk() })
        if (!ready(ai)) {
            NoKeyCard(
                "Writing correction uses an AI model with your own API key. The key stays on this device and goes only to the provider you pick.",
                onSettings,
            )
            return
        }
        Text("Level ${level.code} · ${level.words} words known or learning", color = scheme.onSurfaceVariant, fontSize = 13.sp)

        if (result == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("✍️ WRITING PROMPT · ${prompt.level}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.tertiary)
                        TextButton(onClick = { promptIndex = (promptIndex + 1) % prompts.size }) { Text("Another prompt") }
                    }
                    Text(prompt.fr, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(prompt.en, color = scheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                }
            }
            OutlinedTextField(
                value = essay,
                onValueChange = { essay = it },
                label = { Text("Écris en français…") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { check() }, enabled = essay.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Check my writing", fontSize = 18.sp)
            }
            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(top = 4.dp))
                    Text("Pluton lit…", color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (error.isNotBlank()) Text(error, color = scheme.error)
        } else {
            val shown = result!!
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✏️ CORRECTED", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.tertiary)
                    Text(shown.corrected, fontSize = 18.sp, lineHeight = 26.sp)
                    if (shown.level.isNotBlank()) Text("Estimated level: ${shown.level}", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            if (shown.errors.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("What changed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        for (e in shown.errors) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(e.original, textDecoration = TextDecoration.LineThrough, color = scheme.error)
                                    Text("→", color = scheme.onSurfaceVariant)
                                    Text(e.fix, fontWeight = FontWeight.Bold)
                                }
                                if (e.why.isNotBlank()) Text(e.why, color = scheme.onSurfaceVariant, fontSize = 14.sp)
                                if (e.kind.isNotBlank()) Text(e.kind, color = scheme.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (shown.feedback.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Feedback", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(shown.feedback)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { another() }) { Text("Write another") }
                TextButton(onClick = { result = null }) { Text("Revise this one") }
            }
        }
    }
}
