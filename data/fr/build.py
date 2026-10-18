"""Build the French data into public/data/fr/: words.json, forms.json, audio/*.mp3.

    uv run data/fr/build.py            # full build (TTS is resumable: existing mp3s are skipped)
    uv run data/fr/build.py --no-audio # data only

Expects the raw downloads listed in README.md under data/fr/raw/.
"""
import bz2
import csv
from collections import Counter
import hashlib
import io
import json
import re
import subprocess
import unicodedata
import sys
import wave
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

RAW = Path(__file__).resolve().parent / "raw"
OUT = Path(__file__).resolve().parents[2] / "public" / "data" / "fr"
N_GLOSS = 20_000  # lemmas shipped with a gloss (reader lookups, mining)
N_DECK = 8_000  # lemmas that get a sentence + audio: the road to C1-level reading (~400 days at 20/day)
EASY = 300  # sentence words within this rank don't count as "hard" (learned in the first weeks)
VOICE = RAW / "fr_FR-siwis-medium.onnx"
TTS_WORKERS, TTS_THREADS = 12, 2  # ONNX Runtime defaults to a thread per core in *each* worker: cap it

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
POS_OVERRIDES = {"peu": "adv"}

# --- conjugation drills ---
N_CONJ = 60  # most frequent verbs that get conjugation tables
PERSONS = ["1s", "2s", "3s", "1p", "2p", "3p"]
PRONOUNS = ["je", "tu", "il", "nous", "vous", "ils"]
TENSES = ["ind:pre", "ind:imp", "ind:fut", "cnd:pre", "sub:pre"]  # + "pc" (passé composé), built from aux + participle
ETRE_VERBS = {"aller", "venir", "arriver", "partir", "entrer", "sortir", "monter", "descendre", "naître", "mourir",
              "rester", "tomber", "retourner", "devenir", "revenir", "rentrer", "parvenir"}
AUX = {"avoir": ["ai", "as", "a", "avons", "avez", "ont"], "être": ["suis", "es", "est", "sommes", "êtes", "sont"]}
# Lexique is corpus-based, so rare forms are missing. Two fills that hold for every verb outside these sets:
# subjonctif nous/vous = imparfait nous/vous; subjonctif ils = présent ils. (Compounds of faire follow faire.)
SUB_IRREG_NOUS = {"être", "avoir", "faire", "pouvoir", "savoir"}
SUB_IRREG_ILS = {"être", "avoir", "aller", "faire", "pouvoir", "savoir", "vouloir", "valoir"}
IMPERSONAL = {"falloir", "pleuvoir"}
PRONOMINAL = {"souvenir", "enfuir", "évanouir", "absenter", "méfier", "moquer"}  # "je me souviens": needs reflexives
BOTH_AUX = {"passer"}  # "j'ai passé" and "je suis passé" are both everyday: no passé composé drill
# Written out: the two most irregular verbs, and Lexique mis-tags them ("sommes" as subjonctif, "étaient" as présent).
FULL = {
    "être": {"ind:pre": ["suis", "es", "est", "sommes", "êtes", "sont"],
             "ind:imp": ["étais", "étais", "était", "étions", "étiez", "étaient"],
             "ind:fut": ["serai", "seras", "sera", "serons", "serez", "seront"],
             "cnd:pre": ["serais", "serais", "serait", "serions", "seriez", "seraient"],
             "sub:pre": ["sois", "sois", "soit", "soyons", "soyez", "soient"]},
    "avoir": {"ind:pre": ["ai", "as", "a", "avons", "avez", "ont"],
              "ind:imp": ["avais", "avais", "avait", "avions", "aviez", "avaient"],
              "ind:fut": ["aurai", "auras", "aura", "aurons", "aurez", "auront"],
              "cnd:pre": ["aurais", "aurais", "aurait", "aurions", "auriez", "auraient"],
              "sub:pre": ["aie", "aies", "ait", "ayons", "ayez", "aient"]},
}
ENDINGS = {"ind:imp": ["ais", "ais", "ait", "ions", "iez", "aient"], "ind:fut": ["ai", "as", "a", "ons", "ez", "ont"],
           "cnd:pre": ["ais", "ais", "ait", "ions", "iez", "aient"], "sub:pre": ["e", "es", "e", "ions", "iez", "ent"]}
