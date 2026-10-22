# Pluton — plan & progress

Goal: a French learning app that actually teaches (FSRS spaced repetition, production
not recognition, frequency order, comprehensible input) and is fun (combo/points, real
content unlocks, short runs). Local-first: the PWA now, native iOS/Android next (see "Native rewrite").

**Target (user, 2026-10-18): a learner can reach C1 with this app.** That makes the app the hub for all of
C1's skills, not a vocab drill. See "Road to C1" below.

**Progression (user, 2026-10-18): a real path, not a pile of options, and every feature and reading stays open
regardless of it.** Le Chemin = 10 stages (A1→C1), goals per stage (words learned, verb tables of the stage's tense,
its story chapter (+ a classic from B2), listening minutes; talk/speak/write = bonus ★). Targets come from measurement:
the classics only reach 95% coverage at 3.2k–7k words, so A1–B1 reading is Pluton's story, written per stage and
checked against the stage's vocabulary. The stage reached is stored (forgetting never demotes); any later stage can be
made current from the map. Nothing is gated: chapters, classics, verbs (Verbs tab: Practice any tense), all tabs.

## Decisions
- **Native apps: Swift/Kotlin, not a WebView** (user, 2026-10-21). Supersedes the Capacitor decision below; see
  "Native rewrite" for the architecture. One shared Kotlin Multiplatform core + SwiftUI/Compose UIs.
- **PWA + Capacitor for the native apps** (user, 2026-10-18: wants real Android/iOS apps; superseded 2026-10-21). One codebase: the
  same Vite/Svelte build is the web PWA and is bundled into the two native projects (`android/`, `ios/`).
  Capacitor over Tauri: the app is 100% WebView APIs (IndexedDB, fetch, WebAudio), so a Rust shell buys nothing,
  and Capacitor ships the plugins this app needs (share/files for backup, browser for external links) with SPM on
  iOS, so the iOS project can be generated and versioned from Linux. iOS still needs a Mac + Xcode to compile and
  an Apple Developer account to ship; Android builds anywhere with the SDK + JDK 21.
- **Vite + Svelte 5 (plain JS)** — least ceremony.
- **Raw IndexedDB with a ~30-line wrapper** instead of Dexie — 4 stores, get/put/getAll is all we need.
- **Append-only review log** (`log` store) next to derived card state — sync later = union logs + replay FSRS.
- **ts-fsrs** for scheduling. One card per lemma; exercise mode alternates cloze/dictation.
- **Data**: Lexique 3.83 (frequency + form→lemma), Tatoeba fra-eng (sentences), kaikki Wiktionary (glosses),
  Piper `fr_FR-siwis-medium` TTS → mp3. Built by `data/fr/build.py`, output in `public/data/fr/` (generated, gitignored).
- **Sentence choice**: fewest words beyond the first 300 lemmas or ranked after the target, names count as hard,
  target in one of its 3 most frequent forms, length near 7. Lexique form frequency is summed across POS rows
  (otherwise "suis" maps to suivre).
- **Multi-language later** (user, 2026-10-18): French bits live in `src/lib/lang.js` + `data/fr/`; DB `pluton-<code>`.
  No picker until a second language's data exists.
- **Mascot**: Pluton, Poe's black cat (Baudelaire's name), original SVG, moods idle/happy/sad/fire/sleep.
- **Honest numbers**: "known" = FSRS stability ≥ 21d; words added but never reviewed are "queued" and do not
  count toward a text's coverage (else bulk-queuing would fake an unlock).
- **Audio** goes through one AudioContext unlocked on the Start tap (iOS autoplay rules).
- **Grading**: exact → good; accent-only / one-char typo that isn't a real word / right lemma wrong form → "almost" (Hard);
  anything else → Again. Typo leniency only for non-words, because French endings (-e/-es/-ent) differ by one char.
