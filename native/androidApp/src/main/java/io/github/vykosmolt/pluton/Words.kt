package io.github.vykosmolt.pluton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.KNOWN_DAYS
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.isWord
import io.github.vykosmolt.pluton.core.status
import kotlin.math.ln
import kotlin.math.min

private fun label(status: WordStatus): String = when (status) {
    WordStatus.Known -> "known"
    WordStatus.Learning -> "learning"
    WordStatus.Queued, WordStatus.New -> "queued"
}

private fun strength(card: CardState): Float =
    if (card.known) 1f else min(1.0, ln(1 + card.fsrs.stability) / ln(1 + KNOWN_DAYS)).toFloat()

private fun due(card: CardState, now: Long): String {
    if (card.known) return ""
    if (card.fsrs.reps == 0) return "not started"
    val days = (card.fsrs.due - now) / 86_400_000.0
    return when {
        days <= 0 -> "due now"
        days < 1 -> "later today"
        else -> "in ${kotlin.math.round(days).toInt()} d"
    }
}

/** Every word in the deck, newest first, with its memory strength and next review. Tap to hear it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordsScreen(words: Map<String, Word>, cards: Map<String, CardState>, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val clip = rememberClipPlayer()
    var q by remember { mutableStateOf("") }
    var only by remember { mutableStateOf("all") }
    val now = System.currentTimeMillis()
    val rows = cards.values.filter { isWord(it) }.sortedByDescending { it.added }
    val tally = rows.groupingBy { label(status(it, words[it.lemma])) }.eachCount()
    val query = q.trim().lowercase()
    val shown = rows.filter { card ->
        val w = words[card.lemma]
        val st = label(status(card, w))
        (only == "all" || st == only) &&
            (query.isEmpty() || card.lemma.contains(query) || w?.gloss?.lowercase()?.contains(query) == true)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("← More") }
                Text("Words", fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(
                    "${rows.size} in your deck · ${tally["known"] ?: 0} known · ${tally["learning"] ?: 0} learning · ${tally["queued"] ?: 0} queued",
                    color = scheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Search French or English…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (filter in listOf("all", "learning", "queued", "known")) {
                        TextButton(onClick = { only = filter }) {
                            Text(
                                filter,
                                color = if (only == filter) scheme.primary else scheme.onSurfaceVariant,
                                fontWeight = if (only == filter) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (rows.isEmpty()) "Words you meet in runs and the library collect here." else "No match.",
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { it.lemma }) { card ->
            val w = words[card.lemma]
            val st = status(card, w)
            Row(
                Modifier.fillMaxWidth().clickable { clip.play(w?.audioWord) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        w?.display ?: card.lemma,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (w?.gender == "f") scheme.error else scheme.primary,
                    )
                    w?.gloss?.takeIf { it.isNotBlank() }?.let { Text(it, color = scheme.onSurfaceVariant, fontSize = 13.sp) }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        label(st),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (st) {
                            WordStatus.Known -> scheme.secondary
                            WordStatus.Learning -> scheme.tertiary
                            else -> scheme.primary
                        },
                    )
                    LinearProgressIndicator(progress = { strength(card) }, modifier = Modifier.width(96.dp))
                    Text(due(card, now), color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}
