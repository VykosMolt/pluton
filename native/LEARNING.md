# Learning design: what the evidence says Pluton should add

**Date:** 2026-10-21
**Scope:** How adults actually learn a second language, how Pluton compares, and what to add or interweave. Written for the native app (Android + iOS, local-only, BYOK AI).
**Status (2026-10-21):** P0 shipped — daily Today loop/onboarding/adaptive pace/weekly consistency, coverage labels and 98% target, writing persistence/revision/error log, and separate speaking goals. P1 built by CI — voice Talk, shadowing, 4/3/2, and a curated French Sounds module on Android and iOS. P2: chunks as SRS cards, mined/dictionary context alternation, grammar focus in AI prompts and reading notices, dictogloss and active listening on both platforms, and local text continuation. P3 first slice: a local Progress report with a broad CEFR vocabulary estimate, last-30-review accuracy, stage can-do self-assessment and milestones. Remaining: more chunks, a broader story series, and deeper assessment checkpoints. The iOS Listen tab is the newest code and awaits its first CI compile.

---

## 0. Bottom line

1. **There is no trick method.** The best-supported model is a balanced diet: lots of comprehensible, interesting input; meaningful output; deliberate study of vocabulary, grammar and pronunciation; and fluency practice with already-known language. Paul Nation calls these the **four strands**, ideally roughly equal in a course. Pluton is strong on input and deliberate study, weak on fluency, and only partly covers output.
2. **Pluton's foundation is good.** Frequency-ordered vocabulary, FSRS-6, sentence-mined cards, coverage-coloured reading, read-along audio, the Norman Bridge, and AI feedback are all evidence-aligned. Do not replace them. The gains now come from *interweaving* them and filling the missing strand.
3. **The four highest-leverage additions are:**
   - a **daily 10–15 minute loop** that mixes the strands (`Today` card, adaptive difficulty, onboarding, persistence);
   - **spoken output and fluency** (voice Talk, 4/3/2, shadowing);
   - **writing revision + a persistent error log** that feeds targeted review;
   - a **French sounds module** (nasals, /y/–/u/, /ʁ/, liaison, rhythm) with perception and production practice.
4. **Interweave, don't bolt on.** Target the current grammar/tense inside AI chat and writing; turn corrections into review cards; show chunks in the word sheet; add comprehension checks to Listen; add speaking/fluency goals to Le Chemin; use coverage labels to recommend the next text.
5. **“Addictive” should mean flow, mastery, story and autonomy — not compulsion.** Loss-framed streaks, hearts/energy, leaderboards, guilt notifications and loot boxes raise session counts but shift motivation to metric maintenance; the engagement literature documents stress, guilt, and abandonment after a break. The healthy hook is a short ritual with visible progress, a continuing story, and a clear end.
6. **Measure learning, not just time in app.** Retention, coverage growth, speech rate, error recurrence and can-do checkpoints should be the dashboard; streak length alone is a vanity metric.

---

## 1. How to read the evidence

I separate three levels:

- **Strong:** replicated meta-analyses or large syntheses (spacing, retrieval practice, corrective feedback, captions, vocabulary apps).
- **Moderate:** single RCTs, quasi-experiments, or meta-analyses with contested inclusion criteria (4/3/2, shadowing, AI chatbots, dictogloss).
- **Emerging/weak:** consumer research, qualitative studies, one-site pilots (streaks, notifications, most gamification claims).

Several key papers are paywalled. Where I could only see an abstract or a search summary, I say so. Numbers below are from the cited sources; do not treat them as precise effect sizes for Pluton's population.

---

## 2. What the evidence says

### 2.1 Input: coverage, volume, narrowness

**Coverage.** The mainstream figures are **95% known words for reasonable comprehension with support, and 98% for comfortable, independent reading**. Hu & Nation (2000) is the usual source for 98%; Van Zeeland & Schmitt (2012) found **95% may be enough for listening** (2,000–3,000 word families), and Nation (2006) estimated 8,000–9,000 families for 98% of written English and 6,000–7,000 for spoken. Schmitt, Jiang & Grabe (2011) found a roughly linear relationship rather than a sharp threshold, and a 2025 partial replication (Webb, Pellicer-Sánchez & Wang) found no significant comprehension difference between 90%, 95% and 98% — only 100% was clearly better. Treat 95–98% as a useful design target, not a law.

**French is harder than English for coverage.** Cobb's LexTutor analysis estimates French needs far more words than English for the same coverage (roughly 16,000 vs 8,000 for 98%, depending on how words/families are counted), and an Einlang corpus study of 76 public-domain French books found a median of ~7,500 word families for 95%, with huge variation by book (4,000–18,000). The practical implication: do not promise a fixed “fluent at 5,000 words” number, and do not push learners into 90% texts and call it extensive reading.

