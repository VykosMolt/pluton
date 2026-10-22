package io.github.vykosmolt.pluton

import android.os.Bundle
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vykosmolt.pluton.core.CHUNKS
import io.github.vykosmolt.pluton.core.CardState
import io.github.vykosmolt.pluton.core.ConjTable
import io.github.vykosmolt.pluton.core.DicteeMark
import io.github.vykosmolt.pluton.core.FRENCH_TENSES
import io.github.vykosmolt.pluton.core.GRAM
import io.github.vykosmolt.pluton.core.GradeResult
import io.github.vykosmolt.pluton.core.GrammarTopic
import io.github.vykosmolt.pluton.core.Pace
import io.github.vykosmolt.pluton.core.PathState
import io.github.vykosmolt.pluton.core.PlanStep
import io.github.vykosmolt.pluton.core.Rating
import io.github.vykosmolt.pluton.core.STAGES
import io.github.vykosmolt.pluton.core.Stats
import io.github.vykosmolt.pluton.core.ReviewEntry
import io.github.vykosmolt.pluton.core.RunItem
import io.github.vykosmolt.pluton.core.Scheduler
import io.github.vykosmolt.pluton.core.ShelfText
import io.github.vykosmolt.pluton.core.Store
import io.github.vykosmolt.pluton.core.StoryChapter
import io.github.vykosmolt.pluton.core.Word
import io.github.vykosmolt.pluton.core.WordStatus
import io.github.vykosmolt.pluton.core.db.PlutonDb
import io.github.vykosmolt.pluton.core.blank
import io.github.vykosmolt.pluton.core.buildRun
import io.github.vykosmolt.pluton.core.chunkWord
import io.github.vykosmolt.pluton.core.contextForm
import io.github.vykosmolt.pluton.core.contextSentence
import io.github.vykosmolt.pluton.core.decodeCognates
import io.github.vykosmolt.pluton.core.grammarFocus
import io.github.vykosmolt.pluton.core.suggestedNewWords
import io.github.vykosmolt.pluton.core.decodeConj
import io.github.vykosmolt.pluton.core.decodeForms
import io.github.vykosmolt.pluton.core.decodeGrammar
import io.github.vykosmolt.pluton.core.decodeShelf
import io.github.vykosmolt.pluton.core.decodeStory
import io.github.vykosmolt.pluton.core.decodeWords
import io.github.vykosmolt.pluton.core.gotIt
import io.github.vykosmolt.pluton.core.grade
import io.github.vykosmolt.pluton.core.focus
import io.github.vykosmolt.pluton.core.gradeConj
import io.github.vykosmolt.pluton.core.gradeDictee
import io.github.vykosmolt.pluton.core.gradeGram
import io.github.vykosmolt.pluton.core.gradeProduce
import io.github.vykosmolt.pluton.core.gramKey
import io.github.vykosmolt.pluton.core.modeFor
import io.github.vykosmolt.pluton.core.parseConj
import io.github.vykosmolt.pluton.core.produceAnswer
import io.github.vykosmolt.pluton.core.pathMetrics
import io.github.vykosmolt.pluton.core.pathState
import io.github.vykosmolt.pluton.core.requeue
import io.github.vykosmolt.pluton.core.status
import io.github.vykosmolt.pluton.core.subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlutonTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { PlutonApp() }
            }
        }
    }
}

private class Deck(val words: List<Word>, val forms: Map<String, String>) {
    val wordMap: Map<String, Word> = words.associateBy { it.lemma }
}

private data class Tally(val right: Int = 0, val almost: Int = 0, val wrong: Int = 0) {
    fun add(g: GradeResult) = when (g.result) {
        "right" -> copy(right = right + 1)
        "almost" -> copy(almost = almost + 1)
        else -> copy(wrong = wrong + 1)
    }
}

private data class Summary(val introduced: Int, val done: Int, val tally: Tally)

private enum class Tab { Home, Read, Listen, Speak, More }

private class Loaded(
    val deck: Deck,
    val story: List<StoryChapter>,
    val shelf: List<ShelfText>,
    val conj: Map<String, ConjTable>,
    val grammar: List<GrammarTopic>,
    val cognates: Map<String, List<String>>,
    val cards: List<CardState>,
    val newToday: Int,
    val read: Set<String>,
    val pace: Pace,
)

