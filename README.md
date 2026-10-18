# Pluton

Learn French for real, with Pluton the black cat (Poe's cat, as Baudelaire's translation names him).

- **Runs**: ~10-minute sessions. New words are introduced in a real sentence, then you *type* them back
  (fill-the-gap, or listen-and-type). FSRS schedules every review. Combos, points, a streak, and a cat with opinions.
- **Library**: paste lyrics, an article, or subtitles. Every word is coloured by whether you know it. A text
  unlocks at 95% understood, and one tap queues exactly the words that get you there.

One local-first PWA for Linux, Android and iOS. Progress lives on the device (IndexedDB); use Backup/Restore on
the home screen to move it.

## Run it

```bash
npm install
uv run data/fr/build.py      # needs the downloads below; ~1 min data, ~12 min TTS the first time
npm run dev                  # http://localhost:5173, also on your LAN (--host)
```

Checks:

```bash
npm test                     # logic: tokenizer, grading, scheduling queue, coverage, streak
npm run smoke                # builds, then plays a full run + library flow in headless Chromium
                             # (first time: npx playwright install chromium)
```

`data/fr/build.py` also asserts on its own output (deck size, every sentence's gap is unambiguous,
every audio file exists, form→lemma spot checks).

## On your phone

Install needs HTTPS (service worker). Build with `npm run build` and put `dist/` on any static HTTPS host
(GitHub Pages, Cloudflare Pages, Netlify), or serve it from this machine with `tailscale serve`.
Then: iOS Safari → Share → Add to Home Screen; Android Chrome → Install app; Linux Chromium → Install.

## Data (French)

Download into `data/fr/raw/`:

| File | Source | Licence |
|---|---|---|
| `Lexique383.tsv` | http://www.lexique.org/databases/Lexique383/Lexique383.tsv | CC BY-SA 4.0 |
| `fra_sentences.tsv.bz2`, `fra-eng_links.tsv.bz2` | https://downloads.tatoeba.org/exports/per_language/fra/ | CC BY 2.0 FR |
| `eng_sentences.tsv.bz2` | https://downloads.tatoeba.org/exports/per_language/eng/ | CC BY 2.0 FR |
| `kaikki.org-dictionary-French.jsonl` | https://kaikki.org/dictionary/French/ (Wiktionary) | CC BY-SA 4.0 |
| `fr_FR-siwis-medium.onnx` (+ `.onnx.json`) | https://huggingface.co/rhasspy/piper-voices (fr/fr_FR/siwis/medium) | see voice card |

Frequencies and lemmas: Lexique 3 (New, Pallier et al.). Sentences: Tatoeba contributors.
Glosses: English Wiktionary via kaikki.org. Voice: Piper TTS. Attribution is required if you publish this.

## Layout

```
data/fr/build.py      Lexique + Tatoeba + Wiktionary + Piper → public/data/fr/{words,forms}.json, audio/
src/lib/logic.js      pure logic (tested): tokenizer, grading, FSRS run queue, coverage, streak
src/lib/lang.js       everything French-specific on the app side
src/lib/db.js         IndexedDB: cards, append-only review log, texts, meta
src/lib/sound.js      clips + sound effects through one AudioContext
src/{App,Run,Reader,Cat}.svelte
public/sw.js          offline cache
```

## Adding a language

Write `data/<code>/build.py` producing the same `words.json` / `forms.json` / `audio/` shape (Lexique is
French-only; elsewhere use a frequency list plus Wiktionary forms), add a `LANG` object like the French one
in `src/lib/lang.js`, and a picker. Each language gets its own database (`pluton-<code>`).
