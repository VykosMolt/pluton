package io.github.vykosmolt.pluton

import android.text.format.DateUtils
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
import androidx.compose.runtime.LaunchedEffect
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
import io.github.vykosmolt.pluton.core.CorrectionRecord
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.WritingRecord
import io.github.vykosmolt.pluton.core.WritingResult
import io.github.vykosmolt.pluton.core.levelOf
import io.github.vykosmolt.pluton.core.parseWriting
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.suggestedWritingPrompts
import io.github.vykosmolt.pluton.core.writeSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A short text in French, corrected by the AI, saved locally, with a revision and an error log. */
@Composable
internal fun WritingScreen(
    ai: AiConfig?,
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    store: Store,
    focus: String? = null,
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
    val restoredRevision = remember {
        store.meta("revision_id")?.toLongOrNull()?.let { id -> store.writings().firstOrNull { it.id == id } }
    }
    var promptIndex by remember { mutableStateOf(store.meta("writing_prompt")?.toIntOrNull() ?: 0) }
    val prompt = prompts[promptIndex % prompts.size]
    var essay by remember {
        mutableStateOf(
            if (restoredRevision != null) store.meta("revision_draft").orEmpty()
            else store.meta("writing_draft").orEmpty(),
        )
    }
    var result by remember { mutableStateOf<WritingResult?>(null) }
    var current by remember { mutableStateOf<WritingRecord?>(restoredRevision) }
    var revising by remember { mutableStateOf(restoredRevision != null) }
    var history by remember { mutableStateOf(store.writings()) }
    var corrections by remember { mutableStateOf(store.corrections()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    // Keep unsent drafts across restarts. A revision is stored under its record id so reopening it never
    // clobbers the separate new-writing draft.
    LaunchedEffect(essay, promptIndex, revising, current?.id) {
        delay(400)
        val editing = current
        if (revising && editing != null) {
            store.putMeta("revision_draft", essay)
            store.putMeta("revision_id", editing.id.toString())
        } else if (!revising && editing == null && result == null) {
            store.putMeta("writing_draft", essay)
            store.putMeta("writing_prompt", promptIndex.toString())
        }
    }

    LaunchedEffect(Unit) {
        if (restoredRevision == null) {
            store.putMeta("revision_id", "")
            store.putMeta("revision_draft", "")
        }
    }

    fun refreshHistory() {
        history = store.writings()
        corrections = store.corrections()
    }

    fun check() {
        val config = ai ?: return
        if (essay.isBlank() || busy) return
        busy = true
        error = ""
        note = ""
        scope.launch {
            runCatching {
                val asked = if (revising) (current?.prompt ?: prompt.fr) else prompt.fr
                val reply = aiChat(config, writeSystem(level, asked, focus), listOf(ChatMessage("user", essay.trim())))
                parseWriting(reply) ?: throw AiError("The model did not return the expected format. Try again.")
            }.onSuccess { parsed ->
                val now = System.currentTimeMillis()
                val editing = current
                if (revising && editing != null) {
                    store.updateWritingRevision(editing.id, essay.trim(), now)
                    store.addCorrections("revision", parsed.errors, now)
                    store.putMeta("revision_draft", "")
                    store.putMeta("revision_id", "")
                    current = editing.copy(revised = essay.trim(), updated = now)
                    note = "Revision saved on this device."
                } else {
                    current = store.saveWriting(prompt.fr, essay.trim(), parsed, now)
                    store.addWritten()
                    store.putMeta("writing_draft", "")
                    store.putMeta("revision_draft", "")
                    store.putMeta("revision_id", "")
                    note = "Saved on this device."
                }
                result = parsed
                revising = false
                essay = ""
                onWritten()
                refreshHistory()
            }.onFailure { error = it.message ?: "The AI request failed." }
            busy = false
        }
    }

    fun preserveDraft() {
        // Only a fresh editor's text is the global draft; a history/revision view must not overwrite it.
        if (!revising && current == null && result == null) {
            store.putMeta("writing_draft", essay)
            store.putMeta("writing_prompt", promptIndex.toString())
        }
    }

    fun revise(record: WritingRecord) {
        preserveDraft()
        current = record
        essay = record.revised ?: record.draft
        store.putMeta("revision_draft", essay)
        store.putMeta("revision_id", record.id.toString())
        result = null
        revising = true
        error = ""
        note = ""
    }

    fun open(record: WritingRecord) {
        preserveDraft()
        current = record
        essay = record.revised ?: record.draft
        result = WritingResult(record.corrected, record.errors, record.feedback, record.level)
        revising = false
        error = ""
    }

    fun backToDraft() {
        store.putMeta("revision_draft", "")
        store.putMeta("revision_id", "")
        current = null
        essay = store.meta("writing_draft").orEmpty()
        promptIndex = store.meta("writing_prompt")?.toIntOrNull() ?: 0
        result = null
        revising = false
        error = ""
        note = ""
    }

    fun another() {
        store.putMeta("writing_draft", "")
        store.putMeta("revision_draft", "")
        store.putMeta("revision_id", "")
        current = null
        essay = ""
        result = null
        revising = false
        error = ""
        note = ""
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
        } else {
            Text("Level ${level.code} · ${level.words} words known or learning", color = scheme.onSurfaceVariant, fontSize = 13.sp)

            if (revising && current != null) {
                SavedCorrection(current!!)
            }

            if (result == null) {
                if (!revising) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("WRITING PROMPT · ${prompt.level}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.tertiary)
                                TextButton(onClick = { promptIndex = (promptIndex + 1) % prompts.size }) { Text("Another prompt") }
                            }
                            Text(prompt.fr, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(prompt.en, color = scheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                        }
                    }
                } else {
                    Text("Rewrite it with the feedback in view. The first draft stays saved.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = essay,
                    onValueChange = { essay = it },
                    label = { Text(if (revising) "Ta révision…" else "Écris en français…") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { check() }, enabled = essay.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (revising) "Check my revision" else "Check my writing", fontSize = 18.sp)
                }
                if (busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.padding(top = 4.dp))
                        Text("Pluton lit…", color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                if (note.isNotBlank()) Text(note, color = scheme.secondary, fontSize = 13.sp)
                if (error.isNotBlank()) Text(error, color = scheme.error)
            } else {
                val shown = result!!
                CorrectionResult(shown)
                if (note.isNotBlank()) Text(note, color = scheme.secondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { current?.let { revise(it) } ?: another() }) { Text(if (current != null) "Revise this one" else "Write another") }
                    TextButton(onClick = { another() }) { Text("Start fresh") }
                    if (current != null) TextButton(onClick = { backToDraft() }) { Text("My draft") }
                }
            }
        }

        if (history.isNotEmpty()) {
            WritingHistory(history, onOpen = { open(it) }, onRevise = { revise(it) })
        }
        if (corrections.isNotEmpty()) {
            ErrorSummary(corrections)
        }
    }
}

@Composable
private fun SavedCorrection(record: WritingRecord) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("YOUR CORRECTION", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.tertiary)
            Text(record.corrected, fontSize = 17.sp, lineHeight = 24.sp)
            if (record.feedback.isNotBlank()) Text(record.feedback, color = scheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CorrectionResult(shown: WritingResult) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("CORRECTED", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = scheme.tertiary)
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
}

@Composable
private fun WritingHistory(
    history: List<WritingRecord>,
    onOpen: (WritingRecord) -> Unit,
    onRevise: (WritingRecord) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your writing", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Saved on this device; nothing leaves it except the AI request.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            for (record in history.take(6)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            DateUtils.getRelativeTimeSpanString(record.created).toString(),
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        if (record.revised != null) Text("revised", color = scheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(record.prompt, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(record.corrected, color = scheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onOpen(record) }) { Text("Open") }
                        TextButton(onClick = { onRevise(record) }) { Text("Revise") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorSummary(corrections: List<CorrectionRecord>) {
    val scheme = MaterialTheme.colorScheme
    val groups = corrections.groupBy { it.kind.ifBlank { "other" } }
        .toList()
        .sortedByDescending { it.second.size }
        .take(6)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your errors", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("${corrections.size} corrections saved. Recurring kinds are the ones to watch.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            for ((kind, list) in groups) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(kind, fontWeight = FontWeight.SemiBold)
                    Text("${list.size}", color = scheme.tertiary, fontWeight = FontWeight.Bold)
                }
                val example = list.first()
                Text("${example.original} → ${example.fix}", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}
