package io.github.vykosmolt.pluton

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.ConjTable
import io.github.vykosmolt.pluton.core.FRENCH_TENSES
import io.github.vykosmolt.pluton.core.GoalKind
import io.github.vykosmolt.pluton.core.GrammarTopic
import io.github.vykosmolt.pluton.core.Pace
import io.github.vykosmolt.pluton.core.PathState
import io.github.vykosmolt.pluton.core.STAGES
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.SyncEngine
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.conjKey
import io.github.vykosmolt.pluton.core.createEmptyCard
import io.github.vykosmolt.pluton.core.gramKey
import io.github.vykosmolt.pluton.core.isWord
import io.github.vykosmolt.pluton.core.subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Section { Hub, Path, Words, Verbs, Grammar, Account, Settings }

/** The reference shelf: conjugation tables and grammar topics, both open from day one. */
@Composable
internal fun MoreScreen(
    conj: Map<String, ConjTable>,
    grammar: List<GrammarTopic>,
    words: Map<String, Word>,
    cards: SnapshotStateMap<String, CardState>,
    store: Store,
    path: PathState,
    pace: Pace,
    onPace: (Pace) -> Unit,
    ai: AiConfig?,
    aiStore: AiStore,
    onAi: (AiConfig?) -> Unit,
    syncPrefs: SyncPrefs,
    onSynced: () -> Unit,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var section by remember { mutableStateOf(Section.Hub) }
    fun practice(key: String) {
        val now = System.currentTimeMillis()
        val card = CardState(key, createEmptyCard(now), added = now)
        store.saveCard(card, now)
        cards[key] = card
    }
    BackHandler(enabled = section != Section.Hub) { section = Section.Hub }
    when (section) {
        Section.Hub -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("More", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Reference and practice, all open from day one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth().clickable { section = Section.Path }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Path", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (path.current >= STAGES.size) "Le Chemin · done"
                        else "Le Chemin · stage ${path.current + 1} of ${STAGES.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(Modifier.fillMaxWidth().clickable { section = Section.Words }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Words", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${cards.values.count { isWord(it) }} in your deck · search, filter, hear them",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(Modifier.fillMaxWidth().clickable { section = Section.Verbs }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Verbs", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${conj.size} conjugation tables, with audio", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.fillMaxWidth().clickable { section = Section.Grammar }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Grammar", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${grammar.size} topics with exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.fillMaxWidth().clickable { section = Section.Account }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Account", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Sync cards, reviews and texts between devices", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Card(Modifier.fillMaxWidth().clickable { section = Section.Settings }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Pace, backup, about", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Section.Path -> PathScreen(path) { section = Section.Hub }
        Section.Words -> WordsScreen(words, cards) { section = Section.Hub }
        Section.Verbs -> VerbsScreen(conj, words, cards, ::practice) { section = Section.Hub }
        Section.Grammar -> GrammarScreen(grammar, cards, ::practice) { section = Section.Hub }
        Section.Account -> AccountScreen(store, syncPrefs, onSynced) { section = Section.Hub }
        Section.Settings -> SettingsScreen(pace, onPace, store, ai, aiStore, onAi, onRestored) { section = Section.Hub }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    pace: Pace,
    onPace: (Pace) -> Unit,
    store: Store,
    ai: AiConfig?,
    aiStore: AiStore,
    onAi: (AiConfig?) -> Unit,
    onRestored: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var draft by remember(ai) { mutableStateOf(ai ?: AiConfig()) }
    var aiNote by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    val provider = providerOf(draft.provider)
    fun testAndSave() {
        aiBusy = true
        aiNote = "…"
        scope.launch {
            aiNote = try {
                val reply = aiChat(
                    draft,
                    "Reply in French in one short, friendly sentence.",
                    listOf(ChatMessage("user", "Dis bonjour !")),
                )
                aiStore.save(draft)
                onAi(draft)
                "✓ $reply"
            } catch (e: Exception) {
                "⚠️ ${e.message}"
            }
            aiBusy = false
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(store.export().toByteArray()) }
                    ?: error("Couldn't open the file")
            }
            note = result.fold({ "Backup saved." }, { "Backup failed: ${it.message}" })
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val result = runCatching {
                val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                store.import(text)
            }
            result.onSuccess {
                onRestored()
                note = "Backup restored."
            }.onFailure { note = "Restore failed, nothing changed: ${it.message}" }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Black)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🤖 AI tutor", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Talking practice uses an AI model with your own API key. Chat subscriptions (Claude Pro, ChatGPT Plus…) can't be connected to other apps; an API key is billed per use, usually cents per session. The key stays on this device, goes only to the provider you pick, and is never included in backups.",
                    color = scheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in AI_PROVIDERS) {
                        TextButton(onClick = {
                            if (p.id != draft.provider) {
                                draft = AiConfig(provider = p.id, key = draft.key, model = p.defaultModel, base = "")
                            }
                        }) {
                            Text(
                                p.name.substringBefore(" ("),
                                color = if (draft.provider == p.id) scheme.primary else scheme.onSurfaceVariant,
                                fontWeight = if (draft.provider == p.id) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                if (!provider.noKey) {
                    OutlinedTextField(
                        value = draft.key,
                        onValueChange = { draft = draft.copy(key = it) },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.base,
                    onValueChange = { draft = draft.copy(base = it) },
                    label = { Text("Server URL (optional)") },
                    placeholder = { Text(provider.base.ifBlank { "https://…/v1" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { testAndSave() }, enabled = ready(draft) && !aiBusy) { Text("Test & save") }
                    if (ai != null) {
                        TextButton(onClick = {
                            aiStore.clear()
                            onAi(null)
                            draft = AiConfig()
                            aiNote = "Disconnected. The key is deleted from this device."
                        }) { Text("Disconnect") }
                    }
                }
                if (aiNote.isNotBlank()) {
                    Text(aiNote, color = if (aiNote.startsWith("⚠️")) scheme.error else scheme.secondary)
                }
                ai?.let { Text("Connected: ${providerOf(it.provider).name} · ${it.model}", color = scheme.onSurfaceVariant, fontSize = 13.sp) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⏱️ Pace", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("New words per run", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in listOf(3, 5, 8, 10)) {
                        TextButton(onClick = { onPace(pace.copy(perRun = n)) }) {
                            Text(
                                "$n",
                                color = if (pace.perRun == n) scheme.primary else scheme.onSurfaceVariant,
                                fontWeight = if (pace.perRun == n) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Text("New words per day", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (n in listOf(10, 15, 20, 30, 40)) {
                        TextButton(onClick = { onPace(pace.copy(perDay = n)) }) {
                            Text(
                                if (n == 20) "$n (recommended)" else "$n",
                                color = if (pace.perDay == n) scheme.primary else scheme.onSurfaceVariant,
                                fontWeight = if (pace.perDay == n) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Text(
                    "Every new word comes back for reviews, so a higher pace means more reviews in the weeks after.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💾 Your data", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Progress lives on this device. Back it up to move it to another one. API keys, when they exist, are never included.",
                    color = scheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        saveBackup.launch("pluton-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json")
                    }) { Text("Download backup") }
                    TextButton(onClick = { confirmRestore = true }) { Text("Restore…") }
                }
                if (note.isNotBlank()) Text(note, fontWeight = FontWeight.Bold, color = scheme.secondary)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📜 About", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Pluton is the black cat from Edgar Allan Poe's “The Black Cat” (1843), named as in Baudelaire's French translation, “Le Chat noir”.")
                Text(
                    "Word frequencies, lemmas and verb forms: Lexique 3.83 (New & Pallier), CC BY-SA 4.0. Example sentences: Tatoeba contributors, CC BY 2.0 FR. Definitions: English Wiktionary via kaikki.org, CC BY-SA 4.0. Voice: Piper TTS, SIWIS dataset (CC BY 4.0). Classics: public-domain texts via Wikisource. Scheduling: FSRS via ts-fsrs.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Replace all progress?") },
            text = { Text("The cards, reviews, saved texts and stats on this device are replaced by the backup's.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    pickRestore.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("Choose backup") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountScreen(
    store: Store,
    syncPrefs: SyncPrefs,
    onSynced: () -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val client = remember { SupabaseClient(syncPrefs) }
    var saved by remember { mutableStateOf(syncPrefs.config()) }
    var config by remember { mutableStateOf(saved ?: SupabaseConfig()) }
    var session by remember { mutableStateOf(syncPrefs.session()) }
    var email by remember { mutableStateOf(session?.email ?: "") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun launchNote(block: suspend () -> String) {
        busy = true
        note = "…"
        scope.launch {
            note = try {
                block()
            } catch (e: Exception) {
                "⚠️ ${e.message}"
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Account", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "Progress is local-first. Sign in to share cards, reviews and your texts between devices. Stats, settings and AI keys stay on each device.",
            color = scheme.onSurfaceVariant,
        )

        if (saved == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sync server", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Use the same Supabase project (and migration) as the web app. The URL and publishable key are stored only on this device.",
                        color = scheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = config.url,
                        onValueChange = { config = config.copy(url = it) },
                        label = { Text("Supabase URL") },
                        placeholder = { Text("https://…supabase.co") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.anonKey,
                        onValueChange = { config = config.copy(anonKey = it) },
                        label = { Text("Anon / publishable key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            val next = config.copy(url = config.url.trim(), anonKey = config.anonKey.trim())
                            syncPrefs.saveConfig(next)
                            saved = next
                            note = "Server saved."
                        },
                        enabled = config.url.isNotBlank() && config.anonKey.isNotBlank() && !busy,
                    ) { Text("Save server") }
                }
            }
        } else if (session == null) {
            val server = saved!!
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sign in", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(server.url, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            launchNote {
                                client.sendCode(email.trim())
                                "Code sent. Check your email."
                            }
                        },
                        enabled = '@' in email && !busy,
                    ) { Text("Email me a code") }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("6-digit code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            launchNote {
                                val signed = client.verifyCode(email.trim(), code.trim())
                                session = signed
                                email = signed.email
                                val result = withContext(Dispatchers.IO) { SyncEngine(store, client).sync() }
                                onSynced()
                                "✓ Signed in as ${signed.email} · ${result.pulled} pulled · ${result.pushed} pushed"
                            }
                        },
                        enabled = code.trim().length >= 6 && !busy,
                    ) { Text("Sign in") }
                }
            }
        } else {
            val active = session!!
            val server = saved!!
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Signed in as ${active.email}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(server.url, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                launchNote {
                                    val result = withContext(Dispatchers.IO) { SyncEngine(store, client).sync() }
                                    onSynced()
                                    "✓ ${result.pulled} pulled · ${result.pushed} pushed"
                                }
                            },
                            enabled = !busy,
                        ) { Text("Sync now") }
                        TextButton(
                            onClick = {
                                launchNote {
                                    client.signOut()
                                    session = null
                                    "Signed out."
                                }
                            },
                            enabled = !busy,
                        ) { Text("Sign out") }
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text("Delete my account", color = scheme.error)
                    }
                    Text(
                        "Deleting removes the account and its synced records from the server; this device keeps its progress.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (note.isNotBlank()) {
            Text(note, color = if (note.startsWith("⚠️")) scheme.error else scheme.secondary, fontWeight = FontWeight.Bold)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = { Text("This deletes the account and every record synced to the server. Progress on this device is not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    launchNote {
                        client.deleteAccount()
                        session = null
                        "Account deleted. This device keeps its progress."
                    }
                }) { Text("Delete", color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PathScreen(path: PathState, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(path.current) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Le Chemin", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            "Pluton's journey from Poe's wall to the Paris Opéra, A1 to C1. Nothing is locked: the goals just show the next useful step. ★ goals are a bonus.",
            color = scheme.onSurfaceVariant,
        )
        for (s in path.stages) {
            val expanded = open == s.index
            Card(Modifier.fillMaxWidth().clickable { open = if (expanded) -1 else s.index }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (s.complete) "✓ Étape ${s.index + 1} · ${s.stage.level}"
                            else "Étape ${s.index + 1} / ${STAGES.size} · ${s.stage.level}",
                            color = if (s.complete) scheme.secondary else scheme.tertiary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                        )
                        Text("${(s.pct * 100).toInt()}%", color = scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                    Text("${s.stage.place} · ${s.stage.en}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { s.pct.toFloat() }, modifier = Modifier.fillMaxWidth())
                    if (expanded) {
                        Text(s.stage.fr, fontStyle = FontStyle.Italic)
                        Text(s.stage.say, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                        for (g in s.goals) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (g.done) "✅" else if (g.bonus) "★" else "•")
                                Text(
                                    g.label + if (!g.done && g.kind != GoalKind.Read) " · ${minOf(g.have, g.need)} / ${g.need}" else "",
                                    color = if (g.done) scheme.onSurfaceVariant else scheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerbsScreen(
    conj: Map<String, ConjTable>,
    words: Map<String, Word>,
    cards: Map<String, CardState>,
    practice: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val clip = rememberClipPlayer()
    val verbs = conj.keys.filter { q.isBlank() || it.contains(q.trim().lowercase()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Verbs", fontSize = 30.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            label = { Text("Find a verb…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (v in verbs) {
                TextButton(onClick = { open = if (open == v) null else v }) {
                    Text(
                        v,
                        color = if (open == v) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = if (open == v) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        val verb = open
        val table = verb?.let { conj[it] }
        if (verb != null && table != null) {
            val gloss = words[verb]?.gloss?.split(";")?.firstOrNull()?.trim().orEmpty()
            Text(
                listOfNotNull(verb, gloss.ifBlank { null }, "past participle ${table.pp}".takeIf { table.pp.isNotBlank() }, "with ${table.aux}".takeIf { table.aux.isNotBlank() })
                    .joinToString(" · "),
                color = scheme.onSurfaceVariant,
            )
            for (tense in FRENCH_TENSES) {
                val forms = table.t[tense.id] ?: continue
                val audio = table.audio[tense.id] ?: emptyList()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val key = conjKey(verb, tense.id)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tense.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (cards[key] == null) {
                                TextButton(onClick = { practice(key) }) { Text("Practice") }
                            } else {
                                Text("queued for your runs", color = scheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                        Text(tense.en, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                        for (row in 0 until 3) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (col in 0..1) {
                                    val i = row + col * 3
                                    val form = forms.getOrNull(i) ?: continue
                                    Column(
                                        Modifier.weight(1f).clickable { clip.play(audio.getOrNull(i)) }.padding(6.dp),
                                    ) {
                                        Text(subject(i, tense.id), color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                        Text(form, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                    }
                                }
                            }
                        }
                        Text(tense.how, color = scheme.onSurfaceVariant, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                    }
                }
            }
        } else {
            Text("Pick a verb to see its tables.", color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GrammarScreen(
    grammar: List<GrammarTopic>,
    cards: Map<String, CardState>,
    practice: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("← More") }
        Text("Grammar", fontSize = 30.sp, fontWeight = FontWeight.Black)
        for (topic in grammar) {
            val expanded = open == topic.id
            Card(Modifier.fillMaxWidth().clickable { open = if (expanded) null else topic.id }) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(topic.level, color = scheme.tertiary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Column {
                            Text(topic.name, fontWeight = FontWeight.Bold)
                            Text(topic.en, color = scheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                    if (expanded) {
                        Text(topic.how, fontSize = 15.sp)
                        val key = gramKey(topic.id)
                        if (cards[key] == null) {
                            TextButton(onClick = { practice(key) }) { Text("Practice this topic") }
                        } else {
                            Text("queued for your runs", color = scheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        topic.items.forEachIndexed { i, item ->
                            val k = "${topic.id}/$i"
                            val parts = item.q.split("___")
                            Column {
                                Text("${parts[0]} ____ ${parts.getOrElse(1) { "" }}", fontSize = 17.sp)
                                if (k in shown) {
                                    Text(item.a.joinToString(" / "), color = scheme.secondary, fontWeight = FontWeight.Bold)
                                } else {
                                    TextButton(onClick = { shown = shown + k }) { Text("Show answer") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
