# Pluton review — 2026-10-23

Pluton's strongest idea is the connection between reading, noticing a word, practising it, and returning to the
text. The cat, the story, and the Norman Bridge give that loop a distinct identity. Frequency ordering, typed
retrieval, FSRS, sentence context, correction review, and local ownership of progress are foundations worth keeping.

My main criticism is that feature breadth has overtaken consistency and release confidence. There is already enough
functionality for a serious study companion. The next improvement should make a normal twenty-minute session,
device change, interrupted recording, and recovery from a backup dependable. More modes and progress counters would
currently add less value.

This review covered the Svelte app, shared Kotlin core, Android and SwiftUI sources, generated French assets,
Supabase migration, offline worker, packaging, and existing tests. I exercised the web app in Chromium and ran the
native JVM suites and Android build. Swift changes received source review; they still need macOS compilation and
device validation. Changes are local and have not been deployed or pushed.

**Bugs fixed**

| Area | Verified problem and resulting change |
| --- | --- |
| Native restore | Default deserialization treated unrelated JSON as an empty backup and erased progress. Reject unsupported versions, empty versionless payloads, invalid scheduler records, and duplicate IDs before replacement. |
| Native reading history | Restoring texts assigned new IDs, breaking `text-<id>` completion references. Backup v4 preserves text IDs and update times, plus pace and practice days. Older formats remain accepted. |
| Web restore | A malformed late record could throw after destructive writes were queued. Validate and clone records before writing, validate FSRS fields/dates, and explicitly abort synchronous write failures. Export now reads all stores in one consistent transaction. |
| Account wipe | Deleting an open IndexedDB database could stay blocked while the app reloaded. Clear the stores through the existing connection and await completion. |
| Sync replay | A pulled card operation was compared to a lemma string rather than its card's lemma. Replaying a remote review could then append a stale local card over the newer pulled state, including a marked-known state. |
| Sync push | Delete/re-add cycles emitted both a live record and its retained tombstone. Coalesce to the newest operation before the Postgres upsert; a live local record wins a same-millisecond tie. Apply the rule in web and shared Kotlin code. |
| AI configuration | Native provider changes retained the previous provider's secret, including a hidden key for Ollama. Reset credentials when switching providers and omit authorization for Ollama. Save the configuration actually tested, and report iOS Keychain write failures. |
| AI response handling | Editing or navigating during a request could attach corrections to a different draft, or let late callbacks update abandoned views. Capture submitted state, guard/cancel callbacks, and preserve drafts. Reject empty or truncated replies; retain the final SSE event without a trailing newline. |
| Android navigation/audio | Talk and Write displayed Fluency/Sounds navigation that did not navigate. Wire those actions, add device TTS for mined dictation without a clip, and clean up playback/recognition on navigation and backgrounding. |
| iOS recording | Multiple practice views shared one temporary recording filename. Use unique files, invalidate stale callbacks, clean up synchronously, and guard recording finalization and sentence changes. Recording-only modes no longer require speech-recognition permission. Delayed Talk replies cannot restart speech in the background. |
| Web media | Adjacent subtitle cues could re-arm the next line before pausing the previous one. Check the previous boundary first and reset the armed cue when replacing files. Guard pending microphone requests and stale recordings; resolve cancelled TTS playback promises. |
| Offline use | The Bridge's lazily fetched cognates were absent from precache. Cache all required data, retain the previous worker if required precaching fails, isolate cache cleanup by app scope, and preserve successful network responses when a cache write fails. |
| Generated content | Inflection descriptions and parenthetical annotations produced false cognates such as `former → forme` and `sound → sourd`. Exclude those inputs and ambiguous `sound`/`longer` replacements. Regenerated the local cognate asset without TTS or network: 5,769 mappings remain. |
| Packaging | Incomplete generated assets could reach packaging, and deployment could mistake a failed commit for an unchanged build. Build/smoke/deploy now validate all seven JSON files, cross-references, read-along offsets, and referenced clips; actual commit failures propagate. |

Regression coverage was added to existing suites where suitable. Offline-worker and asset-validator behavior have
small dedicated script suites, included in `npm test`. See [the smoke checks](scripts/smoke.mjs),
[backup storage](src/lib/db.js), [native storage](native/shared/src/commonMain/kotlin/io/github/vykosmolt/pluton/core/Store.kt),
and [asset validation](scripts/check-data.py).

**What I would improve next, in order**

1. **Make native migration a supported user journey.** The native Android app replaces the Capacitor app under the
   same application ID, but it starts with different storage and cannot import the web backup format. A user should
   be able to export from the shipped app, import into native, and keep cards, review history, texts, and completion
   references. This deserves a versioned conversion contract and a round-trip fixture before native replaces the
   shipped app. The installation documentation now states the current limitation explicitly.

2. **Reconcile the meanings of progress.** The tutor reaches its C1 prompt band at 4,000 known-or-learning words
   ([Tutor.kt](native/shared/src/commonMain/kotlin/io/github/vykosmolt/pluton/core/Tutor.kt)). Progress uses known
   single words, excludes chunks, and calls 3,000–4,999 words B1–B2
   ([Progress.kt](native/shared/src/commonMain/kotlin/io/github/vykosmolt/pluton/core/Progress.kt)). Path goals count
   any word in Review/Relearning, including chunks
   ([Path.kt](native/shared/src/commonMain/kotlin/io/github/vykosmolt/pluton/core/Path.kt)). These can be useful
   internal measures, but they should not sound interchangeable. Keep studied words, retained words, and demonstrated
   skills distinct; allow the learner to adjust conversation difficulty. Do not present a vocabulary threshold or
   completed path as evidence of C1 proficiency.