**Volume.** Extensive-reading studies associate gains with roughly **100,000–115,000+ words per year**, and advanced reading with **1,000,000+ cumulative words**; effects are dose-dependent. A learner reading 10 minutes a day at 150 wpm reads ~75,000 words/year — meaningful, but the app should make reading easy enough that minutes accumulate.

**Narrow reading/listening.** Repeated exposure to the same author, series, topic or speakers recycles vocabulary and reduces the load. Cho & Krashen (1994) reported ~62% of unknown words picked up from a book series; narrow listening has supportive but smaller-scale evidence. Reading-while-listening (RWL) generally beats listening-only for comprehension and helps form–meaning learning, especially for beginners.

**Implication for Pluton:** label texts by comfort (98%+ comfortable, 95–98% stretch, <95% challenge); target 98% for the “queue words to make this comfortable” action; make read-and-listen one tap; group Bridge texts by author and story chapters as a series; track words read as a progress metric.

### 2.2 Vocabulary: frequency, encounters, depth

- **Frequency-first is right.** The deck is already ordered by corpus frequency, and the first 7,334 lemmas have sentences and audio; that is the correct backbone. The top ~2,000–3,000 lemmas cover most everyday speech; 5,000+ is where C1 reading starts to feel possible.
- **Encounters.** Estimates vary with the depth of knowledge: ~6 encounters can support recognition; ~10–12 for form–meaning; 15–20+ for productive, flexible knowledge. A meta-regression cited in a UNSW thesis predicted ~11–12 encounters for >50% on all measures. One sentence per word is not enough for depth.
- **Depth and chunks.** Up to ~50% of language is estimated to be formulaic (Erman & Warren, 2000). Collocations and multiword sequences are processed holistically, improve fluency and idiomaticity, and are often learned poorly from single words. Word families (noun/verb/adjective forms) matter too.
- **Context variation.** Learning a word in one sentence can become sentence memorisation. Varied contexts and productive retrieval are what transfer.

**Implication:** keep frequency order; add a second/third example sentence for reviews (from the corpus or AI); add chunk/collocation cards; add a “trouble words” (leech) view with extra contexts; do not simply increase the daily new-word count.

### 2.3 Retrieval practice and spacing

Spacing and retrieval practice are among the most robust findings in learning science. FSRS-6 is a good implementation; the app's four card modes (cloze, dictation, production, full dictée) already mix recognition and production. Interleaving helps when categories are confusable (similar tenses, ser/estar-type contrasts), especially across sessions and with feedback; for low-achieving beginners, blocked-then-interleaved is often safer than pure interleaving.

**Implication:** keep FSRS; add new-context cards and chunk cards; interleave grammar forms when they are confusable; do not add a second SRS scheduler or a currency economy.

### 2.4 Output, interaction and corrective feedback

- **Output and interaction** promote noticing, hypothesis testing, and fluency. Task-based language teaching has positive but contested meta-analytic support (Bryfonski & McKay 2019 d≈0.93; a 2025 re-analysis g≈0.61; Boers & Faez argue the field is not ready for a clean meta-analysis). The direction is positive; the magnitude is uncertain.
- **Corrective feedback** has moderate-to-large effects: Li (2010) d≈0.64; Lyster & Saito (2010) ≈0.74; written CF ≈0.5–0.7 (Kang & Han 2015; Brown et al. 2026 Bayesian meta-analysis); Truscott's dissent is much smaller (~0.15) but a minority. Explicit + metalinguistic feedback often beats implicit recasts. Timing is mixed: immediate feedback tends to win for simple tasks/grammar (Fu & Li 2022), delayed can be better for complex tasks; the review literature calls the advantage “equivocal.”
- **Revision matters.** Asking learners to revise after feedback adds to accuracy gains, especially when they keep the corrected draft; multiple drafts help, though effects can fade on delayed tasks. Direct feedback + metalinguistic explanation tends to outperform indirect feedback.
- **AI writing feedback** is strong on grammar/lexis and fast, weaker on content/organisation and affective nuance; hybrid human+AI is best in the studies, and hallucinated corrections are a real risk. ChatGPT feedback studies (2024–2025) show gains, mostly small/short and in EFL contexts.
- **AI conversation partners** are promising but early: Ding & Yusof (2025), n=60 Chinese B1 learners, 6 weeks, found IELTS speaking +0.72 (d=0.60) and speaking anxiety −4.13 (d=0.76) versus a matched practice control; a 2025 systematic review of 30 studies is positive but notes vague feedback and emotional detachment. One Korean beginner study found no overall speaking gain. Treat AI chat as a supplement, not a replacement for human interaction.

