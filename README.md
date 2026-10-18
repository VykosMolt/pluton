# Pluton

Learn French for real, with Pluton the black cat (Poe's cat, as Baudelaire's translation names him).

**Use it:** https://vykosmolt.github.io/pluton/ — installs as an app on iOS (Safari → Share → Add to Home Screen),
Android (Chrome → Install app) and Linux (Chromium → Install). Progress stays on your device.

Aimed at C1: see the Road to C1 in [PLAN.md](PLAN.md) for what's covered and what isn't yet.

- **Le Chemin (the path)**: ten stages from A1 to C1, framed as Pluton's journey from Poe's wall to the Paris Opéra.
  Each stage has goals across skills: words learned, verb tables, its chapter of Pluton's story (written for that
  stage's French) plus a classic from B2 on, and listening time; talking and writing are bonus ★. Home shows the
  current stage and today's next step. **The path guides; it never locks**: every text, chapter, verb table and
  feature is open from day one.

- **Runs**: ~10-minute sessions over an 8,000-word deck (spoken + written frequency). New words are introduced in
  a real sentence, then you *type* them back: fill the gap, listen and type, recall from English (nouns with the
  right gender), and full-sentence dictées once a word is solid. Conjugation tables for 59 verbs unlock tense by
  tense. FSRS schedules every review. Combos, points, levels, a streak, and a cat with opinions.
- **Library**: Pluton's story (10 chapters, A1 to C1), your own texts (lyrics, articles, subtitles), and a shelf of
  public-domain classics (La Fontaine, Perrault, Daudet, Maupassant, Verne). Every word is coloured by whether you know
  it; the story and classics read themselves aloud with the sentence highlighted. A text gets comfortable at 95% of
  words known, and one tap queues exactly the words that get you there.
- **Norman Bridge**: nearly a third of English came from French. Read Poe and Lovecraft in English with those words
  highlighted, then woven back into French as you learn them. Tap a word for its French twin and the sound change
  that hides it (forêt → forest: the circumflex is a lost s). Classic false friends are flagged.
- **Listen**: play your own French video or podcast with its subtitles; tap words in the current line, replay
  lines, pause after each line. Hours listened count toward the Road to C1.
- **Talk** (optional, your own AI key): converse with Pluton, who answers at your level and corrects you as you go
  (typing or speaking), and get your writing marked with every error explained.
- **Words / Verbs**: the whole deck as a collection with memory strength and next review, and every conjugation
  table of 59 verbs, with audio and a Practice button whatever your stage.
- First launch offers a placement check, so people who know some French skip what they know.

## AI: bring your own key

Settings → AI tutor. Works with Claude (Anthropic API key; default model Claude Opus 5.5), OpenAI, DeepSeek,
OpenRouter, a local model through Ollama (free; start it with `OLLAMA_ORIGINS=<the app's origin> ollama serve`), or
any OpenAI-compatible server. Chat subscriptions (Claude Pro, ChatGPT Plus…) can't be connected to other apps.

There is no Pluton server: the browser calls the provider directly, the key is stored only on the device, and it is
never included in backups.

## Run it

```bash
npm install
uv run data/fr/build.py      # needs the downloads below; ~1 min data, ~5 min TTS the first time (resumable)
uv run data/fr/shelf.py      # only to refresh the reading shelf (data/fr/shelf.json is committed)
npm run dev                  # http://localhost:5173, also on your LAN (--host)
```

Checks:

```bash
npm test                     # logic: tokenizer, grading, scheduling, coverage, conjugation, cognates, tutor prompts
npm run smoke                # builds, then drives the real app in headless Chromium: runs over several days,
                             # library, bridge, listening, placement, and the AI features against a mock provider
                             # (first time: npx playwright install chromium)
```

`data/fr/build.py` also asserts on its own output (deck size, unambiguous gaps, every audio clip exists, known
conjugations, cognate and false-friend spot checks).

Deploy: `npm run deploy` publishes `dist/` (with the generated data) to the `gh-pages` branch.

## Data (French)

Download into `data/fr/raw/`:

| File | Source | Licence |
|---|---|---|
| `Lexique383.tsv` | http://www.lexique.org/databases/Lexique383/Lexique383.tsv | CC BY-SA 4.0 |
| `fra_sentences.tsv.bz2`, `fra-eng_links.tsv.bz2` | https://downloads.tatoeba.org/exports/per_language/fra/ | CC BY 2.0 FR |
| `eng_sentences.tsv.bz2` | https://downloads.tatoeba.org/exports/per_language/eng/ | CC BY 2.0 FR |
| `kaikki.org-dictionary-French.jsonl` | https://kaikki.org/dictionary/French/ (Wiktionary) | CC BY-SA 4.0 |
| `fr_FR-siwis-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices (fr/fr_FR/siwis/medium) | SIWIS: CC BY 4.0 |

Frequencies, lemmas and verb forms: Lexique 3 (New, Pallier et al.). Sentences: Tatoeba contributors.
Glosses and cognates: English Wiktionary via kaikki.org. Voice: Piper TTS. Shelf: Wikisource (public-domain texts).
Attribution is required if you publish this.

## Layout

```
data/fr/build.py      Lexique + Tatoeba + Wiktionary + Piper → public/data/fr/{words,forms,conj,cognates,shelf}.json, audio/
data/fr/shelf.py      fetches the French classics and the English Norman Bridge texts from Wikisource
src/lib/logic.js      pure logic (tested): tokenizer, grading, FSRS run queue, coverage, cues, cognate lookup
src/lib/lang.js       everything French-specific on the app side (accents, tenses, clue texts)
src/lib/tutor.js      what Pluton asks the AI (levels, prompts)
src/lib/path.js       the ten stages, their goals, and today's next step (tested)
data/fr/story.json    Pluton's story, hand-written, one chapter per stage
src/lib/ai.js         bring-your-own-key client (Anthropic SDK + OpenAI-compatible)
src/lib/db.js         IndexedDB: cards, append-only review log, texts, meta
src/lib/sound.js      clips + sound effects through one AudioContext
src/lib/deck.js       card changes outside runs (add, mark known)
src/*.svelte          App, Welcome, Run, Reader, Bridge, Listen, Talk, Words, Settings, Cat + small shared pieces
public/sw.js          offline cache
scripts/              smoke test, mock AI provider, deploy
```

## Adding a language

Write `data/<code>/build.py` producing the same `words.json` / `forms.json` / `audio/` shape (Lexique is
French-only; elsewhere use a frequency list plus Wiktionary forms), add a `LANG` object like the French one
in `src/lib/lang.js` and a tutor module like `src/lib/tutor.js`, and a picker. Each language gets its own
database (`pluton-<code>`).
