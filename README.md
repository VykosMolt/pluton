# Pluton

Learn French for real, with Pluton the black cat (Poe's cat, as Baudelaire's translation names him).

Aimed at C1: see the Road to C1 in [PLAN.md](PLAN.md) for what's covered and what isn't yet.

- **Runs**: ~10-minute sessions over an 8,000-word deck (spoken + written frequency). New words are introduced in
  a real sentence, then you *type* them back: fill the gap, listen and type, later recall from English (nouns with
  the right gender). Conjugation tables for 59 verbs unlock tense by tense. FSRS schedules every review.
  Combos, points, levels, a streak, and a cat with opinions.
- **Library**: paste lyrics, an article, or subtitles, or pick from a shelf of public-domain classics (La Fontaine,
  Perrault, Daudet, Maupassant, Verne). Every word is coloured by whether you know it. A text unlocks at 95%
  understood, and one tap queues exactly the words that get you there.
- **Listen**: play your own French video or podcast with its subtitles; tap words in the current line, replay
  lines, pause after each line. Hours listened count toward the Road to C1.
- **Words**: the whole deck as a collection, with memory strength and next review.

One local-first PWA for Linux, Android and iOS. Progress lives on the device (IndexedDB); use Backup/Restore on
the home screen to move it.

## Run it

```bash
npm install
uv run data/fr/build.py      # needs the downloads below; ~1 min data, ~1 h TTS the first time (resumable)
uv run data/fr/shelf.py      # only to refresh the classics shelf (data/fr/shelf.json is committed)
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

Quick test: `npm run dev`, then open `http://<this machine's LAN IP>:5173` on the phone (same Wi-Fi).
Everything works over plain HTTP except offline mode and the install prompt.

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

Frequencies, lemmas and verb forms: Lexique 3 (New, Pallier et al.). Sentences: Tatoeba contributors.
Glosses: English Wiktionary via kaikki.org. Voice: Piper TTS. Classics: French Wikisource (public-domain texts).
Attribution is required if you publish this.

## Layout

```
data/fr/build.py      Lexique + Tatoeba + Wiktionary + Piper → public/data/fr/{words,forms,conj,shelf}.json, audio/
data/fr/shelf.py      fetches the classics shelf from Wikisource → data/fr/shelf.json
src/lib/logic.js      pure logic (tested): tokenizer, grading, FSRS run queue, coverage, streak
src/lib/lang.js       everything French-specific on the app side
src/lib/db.js         IndexedDB: cards, append-only review log, texts, meta
src/lib/sound.js      clips + sound effects through one AudioContext
src/lib/deck.js       card changes outside runs (add, mark known)
src/{App,Run,Reader,Listen,Words,Cat,Tokens,WordSheet}.svelte
public/sw.js          offline cache
```

## Adding a language

Write `data/<code>/build.py` producing the same `words.json` / `forms.json` / `audio/` shape (Lexique is
French-only; elsewhere use a frequency list plus Wiktionary forms), add a `LANG` object like the French one
in `src/lib/lang.js`, and a picker. Each language gets its own database (`pluton-<code>`).