**Implication:** add voice conversation, 4/3/2 and shadowing; persist drafts and revise; store error kinds (the writing prompt already returns `grammar|spelling|agreement|conjugation|vocabulary|style`); turn recurring errors into targeted cards. Keep AI feedback focused on 1–3 high-impact issues, and label it as a suggestion.

### 2.5 Grammar

Explicit focus-on-form works (meta-analyses cluster around ES ≈0.6–0.9), and explicit often beats implicit; but isolated drills transfer poorly to communication. Integrated focus-on-form — noticing a form in meaningful input, then using it in output — is the better bet. Interleaving confusable forms helps, especially with feedback and across sessions.

**Implication:** do not remove the conjugation/grammar drills; make them serve communication. Target the current grammar/tense in AI chat/writing prompts; highlight it in reading/listening as an optional “notice” layer; convert grammar corrections into queued grammar cards.

### 2.6 Pronunciation

- The goal should be **intelligibility/comprehensibility, not native accent**. A 2025 meta-analysis (Hu, Kuo & Dixon) found pronunciation interventions improved intelligibility/comprehensibility with g≈0.62 (18 studies, 33 effects); Saito's (2021) meta-analysis found instruction improves comprehensibility more than nativelikeness, with prosodic/suprasegmental training especially useful.
- French-specific targets recur in the literature: nasal vowels, /y/ vs /u/, /ʁ/, liaison/enchaînement, silent letters, rhythm. French pronunciation instruction studies show production gains, but not always significant listener-rated comprehensibility gains (Inceoglu 2019) — a reminder that “we practised” is not the same as “listeners understood.”
- **Shadowing** improves perception and production in several studies (Hashimoto et al. 2022, n=16; Nakanishi's 42-day shadowing marathon; mobile shadowing studies), but effects are sound-dependent and often measured on trained material. **High-variability phonetic training** works, though the multitalker advantage is contested in recent studies.
- **ASR feedback** (ELSA Speak and similar) shows gains in small quasi-experiments, but ASR is better at segmental errors than prosody and can be unreliable across accents. Do not present a recognizer match as a phoneme score.

**Implication:** add a Sounds module with minimal pairs and explicit tips, perception first then production; use shadowing; let learners record and compare; use ASR only as a gross “did it hear the words” check.

### 2.7 Fluency: the missing strand

Fluency development means using **already-known** language faster and more smoothly — no new vocabulary or grammar. The best-known technique is **4/3/2**: speak on the same topic for 4, then 3, then 2 minutes. It reliably improves speech rate, pauses and mean length of runs (de Jong & Perfetti 2011; Boers 2014; Tran & Saito 2021); effects on accuracy are mixed, and adding self-transcription/feedback helps accuracy (Tran & Saito 2021; Kirk's self-awareness work). Task repetition and formulaic language are the mechanisms. Repeated reading and shadowing are the reading/listening equivalents.

Pluton currently has almost no fluency-development loop: read-aloud practice, AI chat and writing are output, but nothing asks the learner to repeat known language under time pressure. This is the clearest gap against the four-strands model.

**Implication:** add a Fluency mode with 4/3/2, repeated reading, and shadowing. It is cheap to build relative to its evidence and fits the existing Speak/Listen surfaces.

### 2.8 Motivation, flow and the “hooked” requirement

- **Self-determination theory:** autonomy, competence and relatedness drive intrinsic motivation. Competence tends to be the strongest predictor in language-learning studies; autonomy (choice) and relatedness (Pluton as a character, the story world) matter too.
- **Flow:** clear goals, immediate feedback, and challenge–skill balance. A 2025 systematic review of 123 studies found challenge–skill balance among the most operationalised flow dimensions; flow predicts oral proficiency gains in longitudinal work. The practical target is ~85–90% success, not 100%.
- **Gamification:** can support habit, but the evidence on streaks is mostly consumer research and qualitative work, not SLA RCTs. Silverman et al. (2023, JCR) found users can value extending a streak over the underlying activity, and streak-breakers are more likely to abandon the platform. Etkin (2016) found tracking can increase behaviour while reducing enjoyment. The Waterloo “streak creep” study describes children shifting from mastery to “metric maintenance — not quitting, not learning, just maintaining.” Duolingo's own reported data and user studies show streaks are the strongest engagement driver, alongside stress and guilt. The De Gruyter review calls much app gamification “black hat” (loss aversion, scarcity, unpredictability) rather than positive motivation.
- **Notifications:** a 9-week RCT with 150 adults found algorithmically timed notifications produced 82% daily compliance vs 67% self-scheduled and 49% none — but consistency dropped sharply when reminders stopped, suggesting dependency rather than habit. Implementation intentions (“after I pour my coffee, I practise 10 minutes”) and user-chosen cues are the healthier version.
- **Duolingo efficacy:** peer-reviewed studies (some company-funded) generally show reading/listening/receptive-vocabulary gains comparable to or above classroom instruction; speaking/writing are weaker. Treat commercial efficacy claims with the usual conflict-of-interest caution.

**Implication:** build a daily ritual, not a slot machine. Use autonomy (choose the story/topic/pace), competence (visible mastery, adaptive difficulty), relatedness (Pluton remembers; the story continues), flow (short, clear, just-hard-enough), and healthy habit cues (opt-in, user-chosen time, rest days). Do not use loss-framed streaks, hearts, leaderboards, guilt notifications, loot boxes or infinite play.

### 2.9 Assessment and level

The CEFR does not prescribe vocabulary sizes, and estimates vary: Milton's research suggests roughly A1 1,000–1,500, A2 2,000, B1 3,000, B2 3,500–4,000, C1 5,000 word families; popular claims are higher. Vocabulary size predicts reading proficiency but is not a test. Pluton's `levelOf` thresholds (150/600/2,000/4,000 known-or-learning) are lower than these estimates at A2/B1, which may under-challenge the AI tutor; the count is also “known or learning,” not verified receptive/productive knowledge.

**Implication:** show the level as an estimate with a range, separate receptive and productive estimates, and add short can-do/checkpoint tasks rather than claiming a CEFR level from card count alone.

---

## 3. Audit: what Pluton already does, and what is missing

| Surface | What exists | Evidence verdict | What to change |
|---|---|---|---|
| Run | FSRS-6, cloze/dictation/produce/dictée, frequency order, sentence + audio, conj/grammar slots | Strong foundation | Add new-context and chunk cards; leech/trouble-word drills; error-targeted cards |
| Read | Coverage colouring, 95% unlock list, tap-to-look-up, TTS/read-along, quiz, retell, Bridge, classics, user texts | Strong input layer | Comfort labels; 98% option; read-and-listen one tap; notice-form layer; series grouping |
| Listen | Own audio/video, subtitles, line replay, speed, tap words | Good input, passive | Add comprehension checks, dictogloss, shadowing, narrow-listening mode |
| Speak | Read-aloud, record/playback, ASR match, text Talk, writing correction | Partial | Add voice Talk, 4/3/2, shadowing, Sounds; persist and revise writing |
| More | Le Chemin, Words, Verbs, Grammar, Settings | Good reference, separate from communication | Add speaking/fluency goals; level report; cabinet; interweave grammar |
| Home | Stats, path stage, start run, summary | Functional | Add `Today` plan, onboarding, adaptive pace, last-session persistence |
| Local-only | SQLite, BYOK key, no server | Strong autonomy/privacy | Keep; prefer on-device ASR and label when speech recognition may use the network |

**What is genuinely missing:** a fluency-development loop; spoken interaction; pronunciation instruction; revision and error tracking; chunks; active listening; a habit/flow layer.

**What is not missing:** input volume potential, vocabulary data, SRS, feedback, stories, AI, local storage.

---

## 4. The hook: “The Daily Pluton”

The strongest hook is not a new currency. It is a short, reliable ritual that makes the learner feel competent and curious, and that continues a story. Design it as a **10–15 minute daily session** with a clear end:

1. **Wake Pluton (2–3 min).** FSRS review run (10–20 cards). Adaptive: if recent accuracy is <80%, fewer new words; if >90% and reviews are on time, offer +1–2 (the learner accepts, not automatic).
2. **Story (4–5 min).** One scene/chapter at ~98% coverage, read + listen, one comprehension question, 2–3 target words highlighted. Collect one chunk. The next scene is always available; practice makes it richer, not locked.
3. **Voice (2–3 min).** Shadow one line from the scene, then a 4/3/2 prompt tied to it. Record; AI gives one fluency tip and one accuracy tip. Alternate with voice chat with Pluton.
4. **Write (2–3 min).** One short paragraph using two target chunks; AI corrects 1–2 high-impact errors; the learner revises once.
5. **Close.** “Today's haul”: words strengthened, coverage gained, a chunk, a sound, Pluton's reaction, next scene teaser. A weekly consistency goal (e.g., 4 days), rest days built in, no loss framing.

**Why this should hook:**
- **Competence:** the learner sees “you now understand 94% of this story” and “you spoke for 2 minutes at 95 wpm,” not just XP.
- **Autonomy:** choose the story/topic, the session length, the pace; skip or swap a step.
- **Relatedness:** Pluton remembers the last topic and references it; the story world recurs. No social network needed.
- **Flow:** short tasks, clear goals, immediate feedback, difficulty just above current skill.
- **Investment:** the deck, the error log, the texts they pasted, the stories they chose — the app becomes more theirs over time.
- **Curiosity:** a daily “curio” (an etymology, a false friend, a line from Poe, a Bridge word) — deterministic, not a loot box.
- **Habit:** opt-in reminder at a user-chosen time, framed as an implementation intention (“After dinner, 10 minutes with Pluton”), max one per day, no guilt.

**What to avoid:** hearts/energy, leaderboards, loss-framed streaks, “you made Pluton sad,” artificial scarcity, pay-to-win, infinite feeds, and notifications that shame. These raise short-term metrics and damage the learner's relationship with the app; they also conflict with the local-first, no-account trust proposition.

---

## 5. Recommendations, prioritised

Effort is rough and covers shared core + both platforms.

### P0 — Make the existing loops stick (1–2 weeks)

| # | Change | Where | Effort | Evidence |
|---|---|---|---|---|
| 1 | **Onboarding + Today plan + adaptive pace + last-session persistence** | Home, Settings, shared `plan` | M–L | Flow, SDT, habit/implementation intentions, four strands |
| 2 | **Comfort labels + 98% target + read-and-listen** | Read, shared `unlockList`/`coverage` | S | Coverage research; RWL; extensive reading |
| 3 | **Persist writing drafts + revision + error log** | Write, store schema | M | Written CF + revision; error logs |
| 4 | **Speaking/fluency goals in Le Chemin** | Path, stats | S | Four strands; output/fluency |
| 5 | **Trouble-words view + extra context** | Words, Run | S–M | Encounters/context variation; leech handling |
| 6 | **Level estimate with a range, not a false CEFR claim** | More/Home | S | CEFR vocabulary research |

### P1 — Fill the missing output/fluency strand (2–4 weeks)

| # | Change | Where | Effort | Evidence |
|---|---|---|---|---|
| 7 | **Voice Talk** (push-to-talk → ASR → AI → TTS) | Speak/Talk | M | Output/interaction; AI chatbot studies |
| 8 | **4/3/2 + shadowing + repeated reading** | Speak, Listen, Read | M | 4/3/2, shadowing, task repetition |
| 9 | **Sounds module** (nasals, /y/–/u/, /ʁ/, liaison, rhythm; minimal pairs; record/compare) | Speak | M–L | Pronunciation instruction; HVPT; French-specific errors |
| 10 | **Corrections → targeted review cards** | Write/Talk → Run/Grammar | M | Corrective feedback; individualized remediation |

### P2 — Depth and input quality (3–6 weeks)

| # | Change | Where | Effort | Evidence |
|---|---|---|---|---|
| 11 | **Chunks/collocations + multiple contexts** | WordSheet, Run, data build | M–L | Formulaic sequences; depth; context variation |
| 12 | **Active listening** (comprehension check, dictogloss, narrow listening) | Listen | M | Dictogloss; narrow listening; captions |
| 13 | **Serialised stories / narrow reading** | Read, story generator | S–M | Narrow reading; extensive reading; narrative engagement |
| 14 | **Grammar targeting in AI prompts + notice layer in reading** | Talk/Write/Read | S–M | Focus-on-form; interleaving; transfer |

### P3 — Assessment and polish (later)

| # | Change | Where | Effort | Evidence |
|---|---|---|---|---|
| 15 | **CEFR checkpoints / can-do statements** | More/Path | M–L | Assessment; goal setting |
| 16 | **Collection/narrative layer** (cabinet, milestones, Pluton callbacks) | Home/More | M | SDT/competence; flow; narrative engagement |
| 17 | **Learning dashboard + experiments** | More | M | Measurement |

---

## 6. Concrete specs for the top five

### 6.1 Today plan and adaptive pace

- Shared: `DailyPlan(steps, estimatedMinutes, reason)`, `suggestedNewWords(recentAccuracy, dueCount, pace)`, `planFor(...)` with tests.
- Store: meta keys for onboarding answers, daily minutes, practice days, last plan, last position. No schema migration for the plan itself.
- UI: one `Today` card on Home with a progress ring and a single primary button; a “short session” option; each step opens the existing surface.
- Adaptive rule: target 85–90% review accuracy. If the last 20 reviews are <80%, reduce new words by 2 and add one extra context card; if >90% and reviews are on time, offer +2. Present as a suggestion with a reason (“You're at 92% — ready for two more?”).
- Onboarding: why (travel/reading/family), interests (Poe, Lovecraft, food, history…), daily time (5/10/20), optional 20-word check. Use answers to choose stories/Bridge texts and pace. Fully local.
- Persistence: remember the last tab, reading position, draft, and plan completion. Losing an essay or a chat on app close is an engagement bug.

### 6.2 Voice Talk, 4/3/2 and shadowing

- Voice Talk: a mic button in Talk. Record → on-device speech recognition where available → send the transcript (not audio) to the AI → show the text bubble → speak the reply with TTS. Keep the typed fallback. Label network use honestly.
- 4/3/2: one prompt, three rounds (4, 3, 2 minutes), record each; transcript word count and speech rate; AI feedback on fluency (rate, fillers, repetition) and one accuracy point; optional self-transcription and re-record.
- Shadowing: a sentence from the current reading/listening; play model → record → play both → ASR check → next. Add a “shadow this line” button in Read and Listen.
- Do not claim phoneme-level pronunciation scores. Use “the recognizer heard these words” language, as the app already does.

### 6.3 Writing revision and error log

- New local tables: `writing` (prompt, draft, corrected, errors JSON, feedback, level, revised draft) and/or `corrections` (source, kind, original, fix, why, created, resolved). The existing `WritingResult` already carries `kind`, so no prompt change is required.
- UI: a history list in Write; after feedback, “Revise this draft”; show the diff; store the second draft. A “Your errors” card aggregates by kind and links to the matching grammar topic or a mini-drill.
- Talk corrections: parse the correction line into the same error log (currently in-memory only). Keep the AI's correction as a suggestion, not ground truth.
- Focus: ask the model for at most 1–2 high-impact errors in Talk, and 3–5 in writing; too much feedback is noise.

### 6.4 Sounds module

- Curated `sounds.json`: target (e.g., “nasal /ɑ̃/”, “/y/ vs /u/”, “liaison”), a one-line tip in English, 5–10 minimal pairs/words, and example sentences. Audio from existing clips where possible, TTS otherwise.
- Loop: hear it → choose which you heard (minimal-pair discrimination) → say it → record/compare → next. Multiple voices where available.
- Integrate: when a word card contains a target sound, show a small “sound” chip in the word sheet; add a “Sounds” tab under Speak; add one sound goal to Le Chemin.
- French priorities: nasal vowels, /y/–/u/, /ʁ/, liaison/enchaînement, silent letters, rhythm. These recur in French learner-error studies.

### 6.5 Coverage-aware input

- Shared: `comfort(cov)` → `comfortable` (≥98%), `stretch` (95–98%), `challenge` (<95%); `wordsToComfort(tokens, statusOf, 0.98)`.
- Read: badges on every text; “Queue the words to reach 98%” (or a 95/98 toggle); recommend the next text by comfort + interest, not by list order.
- Read: “Read & listen” as the primary action for classics/story; TTS-only fallback for user texts.
- Listen: estimate subtitle coverage and show the same badges; recommend shorter/easier scenes first.
- Series: group Bridge texts by author; group story chapters; add “continue the story” to the AI story generator with recurring characters and recycled vocabulary.

---

## 7. What not to build

- **Hearts/energy/lives, leaderboards, loss-framed streaks, guilt notifications, loot boxes, pay-to-win, infinite feeds.** They can raise session counts and reduce learning/wellbeing; they also undermine the local-first, no-account trust position.
- **More isolated grammar drills as the main course.** Keep them as targeted remediation, not the product.
- **Phoneme-level scores from ASR.** Use recognition as a rough check and let learners compare recordings.
- **Auto-translating every sentence.** It removes the desirable difficulty that drives learning; keep tap-to-look-up and optional hints.
- **Locking content behind streaks or currency.** Pluton's “nothing is locked” is a strength; keep it.
- **Social features.** The local-only promise is a feature; relatedness comes from Pluton and the story, not from other users.

---

## 8. How to measure whether it works

**Learning**
- 30/90-day review retention (FSRS predicted vs. observed).
- Coverage growth on a fixed benchmark text; words read/listened per week.
- Reading speed (wpm) and speaking fluency (speech rate, mean length of runs, pauses) on repeated tasks.
- Error recurrence by kind (are the same errors coming back?).
- Can-do checkpoints at each CEFR stage (read/listen/speak/write tasks), not just vocabulary count.

**Engagement**
- D1/D7/D30 retention; session completion; time to first action; voluntary return after a break; self-reported enjoyment and perceived progress.
- Weekly consistency, not streak length. Track “practice days per week” and “returned after a missed day.”

**Guardrails**
- Never optimize time-on-app without a learning outcome.
- Cap notifications at one/day, opt-in, no guilt.
- Offer a clear session end and rest days.
- Run A/B tests on learning outcomes, not just session counts.

---

## 9. Key sources

**Input, coverage, extensive reading**
- Schmitt, Cobb, Horst & Schmitt (2017), *Language Teaching* — [Cambridge](https://www.cambridge.org/core/journals/language-teaching/article/how-much-vocabulary-is-needed-to-use-english-replication-of-van-zeeland-schmitt-2012-nation-2006-and-cobb-2007/1D217A56A2E0056E67802A6A8360FDDE)
- Hu & Nation (2000) — [record](https://experts.azregents.edu/en/publications/the-percentage-of-words-known-in-a-text-and-reading-comprehension/)
- Webb, Pellicer-Sánchez & Wang (2025) — [UCL](https://discovery.ucl.ac.uk/id/eprint/10216385/)
- Nation's four strands, applied — [J-STAGE](https://www.jstage.jst.go.jp/article/jacetselectedpapers/7/0/7_160/_article)
- Extensive reading volume — [Reading in a Foreign Language](https://scholarspace.manoa.hawaii.edu/server/api/core/bitstreams/dbad6713-e32d-41b3-8f1b-08ba19bdf38b/content)
- Narrow listening — [JALT Language Teacher](https://jalt-publications.org/files/pdf/the_language_teacher/06_2009tlt.pdf)
- Reading while listening — [Language Learning](https://onlinelibrary.wiley.com/doi/10.1111/lang.12721)
- French coverage — [Einlang](https://einlang.com/research/how-many-words-to-read-a-book/french)

**Vocabulary**
- Encounters meta-regression — [UNSW thesis](https://unsworks.unsw.edu.au/bitstreams/be264806-affd-4ee0-a4fa-b65a0241a2e6/download)
- Mobile-assisted vocabulary meta-analysis — [ReCALL](https://www.cambridge.org/core/journals/recall/article/metaanalysis-on-mobileassisted-vocabulary-learning-do-mobile-applications-help/79C8F00E48D521ED3BCFE0625B5549F3)
- Digital personalized vocabulary g=0.761 — [IJAL](https://onlinelibrary.wiley.com/doi/abs/10.1111/ijal.70269)
- Technology-assisted L2 vocabulary g=.64 — [ReCALL 2022](https://www.cambridge.org/core/services/aop-cambridge-core/content/view/08A549A6CFD1078406E6A4F8AFE28184/S0958344021000239a.pdf/a-meta-analysis-examining-technology-assisted-l2-vocabulary-learning.pdf)
- Formulaic sequences — [Boers & Lindstromberg 2012](https://www.cambridge.org/core/journals/annual-review-of-applied-linguistics/article/abs/formulaic-language-and-language-teaching/9900AC257193D8E5FB86F0E4EDC04D4F)

**Spacing, retrieval, interleaving**
- Spacing meta-analysis (Kim & Webb 2022) — [Language Learning](https://onlinelibrary.wiley.com/action/downloadSupplement?doi=10.1111%2Flang.12553&file=lang12553-sup-0002-Appendix.pdf)
- Quizlet meta-analysis — [Frontiers](https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2024.1349835/full)
- Interleaved grammar — [Learning and Instruction](https://www.sciencedirect.com/science/article/abs/pii/S0959475224001725)
- Interleaving boundary conditions — [Modern Language Journal](https://onlinelibrary.wiley.com/doi/10.1111/modl.70082)

**Output, interaction, feedback**
- Corrective feedback meta-analyses — [Li 2010](https://onlinelibrary.wiley.com/doi/10.1111/j.1467-9922.2010.00576.x); [Lyster & Saito 2010](https://doi.org/10.1017/S0272263110000264)
- Immediate vs delayed feedback — [Fu & Li 2022, SSLA](https://www.cambridge.org/core/journals/studies-in-second-language-acquisition/article/abs/effects-of-immediate-and-delayed-corrective-feedback-on-l2-development/B4B2D455749B752BD4F6DD636ACD688F)
- Revision after WCF — [System 2020](https://www.sciencedirect.com/science/article/abs/pii/S0346251X20307016)
- AI writing feedback — [CALL 2025](https://www.tandfonline.com/doi/full/10.1080/09588221.2025.2454541); [System 2025](https://www.sciencedirect.com/science/article/abs/pii/S0346251X25002155)
- AI conversation partners — [Ding & Yusof 2025, Nature HSSC](https://www.nature.com/articles/s41599-025-05550-z); [systematic review](https://www.sciencedirect.com/science/article/pii/S2215039025000086)

**Grammar**
- Focus-on-form meta-analyses — [KCI](https://www.kci.go.kr/kciportal/landing/article.kci?arti_id=ART002460229); [input vs output FFI](https://zenodo.org/records/14790529)
- Task-based language teaching — [Bryfonski & McKay](https://www.fachportal-paedagogik.de/literatur/vollanzeige.html?FId=eric_ej1500344)

**Pronunciation**
- Intelligibility meta-analysis — [Hu, Kuo & Dixon](https://doi.org/10.1177/13621688221106832)
- Saito (2021) — [TESOL Quarterly](https://doi.org/10.1002/tesq.3027); [OASIS summary](https://discovery.ucl.ac.uk/id/eprint/10156224/1/OASIS_Summary_Saito_2021.pdf)
- Shadowing — [Hashimoto et al. 2022](https://www.jstage.jst.go.jp/article/onseikenkyu/26/1/26_13/_article/-char/en); [Nakanishi 2022](https://researchmap.jp/nnakanishi/published_papers/44426247?lang=en)
- HVPT — [SSLA](https://www.cambridge.org/core/journals/studies-in-second-language-acquisition/article/does-timing-matter/B0E32465A5798D82AEACB078A5D24EB8)
- ELSA Speak quasi-experiment — [Taylor & Francis 2026](https://www.tandfonline.com/doi/full/10.1080/2331186X.2026.2748105)
- French pronunciation — [Inceoglu 2019](https://researchportalplus.anu.edu.au/en/publications/exploring-the-effects-of-instruction-on-l2-french-learner-pronunc)

**Fluency**
- 4/3/2 reappraisal — [Boers 2014 record](https://www.fachportal-paedagogik.de/literatur/vollanzeige.html?FId=eric_ej1047012)
- 4/3/2 + feedback — [Tran & Saito / UCL](https://discovery.ucl.ac.uk/id/eprint/10128571/)
- 4/3/2 recent test — [Guo & Saito 2025, System](https://www.sciencedirect.com/science/article/abs/pii/S0346251X2500346X)
- Task repetition meta-analysis — [System 2025](https://www.sciencedirect.com/science/article/abs/pii/S0346251X25002787)
- Dictogloss — [Nabei 1996](https://archive.org/details/ERIC_ED401759)

**Engagement, motivation, habit**
- Flow systematic review — [Language Teaching Research 2025](https://doi.org/10.1177/13621688251389358)
- SDT and language learning — [SSLA](https://www.cambridge.org/core/journals/studies-in-second-language-acquisition/article/abs/development-of-selfdetermination-across-the-language-course/183DC3D831E394C0A3BFE35D79D382A9)
- Streak creep — [The Decision Lab](https://thedecisionlab.com/es-ES/insights/consumer-insights/streak-creep-the-perils-of-too-much-gamification)
- Dark patterns in gamified apps — [Waterloo IDC 2026](https://csl.uwaterloo.ca/download/documents/reportsarticles/idc26a_sub2151_i6pdf;v1?attachment=1)
- Perfunctory gamification — [JCCALL](https://doi.org/10.1515/jccall-2021-0005)
- Notifications RCT — [KMAN](https://journals.kmanpub.com/index.php/aitechbesosci/article/download/4724/9100/28125)
- Duolingo efficacy — [Duolingo](https://en.duolingo.com/efficacy/studies); [SSLA French study](https://www.cambridge.org/core/journals/studies-in-second-language-acquisition/article/comparing-the-effectiveness-of-duolingo-classroom-instruction-and-classroom-duolingo-instruction-conditions-on-beginnerlevel-french-language-development/68C0E7E296669798089C84CDC7F3BB9E)

**Assessment**
- CEFR vocabulary estimates — [Milton](http://www.eurosla.org/monographs/EM01/211-232Milton.pdf); [Nation & Crabbe](https://www.wgtn.ac.nz/lals/resources/paul-nations-resources/vocabulary-lists/vocabulary-cefr-and-word-family-size/vocabulary-and-the-cefr-docx)
- Vocabulary size as a screener — [govtilr](https://govtilr.org/TC/ILR%20Reading%20and%20Vocabulary%20Size.pdf)

---

## 10. One-paragraph answer to “what is the best actual way to learn a language?”

Learn a lot of language you can almost understand, from many sources, about things you care about; use it to say and write real things; study the words, chunks, grammar and sounds that keep blocking you; and repeatedly practise the language you already know until it comes out quickly. Get feedback on meaning first and form second, space the review, and keep going for hundreds of hours. Apps help most when they make that diet easy, visible and enjoyable — not when they replace it with a single clever trick.
