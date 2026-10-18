"""Build the French data into public/data/fr/: words.json, forms.json, audio/*.mp3.

    uv run data/fr/build.py            # full build (TTS is resumable: existing mp3s are skipped)
    uv run data/fr/build.py --no-audio # data only

Expects the raw downloads listed in README.md under data/fr/raw/.
"""
import bz2
import csv
import hashlib
import io
import json
import re
import subprocess
import sys
import wave
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

RAW = Path(__file__).resolve().parent / "raw"
OUT = Path(__file__).resolve().parents[2] / "public" / "data" / "fr"
N_GLOSS = 10_000  # lemmas shipped with a gloss (reader lookups, mining)
N_DECK = 2_000  # lemmas that get a sentence + audio (the default deck)
EASY = 300  # sentence words within this rank don't count as "hard" (learned in the first weeks)
VOICE = RAW / "fr_FR-siwis-medium.onnx"

POS = {"NOM": "noun", "VER": "verb", "AUX": "verb", "ADJ": "adj", "ADV": "adv", "PRE": "prep",
       "CON": "conj", "ONO": "interj", "ADJ:num": "num"}
KAIKKI_POS = {"interj": "intj"}
SKIP_TAGS = {"obsolete", "archaic", "rare", "dated", "historical"}

# Hand-written glosses for the first cards a learner meets. Wiktionary's first senses for function words are often
# explanations ("du: Forms the partitive article."), and English->French recall needs ne/pas to be distinguishable.
GLOSS = {
    "de": "of; from", "ne": "not (the ne of ne … pas)", "pas": "not (the pas of ne … pas)",
    "le": "the (masculine); him, it", "la": "the (feminine); her, it", "les": "the (plural); them",
    "un": "a, an (masculine); one", "une": "a, an (feminine); one", "des": "some (plural of un / une)",
    "du": "some (de + le); of the", "au": "to the, at the (à + le)", "aux": "to the, at the (à + les)",
    "vous": "you (plural or formal)", "il": "he; it", "elle": "she; her; it", "nous": "we; us",
    "à": "to; at; in", "qui": "who; which, that", "en": "in; to; some, of it", "ça": "that; this; it",
    "que": "that; what; than", "ce": "this, that; it", "y": "there; (about) it", "plus": "more; no more (ne … plus)",
    "te": "you (object, informal)", "me": "me; to me", "se": "oneself; himself, herself, themselves",
    "moi": "me; I (stressed)", "toi": "you (informal, stressed)", "lui": "him; to him, to her",
    "si": "if; so; yes (contradicting)", "là": "there; here", "mon": "my (masculine)", "ma": "my (feminine)",
    "mes": "my (plural)", "ton": "your (informal, masculine)", "ta": "your (informal, feminine)",
    "tes": "your (informal, plural)", "son": "his, her, its (masculine)", "sa": "his, her, its (feminine)",
    "ses": "his, her, its, their (plural)", "notre": "our", "nos": "our (plural)", "votre": "your (plural or formal)",
    "vos": "your (plural or formal; plural noun)", "leurs": "their (plural noun)", "cette": "this, that (feminine)",
    "ces": "these, those", "quelques": "a few, some", "autres": "others", "ok": "OK",
    "dire": "to say; to tell", "vouloir": "to want", "venir": "to come", "suivre": "to follow",
    "parler": "to speak, to talk", "aimer": "to love; to like", "falloir": "to be necessary (il faut: one must)",
    "penser": "to think", "trouver": "to find", "arriver": "to arrive; to happen", "donner": "to give",
    "arrêter": "to stop; to arrest", "connaître": "to know (be familiar with)", "tuer": "to kill",
    "peu": "little, not much (un peu: a bit)", "homme": "man", "vie": "life", "dieu": "god",
    "fois": "time (occurrence), once", "chez": "at (someone's place)", "ami": "friend", "après": "after",
    "accord": "agreement (d'accord: OK)", "monsieur": "sir; Mr.; gentleman", "enfant": "child", "seul": "alone; only",
}
POS_OVERRIDES = {"peu": "adv"}  # Lexique tags "un peu" as a noun, which made the card "le peu"