VOWEL = "aeiouhâàéèêëîïôûœ"  # Lexique tags "un peu" as a noun, which made the card "le peu"


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
            # Spoken (film subtitles) and written (books) frequency, averaged: C1 means reading too, not just dialogue.
            ff = (float(r["freqfilms2"] or 0) + float(r["freqlivres"] or 0)) / 2
            lf = (float(r["freqlemfilms2"] or 0) + float(r["freqlemlivres"] or 0)) / 2
            lem_freq[lemma, cgram] = max(lem_freq.get((lemma, cgram), 0), lf)
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
    """(lemma -> short English gloss, preferring the kaikki entry whose POS matches Lexique's;
        lemma -> English words in its first glosses, for cognates; lemmas French borrowed from English)."""
    best, gloss_words, from_english = {}, defaultdict(set), set()
    with open(RAW / "kaikki.org-dictionary-French.jsonl", encoding="utf-8") as f:
        for line in f:
            d = json.loads(line)
            w = (d.get("word") or "").replace("œ", "oe")
            if w not in wanted:
                continue
            senses = [s for s in d.get("senses", []) if s.get("glosses") and not SKIP_TAGS & set(s.get("tags", []))]
            if not senses:
                continue
            ety = d.get("etymology_text") or ""
            if re.match(r"(?:(?:Partly )?[Bb]orrowed|Unadapted borrowing|Semantic loan|Calque) from English|From English", ety) or any(
                    t.get("name") in ("bor", "bor+", "ubor", "lbor") and t.get("args", {}).get("2") == "en"
                    for t in d.get("etymology_templates", [])):
                from_english.add(w)  # came from English: the wrong direction for the Norman Bridge
            for s in senses[:6]:
                gloss_words[w].update(x.lower() for x in re.findall(r"[A-Za-z]{4,}", s["glosses"][0]))
            gloss = "; ".join(dict.fromkeys(s["glosses"][0] for s in senses[:2]))
            if len(gloss) > 80:
                gloss = gloss[:80].rsplit(" ", 1)[0] + "…"
            score = (KAIKKI_POS.get(wanted[w], wanted[w]) != d.get("pos"), "spelling of" in gloss)
            if w not in best or score < best[w][0]:
                best[w] = (score, gloss)
    return {w: g for w, (_, g) in best.items()}, gloss_words, from_english


# --- Norman Bridge: English words that came from (Old/Norman) French, and the French word they map to ---
# Spelling correspondences English kept from Old French. Each variant of the French word is tried against the
# English words in its own definition, so false friends can't match ("large" is defined as "wide").
SUFFIXES = [("eux", "ous"), ("euse", "ous"), ("té", "ty"), ("ie", "y"), ("eur", "or"), ("eur", "our"), ("ique", "ic"),
            ("ier", "er"), ("aire", "ary"), ("oire", "ory"), ("isme", "ism"), ("iste", "ist"), ("er", ""), ("ir", ""),
            ("re", ""), ("é", "ed"), ("e", "")]
# Classic faux amis: English word -> (French look-alike, what it really means). Taught, not mapped.
FALSE_FRIENDS = {
    "library": ("librairie", "bookshop"), "actually": ("actuellement", "currently"),
    "eventually": ("éventuellement", "possibly"), "sensible": ("sensible", "sensitive"), "large": ("large", "wide"),
    "lecture": ("lecture", "reading"), "location": ("location", "rental"), "deception": ("déception", "disappointment"),
    "sympathetic": ("sympathique", "nice, friendly"), "journey": ("journée", "day"), "rest": ("rester", "to stay"),
    "attend": ("attendre", "to wait"), "assist": ("assister", "to attend"), "demand": ("demander", "to ask"),
    "chance": ("chance", "luck"), "coin": ("coin", "corner"), "pain": ("pain", "bread"), "chair": ("chair", "flesh"),
    "sale": ("sale", "dirty"), "bless": ("blesser", "to injure"), "injure": ("injurier", "to insult"),
    "achieve": ("achever", "to finish"), "resume": ("résumer", "to summarize"), "crayon": ("crayon", "pencil"),
    "petrol": ("pétrole", "crude oil"), "figure": ("figure", "face"), "rude": ("rude", "harsh, rough"),
    "college": ("collège", "middle school"), "raisin": ("raisin", "grape"), "money": ("monnaie", "change, currency"),
    "vacancy": ("vacances", "holidays"), "envy": ("envie", "desire, wish"), "habit": ("habit", "garment"),
    "patron": ("patron", "boss"), "prejudice": ("préjudice", "harm"), "comprehensive": ("compréhensif", "understanding"),
    "luxury": ("luxure", "lust"), "preservative": ("préservatif", "condom"), "grape": ("grappe", "bunch"),
}


