# Pluton iOS

SwiftUI app over the shared Kotlin core (`PlutonCore.framework`). The Xcode project is generated from `project.yml`
with XcodeGen so the spec stays reviewable from Linux.

## Build

**From Linux, without a Mac:** push the repo and run the `iOS unsigned IPA` GitHub Actions workflow
(`.github/workflows/ios.yml`). It builds on a macOS runner and uploads `Pluton-unsigned.ipa`. Sign and install it
from Linux with iloader (free Apple ID) — or use TrollStore on supported iOS versions. Step-by-step:
[`INSTALL.md`](INSTALL.md).

**On a Mac:** `brew install xcodegen`, then `cd native/iosApp && xcodegen generate && open Pluton.xcodeproj`.

The `Pluton` scheme runs two pre-build scripts:

1. `sync-resources.sh` copies `public/data/fr/{words,forms,story,shelf,conj,grammar,cognates}.json` into
   `Data/fr` (run `uv run data/fr/build.py` first if those files are missing).
2. `./gradlew :shared:embedAndSignAppleFrameworkForXcode` builds and embeds `PlutonCore` from the shared module.
   The Xcode process needs `JAVA_HOME` pointing at JDK 21 and network access for Gradle's first run.

A simulator build needs no Apple account. A device build needs a signing team in the target settings.

## What the app does

- **Home**: a Today card with four steps (review, read, speak, write), a weekly consistency row, a daily-time onboarding, the current Chemin
  stage, a pace suggestion from recent review accuracy, and the last tab restored on launch.
- **Run**: intro cards then cloze, listen-and-type, English→French recall, or full-sentence dictée; grading and FSRS
  replay come from the shared core.
- **Read**: Pluton's ten chapters with coverage, comfortable/stretch/challenge labels, a "queue to 98%" action,
  coloured/tappable words (a `pluton://word/<lemma>` link opens the gloss/add/known sheet, which now offers common
  chunks), a grammar notice from the current card state, the story comprehension quiz, the French classics, the
  **Norman Bridge** (English texts with the French-derived words marked, woven into French as your deck grows), and
  your own pasted texts (add/continue/delete). French TTS reads the whole text.
- **Speak**: read-aloud practice with device TTS, `AVAudioRecorder` playback, and `SFSpeechRecognizer` transcript
  checked by the shared `gradeDictee` (recognizer-match percentage, not phonemes); Talk using the shared `chatSystem`
  prompt with a BYOK provider over `URLSession` (Anthropic or OpenAI-compatible), key in the Keychain, with a mic
  button for voice turns and spoken replies; Write with prompts, AI correction, local history, revision and an error
  summary; **Fluency** with shadowing and 4/3/2; and **Sounds** with curated minimal pairs, phrase playback and a
  recording to compare.
- **More**: Le Chemin with goals, the deck browser, all conjugation tables (tap a form to hear it), grammar topics,
  and Settings (pace; AI provider/model/key).

## Status: built by CI, not on this machine

This machine is Linux and has no Xcode or Swift toolchain, so the Swift sources cannot be compiled here. The
`iOS unsigned IPA` workflow compiles the shared framework, generates the project with XcodeGen, builds the app,
packages the unsigned IPA, and separately installs and launches it in a simulator; the last run before the P0/P1 changes was green. The next run is the first compile of the new Today/onboarding, writing, voice, Fluency and Sounds code.

Everything is local: no account, no sync server. Progress is in the app's SQLite store and the AI key is in the
Keychain, never in UserDefaults or a backup.

Known gaps versus Android: no Listen media tab, no story generator/retell, runs are word cards only
(no conjugation/grammar drills), and no bundled sentence audio (TTS stands in). The P0 changes (Today/onboarding,
coverage labels, writing persistence) are in the sources but have not been compiled on this Linux machine; the next
macOS CI run is their first type-check.
