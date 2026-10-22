package io.github.vykosmolt.pluton

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.Cue
import io.github.vykosmolt.pluton.core.DicteeMark
import io.github.vykosmolt.pluton.core.DicteeResult
import io.github.vykosmolt.pluton.core.FRENCH_ELISION
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.Token
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.cueAt
import io.github.vykosmolt.pluton.core.dicteeScore
import io.github.vykosmolt.pluton.core.gradeDictee
import io.github.vykosmolt.pluton.core.parseCues
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.tokenize
import io.github.vykosmolt.pluton.core.chunkWord
import kotlinx.coroutines.delay

/** A file the user plays from their own storage, with its subtitles; playback time counts as listening. */
@Composable
internal fun ListenScreen(
    words: Map<String, Word>,
    forms: Map<String, String>,
    cards: SnapshotStateMap<String, CardState>,
    store: Store,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var media by remember { mutableStateOf<MediaPlayer?>(null) }
    var video by remember { mutableStateOf<VideoView?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var mediaName by remember { mutableStateOf("") }
    var subsName by remember { mutableStateOf("") }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var rate by remember { mutableStateOf(1f) }
    var autoPause by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var sel by remember { mutableStateOf<Token?>(null) }
    var listenStarted by remember { mutableStateOf(0L) }
    var dictCue by remember { mutableStateOf<Cue?>(null) }
    var dictation by remember { mutableStateOf("") }
    var dictResult by remember { mutableStateOf<DicteeResult?>(null) }

    // Real seconds while playing, so seeking and speed changes don't inflate listening time.
    fun countListen() {
        if (listenStarted > 0) {
            val seconds = ((System.currentTimeMillis() - listenStarted) / 1000).toInt()
            if (seconds > 0) store.addListened(seconds)
            listenStarted = 0
        }
    }

    fun pause() {
        if (videoUri != null) video?.let { if (it.isPlaying) it.pause() } else media?.let { if (it.isPlaying) it.pause() }
        playing = false
        countListen()
    }

    fun start() {
        if (videoUri != null) {
            val v = video ?: return
            if (autoPause) {
                val t = v.currentPosition / 1000.0
                val k = cueAt(cues, t)
                val cue = cues.getOrNull(k)
                if (cue != null && t >= cue.end) cues.getOrNull(k + 1)?.let { v.seekTo((it.start * 1000).toInt()) }
            }
            v.start()
            if (listenStarted == 0L) listenStarted = System.currentTimeMillis()
            playing = true
            return
        }
        val mp = media ?: return
        if (autoPause) {
            // The line we stopped on is over; resume at the start of the next one instead of pausing again.
            val t = mp.currentPosition / 1000.0
            val k = cueAt(cues, t)
            val cue = cues.getOrNull(k)
            if (cue != null && t >= cue.end) cues.getOrNull(k + 1)?.let { mp.seekTo((it.start * 1000).toInt()) }
        }
        runCatching { mp.setPlaybackParams(mp.playbackParams.setSpeed(rate)) }
        mp.start()
        if (listenStarted == 0L) listenStarted = System.currentTimeMillis()
        playing = true
    }

    fun seek(ms: Long) {
        if (videoUri != null) {
            video?.seekTo(ms.toInt())
            positionMs = ms
            return
        }
        val mp = media ?: return
        mp.seekTo(ms.toInt())
        positionMs = ms
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                media?.release()
                media = null
                video?.stopPlayback()
                video = null
                videoUri = null
                playing = false
                listenStarted = 0
                positionMs = 0
                durationMs = 0
                val mime = context.contentResolver.getType(uri)
                if (mime?.startsWith("video/") == true) {
                    videoUri = uri
                } else {
                    val mp = MediaPlayer()
                    try {
                        mp.setDataSource(context, uri)
                        mp.prepare()
                    } catch (e: Exception) {
                        mp.release()
                        throw e
                    }
                    media = mp
                    durationMs = maxOf(mp.duration, 0).toLong()
                }
                mediaName = displayName(context, uri) ?: "Selected media"
            }.onFailure { mediaName = "Couldn't open that file" }
        }
    }

    val pickSubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                cues = parseCues(text)
                dictCue = null
                dictation = ""
                dictResult = null
                subsName = "${displayName(context, uri) ?: "Subtitles"} · ${cues.size} lines"
            }.onFailure { subsName = "Couldn't read those subtitles" }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            media?.release()
            media = null
            video?.stopPlayback()
            video = null
            countListen()
        }
    }

    // Drive the subtitle line and the auto-pause from the player's own clock (audio or video).
    LaunchedEffect(playing, cues, autoPause) {
        while (playing) {
            val mp = media
            val v = video
            val position = when {
                mp != null -> runCatching { mp.currentPosition.toLong() }.getOrDefault(positionMs)
                v != null -> runCatching { v.currentPosition.toLong() }.getOrDefault(positionMs)
                else -> -1L
            }
            if (position < 0) {
                playing = false
                break
            }
            positionMs = position
            val stillPlaying = if (mp != null) mp.isPlaying else v?.isPlaying == true
            if (!stillPlaying) {
                playing = false
                countListen()
                break
            }
            if (autoPause) {
                val t = positionMs / 1000.0
                val cue = cues.getOrNull(cueAt(cues, t))
                if (cue != null && t >= cue.end) pause()
            }
            delay(120)
        }
    }

    val t = positionMs / 1000.0
    val cueIndex = cueAt(cues, t)
    val cue = cues.getOrNull(cueIndex)
    val cueTokens = remember(cue?.text, forms) { cue?.let { tokenize(it.text, forms, FRENCH_ELISION) } ?: emptyList() }
    // Built every recomposition (like the reader) so a card added from the sheet recolours the line.
    val annotated = if (cue == null) null else buildAnnotatedString {
        append(cue.text)
        for (tok in cueTokens) {
            if (tok.name) continue
            val bg = when (status(cards[tok.lemma], words[tok.lemma])) {
                WordStatus.Known -> Color.Transparent
                WordStatus.Learning -> scheme.tertiary.copy(alpha = 0.22f)
                WordStatus.Queued -> scheme.primary.copy(alpha = 0.14f)
                WordStatus.New -> scheme.error.copy(alpha = 0.16f)
            }
            if (bg != Color.Transparent) addStyle(SpanStyle(background = bg), tok.start, tok.end)
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Listen", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "Play any French audio or video you have with its French subtitles. Tap a word to look it up or add it. Time spent here counts toward your listening hours.",
            color = scheme.onSurfaceVariant,
        )
        Button(onClick = { pickMedia.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(mediaName.ifBlank { "Choose audio or video" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { pickSubs.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(subsName.ifBlank { "Choose subtitles (.srt / .vtt)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (media != null || videoUri != null) {
            videoUri?.let { uri ->
                key(uri) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(uri)
                                setOnPreparedListener {
                                    durationMs = maxOf(duration, 0).toLong()
                                    if (positionMs > 0) seekTo(positionMs.toInt())
                                }
                                video = this
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (playing) pause() else start() }) {
                    Text(if (playing) "Pause" else "Play", fontSize = 18.sp)
                }
                Text("${clock(positionMs)} / ${clock(durationMs)}", color = scheme.onSurfaceVariant)
            }
            if (durationMs > 0) {
                Slider(
                    value = dragging ?: (positionMs.toFloat() / durationMs).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { seek((it * durationMs).toLong()) }
                        dragging = null
                    },
                )
            }
            if (cues.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { cues.getOrNull(cueIndex - 1)?.let { seek((it.start * 1000).toLong()) } }, enabled = cueIndex > 0) {
                        Text("Previous")
                    }
                    TextButton(onClick = { cues.getOrNull(cueIndex)?.let { seek((it.start * 1000).toLong()) } }, enabled = cueIndex >= 0) {
                        Text("Replay")
                    }
                    TextButton(
                        onClick = { cues.getOrNull(cueIndex + 1)?.let { seek((it.start * 1000).toLong()) } },
                        enabled = cueIndex + 1 < cues.size,
                    ) { Text("Next") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (videoUri == null) {
                        for ((r, label) in listOf(0.75f to "0.75×", 1f to "1×", 1.25f to "1.25×")) {
                            TextButton(onClick = {
                                rate = r
                                media?.let { mp -> runCatching { mp.setPlaybackParams(mp.playbackParams.setSpeed(r)) } }
                            }) {
                                Text(
                                    label,
                                    color = if (rate == r) scheme.primary else scheme.onSurfaceVariant,
                                    fontWeight = if (rate == r) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    Row(Modifier.clickable { autoPause = !autoPause }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoPause, onCheckedChange = null)
                        Text("Pause after each line", color = scheme.onSurfaceVariant)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (annotated != null) {
                    ClickableText(
                        text = annotated,
                        style = TextStyle(color = scheme.onBackground, fontSize = 22.sp, lineHeight = 32.sp),
                        onClick = { offset ->
                            cueTokens.firstOrNull { !it.name && offset >= it.start && offset < it.end }
                                ?.let { sel = it }
                        },
                    )
                } else {
                    Text(
                        if (cues.isEmpty()) "Choose subtitles to see and tap each line." else "…",
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (cue != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dictogloss", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Listen to one line, then type what you heard. Replay it as often as you need; this is listening practice, not a test.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    TextButton(
                        onClick = {
                            dictCue = cue
                            dictation = ""
                            dictResult = null
                            seek((cue.start * 1000).toLong())
                            start()
                        },
                    ) { Text("Use this line") }
                    val chosen = dictCue
                    if (chosen != null) {
                        OutlinedTextField(
                            value = dictation,
                            onValueChange = { dictation = it },
                            label = { Text("Type the line…") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { dictResult = gradeDictee(dictation, chosen.text) },
                                enabled = dictation.isNotBlank(),
                            ) { Text("Check") }
                            TextButton(onClick = {
                                seek((chosen.start * 1000).toLong())
                                start()
                            }) { Text("Hear the line again") }
                        }
                        dictResult?.let { result ->
                            Text(
                                when (result.result) {
                                    "right" -> "Tout est là !"
                                    "almost" -> if (result.why == "accent") "Almost — only accents slipped." else "One word off."
                                    else -> "Not quite; listen once more."
                                },
                                color = when (result.result) {
                                    "right" -> scheme.secondary
                                    "almost" -> scheme.tertiary
                                    else -> scheme.error
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Word match: ${dicteeScore(result)}%",
                                color = scheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                            MarkedLine(chosen.text, result.marks)
                        }
                    }
                }
            }
        }
    }

    sel?.let { token ->
        val sentence = cue?.text ?: ""
        WordSheet(
            token = token,
            word = words[token.lemma],
            sentence = sentence,
            card = cards[token.lemma],
            onAdd = { word ->
                cards[token.lemma] = store.addCard(word, token.text, sentence.takeIf { it.isNotBlank() }, System.currentTimeMillis())
                sel = null
            },
            onAddChunk = { chunk ->
                val w = chunkWord(chunk)
                cards[w.lemma] = store.addCard(w, chunk.text, chunk.example, System.currentTimeMillis())
                sel = null
            },
            onKnown = {
                cards[token.lemma] = store.markKnown(token.lemma, System.currentTimeMillis())
                sel = null
            },
            onUndo = {
                val next = store.unmark(token.lemma, System.currentTimeMillis())
                if (next == null) cards.remove(token.lemma) else cards[token.lemma] = next
                sel = null
            },
            onDismiss = { sel = null },
        )
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

private fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }

@Composable
private fun MarkedLine(sentence: String, marks: List<DicteeMark>) {
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
    Text(text, fontSize = 18.sp, lineHeight = 26.sp)
}
