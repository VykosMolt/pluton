package io.github.vykosmolt.pluton

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.FRENCH_ELISION
import io.github.vykosmolt.pluton.core.QuizQuestion
import io.github.vykosmolt.pluton.core.QuizResult
import io.github.vykosmolt.pluton.core.RETELL
import io.github.vykosmolt.pluton.core.RetellFeedback
import io.github.vykosmolt.pluton.core.ShelfText
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.StoryChapter
import io.github.vykosmolt.pluton.core.StoryQuestion
import io.github.vykosmolt.pluton.core.StorySentence
import io.github.vykosmolt.pluton.core.TextRecord
import io.github.vykosmolt.pluton.core.Token
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.coverage
import io.github.vykosmolt.pluton.core.gradeQuiz
import io.github.vykosmolt.pluton.core.isWord
import io.github.vykosmolt.pluton.core.levelOf
import io.github.vykosmolt.pluton.core.parseRetell
import io.github.vykosmolt.pluton.core.parseStory
import io.github.vykosmolt.pluton.core.readerSystem
import io.github.vykosmolt.pluton.core.sentenceAt
import io.github.vykosmolt.pluton.core.sentencesOf
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.tokenize
import io.github.vykosmolt.pluton.core.unlockList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

private data class Selected(val token: Token, val word: Word?)

private data class Reading(
    val id: String,
    val title: String,
    val sub: String,
    val body: String,
    val questions: List<StoryQuestion>,
    val sentences: List<StorySentence>,
    val userTextId: Long? = null,
)