def plain(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn").replace("œ", "oe")


def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def spellings(fr):
    """[(English-looking spelling of a French word, clue)]: as is, lost s under a circumflex, é- for es-, suffixes."""
    out = [(plain(fr), "")]
    if re.search("[âêîôû]", fr):
        out.append((plain(re.sub("([âêîôû])", lambda m: plain(m.group(1)) + "s", fr)), "circumflex"))
    if re.match("[ée][spct]", fr):
        out += [("s" + plain(fr[1:]), "es"), ("es" + plain(fr[1:]), "es")]
    for a, b in SUFFIXES:
        for v, clue in list(out):
            if v.endswith(plain(a)) and len(v) > len(a) + 2:
                out.append((v[: -len(plain(a))] + b, clue or f"{a}>{b}"))
    return out


def cognates(rank, gloss_words, from_english, deck):
    """english -> [french, clue]: English words found in a French word's definition that look like it. Identical
    spellings must be common French (in the deck): rare ones are mostly unflagged English loans ("house")."""
    best = {}
    for fr, ens in gloss_words.items():
        if len(fr) < 4 or fr in from_english or fr not in rank:
            continue
        for en in ens - FALSE_FRIENDS.keys():
            if en == plain(fr) and fr not in deck:
                continue
            for v, clue in spellings(fr):
                sim = 1 - lev(v, en) / max(len(v), len(en))
                if sim >= 0.75 and (en not in best or (sim, -rank[fr]) > best[en][0]):
                    best[en] = ((sim, -rank[fr]), fr, clue)
    out = {en: [fr, clue] for en, (_, fr, clue) in sorted(best.items())}
    return out | {en: [fr, "faux:" + meaning] for en, (fr, meaning) in FALSE_FRIENDS.items()}


def phrase(i, tense, form):
    """What gets spoken for one cell: "j'irai", "que nous soyons", "qu'il aille", "ils sont allés"."""
    pron = "j'" if i == 0 and form[0] in VOWEL else PRONOUNS[i]
    text = pron + form if pron.endswith("'") else f"{pron} {form}"
    if tense == "sub:pre":
        text = ("qu'" if text.startswith("il") else "que ") + text
    return text


def unsoft(stem):
    """commenç- / mange- -> commenc- / mang-: the spelling change before a/o isn't a different stem."""
    return stem[:-1] + "c" if stem.endswith("ç") else stem[:-1] if stem.endswith("ge") else stem


def agree(cells, endings, persons=range(6)):
    """Keep only cells with the right ending and the stem most cells share; mis-tagged forms drop out."""
    stems = [unsoft(cells[i][: -len(endings[i])]) if cells[i] and cells[i].endswith(endings[i]) else None for i in persons]
    votes = Counter(s for s in stems if s)
    if not votes:
        return {}
    top = votes.most_common(1)[0][0]
    return {i: cells[i] for i, s in zip(persons, stems) if s == top}


def validate(v, t):
    """Tenses with regular endings checked against a shared stem; présent nous/vous/ils endings sanity-checked."""
    out = {tense: [None] * 6 for tense in TENSES}
    for i, f in agree(t["ind:imp"], ENDINGS["ind:imp"]).items():
        out["ind:imp"][i] = f
    fc = agree(t["ind:fut"] + t["cnd:pre"], ENDINGS["ind:fut"] + ENDINGS["cnd:pre"], range(12))  # one stem for both
    for i, f in fc.items():
        out["ind:fut" if i < 6 else "cnd:pre"][i % 6] = f
    for i, f in agree(t["sub:pre"], ENDINGS["sub:pre"], (0, 1, 2, 5)).items():
        out["sub:pre"][i] = f
    for i, f in agree(t["sub:pre"], ENDINGS["sub:pre"], (3, 4)).items():
        out["sub:pre"][i] = f
    pre = t["ind:pre"]
    ok = [True, True, True, bool(pre[3] and pre[3].endswith("ons")),
          bool(pre[4] and (pre[4].endswith("ez") or pre[4] in ("faites", "dites"))),
          bool(pre[5] and pre[5].endswith(("ent", "ont")))]
    out["ind:pre"] = [f if good else None for f, good in zip(pre, ok)]
    return out


def load_conjugations(verbs):
    """verb -> {aux, pp, t: {tense: [6 forms or None]}} from Lexique's infover tags (mood:tense:person)."""
    best = defaultdict(dict)  # verb -> code -> (frequency, form)
    with open(RAW / "Lexique383.tsv", encoding="utf-8") as f:
        for r in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            if r["cgram"] not in ("VER", "AUX") or r["lemme"] not in verbs:
                continue
            ff = float(r["freqfilms2"] or 0) + float(r["freqlivres"] or 0)
            for code in filter(None, r["infover"].split(";")):
                if code == "par:pas" and (r["genre"] == "f" or r["nombre"] == "p"):
                    continue  # keep the masculine singular participle
                if ff >= best[r["lemme"]].get(code, (-1,))[0]:
                    best[r["lemme"]][code] = (ff, r["ortho"])
    out = {}
    for v in verbs:
        p = {code: form for code, (_, form) in best[v].items()}
        if "par:pas" not in p:
            continue
        get = lambda t, i: p.get(f"{t}:{PERSONS[i]}")
        t = FULL.get(v) or {tense: [get(tense, i) for i in range(6)] for tense in TENSES}
        follows_faire = v.endswith("faire")
        if v not in SUB_IRREG_NOUS and not follows_faire:
            t["sub:pre"][3] = t["sub:pre"][3] or get("ind:imp", 3)
            t["sub:pre"][4] = t["sub:pre"][4] or get("ind:imp", 4)
        if v not in SUB_IRREG_ILS and not follows_faire:
            t["sub:pre"][5] = t["sub:pre"][5] or get("ind:pre", 5)
        if v not in FULL:
            t = validate(v, t)
        aux = "être" if v in ETRE_VERBS else "avoir"
        pp = p["par:pas"]
        plural = pp if pp.endswith("s") else pp + "s"
        t["pc"] = [f"{AUX[aux][i]} {plural if aux == 'être' and i >= 3 else pp}" for i in range(6)]
        if v in BOTH_AUX:
            del t["pc"]
        t = {tense: forms for tense, forms in t.items() if sum(f is not None for f in forms) >= 5}  # else too gappy
        out[v] = {"aux": aux, "pp": pp, "t": t,
                  "audio": {tense: [forms[i] and audio_id(phrase(i, tense, forms[i])) for i in range(6)] for tense, forms in t.items()}}
    return out


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
        import onnxruntime
        from piper import PiperVoice
        _voice = PiperVoice.load(VOICE)
        opts = onnxruntime.SessionOptions()
        opts.intra_op_num_threads, opts.inter_op_num_threads = TTS_THREADS, 1
        _voice.session = onnxruntime.InferenceSession(str(VOICE), sess_options=opts, providers=["CPUExecutionProvider"])
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        _voice.synthesize_wav(text, w)
    tmp = path + ".part"  # rename when complete, so an interrupted build never leaves a short clip that counts as done
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "wav", "-i", "-", "-ac", "1", "-b:a", "40k", "-f", "mp3", tmp],
                   input=buf.getvalue(), check=True)
    Path(tmp).replace(path)