- **In-run requeue** for cards due within 20 min (same idea as Anki's learn-ahead limit).
- **Exercise rotation**: cloze → dictation → English→French recall (every 3rd rep, graduated content words only,
  first-letter hint). Nouns must come with an article; wrong gender = "almost" (French's classic weak spot).
- **Name**: Pluton (user approved 2026-10-18). Repo: github.com/VykosMolt/pluton, public since 2026-10-18 after the
  commit-email rewrite (user chose noreply); live at https://vykosmolt.github.io/pluton/; deploy = `npm run deploy`
  → gh-pages (appends, never force-pushes).
- **AI = bring your own key, no server** (user wants Claude/ChatGPT/DeepSeek): subscriptions can't be used by other apps,
  so API keys; Anthropic via the official SDK (default claude-opus-5-5, effort low for chat / medium for marking,
  server-side refusal fallback where supported), others via the OpenAI chat format. Key never in backups.
- **Norman Bridge** (user's idea): cognates only when the English word appears in the French word's own Wiktionary
  definition (so false friends can't match); loans from English excluded; curated faux amis taught instead.
- **Comprehension is assessed, not self-reported** (2026-10-21): each story chapter has 3 hand-written questions,
  and the path's chapter goal needs two thirds right. The text stays open; only the goal waits. Imported/classic
  texts get an optional AI retelling check (BYOK), which judges meaning, not grammar.
- **Speaking starts with record → listen → compare** (2026-10-21): the Speak tab records with MediaRecorder, plays
  the learner's clip beside Pluton's TTS, and uses browser speech recognition for word-level feedback where it
  exists. No pronunciation scoring yet; that needs a real ASR/phoneme model.
- **Unlimited graded input** (2026-10-21): the AI writes 150–250-word stories at the learner's level from their known
  words plus a few they are learning; saved like any text, with browser-voice read-along.
- **Sync pulls replay the log** (2026-10-21): two devices can review the same card offline; the pulled card is rebuilt
  by replaying the unioned review log deterministically (no fuzz), not by trusting one device's last write. Text rows
  compare `updated` regardless of which device created them, so edits/deletes of another device's text apply.

## MVP checklist
- [x] data/fr/build.py → words.json (10k lemmas, 9816 glossed; 1930 with sentence+translation+audio), forms.json, 3.7k mp3s
- [x] Review run: intro new words, cloze + dictation, lenient grader, FSRS, combo/points/sfx/confetti, summary
- [x] Reader/library: paste text or import .srt/.vtt, coverage %, tap word → add / mark known, queue-to-unlock
- [x] Home: streak, learning/known/due tiles, next-unlock card, backup/restore
- [x] PWA: manifest, icons, service worker
- [x] Checks: `npm test` (10 pass), build.py self-asserts, `npm run smoke` (full run + library + reload in Chromium)

## Road to C1
C1 (CEFR): understands long, demanding texts and implicit meaning, follows native-speed speech on unfamiliar
topics, speaks and writes fluently and precisely with complex grammar. Rough scale: 1000+ hours of contact,
recognition vocabulary well past the top few thousand words (estimates vary with how words are counted: treat
the numbers below as waypoints, not CEFR certification).

| Skill | What C1 needs | Status |
|---|---|---|
| Vocabulary | ~8k most frequent lemmas, spoken + written register | **8k deck** (7351 with sentence+audio), ranked by film+book frequency |
| Grammar: verbs | all common tenses productive; passé simple / subj. imparfait receptive | **Conjugation drills**: 59 verbs × présent, passé composé, imparfait, futur, conditionnel, subjonctif (auto-introduced in order; any tense practisable from the Verbs tab); not yet: passé simple recognition, pronominal verbs (se souvenir…) |
| Grammar: rest | pronouns (y/en, order), relatives (dont, lequel), agreement, prepositions, register | not yet: sentence-transformation drills, LLM-checked |
| Reading | unadapted texts: news, novels | **Pluton's story** (10 graded chapters) + **classics shelf** (7 texts) with read-along, plus your own texts; **chapter comprehension quizzes** (a pass counts the chapter as read) and an **AI retelling check** for imported/classic texts; **AI-generated graded stories** for unlimited level-matched input; not yet: more static classics, current news |
| Listening | native speed, varied speakers | **Listen tab**: own media + subtitles, tappable current line, replay/auto-pause, hours listened; **dictée** for known words; generated/user texts read aloud with the browser voice |
| Speaking | fluent, spontaneous | **Speak tab**: read a sentence aloud, record it, compare with Pluton, and (where the browser has speech recognition) see word-level feedback; **Talk** (BYOK AI) gives conversation at the learner's level with inline corrections; not yet: pronunciation scoring, voice roleplay scenarios |
| Writing | clear, structured, precise | **Writing coach** (BYOK AI): level-matched prompts, explained corrections, level estimate; each correction can become a practice card; dictée trains spelling/agreement |
| Bonus | English as a bridge | **Norman Bridge**: Poe/Lovecraft in English, French-derived words woven back into French; sound-change clues; faux amis |

Phases: (1) vocab + verbs [done] → (2) listening player + reading shelf [done, v1] → (3) AI layer, bring-your-own-key
from the browser, no server [done, v1: talk + writing] → (4) Le Chemin + Pluton's story [done] → (5) assessed
comprehension, speaking practice, generated graded input [done 2026-10-21] → next: grammar drills beyond verbs,
writing errors → cards, pronunciation scoring, AI roleplay scenarios / a multi-chapter personalised campaign, a
per-skill level estimate from real performance.

## Not built yet
- Grammar beyond verbs: pronouns (y/en, order), relatives (dont, lequel), agreement, prepositions, register.
- Verbs: passé simple recognition (C1 reading), pronominal verbs, more than 59 verbs.
- Speaking: pronunciation scoring (Whisper or browser), roleplay scenarios (café, interview…). Recording and
  playback work wherever MediaRecorder does; live speech recognition is still absent in Firefox/Zen and native WebViews.
- A connected multi-chapter AI story campaign (on-demand graded stories are built; the hand-written 10-chapter story
  is still the same for everyone).
- Per-skill level estimate from real performance (the path now quizzes chapter comprehension, but listening/talk/write
  goals still count activity).
- Reading: more static classics, current news.
- Second language + picker; precache all audio for full offline listening.
- Native apps: the Swift/Kotlin rewrite is in phase 0 (see "Native rewrite"); the Capacitor apps remain the
  store-ready stopgap. Still missing on native: the real deck/FSRS/storage, audio/speech, store accounts/listing.
- Not verified yet: the native apps on a real device (no device/emulator here, and iOS cannot be built on Linux),
  real AI keys (mock only), Safari/WebKit engine (can't run on Arch), native-speaker review of the story.

## Accounts / sync (2026-10-18)
Done: grammar topics + mistake cards (commit abb7248e). Accounts (committed; verified against the local stack):
- `src/lib/sync.js`: email-code sign-in (Supabase OTP), pull by `synced_at` (60 s overlap, 1000-row pages), push via
  the `push(lang, batch)` RPC (server keeps the newer `updated_at`). Card rows are last-write-wins on the server but
  rebuilt from the unioned log on pull (2026-10-21); texts last-write-wins by `updated`, whatever device created them;
  log keyed `<device>:<n>`; texts get `<device>:<id>` keys; deletes are tombstones (meta `_tomb`);
  meta keys stamped in `_stamps`; the AI key and sync bookkeeping never leave the device or enter backups.
- `src/lib/stats.js`: each device writes only its own `stats:<device>`; totals combine (sums, maxes, union of read
  texts, streak from the union of run days). `records` has a `lang` column, so more languages share one table.
- Settings → Account (`src/Account.svelte`), "I have an account" on the welcome screen. Hidden without
  VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY.
- Unit tests for the merge (sync.test.js, stats.test.js).
Verification: on this machine Docker's published ports reset every host connection (the containers answer each other
fine), so the account smoke runs in a container on `supabase_network_pluton` (image `pluton-smoke:local`: node 22 +
Playwright Chromium + ffmpeg) with a TCP forwarder for 127.0.0.1:54321/54324.
Left: hosted project needs the user's Supabase account (`npx supabase login`, link, `db push`, site URL
https://vykosmolt.github.io/pluton/, real SMTP), then set the two VITE_ variables for `npm run deploy`.

## Done (2026-10-21) — comprehension, speaking, more input
- [x] Comprehension: 3 hand-written questions per story chapter; the path's chapter goal needs a pass (the text stays open)
- [x] AI retelling check for imported/classic texts (optional, BYOK)
- [x] Speak tab: record → play back → compare with Pluton; browser speech recognition gives word-level feedback where available
- [x] Generate a graded story at the learner's level from their known words (BYOK), saved to the library
- [x] Sync: rebuild a pulled card from the unioned review log instead of trusting one device's last write
- [x] Sync: edits and deletes of a text created on another device are no longer dropped
- [x] Tests and smoke updated

## Native rewrite (2026-10-21) — Swift/Kotlin, decision
**User decision:** the product should be native iOS + Android apps, not a WebView. The Capacitor projects
(`android/`, `ios/`) remain the shipped stopgap; the PWA stays live, but new features go native.

Architecture: **one shared core, two native UIs.** Two independent Swift/Kotlin ports would duplicate the
learning logic; Kotlin Multiplatform avoids that while keeping the UI native on both platforms.
- `native/shared/` — Kotlin Multiplatform core: tokenizer, coverage, grading, run queue, FSRS, path, sync merge,
  tutor prompts. Pure Kotlin; JVM tests run on this Linux machine, Android tests on the emulator/device, iOS
  tests on a Mac/CI.
- `native/androidApp/` — Jetpack Compose UI, Kotlin.
- `native/iosApp/` — SwiftUI UI, Swift, consuming the KMP framework. **Not buildable on this machine** (no Xcode;
  Kotlin/Native iOS targets need macOS). Build on a Mac or a macOS GitHub Actions runner; shipping needs the
  Apple Developer Program ($99/yr). Android Play is $25 one-time.
- Data stays `data/fr/build.py` output. JSON is bundled/downloaded; the 181 MB audio becomes a Play asset pack /
  iOS on-demand resource in the packaging phase.
- Storage: SQLite on both platforms (Android Room or SQLDelight, iOS SQLDelight/GRDB) — chosen in phase 2.
  Speech/audio/AI HTTP are platform modules; prompts and domain rules are shared.

Phases:
0. [done 2026-10-21] Toolchain + KMP scaffold + a grading slice with tests + Android Compose shell; debug APK built
   here (12 MB, no data/audio yet). `native/README.md` has the build commands. Shared tests: 9 JVM tests green.
1. [done 2026-10-21] Port tokenizer/coverage, run queue, FSRS; parity fixtures generated from `ts-fsrs`; Android
   review run with the real 20k-word deck bundled (in-memory cards for now). 26 shared JVM tests green: grading (9),
   tokenizer/coverage (4), queue/run flow (6), data decoding (1), FSRS parity (6 sequences). Native scheduling is
   deterministic (no fuzz) to match the fixtures; add a seeded fuzz only if due-date variety matters.
2. [done 2026-10-21] Persistent local store: SQLDelight cards + append-only review log + meta stats; the Android app
   saves each answer in one transaction and reloads cards/new-word budget on start. 29 shared JVM tests.
   Still to do from the original phase 2 scope: settings/path and Android vocab/verbs/grammar parity.
3. [in progress] Reader, Listen, Speak. Reader v1 + story read-along + classics shelf done (coverage,
   tap-to-look-up/add/known, queue-to-unlock, chapter quiz, read history, sentence audio with highlighting; the 7
   French classics use the same reader, with 835 more clips). Speak v1 done: device TTS, record/playback via
   MediaRecorder, Android speech recognition with word-level dictation marks (no pronunciation scoring). Still:
   user texts, the Listen media-player tab, and iOS.
4. iOS: Xcode project (XcodeGen spec), SwiftUI shell, KMP framework; build/test in macOS CI; device build with
   the Apple account.
5. Sync (Supabase), AI (native HTTP/SSE), store packaging, privacy/account forms.

Toolchain verified here: JDK 21 (`~/.local/opt/jdk-21.0.12.1+1`), Android SDK 36 (`~/Android`), Gradle 8.14.4,
Kotlin 2.4.21, AGP 8.13.0, Compose BOM 2026.03.01, activity-compose 1.11.0. The September 2026 BOM needs AGP 9.1
and compileSdk 37; stay on the known AGP 8.13/compileSdk 36 line until the native apps need the newer SDK. No
emulator/device, so the APK is built but not launched.

## Progress log
- 2026-10-18: MVP built and verified end to end (see checklist).
- 2026-10-18: renamed to Pluton, pushed (private). Added recall mode with gender grading, levels, run recap,
  live streak, About/credits, iOS audio session, audio-fallback warnings. Smoke now fakes +3 days for a review run.
- 2026-10-18: C1 target set. Deck → 8k (film+book frequency), conjugation drills (59 verbs, Lexique tags validated:
  hand-written être/avoir, stem agreement for regular-ending tenses), classics shelf, Listen tab, dictée, Words tab,
  Road to C1 panel. TTS: capped ONNX threads per worker (was ~190 threads on 24 cores), atomic clip writes.
- 2026-10-18: commit emails rewritten to noreply (needs one force-push by the user: hook blocks it for Claude).
  AI tutor (talk + writing, 5 providers + custom), Settings, pace, Norman Bridge (5 Poe/Lovecraft texts, 7.4k
  cognates), welcome + placement, deploy script. Checks: 25 unit tests, 30 smoke checks.
- 2026-10-18: Le Chemin (path.js, Path.svelte), Pluton's story (10 chapters), read-along audio for the story and
  classics (1019 sentence clips), "I read it", stage celebration, Verbs browser, tokenizer: mid-sentence capitals
  are names ("Paris" ≠ pari), euphonic -t- skipped. Checks: 29 unit tests, 34 smoke checks.
- 2026-10-18: history rewritten to noreply (user force-pushed), repo PUBLIC, live at https://vykosmolt.github.io/pluton/
  (gh-pages via `npm run deploy`). Live check found offline broken on first visit; fixed (precache on install,
  ignoreVary, no redundant fetches). Live site verified: SW, manifest/icons, audio, read-along, offline reload.
  Smoke: 35 checks.
- 2026-10-18: smoke runs on Firefox too (BROWSER=firefox): 35/35 on Firefox 157 (Zen's engine) and Chromium 156.
  Fixed Safari-incompatible stream reading in the OpenAI-compatible client (no `for await` over ReadableStream).
- 2026-10-18: native apps scaffolded with Capacitor 8 (`android/`, `ios/`, Swift Packages, bundle id
  `io.github.vykosmolt.pluton`). Web build wrapped unchanged; service worker skipped on native; backup uses the
  share sheet via Filesystem+Share (blob downloads don't work in a WebView); external links open in the system
  browser (Browser plugin); icons/splashes generated from the web icon by `scripts/mobile-assets.sh`. Verified:
  37 unit tests, `npm run smoke` (Chromium, all checks), production build + `cap sync` for both platforms, and an
  Android debug APK from a clean SDK install (Gradle 8.14.3, AGP 8.13, JDK 21, SDK 36): `BUILD SUCCESSFUL`,
  149 MB, package `io.github.vykosmolt.pluton`, target SDK 36, all 17,609 audio clips inside. iOS cannot be
  compiled on Linux: the Xcode project is generated and `cap sync ios` resolves the SPM packages, but a Mac is
  needed for the first build.
- 2026-10-21: comprehension, speaking, more input, sync fixes. Each story chapter has 3 hand-written comprehension
  questions (`data/fr/story.json`, validated by build.py); the path's chapter goal needs two thirds right, and the
  reading screen shows the score and explanations. Imported/classic texts get an optional AI retelling check.
  Reader's ✨ Story generates a 150–250-word story at the learner's level from their known/learning words (BYOK),
  saved as a normal text; saved texts now get sentence offsets and read aloud with the browser voice. New Speak tab:
  record (MediaRecorder) → play back → compare with Pluton, plus browser speech-recognition word marks where
  available. Sync now replays the unioned review log deterministically on pull (both devices' reviews count, even
  when one card row wins last-write-wins), and text rows are compared by `updated` regardless of which device created
  them, so another device's edits/deletes apply, and a newer local deletion beats an older remote edit. Checks: 46
  unit tests, 46 smoke checks on Chromium (including the chapter quiz, generated story + retell, speaking
  record/playback, accounts); Firefox 157 passes the same suite with the speaking check skipped. Native mic
  permissions declared; device verification still pending.
- 2026-10-21: native rewrite started (user decision: Swift/Kotlin, not a WebView). `native/` is a Kotlin
  Multiplatform project: `shared/` holds the first core slice (`grade`, `gradeQuiz`, `levenshtein`, 9 JVM tests),
  `androidApp/` is a Compose shell whose sample cloze calls the shared grade function. Toolchain: JDK 21, Android
  SDK 36, Gradle 8.14.4, Kotlin 2.4.21, AGP 8.13.0, Compose BOM 2026.03.01. Verified: `:shared:jvmTest` green,
  `:androidApp:assembleDebug` builds a 12 MB debug APK (package/version/SDK checked with aapt2); no device/emulator,
  so it has not been launched. iOS targets are registered only on macOS; no Xcode here, so iOS is source-only until
  a Mac or macOS CI runner. Next: tokenizer/coverage, run queue, FSRS parity fixtures, real deck.
- 2026-10-21: native phase 1. Shared core now has the tokenizer/coverage/unlock, run queue with in-run requeue,
  FSRS-6 scheduler, word/forms decoding, and the cloze utilities; the Android app loads `words.json` + `forms.json`
  (copied into assets by Gradle from `public/data/fr`), shows Home, and plays a real 5-new-word run with grading and
  FSRS scheduling. Verified: `:shared:jvmTest` 26 tests, 0 failures (FSRS parity is generated from `ts-fsrs` 5.4.2
  by `native/tools/fsrs-fixtures.mjs`); `:androidApp:assembleDebug` builds a 14 MB APK with the 5.3 MB deck inside.
  No device/emulator was available at build time; progress is in-memory (SQLite is phase 2). iOS still source-only.
- 2026-10-21: native phase 2 (persistence). Added SQLDelight 2.4.1 (default SQLite 3.18 dialect, so the schema uses
  `INSERT OR REPLACE` rather than UPSERT): `cards` derived state, append-only `reviews`, and `meta` for the per-day
  new-word budget. `Store.saveAnswer` writes the card and its review in one transaction; the Android app loads saved
  cards at startup, saves every answer, and survives restart. Verified: 29 shared JVM tests (3 new store tests:
  round-trip, upsert+append, day rollover), Android debug APK builds at 14 MB. Emulator: KVM is available and the
  emulator package is installed, but the API 36 system-image download stalled on this network, so the app still has
  not been launched on a device/emulator. No audio/reader/path yet; iOS still source-only.
- 2026-10-21: native reader v1. Shared: `decodeStory`, `sentenceAt`, Store `addCard`/`markKnown`/`unmark`/
  `deleteCard` (cards outside runs don't write a review), `Stats.read` kept across day changes, `addRead`. Android:
  bottom navigation Home/Read; the 10 story chapters list coverage; text is coloured by status and tappable for
  gloss/add/known; one tap queues the words needed for 95% coverage; the chapter quiz uses the shared `gradeQuiz`,
  and a pass marks the chapter read in SQLite. Verified: 33 shared JVM tests (new: sentenceAt, story decode, store
  reader actions, read-history rollover), APK 14 MB with story.json inside. Still not launched: the API 36 emulator
  image download stalled on this network. No audio/classics/user texts/Listen/Speak yet; iOS untouched.
- 2026-10-21: native story read-along. `StoryChapter` now exposes the build.py `[start,end,audioId]` sentence rows;
  `Stats.listened` accumulates seconds across days; the Gradle `syncStoryAudio` task copies only the 155 clips the
  story references (2.1 MB) and keeps mp3 assets uncompressed for `MediaPlayer.openFd`. The reader has a
  Listen/Stop button, plays the chapter sentence by sentence, highlights the current sentence, counts real playback
  time, and releases the player on stop/leave. Verified: 34 shared JVM tests, 0 failures; APK builds at 16 MB with
  the story audio, and the 155 clips are Stored (not deflated) in the APK. Still not launched: emulator image
  download stalled. No classics/user texts/Listen tab/Speak; iOS untouched.
- 2026-10-21: native Speak v1. Shared `gradeDictee` (word alignment; accent-only and one-slip leniency) and
  `Stats.spoken`. Android Speak tab: French TTS for the model sentence, MediaRecorder record/playback (15 s cap,
  runtime mic permission), and Android SpeechRecognizer where available; the recognized transcript is graded and
  the target sentence gets word-level ok/accent/bad marks. Sentence pool comes from saved cards, else the deck.
  Verified: 35 shared JVM tests, 0 failures; APK 16 MB builds. Still not launched on a device (emulator image
  download stalled); the TTS/recording/recognition path is untested at runtime. No pronunciation scoring yet
  (the recognizer returns words, not phonemes); classics/user texts, the Listen tab and iOS remain.
- 2026-10-21: native launch verified on Robolectric. Added `AppLaunchTest` (`createAndroidComposeRule<MainActivity>`,
  SDK 35, `isIncludeAndroidResources`): the real Activity loads the 20k-word deck + story from assets, opens the
  SQLDelight database, renders Home with Read/Speak tabs, and the Read tab lists "Chapitre 1 · Le Mur". 2/2 tests
  pass (~18 s after the first dependency download; the first run took 36 min because Robolectric's Android runtime
  had to download). This exercises the Android runtime, assets, DB and Compose on the JVM, but not a device:
  TTS/MediaRecorder/SpeechRecognizer/MediaPlayer playback are still unverified. Shared tests 35/35; APK 16 MB.
- 2026-10-21: native classics shelf. `ShelfText`/`decodeShelf`; the 7 French classics (835 clips, 17.2 MB) are
  bundled, and the Reader lists story + classics through the same coverage/tap/queue/read-along path. Classics have
  no quiz, so "I read it" marks them. Fixed a Gradle collision: `syncData` was a `Sync` and deleted the `audio/`
  directory written by the audio task; it is now a `Copy`. Verified: 36 shared JVM tests, 0 failures; Robolectric
  `AppLaunchTest` 2/2 (Home + tabs, Read list includes "La Parure"); APK 33 MB with 990 audio clips (19.3 MB, all
  Stored/uncompressed). No physical-device launch; user texts, Listen tab and iOS remain.
- 2026-10-21: native user texts. SQLDelight schema v2 (`texts` table + `1.sqm` migration): `Store.texts`/`addText`
  (blank title falls back to the first 40 chars)/`deleteText`. The Reader lists "Your texts" with a bottom-sheet
  add form (title optional, body required), opens them through the same coverage/tap/queue path (no quiz, no audio
  yet), stores read progress, and deletes them behind a confirm dialog. Verified: 38 shared JVM tests, 0 failures
  (new: text round-trip/order/delete, v1→v2 migration against a hand-built v1 DB); Robolectric 2/2 with the
  "Your texts"/"+ Add" section asserted; APK builds. Still no device launch; per-text audio, Listen tab, path/
  settings, verbs/grammar and iOS remain.
- 2026-10-21: native Listen tab. Shared `parseCues`/`cueAt` ported from `src/lib/logic.js` (SRT + VTT, tag stripping,
  cue sorting, last-line-through-gaps lookup) with 3 tests. Android: fourth tab; the system file picker chooses a
  local audio/video file and an .srt/.vtt; `MediaPlayer` plays it (audio only — video files play their audio track,
  no picture), the current line is coloured by word status and tappable through a `WordSheet` extracted from the
  Reader, with prev/replay/next, 0.75–1.25× speed, and pause-after-each-line driven by the player clock; playback
  seconds feed `Stats.listened`. Verified: 41 shared JVM tests, 0 failures (cue parser, store texts, migration);
  Robolectric 3/3 (new: Listen tab shows both pickers); APK builds. The picker/playback path is not exercised
  on-screen (no device/emulator). Path/settings, verbs/grammar, video rendering, pronunciation scoring, sync and
  iOS remain.
- 2026-10-21: native conjugation/grammar reference. Shared `ConjTable`/`GrammarTopic` decoding, `conjKey`/`gramKey`/
  `parseConj`, the six French tenses with their wording, and `subject()` — keys match the web app's card lemmas.
  Gradle copies `conj.json`/`grammar.json` and the 2,063 conjugation clips into assets. Android: fifth "More" tab
  with Verbs (search, all 59 tables, per-cell audio, null cells skipped) and Grammar (15 topics, revealable
  exercises); no Practice buttons yet, since the run screen only handles word cloze. Verified: 44 shared JVM tests,
  0 failures (new: reference decode/keys/subjects); Robolectric 5/5 (new: open More→Verbs→être shows "suis"/
  "présent"; More→Grammar→object pronouns shows Show answer); APK 43 MB with 3,053 clips. Still no device launch.
  Next: conjugation/grammar practice in runs, Path, settings, sync, iOS.
- 2026-10-21: native conjugation/grammar practice + Le Chemin. Shared `gradeConj` (pronoun stripping, être participle
  agreement leniency) and `gradeGram` (accepted alternatives, lost-apostrophe/accent leniency), `newConj`/
  `newGrammar` with CONJ_PER_RUN=2, UNLOCK_NEXT_TENSE=8 and the GRAMMAR_FROM vocabulary gates, `playable` checks for
  reference cards, and buildRun's table/topic slots. Path: `Path.kt` ports STAGES, goal labels, bonus-goal rules,
  focus() and metrics from src/lib/path.js. Android: Practice buttons in Verbs/Grammar (cards enter the store and the
  run queue), RunScreen conj intro (full table, cell audio) + typed cell and grammar intro/exercise branches with the
  shared graders and the same FSRS save path; Home shows the current stage/next goal; More has Le Chemin with goal
  progress. Classic read ids are now titles, matching the web. Verified: 54 shared JVM tests, 0 failures (new:
  gradeConj/gradeGram, conj/gram unlock rules, path stages/bonus/focus/metrics); Robolectric 7/7 (new: More→Path
  shows Le Mur and Learn 75 words; Practice a table, then Home→run shows NEW TABLE). APK 43 MB. No device launch;
  settings/AI, sync, video and iOS remain.
- 2026-10-21: native exercise modes + settings. Shared `modeFor`, `produceAnswer`, `gradeProduce` (article/gender
  leniency) ported from logic.js; `gradeDictee` now drives whole-sentence dictée in runs. Android RunScreen: cloze,
  listen-and-type, English→French recall (article required for nouns) and full-sentence dictée with word-level
  accent/bad marks; sentence/word clips auto-play where the web auto-plays, plus a replay button; review log records
  the real mode. Settings: pace (perRun/perDay in meta, used by buildRun), transactional JSON backup/restore via the
  system file pickers (cards + reviews + texts + stats; no credentials), licences. Verified: 59 shared JVM tests
  (new: produce grading, modeFor); Robolectric 8/8 (new: Settings shows pace/backup/licences); APK 43 MB.
- 2026-10-21: native AI Talk (BYOK). Shared `Tutor.kt` ports `levelOf`, `chatSystem` and `splitCorrection` with tests;
  `Stats.talked` + `Store.addTalked` feed Le Chemin's talk goals. Android `Ai.kt`: provider list (Claude, OpenAI,
  DeepSeek, OpenRouter, Ollama, custom), private prefs storage (Android backup off, excluded from JSON backup),
  readiness rules, error mapping, and `aiChat`. Claude uses the official `com.anthropic:anthropic-java:2.34.0` SDK
  (`claude-opus-5-5`, `maxTokens` 1024, effort low, refusal check, non-streaming); OpenAI-compatible providers use
  chat-completions over `HttpURLConnection`. Core-library desugaring for the SDK's `java.time` on minSdk 24; cleartext
  allowed only for localhost/127.0.0.1/10.0.2.2. UI: Settings→AI tutor (provider, key, model, server URL, Test &
  save, Disconnect) and Speak→Talk with Pluton (level-aware prompt, learning words recycled, correction line styled,
  error keeps the draft; no-key state links to Settings); conversation is in memory only. Verified: 62 shared JVM
  tests, 0 failures; 14 Android JVM tests, 0 failures (5 new AI tests: body/parse/readiness/errors; Robolectric now
  9/9 incl. Settings AI section and Talk no-key state); APK 55 MB. Not verified: any real provider call (no API key
  used, no device); no streaming, writing correction, story generation, retell, sync or iOS yet.
- 2026-10-21: native AI writing correction. Shared `WRITING_PROMPTS`/`suggestedWritingPrompts`, `writeSystem`, and
  `WritingResult`/`WritingError` with `parseWriting` (first/last braces, tolerates fences/prose, rejects blank
  corrections); `Stats.written` + `Store.addWritten` feed Le Chemin's writing goals. Android: Speak now has three
  modes (Practice / Talk / Write); Write offers level-filtered prompts, a multiline essay field, loading/error states,
  and a result view with corrected text, original→fix pairs, why/kind, feedback and estimated level, plus "Write
  another"/"Revise this one"; no-key state reuses the shared NoKeyCard. Verified: 64 shared JVM tests, 0 failures;
  15 Android JVM tests, 0 failures (10 Robolectric incl. the Write no-key state); APK builds. Not verified: a real
  correction (no API key/device). Remaining AI parity: story generation and reader retell; then sync and iOS.
- 2026-10-21: native AI story generator + reader retell. Shared `readerSystem`, `RETELL`, `parseStory` (title + >=80
  chars) and `parseRetell` (score 1-5, non-blank feedback), all sharing a tolerant first/last-braces JSON extractor;
  `aiChat` gained a Claude effort parameter (low default, medium for retell). Android Reader: "✨ Story" opens a
  generation panel (no-key state links to Settings), sends known/learning word lists, saves the result through
  `Store.addText` and opens it as a user text; classics and user texts get a "Did you understand it?" retell card
  (no-key state, 1-5 score + feedback, try again), while story chapters keep their quiz and do not show it. Verified:
  66 shared JVM tests, 0 failures; 17 Android JVM tests, 0 failures (12 Robolectric incl. story panel no-key state
  and retell presence/absence); APK builds. Not verified: real generation/retell calls (no key/device).
- 2026-10-21: native TTS read-along for user texts. Shared `sentencesOf` (sentence offsets, leading-quote/`—` trim,
  numbers-only skipped) ported from src/lib/logic.js with tests. Android Reader: when a text has no recorded clips,
  a French `TextToSpeech` engine reads it sentence by sentence (waits on UtteranceProgressListener, cancels through the
  existing play job, highlights the current sentence, counts listening seconds); the Listen button appears for those
  texts and is enabled once the engine is ready; a missing French voice shows a clear message. Verified: 67 shared
  JVM tests, 0 failures; 18 Android JVM tests, 0 failures (new Robolectric: add a user text through the sheet, open
  it, Listen button present); APK builds. TTS itself is still unverified on a device. Remaining: video rendering,
  pronunciation scoring, sync, iOS.
- 2026-10-21: native deck browser. More→Words: word cards only, newest first, search by French lemma or English gloss,
  status filters (all/learning/queued/known), per-word memory-strength bar (log scale to KNOWN_DAYS), next-review
  label (due now / later today / in N d / not started), tap to play the word clip; counts in the header. LazyColumn so
  a large deck stays cheap. Verified: 67 shared JVM tests, 0 failures; 19 Android JVM tests, 0 failures (14
  Robolectric incl. the Words browser; hub-card tests now scroll before tapping because the hub grew); APK builds.
- 2026-10-21: native account sync. Shared `Sync.kt` ports the web merge rules (`plan`, `collect`, `replayPulled`,
  deterministic `replayCard`) with tests ported from sync.test.js plus a two-device convergence test; Store gains
  CardState.updated, TextRecord.updated/remoteKey, sync state (`_sync` with a stable device id), tombstones, pulled
  review keys and a transactional `applySync`; SQLDelight schema v3 (`2.sqm`: remote_key columns, unique indexes,
  tombstones). Android `SyncJson` maps the web shapes (snake_case fsrs + numeric state + ISO dates); `SupabaseClient`
  does email OTP, token refresh, paginated pull, batched `push` RPC, logout and `delete_account`; `SyncEngine` pulls,
  replays, applies, then pushes. More→Account: server URL/key, sign-in, Sync now, sign out, delete account; sync also
  runs at sign-in and reloads cards/read/stats. Not synced: stats, pace/settings, AI keys, chat/essay drafts.
  Verified: 79 shared JVM tests, 0 failures; 25 Android JVM tests, 0 failures, including a live end-to-end test
  against the running local Supabase (two JDBC stores, OTP code read from Mailpit, sign-in, push/pull, second
  device answers, both converge, account deleted; the test skips when `.env.smoke.local` or the stack is absent).
  APK builds. Remaining: iOS.
- 2026-10-21: native pronunciation match + video. Speak shows `dicteeScore` for the recognized transcript (0-100,
  full credit per heard word, 0.7 accented, 0 missing/other; labelled word recognition, not phonemes) with tests.
  Listen detects video MIME from the picker and renders a VideoView, while audio keeps MediaPlayer; playback position,
  play/pause, seek, auto-pause and subtitle sync read from whichever player is active; speed stays audio-only.
  Verified: 79 shared JVM tests (score cases), APK builds; video playback itself is unverified without a device.
- 2026-10-21: iOS app scaffold. Shared: iOS framework config (`PlutonCore`, static) and `iosMain` bridge
  (`IosBridge` decoding/coverage/path/grading/store mutations, `IosRun` queue driver) plus SQLDelight native driver.
  `iosApp/`: XcodeGen spec, Info.plist (mic/speech/local-network), resource sync script, and a SwiftUI app with
  Home + Run (intro/cloze/dictation/produce/dictée), Read (library, coverage, tap-to-look-up via custom URL links,
  quiz, user texts, TTS), Speak (TTS, AVAudioRecorder, SFSpeechRecognizer + gradeDictee match, Talk with BYOK AI via
  URLSession/Keychain), and More (Path, Words, Verbs, Grammar, Settings pace/AI). **Unverified**: no macOS/Xcode or
  Swift toolchain here, Kotlin/Native iOS targets cannot be built on Linux; this is a reviewed scaffold that will
  need interop fixes on a Mac. Known gaps: Listen, account sync, story/retell, writing mode, conj/grammar drills.
- 2026-10-21: iOS build/install path from Linux. Added `.github/workflows/ios.yml`: a macOS runner links the shared
  static framework (`linkReleaseFrameworkIosArm64`), generates the Xcode project, builds unsigned and uploads
  `Pluton-unsigned.ipa`. `project.yml`'s Gradle pre-build phase can be skipped in CI (`PLUTON_SKIP_GRADLE=1`) because
  CI prebuilds and links the framework directly. Added `iosApp/INSTALL.md`: TrollStore (iOS 14.0b2–16.6.1, 16.7 RC,
  17.0) for permanent no-computer installs; iloader (Linux, free Apple ID, USB) + SideStore for iOS 15+ including
  26/27 (7-day certificate); TestFlight for a paid account. Kotlin iOS bridge was type-checked on the JVM via a
  temporary copy (no native driver); Swift files pass a tree-sitter syntax parse, but no Swift type-check or Xcode
  build has run yet. First CI run is the remaining verification step.
- 2026-10-21: Norman Bridge. Shared `Bridge.kt` ports the web's English-token/cognate logic (`enLookup` inflection
  fallbacks, faux-amis flags, `bridgeClue`/`bridgeNote`, `decodeCognates`) with tests for inflections, offsets, false
  friends and clues. Android: a Norman Bridge section in Read lists the five English shelf texts (Poe, Lovecraft) with
  French-derived/ready/false-friend counts; the reader colours every inherited word by deck status, flags false
  friends red, and offers English / weave-what-I-know / weave-everything modes that replace the English with French as
  the deck grows. Tapping opens the shared word sheet and adds the French counterpart. iOS: `IosBridge.bridgeShelf`,
  `bridgeTokens`, `bridgeNote`; `BridgeView.swift` renders the same modes with tappable linked spans and a word sheet.
  Verified: 83 shared JVM tests, 0 failures; Android compiles.
- 2026-10-21: local only. Removed the Android Account screen, Supabase client/JSON mappers and their tests; the
  native app has no sign-in, no sync and no server URL. Shared `Sync.kt` and the schema's sync columns/tombstones stay
  (schema history and a possible future export path), but nothing in either app calls them. Settings now says
  everything is saved on this device. Docs updated; native README reports 83 shared + 20 Android tests.
- 2026-10-21: native design pass. Bottom navigation uses Material icons (core `Home`/`MoreVert` plus app-drawn
  `Book`, `Headphones`, `Mic` ImageVectors, so no 40 MB extended-icons dependency); emoji/symbol labels across Run,
  Reader, Listen, Speak, Talk, Write, More and Path were replaced with words or status labels ("Done", "Bonus",
  "Mark as read", "L'Opéra · complete", "3 right", "CONJUGATION · présent"). Path keeps its colour/type personality
  but no longer uses ✅/★/⚠️/✓. The same pass was applied to iOS with SF Symbols and plain labels, and the existing
  cat mark now ships as the Android launcher and iOS app icon. Also fixed a real
  queue bug: a table queued from Verbs was placed after the first three new-word cards; it now follows due reviews
  and opens the next run, which is what `AppLaunchTest` asserts.
  Verified: 83 shared JVM tests, 0 failures; 20 Android JVM tests, 0 failures; Android debug compiles.
- 2026-10-21: learning-design research. Added `native/LEARNING.md`: evidence review (four strands, coverage 95/98%, vocabulary encounters/depth/chunks, retrieval/spacing, output and corrective feedback, focus-on-form, pronunciation/intelligibility, 4/3/2 and shadowing, SDT/flow/habit/gamification, CEFR estimates), an audit of Pluton's current mechanics, and a P0–P3 roadmap. Top recommendations: a daily `Today` loop with adaptive difficulty; voice Talk + 4/3/2 + shadowing; persist writing, revision and an error log that feeds targeted review; a French sounds module; coverage-aware 98% reading. Engagement should come from flow/mastery/story/autonomy, not loss-framed streaks, hearts, leaderboards or guilt notifications. No code changed.
- 2026-10-21: P0 learning loop implemented (Android + iOS, shared core). Shared: `suggestedNewWords` (recent accuracy
  moves the pace 3–10), `coverageComfort` (98% comfortable / 95–98% stretch / <95% challenge), Store meta for
  onboarding/plan/last tab, `recentAccuracy`, SQLDelight v4 (`writing`, `corrections`), writing/correction records
  with backup export/import, and separate Talk/Speak bonus goals in Le Chemin (`m.spoken` now only feeds Speak).
  Android: one-question onboarding (5/10/20/30 min sets the pace), a four-step Today card with checkboxes, a weekly
  consistency row, and an "Open" action per strand, pace suggestion with one-tap accept, last-tab restore, coverage labels, a 98% queue
  target, "Read & listen", writing history/revision/error summary, and Speak mode requests from Home. iOS mirrors
  the same Today/onboarding/coverage features and gains a Write tab with the same local history/revision/error log;
  Talk now counts as talked and both Talk/Practice mark the Speak plan step. Verified: 91 shared JVM tests and 21
  Android JVM tests (5 AI, 16 Robolectric) pass; debug APK builds. iOS is syntax-checked with tree-sitter but not
  type-checked here; the next macOS CI run is its first compile.
- 2026-10-21: P1 speaking loop (Android + iOS). Shared `Sounds.kt` adds nine curated French lessons (nasals, /y/–/u/,
  /ʁ/ vs /l/, /s/–/z/, /ʃ/–/ʒ/, final consonants, liaison, silent letters, e caduc) with minimal pairs and phrase
  lists; `IosBridge.soundLessons()` exposes them. Android `Voice.kt` adds a reusable French TTS engine, clip
  recorder and one-shot speech recognizer. Talk gets voice turns (transcript sent to the AI, reply spoken with TTS,
  voice-reply toggle, TTS stopped before listening). Speak gets Fluency (shadowing + 4/3/2 with 4/3/2-minute
  countdown, recording, playback, no fake score) and Sounds (pair quiz, phrase playback, recording). Counts go into
  the existing `spoken` stat and the Speak path goal. iOS mirrors all three with `SpeechPractice`/`Speaker`; Talk now
  has a mic button and sends the transcript. Verified: 97 shared JVM tests and 22 Android JVM tests (5 AI, 17
  Robolectric) pass; APK builds. Swift syntax is tree-sitter-clean; iOS type-check waits for the macOS CI run.
- 2026-10-21: adversarial P0 review and fixes. Fixed: iOS queue-to-98 could overwrite a card just marked known (status
  guard in `AppModel.addWord(s)`, a root-level `Store.addCard` guard that never overwrites an existing card, and `cardRevision` refreshing the stale list); opening history/starting a revision no
  longer clobbers the unsent new-writing draft (revision id/text stored separately, `My draft` restores it); v1 backup
  restores leave local writings/corrections untouched (backup default version 1, export version 2); existing installs
  skip onboarding via `onboardedOrExisting()` and keep their pace; Android and iOS reload Today/plan/practice/new-word
  budget on date rollover; iOS gained backup export/import with a `FileDocument`, and import errors are caught at the
  Kotlin bridge instead of crashing; iOS batch queue uses the containing sentence, not the whole text; iOS
  `practiceDays` updates on plan toggles; empty runs no longer tick Review. Added v1-backup, second-id, revision-stat,
  onboarding-upgrade and per-day plan tests. Verified: 97 shared JVM tests, 22 Android JVM tests, 0 failures; APK
  builds; all Swift files syntax-parse (iOS type-check still pending CI).
- 2026-10-21: P2 depth slice. Shared `Chunks.kt` adds 24 common French chunks with gloss/example/translation and
  lemma links; `chunkWord` turns a chunk into a normal SRS card with a `chunk:` lemma, and `modeFor` keeps chunks
  cloze-only (no silent dictation). `contextSentence`/`contextForm` alternate the mined context with the dictionary
  sentence and the matching inflected form. `grammarFocus` derives the current grammar topic or conjugation tense
  from the cards and feeds `chatSystem`/`writeSystem` overloads; Android and iOS show a "notice" line in Read.
  Android Listen gains a dictogloss with `gradeDictee`/`dicteeScore`; Android Reader and iOS ReadView gain
  "Continue this text" (shared `continueSystem`, saved locally as a new text). Tests: chunk/context/grammar-focus/
  continuation. Verified: 104 shared JVM tests, 22 Android JVM tests, 0 failures; APK builds; Swift syntax-clean.
- 2026-10-21: adversarial P1 review and fixes. Blocking: added Android `<queries>` for `RecognitionService` and
  `TTS_SERVICE` (Android 11+ package visibility); iOS Fluency now stops/cancels on Shadow/4-3-2 switch and on
  disappear; iOS Sounds stops recording when leaving a lesson; iOS `SpeechPractice` stops once (`finishRecording`,
  generation guard, duplicate-tap removal, input-format check, recognition-error surfacing, playback errors); iOS
  Practice counts a recording even when the transcript is empty; Android `ClipRecorder.stop()` is idempotent and only
  exposes a completed path, with the native recorder released on failure; Android recorders stop TTS first and
  disable Hear/Play while recording; Android permission grants resume the pending action; Sounds' "Next pair" is
  gated on TTS; iOS French-voice errors are surfaced; the Sounds test now asserts the exact nine ids and distinct
  pairs. Verified: 104 shared JVM tests, 22 Android JVM tests, 0 failures; APK builds; Swift syntax-clean.
- 2026-10-22: P3 assessment/polish slice. Shared `Progress.kt` computes a broad receptive CEFR range from graduated
  words, last-30-review accuracy, stage can-do statements, and milestones (words, reading, classics, Bridge texts,
  listening hours, recordings, talk, writing). Can-do ticks persist in local meta (`cando`). Android More→Progress and
  iOS More→Progress show the report; no accounts, no leaderboards. Tests: CEFR bands, report counts/milestones, and an
  Android Robolectric Progress screen test. Verified: 107 shared JVM tests, 23 Android JVM tests, 0 failures; APK
  builds; Swift syntax-clean. The can-do tick round-trip has its own Store test.
- 2026-10-22: iOS Listen parity. `IosBridge` now exposes `parseCues`/`cueAt`; a new `ListenView` plays a local
  audio/video file through `AVPlayer` (bare `AVPlayerLayer` so the app owns the controls), reads `.srt`/`.vtt`,
  shows the tapped/coloured current line, jumps by line, supports 0.75–1.25× and pause-after-line, runs a
  dictogloss, and counts real playback seconds as listening. The tab sits between Read and Speak; Home's Speak/Write
  shortcuts and last-tab restore were renumbered. Also fixed Android's resume-after-auto-pause on the last subtitle
  line (replay that line instead of pausing instantly). Verified: 107 shared JVM tests, 23 Android JVM tests,
  0 failures; APK builds; Swift files parse clean. CI run 39741005344 (commit `cc6dda09`) was green for both the
  unsigned-IPA build and the simulator launch. A follow-up run (`671c6d82`) was also green after fixing SwiftUI
  `ProgressView` name shadowing and a `try` inside a `??` fallback.
- 2026-10-22: adversarial P2 review (commit `cc6dda09`) and fixes. Blocking: iOS word-sheet adds now carry the
  tapped surface form and occurrence (links encode the token offset), so mined cloze/dictée pairs actually blank;
  `blank` now matches typographic apostrophes and treats an elided form (`n'`) as boundary-complete; Android Listen
  snapshots the subtitle line with the tapped token instead of reading the current line when Add is pressed; iOS
  runs now use the alternating `contextSentence`/`contextForm` pair instead of always the mined one; Android run
  feedback freezes the displayed context before grading bumps reps; continuation keeps the *end* of long texts
  (`takeLast`) instead of the opening; Android continuation requests are invalidated on navigation. Also: chunk
  lemmas are filtered out of AI prompt word lists, iOS continuation prompt lists match Android (glossed known words,
  8 targets), the built-in sentence clip is only offered when the built-in sentence is displayed, the dictogloss is
  hidden without media, and two chunk lemma links were corrected (`rendez-vous`, `course`). Tests: real
  blankable mined pairs, elisions/curly apostrophes, long continuation endings, exact 24 chunks × all reps cloze,
  empty/graduated grammar focus, story parsing, and a Robolectric word-sheet test that clicks a chunk's Add.
  Verified: 113 shared JVM tests, 24 Android JVM tests, 0 failures; APK builds; Swift parses; CI for the preceding
  commit was green (build + simulator launch).
- 2026-10-22: final CI and review note. `ed42a2fd` is green for both the unsigned-IPA build and the simulator
  launch (run 39744484091); `afe33049` failed only because the iOS context helper still took `CardState` while
  `RunItem` carries the FSRS card, fixed in `ed42a2fd`. A second adversarial pass focused on the iOS Listen delta
  was started but terminated before it could report (reviewer provider returned HTTP 402, insufficient balance);
  the delta therefore rests on the first P2 review, local Swift syntax checks and the macOS type-check. Android's
  auto-pause baseline now syncs to the player's actual position on resume when no seek happened.
- 2026-10-22: resumed iOS Listen delta review (the earlier attempt hit an API 402). One blocking finding: the iOS
  Bridge milestone read its titles from the French-only `shelf`, so it could never complete; now it uses
  `bridgeShelf`. Non-blocking fixes: listening seconds accumulate only while `AVPlayer.timeControlStatus` is
  `.playing` (suspension/interruption gaps are ignored, flushed on pause/end/background), file-picker cancellation
  no longer shows an error, the temporary media copy is deleted in `stop()`, and auto-pause remembers the crossed
  cue end so Play restarts at the next line instead of mid-line. The same boundary resume fix is in Android Listen.
  Verified: 113 shared JVM tests, 24 Android JVM tests, 0 failures; APK builds; Swift parses; CI for `30e480f8`
  green (build + smoke). This commit awaits its own CI run.
- 2026-10-22: P3 completion. Progress now has a `WeekActivity` (reviews and Good/Easy count from a new
  `reviewsSince` SQL query, 7-day window), a `Checkpoint` computed from the current stage's can-do ticks, and a
  Cabinet listing finished story chapters, classics, Bridge texts and user texts with a small Pluton line. Android
  More passes `story`/finished titles; iOS builds the same list from its loaded data. Tests: 7-day window and empty
  accuracy (Store), checkpoint completion, a week-accuracy assertion in the report test, and Robolectric assertions
  for the new Progress cards. Verified: 115 shared JVM tests, 24 Android JVM tests, 0 failures; APK builds; Swift
  parses. CI pending. Next: adversarial review of P3.
