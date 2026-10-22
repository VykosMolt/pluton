package io.github.vykosmolt.pluton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.Chunk
import io.github.vykosmolt.pluton.core.Token
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.chunksFor
import io.github.vykosmolt.pluton.core.status

/** The tap-a-word sheet, shared by the reader and the listening tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WordSheet(
    token: Token,
    word: Word?,
    sentence: String,
    card: CardState?,
    onAdd: (Word) -> Unit,
    onAddChunk: (Chunk) -> Unit = {},
    onKnown: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val state = status(card, word)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                word?.display ?: token.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (word?.gender == "f") scheme.error else scheme.primary,
            )
            Text(
                listOfNotNull(
                    word?.pos?.ifBlank { null },
                    word?.gender?.let { if (it == "f") "feminine" else "masculine" },
                ).joinToString(" · "),
                color = scheme.onSurfaceVariant,
            )
            Text(word?.gloss?.takeIf { it.isNotBlank() } ?: "No gloss for this one. The sentence will have to teach it.")
            Text(sentence, fontStyle = FontStyle.Italic, color = scheme.onSurfaceVariant)
            val chunks = remember(word?.lemma ?: token.lemma) { chunksFor(word?.lemma ?: token.lemma).take(3) }
            if (chunks.isNotEmpty()) {
                Text("Chunks with this word", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                for (chunk in chunks) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(chunk.text, fontWeight = FontWeight.SemiBold)
                            Text(chunk.gloss, fontSize = 13.sp, color = scheme.onSurfaceVariant)
                            Text(chunk.example, fontSize = 12.sp, fontStyle = FontStyle.Italic, color = scheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onAddChunk(chunk) }) { Text("Add") }
                    }
                }
            }
            when {
                state == WordStatus.New -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAdd(word ?: Word(token.lemma, display = token.text)) }) {
                            Text("+ Add to deck")
                        }
                        TextButton(onClick = onKnown) { Text("I know it") }
                    }
                }
                card?.known == true -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Marked known", color = scheme.secondary, modifier = Modifier.padding(top = 12.dp))
                        TextButton(onClick = onUndo) { Text("Undo") }
                    }
                }
                state == WordStatus.Queued -> Text("Queued for your next runs")
                state == WordStatus.Learning -> Text("In your deck")
                else -> Text("Known")
            }
        }
    }
}