def pos_of(cgram):
    return POS.get(cgram) or {"PRO": "pron", "ART": "article", "ADJ": "det"}.get(cgram.split(":")[0], "other")


# --- tokenizer: keep in sync with src/lib/logic.js ---
WORD = re.compile(r"[^\W\d_]+(?:['’-][^\W\d_]+)*'?")
ELISION = {"l": "le", "d": "de", "j": "je", "m": "me", "t": "te", "s": "se", "n": "ne", "c": "ce",
           "qu": "que", "jusqu": "jusque", "lorsqu": "lorsque", "puisqu": "puisque", "quoiqu": "quoique"}


def tokens(text, forms):
    """[(surface, lemma|None)]. Compounds Lexique knows stay whole; others split on ' and -."""
    out = []
    for m in WORD.finditer(text.replace("’", "'")):
        tok = m.group()
        key = tok.lower().replace("œ", "oe")  # Lexique spells "coeur", Tatoeba/Wiktionary mostly "cœur"
        if key in forms and not tok.endswith("'"):
            out.append((tok, forms[key]))
            continue
        for part in re.split(r"(?<=')|-", tok):
            if part:
                low = part.lower().replace("œ", "oe")
                out.append((part, ELISION.get(low[:-1]) if low.endswith("'") else forms.get(low)))
    return out


def occurrences(form, text):
    return len(re.findall(rf"(?<![^\W\d_]){re.escape(form)}(?![^\W\d_])", text, re.IGNORECASE))


# h aspiré nouns in the top ranks take le/la, not l'. ponytail: hand list, Lexique has no h-aspiré flag.
H_ASPIRE = {"hasard", "haut", "haine", "honte", "héros", "hall", "hache", "hibou", "hockey", "hamburger",
            "hanche", "hangar", "haricot", "harpe", "hauteur", "hâte", "hurlement", "housse", "hutte", "hublot",
            "hérisson", "hiérarchie", "homard", "huit", "haie", "hameau", "hamster", "handicap", "harcèlement",
            "hausse", "héron", "hêtre", "hoquet", "hotte", "houle", "hamac", "hareng", "hold-up", "hippie",
            "hobby", "hot-dog", "halte", "hurle", "hors-la-loi", "harem", "hall", "hollandais", "hongrois"}


def with_article(lemma, gender):
    if gender not in ("m", "f"):
        return lemma
    if lemma[0] in "aeiouhàâéèêëîïôûœ" and lemma not in H_ASPIRE:
        return "l'" + lemma
    return ("le " if gender == "m" else "la ") + lemma


def load_lexique():
    lem_freq = {}  # (lemma, cgram) -> lemma frequency (repeated on every row of that lemma)
    form_freq = defaultdict(float)  # (form, lemma) -> frequency summed over POS rows ("suis": être AUX+VER > suivre)
    noun_gender = {}  # lemma -> (form frequency, gender)
    with open(RAW / "Lexique383.tsv", encoding="utf-8") as f:
        for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            form, lemma, cgram = r["ortho"], r["lemme"], r["cgram"]
            if not cgram or not lemma:
                continue
            ff = float(r["freqfilms2"] or 0)
            lem_freq[lemma, cgram] = max(lem_freq.get((lemma, cgram), 0), float(r["freqlemfilms2"] or 0))
            form_freq[form, lemma] += ff
            if cgram == "NOM" and form == lemma and r["genre"] and ff >= noun_gender.get(lemma, (-1,))[0]:
                noun_gender[lemma] = (ff, r["genre"])
    total, top_cgram = defaultdict(float), {}
    for (lemma, cgram), fr in lem_freq.items():
        total[lemma] += fr
        if fr > top_cgram.get(lemma, ("", -1))[1]:
            top_cgram[lemma] = (cgram, fr)
    ranked = sorted((l for l in total if not l.endswith("'")), key=lambda l: -total[l])
    forms, by_lemma = {}, defaultdict(list)
    for (form, lemma), fr in sorted(form_freq.items(), key=lambda kv: kv[1]):
        forms[form] = lemma  # ascending, so the most frequent lemma for each form wins
        by_lemma[lemma].append(form)
    common = {l: set(fs[-3:]) for l, fs in by_lemma.items()}  # each lemma's 3 most frequent forms
    pos = {l: pos_of(top_cgram[l][0]) for l in ranked} | POS_OVERRIDES
    gender = {l: noun_gender[l][1] for l in ranked if pos[l] == "noun" and l in noun_gender}
    return ranked, forms, pos, gender, common


