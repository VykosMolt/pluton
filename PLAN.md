# Pluton — plan & progress

Goal: a French learning app that actually teaches (FSRS spaced repetition, production
not recognition, frequency order, comprehensible input) and is fun (combo/points, real
content unlocks, short runs). One local-first PWA for Linux, Android, iOS.

**Target (user, 2026-10-18): a learner can reach C1 with this app.** That makes the app the hub for all of
C1's skills, not a vocab drill. See "Road to C1" below.

**Progression (user, 2026-10-18): a real path, not a pile of options, and every feature and reading stays open
regardless of it.** Le Chemin = 10 stages (A1→C1), goals per stage (words learned, verb tables of the stage's tense,
its story chapter (+ a classic from B2), listening minutes; talk/write = bonus ★). Targets come from measurement:
the classics only reach 95% coverage at 3.2k–7k words, so A1–B1 reading is Pluton's story, written per stage and
checked against the stage's vocabulary. The stage reached is stored (forgetting never demotes); any stage can be made
current from the map. Nothing is gated: chapters, classics, verbs (Verbs tab: Practice any tense), all tabs.

## Decisions
- **PWA, not native** — one codebase; iOS installs via Safari "Add to Home Screen", no Mac/Xcode/$99.
  Wrap with Tauri 2 later only if store distribution is wanted.
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
| Reading | unadapted texts: news, novels | **Pluton's story** (10 graded chapters) + **classics shelf** (7 texts) with read-along, plus your own texts; not yet: more classics, current news |
| Listening | native speed, varied speakers | **Listen tab**: own media + subtitles, tappable current line, replay/auto-pause, hours listened; **dictée** for known words |
| Speaking | fluent, spontaneous | **Talk** (BYOK AI): conversation at the learner's level with inline corrections, mic via browser speech recognition; not yet: pronunciation scoring, voice roleplay scenarios |
| Writing | clear, structured, precise | **Writing coach** (BYOK AI): level-matched prompts, explained corrections, level estimate; dictée trains spelling/agreement; next: turn recurring errors into cards |
| Bonus | English as a bridge | **Norman Bridge**: Poe/Lovecraft in English, French-derived words woven back into French; sound-change clues; faux amis |

Phases: (1) vocab + verbs [done] → (2) listening player + reading shelf [done, v1] → (3) AI layer, bring-your-own-key
from the browser, no server [done, v1: talk + writing] → (4) Le Chemin + Pluton's story [done] → next: grammar
drills beyond verbs, writing errors → cards, pronunciation scoring, AI roleplay scenarios / personalised story, a
per-skill level estimate from real performance.

## Not built yet
- Grammar beyond verbs: pronouns (y/en, order), relatives (dont, lequel), agreement, prepositions, register.
- Verbs: passé simple recognition (C1 reading), pronominal verbs, more than 59 verbs.
- Writing: recurring errors from corrections turned into cards.
- Speaking: pronunciation scoring (Whisper or browser), roleplay scenarios (café, interview…); mic input exists
  only where the browser has speech recognition (not Firefox/Zen).
- AI story campaign personalised to the learner's words (the 10-chapter story is hand-written, the same for all).
- Per-skill level estimate from real performance (the path counts activity, it doesn't test).
- Reading: more classics, current news.
- Sync between devices (union the append-only logs + replay FSRS); today: backup/restore.
- Second language + picker; precache all audio for full offline listening; Tauri wrap for app stores.
- Not verified yet: real phones (iOS audio, install), real AI keys (mock only), Safari/WebKit engine (can't run on
  Arch), native-speaker review of the story.

## In progress (2026-10-18, stopped at the usage limit) — resume here
User asked for: grammar beyond verbs, writing mistakes → cards, and accounts/login with sync.
Done (tested, not yet wired into the UI):
- `data/fr/grammar.json`: 15 topics A2→C1 × 10 hand-written items (objets, negation, partitifs, lieux, y-en,
  comparer, relatifs, si, subjonctif, accord, ordre, gerondif, lequel, passe-simple, indirect).
- `src/lib/logic.js`: gram/ and fix/ card keys, `gradeGram`, `newGrammar` (A2 from 150 learned words, B1 600,
  B2 1500, C1 3000; one at a time, previous topic graduated), `fixCard`, `firstChange`, buildRun `grammar` option,
  `playable` for gram/fix. 32 unit tests pass.
- Accounts backend: Supabase chosen (hosted Postgres + email-code auth, free tier, no server to run).
  `supabase/config.toml` (code-based sign-in emails, unused services off), `supabase/migrations/…_sync.sql`
  (records table, RLS, `push` last-write-wins RPC, `delete_account`). Local stack runs via `npx supabase start
  -x studio,storage-api,imgproxy,realtime,edge-runtime,logflare,vector,supavisor,postgres-meta`; migration applies.
Left to do:
1. build.py: validate + copy grammar.json to public/data/fr (and add it to the SW precache list).
2. Run.svelte: 'gram' mode (random item, intro with the explanation + two solved examples) and 'fix' mode;
   App passes `grammar` to buildRun; Words tab gets a Grammar section (all topics open, Practice button).
3. path.js: grammar goal per stage ([0,0,1,4,6,8,10,12,13,15] topics in review) + tests.
4. Talk: "Practice this" on writing errors (fixCard from the corrected sentence) and on chat corrections
   (firstChange between the learner's message and the ✏️ line).
5. Sync: `src/lib/sync.js` with @supabase/supabase-js: email code sign-in (signInWithOtp + verifyOtp), pull pages by
   synced_at (60 s overlap), push dirty records via `push` RPC; cards/texts LWW by an `updated` stamp; log keyed
   `${device}:${n}`; texts need string ids (uuid) to avoid cross-device collisions; tombstones for deletes; stats
   split per device (`stats:<device>`; points/listened/talked/written summed, bestCombo/stage max, read union,
   streak from the union of run days). Never sync meta 'ai'. Settings → Account (send code, verify, sync now,
   sign out, delete account). Config via VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY; UI hidden when unset.
6. Smoke: grammar + fix flows; accounts against the local stack (code from Mailpit at :54324), two devices sync,
   delete account removes server rows.
7. Hosted project: needs the user's Supabase account (`npx supabase login`, then link + `db push`), set the site
   URL to https://vykosmolt.github.io/pluton/, and real SMTP for production email volume.

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
