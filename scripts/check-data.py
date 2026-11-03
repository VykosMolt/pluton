"""Check generated French assets before packaging; no raw downloads or TTS dependencies required."""
import json
from pathlib import Path
import re
import sys

DATA = Path(__file__).resolve().parents[1] / "public/data/fr"
NAMES = ("words", "forms", "conj", "cognates", "story", "shelf", "grammar")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def text(value):
    return isinstance(value, str) and bool(value.strip())


def validate(directory):
    data = {}
    for name in NAMES:
        with (directory / f"{name}.json").open(encoding="utf-8") as file:
            data[name] = json.load(file)
        expected = list if name in ("words", "story", "shelf", "grammar") else dict
        require(isinstance(data[name], expected) and data[name], f"{name}.json: expected a nonempty {expected.__name__}")

    clips, lemmas = set(), set()

    def clip(value, where):
        require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{12}", value), f"{where}: invalid audio id")
        clips.add(value)

    for word in data["words"]:
        require(isinstance(word, dict) and all(text(word.get(k)) for k in ("lemma", "pos", "display"))
                and isinstance(word.get("gloss"), str), "words.json: invalid word")
        require(word["lemma"] not in lemmas, f"words.json: duplicate lemma {word['lemma']}")
        lemmas.add(word["lemma"])
        if "sentence" in word:
            require(all(text(word.get(k)) for k in ("sentence", "translation", "form")), f"{word['lemma']}: incomplete sentence")
            for key in ("audio_word", "audio_sentence"):
                clip(word.get(key), f"{word['lemma']}.{key}")
    require(all(text(form) and lemma in lemmas for form, lemma in data["forms"].items()),
            "forms.json: a form points to an unknown lemma")
    require(all(text(en) and isinstance(pair, list) and len(pair) == 2 and text(pair[0]) and isinstance(pair[1], str)
                for en, pair in data["cognates"].items()), "cognates.json: invalid word pair")

    for verb, table in data["conj"].items():
        require(verb in lemmas and isinstance(table, dict) and table.get("aux") in ("être", "avoir")
                and text(table.get("pp")), f"conj.json: invalid verb {verb}")
        tenses, audio = table.get("t"), table.get("audio")
        require(isinstance(tenses, dict) and tenses and isinstance(audio, dict) and tenses.keys() == audio.keys(),
                f"conj.json: incomplete tables for {verb}")
        for tense, forms in tenses.items():
            ids = audio[tense]
            require(isinstance(forms, list) and len(forms) == 6 and isinstance(ids, list) and len(ids) == 6,
                    f"conj.json: {verb}/{tense} must have six cells")
            for form, audio_id in zip(forms, ids):
                require((form is None and audio_id is None) or text(form), f"conj.json: invalid {verb}/{tense} cell")
                if form is not None:
                    clip(audio_id, f"conj.json: {verb}/{tense}")

    for name in ("story", "shelf"):
        for entry in data[name]:
            require(isinstance(entry, dict) and text(entry.get("title")) and text(entry.get("body")), f"{name}.json: invalid text")
            if name == "shelf":
                require(entry.get("lang") in ("fr", "en") and text(entry.get("author")) and text(entry.get("source")),
                        f"shelf.json: incomplete metadata for {entry['title']}")
            sentences = entry.get("sentences", [])
            require(isinstance(sentences, list) and (sentences or name == "shelf" and entry["lang"] == "en"),
                    f"{entry['title']}: missing read-along sentences")
            previous = 0
            for row in sentences:
                require(isinstance(row, list) and len(row) == 3, f"{entry['title']}: invalid sentence")
                start, end, audio_id = row
                require(isinstance(start, int) and isinstance(end, int) and previous <= start < end <= len(entry["body"])
                        and entry["body"][start:end].strip(), f"{entry['title']}: invalid sentence offsets")
                previous = end
                clip(audio_id, entry["title"])
            if name == "story":
                questions = entry.get("questions")
                require(isinstance(questions, list) and questions, f"{entry['title']}: missing comprehension quiz")
                for question in questions:
                    require(isinstance(question, dict) and text(question.get("q")) and text(question.get("why"))
                            and isinstance(question.get("a"), list) and len(question["a"]) >= 3
                            and all(text(a) for a in question["a"]) and isinstance(question.get("ok"), int)
                            and 0 <= question["ok"] < len(question["a"]), f"{entry['title']}: invalid comprehension question")

    topic_ids = set()
    for topic in data["grammar"]:
        require(isinstance(topic, dict) and all(text(topic.get(k)) for k in ("id", "name", "how"))
                and topic.get("level") in ("A1", "A2", "B1", "B2", "C1", "C2")
                and isinstance(topic.get("items"), list) and topic["items"], "grammar.json: invalid topic")
        require(topic["id"] not in topic_ids, f"grammar.json: duplicate topic {topic['id']}")
        topic_ids.add(topic["id"])
        for item in topic["items"]:
            require(isinstance(item, dict) and text(item.get("q")) and item["q"].count("___") == 1
                    and isinstance(item.get("a"), list) and item["a"] and all(text(a) for a in item["a"]),
                    f"grammar.json: invalid exercise in {topic['id']}")

    missing = [audio_id for audio_id in sorted(clips)
               if not (directory / "audio" / f"{audio_id}.mp3").is_file()
               or (directory / "audio" / f"{audio_id}.mp3").stat().st_size == 0]
    require(not missing, f"{len(missing)} missing or empty audio clips: {', '.join(missing[:5])}")
    return len(lemmas), len(clips)


if __name__ == "__main__":
    try:
        words, clips = validate(Path(sys.argv[1]) if len(sys.argv) > 1 else DATA)
        print(f"French data valid: {words:,} words, {clips:,} referenced audio clips.")
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f"French data invalid: {error}\nBuild the data first: uv run data/fr/build.py", file=sys.stderr)
        sys.exit(1)