3. **Measure delayed success.** Recent accuracy counts repeated same-session answers, so retries can make the pace
   signal look stronger than first-attempt recall. Record first-attempt accuracy separately from eventual success,
   and look at recall after days away. For writing, track whether an earlier error disappears in new writing. For
   speaking, a recognizer-match score and approximate WPM are useful feedback, but do not establish pronunciation
   quality or conversational competence. The existing labels are appropriately cautious; preserve that distinction.

4. **Finish device and release testing.** JVM/Compose tests cannot establish that permissions, pickers, Bluetooth
   audio, interruptions, process death, and background behavior work on phones. Run those journeys on Android and
   iOS, including permission denial and recovery. iOS still lacks Android's conjugation/grammar run drills,
   generated-story/retell flow, and bundled sentence audio. Publish an explicit parity checklist. CI currently
   emphasizes iOS compilation/launch; the web, worker, asset, and shared-core regressions should also become required
   PR checks. Pin generated data to a release rather than fetching a mutable `gh-pages` branch in iOS CI.

5. **Reduce duplicated product policy.** Sharing Kotlin rules is a good boundary. However, the web retains its own
   rules and the two native UIs still duplicate request lifecycles and interaction policy. The provider, writing,
   recording, and navigation bugs found here show the maintenance cost. Keep platform audio/UI code native, while
   centralizing provider capabilities, backup contracts, and learning-state transitions where practical. Split the
   1,142-line Android `MainActivity.kt` by responsibility when a related change needs it; a wholesale rewrite would
   introduce unnecessary risk.

6. **Make the next action more prominent than the dashboard.** The web's visual identity is clear and readable, and
   the correction-to-practice flow is especially good. On a 390-pixel viewport, the mascot and stage goals occupy
   much of the first screen before the primary run action. Put the immediate task and its time estimate first,
   with the full path available on demand. Keep secondary modes discoverable without asking a new learner to choose
   a curriculum every day. Test whether users actually return to a text after practising its words.

7. **Treat the Bridge as curated teaching content.** Meaning plus spelling similarity is a useful candidate
   generator, but it does not prove an English word came from French or identify the intended sense in a sentence.
   The fixes remove concrete bad mappings; the remaining inventory still needs sampling and curated acceptance.
   Prefer a smaller trustworthy set, with explanations, over increasing the highlighted percentage. The corpus
   likewise has 20,000 lookup entries but 7,334 sentence-backed learning cards; describe those separately.

**Remaining technical limits**

Native sync is deliberately disconnected. Before enabling it, address device-clock last-write-wins conflicts and
review/text identity across backup imports; the current native export does not preserve all remote identities.
Web backup transfer into a synced account also deserves explicit cross-device identity/deduplication tests.
Supabase's caller-scoped RLS and account-deletion function showed no confirmed cross-account access defect in this
source review; that is narrower than live integration verification.

Old native backups lack text IDs, so their historical user-text completion links cannot reliably be reconstructed.
Web and native backup formats remain different. Generated assets are ignored by Git: the corrected local
`cognates.json` is ready for a later build/deploy, while committed generator changes reproduce it.

**Validation completed**

- `npm test`: all seven test files pass, including AI-stream, sync, worker, and asset-validator regressions.
- `npm run smoke`: production build plus 48 Chromium assertions pass; no browser errors. Includes runs, reading,
  quizzes, placement, mocked AI, recording, first-use offline Bridge, adjacent subtitle boundaries, rejected malformed
  backups with unchanged data, and a valid restore retaining private AI configuration.
- `:shared:jvmTest`: 134 tests pass.
- `:androidApp:testDebugUnitTest`: 31 tests pass; `:androidApp:assembleDebug` succeeds (approximately 53 MB APK).
- Asset validation: 20,000 words, 86,622 forms, 59 verbs, 15 grammar topics, 12 story chapters, 12 shelf texts,
  and 17,637 referenced audio clips checked. No full TTS rebuild was needed.
- `git diff --check` passes. Live Supabase account checks were skipped because the local backend was unavailable.
  AI network behavior used mocks; iOS compilation and real-phone audio behavior were not verified here.

## Follow-up — 2026-11-03

The release-safety pass fixed the issues that could be fixed and verified locally without publishing:

- Native daily new-word accounting: Android writes each introduction immediately and reports on the system back
  action; iOS records `introduced` after a guarded finish and disables swipe dismissal.
- Android audio: `ClipPlayer` reports whether a bundled clip exists, and run/Words fall back to TTS when deck word
  clips are absent from the APK.
- iOS parity: conjugation/grammar practice cards and run branches exist on iOS now, so Le Chemin is no longer
  blocked at La Cave on a fresh install.
- Web/process: sync is deferred while a run is open; Svelte warnings are clean; a Web checks workflow runs the
  unit suite and a Vite build; Anthropic effort flags are limited to models that support them.
- Content: the highest-frequency examples with wrong translations or the wrong sense are hand-curated in
  `data/fr/build.py`, and the data plus 18 new clips were rebuilt.
- Migration: the web Settings screen now writes a native backup version 4, and a JVM round-trip test imports the
  generated fixture into the shared store. The PWA-to-native hand-off no longer requires a separate script.

Released on 2026-11-03: the review work is committed and pushed, `gh-pages` serves the 12-chapter/current-data
bundle, and the new Web, Native shared and iOS workflows are green. The iOS story generator/retell and
conjugation/grammar run branches compiled and launched in the simulator. Remaining: real-device audio, permissions,
TTS/ASR and interruption behavior; live Supabase account integration; and making the Chromium smoke a required CI
check (it passes locally).