def synth_all(texts):
    (OUT / "audio").mkdir(parents=True, exist_ok=True)
    jobs = [(str(OUT / "audio" / f"{audio_id(t)}.mp3"), t) for t in sorted(set(texts))]
    stale = {str(p) for p in (OUT / "audio").glob("*.mp3")} - {j[0] for j in jobs}
    for p in stale:
        Path(p).unlink()
    jobs = [j for j in jobs if not Path(j[0]).exists()]
    print(f"tts: {len(jobs)} clips to render, {len(stale)} stale removed", flush=True)
    with ProcessPoolExecutor(TTS_WORKERS) as pool:
        for i, _ in enumerate(pool.map(_synth, jobs, chunksize=16), 1):
            if i % 500 == 0:
                print(f"  {i}/{len(jobs)}", flush=True)


def main():
    ranked, forms, pos, gender, common = load_lexique()
    ranked = [l for l in ranked if pos[l] != "other"][:N_GLOSS]
    rank = {l: i for i, l in enumerate(ranked)}
    print(f"lexique: {len(forms)} forms, top lemmas: {ranked[:12]}", flush=True)
    glosses, gloss_words, from_english = load_glosses({l: pos[l] for l in ranked})
    glosses |= GLOSS
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
    verbs = [l for l in ranked if pos[l] == "verb" and l not in IMPERSONAL | PRONOMINAL and l in sentences][:N_CONJ]
    conj = load_conjugations(set(verbs))
    conj = {v: conj[v] for v in verbs if v in conj}  # frequency order
    (OUT / "conj.json").write_text(json.dumps(conj, ensure_ascii=False, separators=(",", ":")))
    cog = cognates(rank, gloss_words, from_english, {w["lemma"] for w in deck})
    (OUT / "cognates.json").write_text(json.dumps(cog, ensure_ascii=False, separators=(",", ":")))
    print(f"cognates: {len(cog)} English words with a French counterpart")
    shelf = json.loads((Path(__file__).parent / "shelf.json").read_text())  # from data/fr/shelf.py
    (OUT / "shelf.json").write_text(json.dumps(shelf, ensure_ascii=False, separators=(",", ":")))
    tts += [phrase(i, tense, f) for c in conj.values() for tense, forms in c["t"].items() for i, f in enumerate(forms) if f]
    print(f"conj: {len(conj)} verbs, {sum(len(c['t']) for c in conj.values())} tables")
    if "--no-audio" not in sys.argv:
        synth_all(tts)
    check(words, forms, conj, cog)


