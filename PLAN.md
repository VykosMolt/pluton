# Pluton — plan & progress

Goal: a French learning app that actually teaches (FSRS spaced repetition, production
not recognition, frequency order, comprehensible input) and is fun (combo/points, real
content unlocks, short runs). One local-first PWA for Linux, Android, iOS.

**Target (user, 2026-10-18): a learner can reach C1 with this app.** That makes the app the hub for all of
C1's skills, not a vocab drill. See "Road to C1" below.

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
- **Name**: Pluton (user approved 2026-10-18). Repo: github.com/VykosMolt/pluton, private (free plan: Pages needs public).

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
| Grammar: verbs | all common tenses productive; passé simple / subj. imparfait receptive | **Conjugation drills**: 59 verbs × présent, passé composé, imparfait, futur, conditionnel, subjonctif; tenses unlock in order |
| Grammar: rest | pronouns (y/en, order), relatives (dont, lequel), agreement, prepositions, register | not yet: sentence-transformation drills, LLM-checked |
| Reading | unadapted texts: news, novels | Library with coverage + unlock + **classics shelf** (7 public-domain texts, La Fontaine → Verne); next: more shelf, news |
| Listening | native speed, varied speakers | **Listen tab**: own media + subtitles, tappable current line, replay/auto-pause, hours listened; **dictée** for known words |
| Speaking | fluent, spontaneous | not yet: shadowing with Whisper scoring, LLM conversation roleplay |
| Writing | clear, structured, precise | dictée trains spelling/agreement; not yet: LLM-corrected writing prompts, errors fed back as cards |

Phases: (1) vocab + verbs [done] → (2) listening player + public-domain reading shelf [done, v1] → (3) LLM layer (server):
writing correction, roleplay, story campaign, grammar-transformation drills → (4) speaking (Whisper) → (5) a
progress view that estimates level per skill from real performance, not from points.

## Later
Sync server (replay `log`), hours-listened metric, language picker, precache all audio for full offline,
Tauri wrap, settings (new words/day).

## Progress log
- 2026-10-18: MVP built and verified end to end (see checklist).
- 2026-10-18: renamed to Pluton, pushed (private). Added recall mode with gender grading, levels, run recap,
  live streak, About/credits, iOS audio session, audio-fallback warnings. Smoke now fakes +3 days for a review run.
- 2026-10-18: C1 target set. Deck → 8k (film+book frequency), conjugation drills (59 verbs, Lexique tags validated:
  hand-written être/avoir, stem agreement for regular-ending tenses), classics shelf, Listen tab, dictée, Words tab,
  Road to C1 panel. TTS: capped ONNX threads per worker (was ~190 threads on 24 cores), atomic clip writes.