@Composable
private fun PlutonApp() {
    val context = LocalContext.current
    val driver = remember { AndroidSqliteDriver(PlutonDb.Schema, context, "pluton.db") }
    val store = remember(driver) { Store(PlutonDb(driver)) }
    val aiStore = remember { AiStore(context) }
    DisposableEffect(driver) { onDispose { driver.close() } }
    var loaded by remember { mutableStateOf<Loaded?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val words = context.assets.open("fr/words.json").bufferedReader().use { it.readText() }
                val forms = context.assets.open("fr/forms.json").bufferedReader().use { it.readText() }
                val story = context.assets.open("fr/story.json").bufferedReader().use { it.readText() }
                val shelf = context.assets.open("fr/shelf.json").bufferedReader().use { it.readText() }
                val conj = context.assets.open("fr/conj.json").bufferedReader().use { it.readText() }
                val grammar = context.assets.open("fr/grammar.json").bufferedReader().use { it.readText() }
                val cognates = context.assets.open("fr/cognates.json").bufferedReader().use { it.readText() }
                val stats = store.stats(todayString())
                Loaded(
                    deck = Deck(decodeWords(words) + CHUNKS.map { chunkWord(it) }, decodeForms(forms)),
                    story = decodeStory(story),
                    shelf = decodeShelf(shelf),
                    conj = decodeConj(conj),
                    grammar = decodeGrammar(grammar),
                    cognates = decodeCognates(cognates),
                    cards = store.cards(),
                    newToday = stats.newCount,
                    read = stats.read.toSet(),
                    pace = store.pace(),
                )
            }
        }
        result.onSuccess { loaded = it }.onFailure { error = it.message ?: it.toString() }
    }

    when {
        error != null -> ErrorScreen(error!!)
        loaded == null -> LoadingScreen()
        else -> MainScreen(loaded!!, store, aiStore)
    }
}

private fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Chargement…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorScreen(message: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pluton couldn't load the deck", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.error)
        Text("Build the data with `uv run data/fr/build.py`, then rebuild the app.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MainScreen(loaded: Loaded, store: Store, aiStore: AiStore) {
    val deck = loaded.deck
    val cards = remember {
        mutableStateMapOf<String, CardState>().apply { loaded.cards.forEach { put(it.lemma, it) } }
    }
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var newToday by remember { mutableStateOf(loaded.newToday) }
    var read by remember { mutableStateOf(loaded.read) }
    var pace by remember { mutableStateOf(loaded.pace) }
    var day by remember { mutableStateOf(todayString()) }
    var dailyMinutes by remember { mutableStateOf(store.onboardingMinutes()?.takeIf { it > 0 } ?: 10) }
    var onboarded by remember { mutableStateOf(store.onboardedOrExisting()) }
    var tab by remember {
        mutableStateOf(
            if (onboarded) Tab.entries.firstOrNull { it.name == store.lastTab() } ?: Tab.Home else Tab.Home,
        )
    }
    var planDone by remember { mutableStateOf(store.planDone(day)) }
    var practiceDays by remember { mutableStateOf(store.practiceDays()) }
    var accuracy by remember { mutableStateOf(store.recentAccuracy()) }
    var speakMode by remember { mutableStateOf(0) }
    var speakRequest by remember { mutableStateOf(0) }
    var ai by remember { mutableStateOf(aiStore.load()) }
    // Listening/speaking write straight to the store, so re-read the stats when the user comes back to a tab.
    var stats by remember { mutableStateOf(Stats(day = day)) }
    LaunchedEffect(tab, running, day) { if (!running) stats = store.stats(day) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val now = todayString()
            if (now != day) {
                day = now
                planDone = store.planDone(day)
                practiceDays = store.practiceDays()
                stats = store.stats(day)
                newToday = stats.newCount
                accuracy = store.recentAccuracy()
            }
        }
    }
    LaunchedEffect(tab) { if (onboarded) store.saveLastTab(tab.name) }
    val titles = remember(loaded.story) {
        loaded.story.mapIndexed { i, c -> "story-$i" to "Chapitre ${i + 1} · ${c.title}" }.toMap()
    }
    val path = pathState(
        pathMetrics(cards.values.toList(), read, stats.listened, stats.spoken, talked = stats.talked, written = stats.written),
        titles,
    )
    val grammarFocusLabel = grammarFocus(cards.values.toList(), loaded.grammar, loaded.conj)

    fun reloadFromStore() {
        val fresh = store.cards()
        cards.clear()
        fresh.forEach { cards[it.lemma] = it }
        day = todayString()
        val current = store.stats(day)
        read = current.read.toSet()
        newToday = current.newCount
        stats = current
        pace = store.pace()
        accuracy = store.recentAccuracy()
        planDone = store.planDone(day)
        practiceDays = store.practiceDays()
        summary = null
    }

    fun markStep(step: String) {
        planDone = store.markPlanDone(day, step)
        practiceDays = store.practiceDays()
    }

    if (!onboarded) {
        OnboardingScreen { minutes ->
            val chosen = onboardingPace(minutes)
            store.saveOnboarding(minutes)
            store.savePace(chosen)
            dailyMinutes = minutes
            pace = chosen
            planDone = store.planDone(day)
            practiceDays = store.practiceDays()
            onboarded = true
            tab = Tab.Home
            store.saveLastTab(Tab.Home.name)
        }
        return
    }

    BackHandler(enabled = running) { running = false }
    BackHandler(enabled = !running && tab != Tab.Home) { tab = Tab.Home }
    if (running) {
        RunScreen(deck, cards, loaded.conj, loaded.grammar, newToday, pace, onSave = { card, entry -> store.saveAnswer(card, entry) }) { result ->
            running = false
            summary = result
            newToday = store.addIntroduced(day, result.introduced).newCount
            if (result.done > 0) markStep(PlanStep.Review.id)
            accuracy = store.recentAccuracy()
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Home,
                        onClick = { tab = Tab.Home },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Read,
                        onClick = { tab = Tab.Read },
                        icon = { Icon(PlutonIcons.Book, contentDescription = null) },
                        label = { Text("Read") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Listen,
                        onClick = { tab = Tab.Listen },
                        icon = { Icon(PlutonIcons.Headphones, contentDescription = null) },
                        label = { Text("Listen") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Speak,
                        onClick = { tab = Tab.Speak },
                        icon = { Icon(PlutonIcons.Mic, contentDescription = null) },
                        label = { Text("Speak") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.More,
                        onClick = { tab = Tab.More },
                        icon = { Icon(Icons.Outlined.MoreVert, contentDescription = null) },
                        label = { Text("More") },
                    )
                }
            },
        ) { padding ->
            when (tab) {
                Tab.Home -> HomeScreen(
                    deck = deck,
                    cards = cards,
                    conj = loaded.conj,
                    grammar = loaded.grammar,
                    path = path,
                    newToday = newToday,
                    pace = pace,
                    summary = summary,
                    planDone = planDone,
                    practiceDays = practiceDays,
                    dailyMinutes = dailyMinutes,
                    accuracy = accuracy,
                    modifier = Modifier.padding(padding),
                    onStart = {
                        summary = null
                        running = true
                    },
                    onTogglePlan = { id ->
                        if (id in planDone) planDone = store.unmarkPlanDone(day, id) else markStep(id)
                    },
                    onUseSuggested = { n ->
                        val next = pace.copy(perRun = n)
                        store.savePace(next)
                        pace = next
                    },
                    onRead = { tab = Tab.Read },
                    onSpeak = {
                        speakMode = 0
                        speakRequest++
                        tab = Tab.Speak
                    },
                    onWrite = {
                        speakMode = 2
                        speakRequest++
                        tab = Tab.Speak
                    },
                )
                Tab.Read -> ReaderScreen(
                    words = deck.wordMap,
                    forms = deck.forms,
                    story = loaded.story,
                    shelf = loaded.shelf,
                    cognates = loaded.cognates,
                    cards = cards,
                    read = read,
                    store = store,
                    ai = ai,
                    focus = grammarFocusLabel,
                    onSettings = { tab = Tab.More },
                    onRead = { id ->
                        read = store.addRead(day, id).read.toSet()
                        markStep(PlanStep.Read.id)
                    },
                    modifier = Modifier.padding(padding),
                )
                Tab.Listen -> ListenScreen(
                    words = deck.wordMap,
                    forms = deck.forms,
                    cards = cards,
                    store = store,
                    modifier = Modifier.padding(padding),
                )
                Tab.Speak -> SpeakScreen(
                    words = deck.wordMap,
                    cards = cards,
                    store = store,
                    ai = ai,
                    focus = grammarFocusLabel,
                    initialMode = speakMode,
                    modeRequest = speakRequest,
                    onSettings = { tab = Tab.More },
                    onTalked = {
                        stats = store.stats(day)
                        markStep(PlanStep.Speak.id)
                    },
                    onSpoken = {
                        stats = store.stats(day)
                        markStep(PlanStep.Speak.id)
                    },
                    onWritten = {
                        stats = store.stats(day)
                        markStep(PlanStep.Write.id)
                    },
                    modifier = Modifier.padding(padding),
                )
                Tab.More -> MoreScreen(
                    conj = loaded.conj,
                    grammar = loaded.grammar,
                    words = deck.wordMap,
                    cards = cards,
                    store = store,
                    path = path,
                    shelf = loaded.shelf,
                    story = loaded.story,
                    pace = pace,
                    onPace = { next ->
                        store.savePace(next)
                        pace = next
                    },
                    ai = ai,
                    aiStore = aiStore,
                    onAi = { ai = it },
                    onRestored = ::reloadFromStore,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private fun lastSevenDays(): List<String> {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val now = Calendar.getInstance()
    return (6 downTo 0).map { back ->
        val day = now.clone() as Calendar
        day.add(Calendar.DAY_OF_YEAR, -back)
        format.format(day.time)
    }
}

@Composable
private fun OnboardingScreen(onStart: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var minutes by remember { mutableStateOf(10) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Bienvenue", fontSize = 38.sp, fontWeight = FontWeight.Black)
        Text(
            "Pluton is a black cat from Poe's story, now your French guide. Each day mixes four things: a short review, something to read, something to say, and something to write.",
            color = scheme.onSurfaceVariant,
        )
        Text("How long can you practise on a normal day?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in listOf(5, 10, 20, 30)) {
                TextButton(onClick = { minutes = m }) {
                    Text(
                        "$m min",
                        color = if (minutes == m) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = if (minutes == m) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Text("You can change the pace later in Settings.", color = scheme.onSurfaceVariant, fontSize = 13.sp)
        Button(onClick = { onStart(minutes) }, modifier = Modifier.fillMaxWidth()) {
            Text("Start with $minutes minutes", fontSize = 18.sp)
        }
        Text(
            "No account, no server. Your words, texts and corrections stay on this device.",
            color = scheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

private fun onboardingPace(minutes: Int): Pace = when (minutes) {
    5 -> Pace(3, 10)
    10 -> Pace(5, 20)
    20 -> Pace(8, 30)
    else -> Pace(10, 40)
}

@Composable
private fun TodayCard(
    done: Set<String>,
    practiceDays: Set<String>,
    minutes: Int,
    accuracy: Double?,
    pace: Pace,
    due: Int,
    fresh: Int,
    onToggle: (String) -> Unit,
    onUseSuggested: (Int) -> Unit,
    onStart: () -> Unit,
    onRead: () -> Unit,
    onSpeak: () -> Unit,
    onWrite: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val suggested = suggestedNewWords(pace.perRun, accuracy)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today", fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("${done.size} / ${PlanStep.entries.size}", color = scheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Text("About $minutes minutes · four small steps", color = scheme.onSurfaceVariant, fontSize = 13.sp)
            LinearProgressIndicator(
                progress = { done.size.toFloat() / PlanStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
            val week = lastSevenDays()
            val practiced = week.count { it in practiceDays }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("This week", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                for (day in week) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(if (day in practiceDays) scheme.secondary else scheme.surfaceVariant, CircleShape),
                    )
                }
                Text("$practiced / 7", color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
            for (step in PlanStep.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = step.id in done, onCheckedChange = { onToggle(step.id) })
                    Column(Modifier.weight(1f)) {
                        Text(step.title, fontWeight = FontWeight.Bold)
                        Text(
                            when (step) {
                                PlanStep.Review -> if (due + fresh == 0) "Nothing due; a bonus review is fine" else "$due due · $fresh new"
                                PlanStep.Read -> "A story, a classic or a Bridge text"
                                PlanStep.Speak -> "Read a sentence aloud or talk with Pluton"
                                PlanStep.Write -> "One paragraph, then one correction"
                            },
                            color = scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = {
                            when (step) {
                                PlanStep.Review -> onStart()
                                PlanStep.Read -> onRead()
                                PlanStep.Speak -> onSpeak()
                                PlanStep.Write -> onWrite()
                            }
                        },
                    ) { Text(if (step.id in done) "Again" else "Open") }
                }
            }
            if (accuracy != null && suggested != pace.perRun) {
                Text(
                    "Recent reviews: ${(accuracy * 100).toInt()}%. Try $suggested new words per run.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                TextButton(onClick = { onUseSuggested(suggested) }) { Text("Use $suggested") }
            }
            if (done.size == PlanStep.entries.size) {
                Text("Pluton purrs. Today is done — anything else is a bonus.", color = scheme.secondary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    deck: Deck,
    cards: Map<String, CardState>,
    conj: Map<String, ConjTable>,
    grammar: List<GrammarTopic>,
    path: PathState,
    newToday: Int,
    pace: Pace,
    summary: Summary?,
    planDone: Set<String>,
    practiceDays: Set<String>,
    dailyMinutes: Int,
    accuracy: Double?,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onTogglePlan: (String) -> Unit,
    onUseSuggested: (Int) -> Unit,
    onRead: () -> Unit,
    onSpeak: () -> Unit,
    onWrite: () -> Unit,
) {
    val known = cards.values.count { status(it, deck.wordMap[it.lemma]) == WordStatus.Known }
    val learning = cards.values.count { status(it, deck.wordMap[it.lemma]) == WordStatus.Learning }
    // Keep the due/new counts fresh while Home stays open, so a card that becomes due is startable without a restart.
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = System.currentTimeMillis()
        }
    }
    val now = if (tick == 0L) System.currentTimeMillis() else tick
    val preview = buildRun(
        cards.values.toList(), deck.wordMap, now, newToday, conj, grammar,
        perRun = pace.perRun, perDay = pace.perDay,
    )
    val due = preview.count { it.phase == "quiz" }
    val fresh = preview.count { it.phase == "intro" }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Pluton", fontSize = 36.sp, fontWeight = FontWeight.Black)
        Text("${deck.words.size} words, all on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)

        TodayCard(
            done = planDone,
            practiceDays = practiceDays,
            minutes = dailyMinutes,
            accuracy = accuracy,
            pace = pace,
            due = due,
            fresh = fresh,
            onToggle = onTogglePlan,
            onUseSuggested = onUseSuggested,
            onStart = onStart,
            onRead = onRead,
            onSpeak = onSpeak,
            onWrite = onWrite,
        )

        val stage = path.stages.getOrNull(path.current)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (stage == null) {
                    Text("LE CHEMIN · DONE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.tertiary)
                    Text("L'Opéra · complete", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("Keep reading, listening and talking: C1 is a habit, not a finish line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "ÉTAPE ${path.current + 1} / ${STAGES.size} · ${stage.stage.level}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text("${stage.stage.place} · ${stage.stage.en}", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    LinearProgressIndicator(progress = { stage.pct.toFloat() }, modifier = Modifier.fillMaxWidth())
                    focus(stage)?.let {
                        Text("Next: ${it.label}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
            }
        }

        summary?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (it.done == 0) "No cards were due." else "Run complete", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    if (it.done > 0) Text("${it.tally.right} right · ${it.tally.almost} almost · ${it.tally.wrong} wrong")
                    Text("${it.introduced} new words introduced")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your deck", fontWeight = FontWeight.Bold)
                Text("$known known · $learning learning · ${cards.size} cards saved")
                Text(
                    "Everything is saved on this device. No account, no server.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onStart, enabled = preview.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(
                if (preview.isEmpty()) "Nothing due today" else "Start a run · $due reviews · $fresh new",
                fontSize = 19.sp,
            )
        }
    }
}

@Composable
private fun RunScreen(
    deck: Deck,
    cards: SnapshotStateMap<String, CardState>,
    conj: Map<String, ConjTable>,
    grammar: List<GrammarTopic>,
    newToday: Int,
    pace: Pace,
    onSave: (CardState, ReviewEntry) -> Unit,
    onExit: (Summary) -> Unit,
) {
    val scheduler = remember { Scheduler() }
    val clip = rememberClipPlayer()
    val queue = remember {
        mutableStateListOf<RunItem>().also {
            it.addAll(
                buildRun(
                    cards.values.toList(), deck.wordMap, System.currentTimeMillis(), newToday, conj, grammar,
                    perRun = pace.perRun, perDay = pace.perDay,
                ),
            )
        }
    }
    var done by remember { mutableStateOf(0) }
    var introduced by remember { mutableStateOf(0) }
    var tally by remember { mutableStateOf(Tally()) }
    val item = queue.firstOrNull()

    if (item == null) {
        LaunchedEffect(Unit) { onExit(Summary(introduced, done, tally)) }
        return
    }

    val lemma = item.lemma
    val conjPair = parseConj(lemma)
    val table = conjPair?.let { conj[it.first] }
    val tenseId = conjPair?.second
    val tid = tenseId.orEmpty()
    val tense = tenseId?.let { id -> FRENCH_TENSES.find { it.id == id } }
    val forms = tenseId?.let { table?.t?.get(it) } ?: emptyList()
    val clips = tenseId?.let { table?.audio?.get(it) } ?: emptyList()
    val topic = if (lemma.startsWith(GRAM)) grammar.find { gramKey(it.id) == lemma } else null
    val pick = remember(lemma, item.phase, done) {
        val n = topic?.items?.size ?: 0
        if (n == 0) 0 else kotlin.random.Random.nextInt(n)
    }
    val exercise = topic?.items?.getOrNull(pick)
    val gramParts = exercise?.q?.split("___") ?: listOf("")
    val person = remember(lemma, item.phase, done) {
        val open = forms.indices.filter { forms[it] != null }
        if (open.isEmpty()) 0 else open.random()
    }
    val expectedConj = forms.getOrNull(person).orEmpty()

    // Freeze the context for this queue item: grading bumps reps before Next, and the sentence must not flip
    // under the answer, the feedback hint, or the dictée marks.
    val base = deck.wordMap[lemma]
    val card = remember(lemma, item.phase, done) { cards[lemma] }
    val sentence = remember(lemma, item.phase, done) { contextSentence(cards[lemma], base) }
    val form = remember(lemma, item.phase, done) { contextForm(cards[lemma], base) }
    val display = base?.display ?: lemma
    val gloss = base?.gloss ?: ""
    val translation = card?.mined?.translation?.takeIf { it.isNotBlank() } ?: base?.translation
    val (before, match, after) = remember(sentence, form) { blank(sentence, form) }
    val mode = when {
        topic != null -> "gram"
        conjPair != null -> "conj"
        base != null -> modeFor(item.card, base)
        else -> "cloze"
    }
    val produceTarget = base?.let { produceAnswer(it) } ?: lemma
    var typed by remember(lemma, item.phase, done) { mutableStateOf("") }
    var feedback by remember(lemma, item.phase, done) { mutableStateOf<GradeResult?>(null) }
    var pending by remember(lemma, item.phase, done) { mutableStateOf<RunItem?>(null) }
    var dicteeMarks by remember(lemma, item.phase, done) { mutableStateOf<List<DicteeMark>>(emptyList()) }

    val expected = when {
        topic != null -> exercise?.a?.joinToString(" / ").orEmpty()
        conjPair != null -> expectedConj
        mode == "dictee" -> sentence
        mode == "produce" -> produceTarget
        else -> match
    }

    // Hear it on arrival for listening exercises, and when a new word is introduced.
    LaunchedEffect(lemma, item.phase, done) {
        if (item.phase == "intro" && base != null) clip.play(base.audioWord)
        else if (item.phase == "quiz" && (mode == "dictation" || mode == "dictee")) clip.play(base?.audioSentence)
    }

    fun submit() {
        if (typed.isBlank() || feedback != null) return
        val dictee = if (mode == "dictee") gradeDictee(typed, sentence) else null
        if (dictee != null) dicteeMarks = dictee.marks
        val g = when {
            topic != null -> gradeGram(typed, exercise?.a ?: emptyList())
            conjPair != null -> gradeConj(typed, expectedConj, tenseId == "pc" && table?.aux == "être")
            dictee != null -> GradeResult(dictee.result, dictee.why)
            mode == "produce" -> gradeProduce(typed, base ?: Word(lemma), deck.forms)
            else -> grade(typed, match, lemma, deck.forms)
        }
        feedback = g
        tally = tally.add(g)
        val rating = when (g.result) {
            "right" -> Rating.Good
            "almost" -> Rating.Hard
            else -> Rating.Again
        }
        val now = System.currentTimeMillis()
        val next = scheduler.next(item.card, now, rating)
        pending = item.copy(card = next, seen = item.seen + 1, missed = item.missed || g.result == "wrong")
        val prior = cards[lemma]
        val updated = (prior ?: CardState(lemma, next, added = now)).copy(fsrs = next)
        cards[lemma] = updated
        onSave(updated, ReviewEntry(lemma, rating.value, mode, now))
    }

    fun next() {
        queue.removeAt(0)
        done++
        val rest = requeue(queue.toList(), pending ?: item, System.currentTimeMillis())
        queue.clear()
        queue.addAll(rest)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Run · ${done + 1} / ${done + queue.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${tally.right} right", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        }

        if (item.phase == "intro") {
            val scheme = MaterialTheme.colorScheme
            val tag = FontWeight.ExtraBold
            when {
                topic != null -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GRAMMAR TOPIC · ${topic.level}", fontSize = 13.sp, fontWeight = tag, color = scheme.tertiary)
                        Text(topic.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text(topic.en, color = scheme.onSurfaceVariant)
                        Text(topic.how)
                        topic.items.take(2).forEach { example ->
                            val p = example.q.split("___")
                            Text(
                                "${p[0]}${example.a.firstOrNull().orEmpty()}${p.getOrElse(1) { "" }}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                conjPair != null -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CONJUGATION · ${tense?.name ?: tid}", fontSize = 13.sp, fontWeight = tag, color = scheme.tertiary)
                        Text("${conjPair.first} · ${tense?.name ?: tid}", fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(
                            listOfNotNull(tense?.en, gloss.takeIf { it.isNotBlank() }).joinToString(" · "),
                            color = scheme.onSurfaceVariant,
                        )
                        Text(tense?.how.orEmpty(), color = scheme.onSurfaceVariant, fontSize = 14.sp)
                        for (row in 0 until 3) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (col in 0..1) {
                                    val i = row + col * 3
                                    val f = forms.getOrNull(i) ?: continue
                                    Column(
                                        Modifier.weight(1f).clickable { clip.play(clips.getOrNull(i)) }.padding(6.dp),
                                    ) {
                                        Text(subject(i, tid), color = scheme.onSurfaceVariant, fontSize = 12.sp)
                                        Text(f, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("NEW WORD", fontSize = 13.sp, fontWeight = tag, color = scheme.tertiary)
                        Text(
                            display,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Black,
                            color = if (base?.gender == "f") scheme.error else scheme.primary,
                        )
                        Text(
                            listOfNotNull(
                                base?.pos?.ifBlank { null },
                                base?.gender?.let { if (it == "f") "feminine" else "masculine" },
                            ).joinToString(" · "),
                            color = scheme.onSurfaceVariant,
                        )
                        gloss.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        MarkedSentence(before, match, after)
                        translation?.let { Text(it, color = scheme.onSurfaceVariant) }
                    }
                }
            }
            Button(
                onClick = {
                    val next = gotIt(queue.toList(), item)
                    queue.clear()
                    queue.addAll(next)
                    introduced++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Got it →", fontSize = 20.sp) }
        } else {
            val scheme = MaterialTheme.colorScheme
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        topic != null -> {
                            Text("GRAMMAR", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                            Text(
                                listOfNotNull(topic.name, exercise?.ask?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                "${gramParts[0]} ______ ${gramParts.getOrElse(1) { "" }}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (feedback == null) {
                                AnswerInput(typed, { typed = it }, ::submit)
                            } else {
                                FeedbackLine(feedback!!, expected)
                                Text("→ $expected", fontWeight = FontWeight.Bold)
                                Text(topic.how, color = scheme.onSurfaceVariant, fontSize = 14.sp)
                                NextButton { next() }
                            }
                        }
                        conjPair != null -> {
                            Text("CONJUGATE", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurfaceVariant)
                            Text("${conjPair.first} · ${tense?.name ?: tid}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            if (feedback == null) {
                                Text("${subject(person, tid)} ______", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                Text(tense?.en.orEmpty(), color = scheme.onSurfaceVariant, fontSize = 14.sp)
                                AnswerInput(typed, { typed = it }, ::submit)
                            } else {
                                FeedbackLine(feedback!!, expected)
                                Text("→ ${subject(person, tid)} $expectedConj", fontWeight = FontWeight.Bold)
                                for (row in 0 until 3) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (col in 0..1) {
                                            val i = row + col * 3
                                            val f = forms.getOrNull(i) ?: continue
                                            Column(
                                                Modifier.weight(1f).clickable { clip.play(clips.getOrNull(i)) }.padding(4.dp),
                                            ) {
                                                Text(subject(i, tid), color = scheme.onSurfaceVariant, fontSize = 11.sp)
                                                Text(f, fontWeight = if (i == person) FontWeight.Black else FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                                NextButton { next() }
                            }
                        }
                        else -> {
                            Text(
                                when (mode) {
                                    "dictation" -> "LISTEN, TYPE THE MISSING WORD"
                                    "dictee" -> "DICTÉE: WRITE THE WHOLE SENTENCE"
                                    "produce" -> "SAY IT IN FRENCH"
                                    else -> "FILL THE GAP"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = scheme.onSurfaceVariant,
                            )
                            when (mode) {
                                "dictee" -> if (feedback == null) {
                                    Text("Écris tout ce que tu entends.", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    DicteeSentence(sentence, dicteeMarks)
                                }
                                "produce" -> Text("Comment dit-on « ${gloss.ifBlank { display }} » ?", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                else -> Text("$before" + "______" + after, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                            // The built-in clip only matches the built-in sentence, not a mined context.
                            val builtIn = sentence == base?.sentence
                            val hear = when {
                                mode == "produce" -> if (feedback != null) base?.audioWord else null
                                mode == "dictation" || mode == "dictee" -> if (builtIn) base?.audioSentence else null
                                else -> if (builtIn) base?.audioSentence ?: base?.audioWord else null
                            }
                            if (hear != null) TextButton(onClick = { clip.play(hear) }) { Text("Hear it") }
                            if (feedback == null) {
                                AnswerInput(typed, { typed = it }, ::submit)
                            } else {
                                FeedbackLine(feedback!!, if (mode == "dictee") match else expected)
                                Text(
                                    if (mode == "produce") "→ $produceTarget" else "$display — $gloss",
                                    fontWeight = FontWeight.Bold,
                                )
                                translation?.let { Text(it, color = scheme.onSurfaceVariant) }
                                NextButton { next() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DicteeSentence(sentence: String, marks: List<DicteeMark>) {
    val scheme = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            append(sentence)
            for (m in marks) {
                if (m.mark == "ok") continue
                val bg = if (m.mark == "accent") scheme.tertiary.copy(alpha = 0.3f) else scheme.error.copy(alpha = 0.22f)
                addStyle(SpanStyle(background = bg), m.start, m.end)
            }
        },
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun AnswerInput(typed: String, onType: (String) -> Unit, onSubmit: () -> Unit) {
    OutlinedTextField(
        value = typed,
        onValueChange = onType,
        label = { Text("Your answer") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = onSubmit, enabled = typed.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Check")
    }
}

@Composable
private fun FeedbackLine(feedback: GradeResult, expected: String) {
    Text(
        feedbackText(feedback, expected),
        color = when (feedback.result) {
            "right" -> MaterialTheme.colorScheme.secondary
            "almost" -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.error
        },
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun NextButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Next →", fontSize = 20.sp) }
}

@Composable
private fun MarkedSentence(before: String, match: String, after: String) {
    val mark = SpanStyle(
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        fontWeight = FontWeight.Bold,
    )
    val text = buildAnnotatedString {
        append(before)
        if (match.isNotEmpty()) withStyle(mark) { append(match) }
        append(after)
    }
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

private fun feedbackText(g: GradeResult, match: String): String = when (g.result) {
    "right" -> "Parfait !"
    "almost" -> when (g.why) {
        "accent" -> "Almost — watch the accent."
        "form" -> "Right word, wrong form: $match."
        "typo" -> "Typo — it's $match."
        "other" -> "That's another word. Here: $match."
        "article" -> "Almost — add le, la, un or une."
        "gender" -> "Almost — wrong article gender."
        else -> "Almost."
    }
    else -> "Not quite — it's $match."
}
