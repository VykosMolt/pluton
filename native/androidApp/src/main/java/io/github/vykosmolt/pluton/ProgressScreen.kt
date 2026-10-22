package io.github.vykosmolt.pluton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import io.github.vykosmolt.pluton.core.ProgressReport
import io.github.vykosmolt.pluton.core.Store

/** Level estimate, self-assessment and concrete milestones. Not a test and not a leaderboard. */
@Composable
internal fun ProgressScreen(
    report: ProgressReport,
    store: Store,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var done by remember(report.canDoDone) { mutableStateOf(report.canDoDone) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Progress", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("A waypoint, not a test. The estimate is based on the words you have actually graduated.", color = scheme.onSurfaceVariant, fontSize = 13.sp)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Vocabulary estimate", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(report.cefr, fontSize = 30.sp, fontWeight = FontWeight.Black, color = scheme.primary)
                Text("${report.knownWords} known · ${report.learningWords} learning", color = scheme.onSurfaceVariant)
                Text("A rough receptive range from word count, not a CEFR exam.", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Review accuracy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                val accuracy = report.accuracy
                if (accuracy < 0) {
                    Text("No reviews yet. Finish a run and this appears.", color = scheme.onSurfaceVariant)
                } else {
                    Text("${(accuracy * 100).toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Black, color = scheme.secondary)
                    Text("Good or Easy answers in your last 30 reviews.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Can you…? · ${report.stageLevel}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Self-assessment for your current stage. Be honest; a tick is not a test result.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                for (item in report.canDos) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.id in done,
                            onCheckedChange = { checked -> done = store.setCanDo(item.id, checked) },
                        )
                        Text(item.text)
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Milestones", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                for (m in report.milestones) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m.label, color = if (m.done) scheme.onSurfaceVariant else scheme.onBackground)
                        Text(
                            if (m.done) "Done" else "${minOf(m.have, m.need)} / ${m.need}",
                            color = if (m.done) scheme.secondary else scheme.onSurfaceVariant,
                            fontWeight = if (m.done) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    }
                    if (!m.done) {
                        LinearProgressIndicator(
                            progress = { (m.have.toFloat() / m.need).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
