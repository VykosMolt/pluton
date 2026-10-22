package io.github.vykosmolt.pluton

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.EnglishToken
import io.github.vykosmolt.pluton.core.ShelfText
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.Token
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.bridgeNote
import io.github.vykosmolt.pluton.core.bridgeReadId
import io.github.vykosmolt.pluton.core.englishTokens
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.chunkWord

internal data class BridgeText(val text: ShelfText, val french: Set<String>, val faux: Int)

@Composable
internal fun BridgeCard(info: BridgeText, cards: Map<String, CardState>, words: Map<String, Word>, onClick: () -> Unit) {
    val ready = info.french.count { status(cards[it], words[it]) != WordStatus.New }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.text.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (info.text.author.isNotBlank()) {
                Text(info.text.author, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Text(
                buildString {
                    append("${info.french.size} French-derived words · $ready in your deck")
                    if (info.faux > 0) append(" · ${info.faux} false friends flagged")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * An English text with its French-derived words marked. In a weaving mode the words you know are shown in French,
 * so the English slowly turns back into French as your deck grows.
 */
@Composable
internal fun BridgeScreen(
    text: ShelfText,
    words: Map<String, Word>,
    cognates: Map<String, List<String>>,
    cards: SnapshotStateMap<String, CardState>,
    store: Store,
    read: Set<String>,
    onRead: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val readId = bridgeReadId(text.title)
    val finished = readId in read
    var mode by remember { mutableStateOf(0) } // 0 English, 1 weave what I know, 2 weave everything
    var sel by remember { mutableStateOf<EnglishToken?>(null) }
    val tokens = remember(text.body, cognates) { englishTokens(text.body, cognates) }
    val frenchTokens = remember(tokens) { tokens.filter { it.fr != null && !it.faux } }
    val frenchWords = remember(frenchTokens) { frenchTokens.mapNotNull { it.fr }.toSet() }
    val ready = frenchWords.count { status(cards[it], words[it]) != WordStatus.New }
    val faux = tokens.count { it.faux }

    fun woven(token: EnglishToken): Boolean {
        val fr = token.fr ?: return false
        if (token.faux) return false
        return mode == 2 || (mode == 1 && status(cards[fr], words[fr]) != WordStatus.New)
    }

    fun shown(token: EnglishToken): String {
        val fr = token.fr ?: return token.text
        if (!woven(token)) return token.text
        return if (token.text.firstOrNull()?.isUpperCase() == true) {
            fr.replaceFirstChar { it.uppercase() }
        } else {
            fr
        }
    }

    data class DisplaySpan(val start: Int, val end: Int, val token: EnglishToken)

    val wovenKey = frenchWords.count { status(cards[it], words[it]) != WordStatus.New }
    val display = remember(text.body, tokens, mode, wovenKey) {
        val builder = StringBuilder()
        val spans = mutableListOf<DisplaySpan>()
        var cursor = 0
        for (token in tokens) {
            if (token.start > cursor) builder.append(text.body, cursor, token.start)
            val piece = shown(token)
            if (token.fr != null) spans += DisplaySpan(builder.length, builder.length + piece.length, token)
            builder.append(piece)
            cursor = token.end
        }
        if (cursor < text.body.length) builder.append(text.body, cursor, text.body.length)
        builder.toString() to spans
    }

    val annotated = buildAnnotatedString {
        append(display.first)
        for (span in display.second) {
            val token = span.token
            val fr = token.fr ?: continue
            val st = status(cards[fr], words[fr])
            val color = when {
                token.faux -> scheme.error
                st == WordStatus.Known -> scheme.secondary
                st == WordStatus.Learning -> scheme.tertiary
                else -> scheme.primary
            }
            addStyle(
                SpanStyle(
                    color = color,
                    fontWeight = if (woven(token)) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (woven(token)) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = TextDecoration.Underline,
                ),
                span.start,
                span.end,
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Library") }
        Text("Norman Bridge", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(text.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (text.author.isNotBlank()) Text(text.author, color = scheme.onSurfaceVariant)
        Text(
            buildString {
                append("After 1066, Norman French poured into English. ")
                append("${frenchTokens.size} words here (")
                append(if (tokens.isEmpty()) 0 else frenchTokens.size * 100 / tokens.size)
                append("%) came that way: ${frenchWords.size} different French words, $ready already in your deck.")
                if (faux > 0) append(" $faux false friends are flagged in red.")
            },
            color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, label) in listOf(0 to "English", 1 to "Weave what I know", 2 to "Weave everything")) {
                TextButton(onClick = { mode = value }) {
                    Text(
                        label,
                        color = if (mode == value) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = if (mode == value) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                ClickableText(
                    text = annotated,
                    style = TextStyle(color = scheme.onBackground, fontSize = 19.sp, lineHeight = 30.sp),
                    onClick = { offset ->
                        sel = display.second.firstOrNull { offset >= it.start && offset < it.end }?.token
                    },
                )
            }
        }
        if (finished) {
            Text("You finished this one", color = scheme.secondary, fontWeight = FontWeight.Bold)
        } else {
            Button(onClick = { onRead(readId) }) { Text("Finish this text") }
        }
    }

    sel?.let { token ->
        val fr = token.fr ?: return@let
        val word = words[fr] ?: Word(fr, display = fr)
        val note = bridgeNote(token)
        WordSheet(
            token = Token(0, fr.length, fr, fr, false),
            word = word,
            sentence = note,
            card = cards[fr],
            onAdd = { chosen ->
                cards[fr] = store.addCard(chosen, fr, null, System.currentTimeMillis())
                sel = null
            },
            onAddChunk = { chunk ->
                val w = chunkWord(chunk)
                cards[w.lemma] = store.addCard(w, chunk.text, chunk.example, System.currentTimeMillis())
                sel = null
            },
            onKnown = {
                cards[fr] = store.markKnown(fr, System.currentTimeMillis())
                sel = null
            },
            onUndo = {
                val next = store.unmark(fr, System.currentTimeMillis())
                if (next == null) cards.remove(fr) else cards[fr] = next
                sel = null
            },
            onDismiss = { sel = null },
        )
    }
}
