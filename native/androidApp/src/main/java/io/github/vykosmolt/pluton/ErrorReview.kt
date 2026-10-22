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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.ErrorDrill
import io.github.vykosmolt.pluton.core.GradeResult
import io.github.vykosmolt.pluton.core.gradeError

/** Retrieval practice for the learner's own writing corrections: see the mistake, type the fix, read why. */
@Composable
internal fun ErrorReviewScreen(
    drills: List<ErrorDrill>,
    hasCorrections: Boolean,
    onGraded: (ErrorDrill, GradeResult) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Snapshot the due list for this session: parent recompositions (the day ticker, deck changes) must not
    // swap the drill under the learner after it has been scheduled.
    val sessionDrills = remember { drills }
    var index by remember { mutableStateOf(0) }
    var typed by remember(index) { mutableStateOf("") }
    var result by remember(index) { mutableStateOf<GradeResult?>(null) }
    var right by remember { mutableStateOf(0) }
    val safeIndex = if (sessionDrills.isEmpty()) 0 else index.coerceIn(0, sessionDrills.lastIndex)
    val drill = sessionDrills.getOrNull(safeIndex)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Error review", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "Type the corrected version of your own sentence. Correct drills return after a day, then less often; " +
                "six correct reviews retire a correction.",
            color = scheme.onSurfaceVariant,
        )

        if (drill == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (hasCorrections) "No drills due right now" else "Nothing to review yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        if (hasCorrections) "New corrections appear here; this list only shows what is due."
                        else "Write a paragraph in Speak → Write and get it corrected; the fixes will land here.",
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text(
                "${safeIndex + 1} / ${sessionDrills.size} · $right right",
                color = scheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YOU WROTE", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                    Text(drill.original, fontSize = 20.sp)
                    if (drill.kind.isNotBlank()) {
                        Text(drill.kind, fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { if (result == null) typed = it },
                        label = { Text("The fix") },
                        minLines = 2,
                        readOnly = result != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val graded = gradeError(typed, drill.fix)
                                result = graded
                                if (graded.result == "right") right++
                                onGraded(drill, graded)
                            },
                            enabled = typed.isNotBlank() && result == null,
                        ) { Text("Check") }
                        if (result != null) {
                            TextButton(onClick = {
                                if (safeIndex + 1 >= sessionDrills.size) right = 0
                                index = (safeIndex + 1) % sessionDrills.size
                                typed = ""
                                result = null
                            }) {
                                Text(if (safeIndex + 1 >= sessionDrills.size) "Start over" else "Next")
                            }
                        }
                    }
                }
            }
            result?.let { graded ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            outcome(graded),
                            color = when (graded.result) {
                                "right" -> scheme.secondary
                                "almost" -> scheme.tertiary
                                else -> scheme.error
                            },
                            fontWeight = FontWeight.Bold,
                        )
                        Text("The fix: ${drill.fix}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (drill.why.isNotBlank()) {
                            Text(drill.why, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun outcome(result: GradeResult): String = when (result.result) {
    "right" -> "Exactly."
    "almost" -> when (result.why) {
        "accent" -> "Almost — only the accents differ."
        "case" -> "Almost — check the capitals."
        else -> "Almost — one small typo."
    }
    else -> "Not quite. Compare with the fix."
}