def load_glosses(wanted):
    """lemma -> short English gloss, preferring the kaikki entry whose POS matches Lexique's."""
    best = {}
    with open(RAW / "kaikki.org-dictionary-French.jsonl", encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            w = (d.get("word") or "").replace("œ", "oe")
            if w not in wanted:
                continue
            senses = [s for s in d.get("senses", []) if s.get("glosses") and not SKIP_TAGS & set(s.get("tags", []))]
            if not senses:
                continue
            gloss = "; ".join(dict.fromkeys(s["glosses"][0] for s in senses[:2]))
            if len(gloss) > 80:
                gloss = gloss[:80].rsplit(" ", 1)[0] + "…"
            score = (KAIKKI_POS.get(wanted[w], wanted[w]) != d.get("pos"), "spelling of" in gloss)
            if w not in best or score < best[w][0]:
                best[w] = (score, gloss)
    return {w: g for w, (_, g) in best.items()}


def tsv_bz2(name):
    with bz2.open(RAW / name, "rt", encoding="utf-8") as f:
        for line in f:
            yield line.rstrip("\n").split("\t")


def pick_sentences(rank, forms, common):
    """Best Tatoeba sentence per deck lemma: fewest hard words (ranked after it and past the first EASY lemmas,
    or names), then the target in one of its common forms ("a", not "aurais"), then length near 7.
    EASY keeps the very first words from all getting 3-word stubs."""
    fra_eng = {}
    for fra, eng in tsv_bz2("fra-eng_links.tsv.bz2"):
        fra_eng.setdefault(int(fra), int(eng))
    best = {}  # lemma -> (score, fra_id, text, form)
    for sid, _, text in tsv_bz2("fra_sentences.tsv.bz2"):
        sid = int(sid)
        if sid not in fra_eng or len(text) > 90 or re.search(r"[\d\"«»()\[\]]", text):
            continue
        toks = tokens(text, forms)
        if not 4 <= len(toks) <= 12 or any(l is None and not s[0].isupper() and not s.endswith("'") for s, l in toks):
            continue
        ranks = [rank.get(l, 10**6) for s, l in toks if l or not s.endswith("'")]  # names rank as unknown
        for surf, lemma in toks:
            r = rank.get(lemma, 10**6)
            if r >= N_DECK or surf.endswith("'"):
                continue
            rare_form = surf.lower().replace("œ", "oe") not in common.get(lemma, ())
            score = (sum(x > max(r, EASY) for x in ranks), rare_form, abs(len(toks) - 7))
            if (lemma not in best or score < best[lemma][0]) and occurrences(surf, text) == 1:
                best[lemma] = (score, sid, text, surf.lower())
    need = {v[1]: fra_eng[v[1]] for v in best.values()}
    want_eng = set(need.values())
    eng = {int(i): t for i, _, t in tsv_bz2("eng_sentences.tsv.bz2") if int(i) in want_eng}
    return {l: (text, eng[fra_eng[sid]], form) for l, (_, sid, text, form) in best.items() if fra_eng[sid] in eng}


def audio_id(text):
    return hashlib.sha1(text.encode()).hexdigest()[:12]


_voice = None


def _synth(job):
    global _voice
    path, text = job
    if _voice is None:
        from piper import PiperVoice
        _voice = PiperVoice.load(VOICE)
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        _voice.synthesize_wav(text, w)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "wav", "-i", "-", "-ac", "1", "-b:a", "40k", path],
                   input=buf.getvalue(), check=True)