@Composable
private fun ReadingCard(
    reading: Reading,
    forms: Map<String, String>,
    statusOf: (String) -> WordStatus,
    isRead: Boolean,
    onClick: () -> Unit,
) {
    val tokens = tokenize(reading.body, forms, FRENCH_ELISION)
    val cov = coverage(tokens, statusOf)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(reading.title, fontWeight = FontWeight.Bold)
                if (isRead) Text("✓ read", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
            if (reading.sub.isNotBlank()) Text(reading.sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(
                "${(cov.pct * 100).toInt()}% understood · ${cov.total} words",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { cov.pct.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTextSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a text", fontSize = 22.sp, fontWeight = FontWeight.Black)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Text in French") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSave(title, body) },
                enabled = body.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

/** Pluton's story and the classics: coverage-coloured text, tap-to-look-up, queue-to-unlock, read-along, quiz. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    words: Map<String, Word>,
    forms: Map<String, String>,
    story: List<StoryChapter>,
    shelf: List<ShelfText>,
    cards: SnapshotStateMap<String, CardState>,
    read: Set<String>,
    store: Store,
    ai: AiConfig?,
    onSettings: () -> Unit,
    onRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<Reading?>(null) }
    var sel by remember { mutableStateOf<Selected?>(null) }
    var quizOpen by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<List<Int>>(emptyList()) }
    var quizResult by remember { mutableStateOf<QuizResult?>(null) }
    var userTexts by remember { mutableStateOf(store.texts()) }
    var addOpen by remember { mutableStateOf(false) }
    var genOpen by remember { mutableStateOf(false) }
    var genBusy by remember { mutableStateOf(false) }
    var genError by remember { mutableStateOf("") }
    var retellText by remember { mutableStateOf("") }
    var retellBusy by remember { mutableStateOf(false) }
    var retellResult by remember { mutableStateOf<RetellFeedback?>(null) }
    var retellError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val vocab = cards.values.count {
        status(it, words[it.lemma]).let { s -> s == WordStatus.Known || s == WordStatus.Learning }
    }
    val level = remember(vocab) { levelOf(vocab) }

    val statusOf: (String) -> WordStatus = { lemma -> status(cards[lemma], words[lemma]) }
    val storyReadings = remember(story) {
        story.mapIndexed { i, c -> Reading("story-$i", "Chapitre ${i + 1} · ${c.title}", "", c.body, c.questions, c.audioSentences()) }
    }
    val classicReadings = remember(shelf) {
        shelf.map { t ->
            Reading(
                id = t.title, // titles are unique; the path and the web app refer to classics by title
                title = t.title,
                sub = listOfNotNull(t.author.takeIf { it.isNotBlank() }, t.year.takeIf { it > 0 }?.toString()).joinToString(", "),
                body = t.body,
                questions = emptyList(),
                sentences = t.audioSentences(),
            )
        }
    }

    LaunchedEffect(open) {
        quizOpen = false
        answers = emptyList()
        quizResult = null
        sel = null
        retellText = ""
        retellResult = null
        retellError = ""
        retellBusy = false
        genError = ""
    }

    fun generate() {
        val config = ai ?: return
        genBusy = true
        genError = ""
        scope.launch {
            runCatching {
                val known = cards.values
                    .filter { c ->
                        isWord(c) && status(c, words[c.lemma]) != WordStatus.New &&
                            words[c.lemma]?.gloss?.isNotBlank() == true
                    }
                    .map { it.lemma }.take(40)
                val targets = cards.values
                    .filter { c -> isWord(c) && status(c, words[c.lemma]) == WordStatus.Learning && !c.known }
                    .map { it.lemma }.take(8)
                val reply = aiChat(
                    config,
                    readerSystem(level, known, targets),
                    listOf(ChatMessage("user", "Write a new story for me.")),
                )
                parseStory(reply) ?: throw AiError("The model returned an unexpected story. Try again.")
            }.onSuccess { story ->
                val record = store.addText(story.title, story.body, System.currentTimeMillis())
                userTexts = store.texts()
                genOpen = false
                open = Reading(
                    id = "text-${record.id}",
                    title = record.title,
                    sub = "Your text",
                    body = record.body,
                    questions = emptyList(),
                    sentences = emptyList(),
                    userTextId = record.id,
                )
            }.onFailure { genError = it.message ?: "The AI request failed." }
            genBusy = false
        }
    }

    if (open == null) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Pluton's story · ${storyReadings.size} chapters", color = MaterialTheme.colorScheme.onSurfaceVariant)
            storyReadings.forEach { reading ->
                ReadingCard(reading, forms, statusOf, read.contains(reading.id)) { open = reading }
            }
            if (classicReadings.isNotEmpty()) {
                Text("Classics", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                Text("Public-domain French, with read-along audio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                classicReadings.forEach { reading ->
                    ReadingCard(reading, forms, statusOf, read.contains(reading.id)) { open = reading }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your texts", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                Row {
                    TextButton(onClick = { genOpen = !genOpen }) { Text("✨ Story") }
                    TextButton(onClick = { addOpen = true }) { Text("+ Add") }
                }
            }
            Text(
                if (userTexts.isEmpty()) "Paste anything you want to read: an article, a recipe, a chapter."
                else "Anything you paste, coloured by the words you know.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (genOpen) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✨ A story at your level", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (!ready(ai)) {
                            Text(
                                "Connect an AI with your own key and Pluton will write a short story using the words you know.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onSettings) { Text("Open Settings") }
                                TextButton(onClick = { genOpen = false }) { Text("Cancel") }
                            }
                        } else {
                            Text(
                                "Pluton writes 150–250 words at your level (~$vocab words), recycling words you are learning. It lands in Your texts.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { generate() }, enabled = !genBusy) {
                                    Text(if (genBusy) "Writing…" else "Write it")
                                }
                                TextButton(onClick = { genOpen = false }, enabled = !genBusy) { Text("Cancel") }
                            }
                            if (genError.isNotBlank()) Text(genError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            for (text in userTexts) {
                val reading = Reading(
                    id = "text-${text.id}",
                    title = text.title,
                    sub = "Your text",
                    body = text.body,
                    questions = emptyList(),
                    sentences = emptyList(),
                    userTextId = text.id,
                )
                ReadingCard(reading, forms, statusOf, read.contains(reading.id)) { open = reading }
            }
        }
        if (addOpen) {
            AddTextSheet(onDismiss = { addOpen = false }) { title, body ->
                store.addText(title, body, System.currentTimeMillis())
                userTexts = store.texts()
                addOpen = false
            }
        }
        return
    }

    val reading = open!!
    val id = reading.id
    val tokens = remember(reading.body, forms) { tokenize(reading.body, forms, FRENCH_ELISION) }
    val cov = coverage(tokens, statusOf)
    val addable = unlockList(tokens, statusOf).filter { statusOf(it) == WordStatus.New }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sentences = remember(reading.body) { reading.sentences }
    // Text without recorded clips (your texts, generated stories) is read by the device's French voice.
    val ttsSpans = remember(reading.body, reading.sentences) {
        if (reading.sentences.isEmpty()) sentencesOf(reading.body) else emptyList()
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
            tts = null
        }
    }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var playingIndex by remember { mutableStateOf(-1) }
    var listenStarted by remember { mutableStateOf(0L) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun countListen() {
        if (listenStarted > 0) {
            val seconds = ((System.currentTimeMillis() - listenStarted) / 1000).toInt()
            if (seconds > 0) store.addListened(seconds)
            listenStarted = 0
        }
    }

    fun stopListening() {
        playJob?.cancel()
        playJob = null
        playingIndex = -1
        countListen()
    }

    fun listen() {
        if (playJob != null) {
            stopListening()
            return
        }
        if (sentences.isEmpty() && ttsSpans.isEmpty()) return
        listenStarted = System.currentTimeMillis()
        playJob = scope.launch {
            if (sentences.isNotEmpty()) {
                for (i in sentences.indices) {
                    playingIndex = i
                    val sentence = sentences[i]
                    val mp = MediaPlayer()
                    try {
                        context.assets.openFd("fr/audio/${sentence.audio}.mp3").use { afd ->
                            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                        suspendCancellableCoroutine<Unit> { cont ->
                            mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                            mp.setOnErrorListener { _, _, _ ->
                                if (cont.isActive) cont.resume(Unit)
                                true
                            }
                            cont.invokeOnCancellation { try { mp.stop() } catch (_: Exception) {} }
                            mp.prepare()
                            mp.start()
                        }
                    } catch (_: Exception) {
                        // a missing clip shouldn't stop the rest of the chapter
                    } finally {
                        try { mp.stop() } catch (_: Exception) {}
                        mp.release()
                    }
                }
            } else {
                for (i in ttsSpans.indices) {
                    val engine = tts ?: break
                    playingIndex = i
                    val span = ttsSpans[i]
                    suspendCancellableCoroutine<Unit> { cont ->
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                if (cont.isActive) cont.resume(Unit)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (cont.isActive) cont.resume(Unit)
                            }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                if (cont.isActive) cont.resume(Unit)
                            }
                        })
                        cont.invokeOnCancellation { engine.stop() }
                        val ok = engine.speak(
                            reading.body.substring(span.start, span.end),
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "pluton-read-$i",
                        )
                        if (ok != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
                    }
                }
            }
            playingIndex = -1
            playJob = null
            countListen()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            playJob = null
            countListen()
        }
    }

    fun askRetell() {
        val config = ai ?: return
        retellBusy = true
        retellError = ""
        scope.launch {
            runCatching {
                val reply = aiChat(
                    config,
                    RETELL,
                    listOf(
                        ChatMessage(
                            "user",
                            "TEXT:\n${reading.body.take(6000)}\n\nLEARNER'S RETELLING:\n${retellText.trim()}",
                        ),
                    ),
                    effort = "medium",
                )
                parseRetell(reply) ?: throw AiError("The model returned an unexpected format. Try again.")
            }.onSuccess { retellResult = it }
                .onFailure { retellError = it.message ?: "The AI request failed." }
            retellBusy = false
        }
    }

    val highlight = if (sentences.isNotEmpty()) {
        sentences.getOrNull(playingIndex)?.let { it.start to it.end }
    } else {
        ttsSpans.getOrNull(playingIndex)?.let { it.start to it.end }
    }
    val annotated = buildAnnotatedString {
        append(reading.body)
        highlight?.let { addStyle(SpanStyle(background = scheme.primary.copy(alpha = 0.26f)), it.first, it.second) }
        for (t in tokens) {
            if (t.name) continue
            val bg = when (statusOf(t.lemma)) {
                WordStatus.Known -> Color.Transparent
                WordStatus.Learning -> scheme.tertiary.copy(alpha = 0.22f)
                WordStatus.Queued -> scheme.primary.copy(alpha = 0.14f)
                WordStatus.New -> scheme.error.copy(alpha = 0.16f)
            }
            if (bg != Color.Transparent) addStyle(SpanStyle(background = bg), t.start, t.end)
        }
    }

    fun queueAll() {
        val now = System.currentTimeMillis()
        for (lemma in addable) {
            val token = tokens.firstOrNull { it.lemma == lemma && !it.name } ?: continue
            val word = words[lemma] ?: Word(lemma, display = lemma)
            val card = store.addCard(word, token.text, sentenceAt(reading.body, token.start), now)
            cards[lemma] = card
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { open = null }) { Text("← Library") }
        Text(reading.title, fontSize = 26.sp, fontWeight = FontWeight.Black)
        if (reading.sub.isNotBlank()) Text(reading.sub, color = scheme.onSurfaceVariant)
        Text(
            "${(cov.pct * 100).toInt()}% understood · ${cov.total} words · ${addable.size} new words to queue",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (addable.isNotEmpty()) {
            TextButton(onClick = { queueAll() }) {
                Text("Queue the ${addable.size} words that make this comfortable")
            }
        }
        if (sentences.isNotEmpty() || ttsSpans.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { listen() }, enabled = sentences.isNotEmpty() || ttsReady) {
                    Text(if (playJob == null) "▶ Listen" else "⏸ Stop")
                }
                if (playJob != null) {
                    Text(
                        "${playingIndex + 1} / ${if (sentences.isNotEmpty()) sentences.size else ttsSpans.size}",
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (sentences.isEmpty() && ttsError.isNotBlank()) Text(ttsError, color = scheme.error, fontSize = 13.sp)
        }
        ClickableText(
            text = annotated,
            style = TextStyle(color = scheme.onBackground, fontSize = 20.sp, lineHeight = 30.sp),
            onClick = { offset ->
                val token = tokens.firstOrNull { !it.name && offset >= it.start && offset < it.end }
                if (token != null) sel = Selected(token, words[token.lemma])
            },
        )

        if (reading.questions.isEmpty()) {
            if (read.contains(id)) {
                Text("✓ You read this one", color = scheme.secondary, fontWeight = FontWeight.Bold)
            } else {
                Button(onClick = { onRead(id) }) { Text("✓ I read it") }
            }
        } else if (quizResult != null) {
            val result = quizResult!!
            Text(
                if (result.pass) "✓ Compris ! ${result.right} / ${result.total}" else "Pas encore… ${result.right} / ${result.total}",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (result.pass) scheme.secondary else scheme.error,
            )
            reading.questions.forEachIndexed { i, q ->
                Text("${i + 1}. ${q.question}", fontWeight = FontWeight.Bold)
                Text(
                    "${q.options.getOrNull(answers.getOrElse(i) { -1 }) ?: "—"} → ${q.options.getOrNull(q.correct) ?: ""}",
                    color = if (answers.getOrElse(i) { -1 } == q.correct) scheme.secondary else scheme.error,
                )
                if (q.why.isNotBlank()) Text(q.why, color = scheme.onSurfaceVariant, fontSize = 14.sp)
            }
            if (result.pass) {
                Text("This chapter counts as read.", color = scheme.secondary)
            } else {
                Button(onClick = {
                    answers = List(reading.questions.size) { -1 }
                    quizResult = null
                }) { Text("Try again") }
            }
        } else if (read.contains(id)) {
            Text("✓ You read and understood this chapter.", color = scheme.secondary, fontWeight = FontWeight.Bold)
        } else if (!quizOpen) {
            Button(onClick = {
                answers = List(reading.questions.size) { -1 }
                quizOpen = true
            }) { Text("Check understanding") }
        } else {
            reading.questions.forEachIndexed { i, q ->
                Text("${i + 1}. ${q.question}", fontWeight = FontWeight.Bold)
                q.options.forEachIndexed { k, option ->
                    Row(Modifier.fillMaxWidth().clickable {
                        answers = answers.toMutableList().also { it[i] = k }
                    }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadioButton(selected = answers.getOrElse(i) { -1 } == k, onClick = {
                            answers = answers.toMutableList().also { it[i] = k }
                        })
                        Text(option, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            Button(
                onClick = {
                    val result = gradeQuiz(
                        reading.questions.map { QuizQuestion(it.options, it.correct) },
                        answers,
                    )
                    quizResult = result
                    if (result.pass) onRead(id)
                },
                enabled = answers.size == reading.questions.size && answers.none { it < 0 },
            ) { Text("Check answers") }
        }

        if (!reading.id.startsWith("story-")) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Did you understand it?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    when {
                        !ready(ai) -> {
                            Text(
                                "Connect an AI with your own key and Pluton will check your retelling. Reading stays open without one.",
                                color = scheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onSettings) { Text("Open Settings") }
                        }
                        retellResult != null -> {
                            val result = retellResult!!
                            Text("Pluton's score: ${result.score} / 5", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = scheme.secondary)
                            Text(result.feedback)
                            TextButton(onClick = { retellResult = null }) { Text("Try again") }
                        }
                        else -> {
                            Text(
                                "Tell Pluton what this text was about, in your own words. He checks the meaning, not your grammar.",
                                color = scheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = retellText,
                                onValueChange = { retellText = it },
                                label = { Text("De quoi parle ce texte ?") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { askRetell() },
                                enabled = retellText.trim().length >= 10 && !retellBusy,
                            ) { Text(if (retellBusy) "Reading…" else "Ask Pluton") }
                            if (retellError.isNotBlank()) Text(retellError, color = scheme.error)
                        }
                    }
                }
            }
        }

        if (reading.userTextId != null) {
            TextButton(onClick = { confirmDelete = true }) {
                Text("Delete this text", color = scheme.error)
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete this text?") },
                    text = { Text("The text goes away; the words you queued from it stay in your deck.") },
                    confirmButton = {
                        TextButton(onClick = {
                            store.deleteText(reading.userTextId)
                            userTexts = store.texts()
                            confirmDelete = false
                            open = null
                        }) { Text("Delete", color = scheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
                )
            }
        }
    }

    sel?.let { selected ->
        val lemma = selected.token.lemma
        val sentence = sentenceAt(reading.body, selected.token.start)
        WordSheet(
            token = selected.token,
            word = selected.word,
            sentence = sentence,
            card = cards[lemma],
            onAdd = { word ->
                cards[lemma] = store.addCard(word, selected.token.text, sentence, System.currentTimeMillis())
                sel = null
            },
            onKnown = {
                cards[lemma] = store.markKnown(lemma, System.currentTimeMillis())
                sel = null
            },
            onUndo = {
                val next = store.unmark(lemma, System.currentTimeMillis())
                if (next == null) cards.remove(lemma) else cards[lemma] = next
                sel = null
            },
            onDismiss = { sel = null },
        )
    }
}