def check(words, forms, conj, cog):
    deck = [w for w in words if "sentence" in w]
    assert len(deck) >= 0.75 * N_DECK, len(deck)  # most deck lemmas find a usable Tatoeba sentence
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
    # conjugation spot checks: irregulars from Lexique, the two fills, passé composé agreement, spoken phrases
    assert conj["aller"]["t"]["ind:fut"][3] == "irons" and conj["être"]["t"]["sub:pre"][3] == "soyons"
    assert conj["faire"]["t"]["sub:pre"][3] == "fassions" and conj["prendre"]["t"]["sub:pre"][5] == "prennent"
    assert conj["venir"]["t"]["sub:pre"][3] == "venions" and conj["parler"]["t"]["ind:imp"][5] == "parlaient"
    assert conj["aller"]["t"]["pc"][0] == "suis allé" and conj["aller"]["t"]["pc"][3] == "sommes allés"
    assert conj["avoir"]["t"]["pc"][0] == "ai eu" and conj["pouvoir"]["t"]["cnd:pre"][0] == "pourrais"
    assert conj["être"]["t"]["sub:pre"] == FULL["être"]["sub:pre"]
    assert conj["commencer"]["t"]["ind:imp"][0] == "commençais" and conj["commencer"]["t"]["ind:imp"][3] == "commencions"
    assert conj["manger"]["t"]["ind:imp"][3] == "mangions" and conj["manger"]["t"]["ind:imp"][2] == "mangeait"
    assert phrase(0, "ind:fut", "irai") == "j'irai" and phrase(2, "sub:pre", "aille") == "qu'il aille"
    assert phrase(3, "sub:pre", "soyons") == "que nous soyons" and "falloir" not in conj and "souvenir" not in conj
    assert "pc" not in conj["passer"]["t"] and "pc" in conj["parler"]["t"]
    # Norman Bridge spot checks: plain cognates, the two sound-change clues, loanwords out, faux amis taught
    assert cog["melancholy"][0] == "mélancolie" and cog["horror"][0] == "horreur" and cog["chamber"][0] == "chambre"
    assert cog["forest"] == ["forêt", "circumflex"] and cog["strange"] == ["étrange", "es"]
    assert "house" not in cog and cog["library"] == ["librairie", "faux:bookshop"]
    assert "door" not in cog and "window" not in cog  # Germanic, not Norman
    if "--no-audio" not in sys.argv:
        clips = [a for c in conj.values() for ids in c["audio"].values() for a in ids if a]
        assert all((OUT / "audio" / f"{a}.mp3").stat().st_size > 0 for a in clips), "conjugation clip missing"
    print("checks ok")


if __name__ == "__main__":
    main()
