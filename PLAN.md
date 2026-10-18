# Pluton — plan & progress

Goal: a French learning app that actually teaches (FSRS spaced repetition, production
not recognition, frequency order, comprehensible input) and is fun (combo/points, real
content unlocks, short runs). One local-first PWA for Linux, Android, iOS.

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

## MVP checklist
- [x] data/fr/build.py → words.json (10k lemmas, 9816 glossed; 1930 with sentence+translation+audio), forms.json, 3.7k mp3s
- [x] Review run: intro new words, cloze + dictation, lenient grader, FSRS, combo/points/sfx/confetti, summary
- [x] Reader/library: paste text or import .srt/.vtt, coverage %, tap word → add / mark known, queue-to-unlock
- [x] Home: streak, learning/known/due tiles, next-unlock card, backup/restore
- [x] PWA: manifest, icons, service worker
- [x] Checks: `npm test` (10 pass), build.py self-asserts, `npm run smoke` (full run + library + reload in Chromium)

## Later (not in MVP)
Sync server (replay `log`), LLM story campaign + roleplay, speaking (Whisper), hours-listened metric,
language picker, precache all audio for full offline, Tauri wrap, settings (new words/day).

## Progress log
- 2026-10-18: MVP built and verified end to end (see checklist). Not deployed, not committed.