def synth_all(texts):
    (OUT / "audio").mkdir(parents=True, exist_ok=True)
    jobs = [(str(OUT / "audio" / f"{audio_id(t)}.mp3"), t) for t in sorted(set(texts))]
    stale = {str(p) for p in (OUT / "audio").glob("*.mp3")} - {j[0] for j in jobs}
    for p in stale:
        Path(p).unlink()
    jobs = [j for j in jobs if not Path(j[0]).exists()]
    print(f"tts: {len(jobs)} clips to render, {len(stale)} stale removed", flush=True)
    with ProcessPoolExecutor(8) as pool:
        for i, _ in enumerate(pool.map(_synth, jobs, chunksize=16), 1):
            if i % 500 == 0:
                print(f"  {i}/{len(jobs)}", flush=True)


def main():
    ranked, forms, pos, gender, common = load_lexique()
    ranked = [l for l in ranked if pos[l] != "other"][:N_GLOSS]
    rank = {l: i for i, l in enumerate(ranked)}
    print(f"lexique: {len(forms)} forms, top lemmas: {ranked[:12]}", flush=True)
    glosses = load_glosses({l: pos[l] for l in ranked}) | GLOSS
    deck_lemmas = [l for l in ranked[:N_DECK] if pos[l] != "interj" and not (pos[l] == "noun" and len(l) < 2)]
    sentences = pick_sentences(rank, forms, common)
    words, tts = [], []
    for l in ranked:
        w = {"lemma": l, "pos": pos[l], "display": with_article(l, gender.get(l)), "gloss": glosses.get(l, "")}
        if l in gender:
            w["gender"] = gender[l]
        if l in deck_lemmas and l in sentences:
            w["sentence"], w["translation"], w["form"] = sentences[l]
            w["audio_word"], w["audio_sentence"] = audio_id(w["display"]), audio_id(w["sentence"])
            tts += [w["display"], w["sentence"]]
        words.append(w)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "words.json").write_text(json.dumps(words, ensure_ascii=False, separators=(",", ":")))
    # Only forms of shipped lemmas: unknown forms fall back to "unknown word" in the reader anyway.
    (OUT / "forms.json").write_text(json.dumps({f: l for f, l in forms.items() if l in rank}, ensure_ascii=False,
                                               separators=(",", ":")))
    deck = [w for w in words if "sentence" in w]
    print(f"words: {len(words)} ({sum(bool(w['gloss']) for w in words)} glossed), deck: {len(deck)}/{len(deck_lemmas)}")
    if "--no-audio" not in sys.argv:
        synth_all(tts)
    check(words, forms)


def check(words, forms):
    deck = [w for w in words if "sentence" in w]
    assert len(deck) >= 1500, len(deck)
    assert sum(bool(w["gloss"]) for w in words[:N_DECK]) >= 0.9 * N_DECK
    for w in deck:
        assert occurrences(w["form"], w["sentence"]) == 1, w
        if "--no-audio" not in sys.argv:
            for k in ("audio_word", "audio_sentence"):
                assert (OUT / "audio" / f"{w[k]}.mp3").stat().st_size > 0, w
    assert forms["est"] == "être" and forms["suis"] == "être" and forms["a"] == "avoir", "form→lemma"
    assert [l for _, l in tokens("L'homme qu'il voit aujourd'hui, dit-il.", forms)] == \
        ["le", "homme", "que", "il", "voir", "aujourd'hui", "dire", "il"]
    assert with_article("homme", "m") == "l'homme" and with_article("hasard", "m") == "le hasard"
    by = {w["lemma"]: w for w in words}
    assert by["vous"]["gloss"] == GLOSS["vous"] and by["peu"]["display"] == "peu", "overrides applied"
    assert all(l in by for l in GLOSS), [l for l in GLOSS if l not in by]
    print("checks ok")


if __name__ == "__main__":
    main()
