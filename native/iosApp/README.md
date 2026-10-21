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

1. `sync-resources.sh` copies `public/data/fr/{words,forms,story,shelf,conj,grammar}.json` into
   `Data/fr` (run `uv run data/fr/build.py` first if those files are missing).
2. `./gradlew :shared:embedAndSignAppleFrameworkForXcode` builds and embeds `PlutonCore` from the shared module.
   The Xcode process needs `JAVA_HOME` pointing at JDK 21 and network access for Gradle's first run.

A simulator build needs no Apple account. A device build needs a signing team in the target settings.

## What the app does

- **Home**: words loaded, current Chemin stage and next goal, deck counts, start a run.
- **Run**: intro cards then cloze, listen-and-type, English→French recall, or full-sentence dictée; grading and FSRS
  replay come from the shared core.
- **Read**: Pluton's ten chapters with coverage, coloured/tappable words (a `pluton://word/<lemma>` link opens the
  gloss/add/known sheet), the story comprehension quiz, the French classics, and your own pasted texts (add/delete).
  French TTS reads the whole text.
- **Speak**: read-aloud practice with device TTS, `AVAudioRecorder` playback, and `SFSpeechRecognizer` transcript
  checked by the shared `gradeDictee` (recognizer-match percentage, not phonemes); plus a Talk tab using the shared
  `chatSystem` prompt with a BYOK provider over `URLSession` (Anthropic or OpenAI-compatible). The key is in the
  Keychain, not UserDefaults.
- **More**: Le Chemin with goals, the deck browser, all conjugation tables (tap a form to hear it), grammar topics,
  and Settings (pace; AI provider/model/key).

## Status: written, never compiled

This machine is Linux and has no Xcode or Swift toolchain, so **none of the Swift code has been type-checked or run**,
and Kotlin/Native iOS targets cannot be built here either (Gradle registers them only on a macOS host). Treat the
Swift sources as a reviewed scaffold, not as a working build: the first `xcodegen` + Xcode build on a Mac will
certainly need interop fixes (Kotlin class/property names, SwiftUI edge cases).

Known gaps versus Android: no Listen media tab, no account sync, no story generator/retell, no writing-correction
mode, runs are word cards only (no conjugation/grammar drills), and no bundled sentence audio (TTS stands in).
