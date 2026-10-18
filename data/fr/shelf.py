"""Fetch the built-in reading shelf from Wikisource into data/fr/shelf.json: public-domain French classics, plus
English Gothic classics (Poe, Lovecraft) for the Norman Bridge, which reads English with its French roots showing.

    uv run data/fr/shelf.py

Texts are public domain; the source page is recorded for each. build.py copies the result into public/data/fr/.
"""
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

OUT = Path(__file__).resolve().parent / "shelf.json"
API = "https://{}.wikisource.org/w/api.php"
SHELF = [  # (title, author, year, Wikisource page[, phrase the text starts at[, phrase it ends with]])
    ("Le Corbeau et le Renard", "Jean de La Fontaine", 1668, "Fables de La Fontaine (éd. 1874)/Le Corbeau et le Renard"),
    ("Le Petit Chaperon rouge", "Charles Perrault", 1697, "Contes de Perrault (éd. 1902)/Le petit Chaperon rouge",
     "Il était une fois"),
    ("La Chèvre de monsieur Seguin", "Alphonse Daudet", 1866, "Lettres de mon moulin/La chèvre de monsieur Seguin"),
    ("Les Étoiles", "Alphonse Daudet", 1869, "Lettres de mon moulin/Les étoiles"),
    ("Aux champs", "Guy de Maupassant", 1882, "Contes de la bécasse (Havard, 1894)/Aux champs"),
    ("La Parure", "Guy de Maupassant", 1884, "Contes du jour et de la nuit (éd. Flammarion, 1885)/La Parure"),
    ("Le Tour du monde en quatre-vingts jours, chapitre 1", "Jules Verne", 1872,
     "Le Tour du monde en quatre-vingts jours/Chapitre 1"),
]
BRIDGE = [  # English originals, read through their French-derived words
    ("The Black Cat", "Edgar Allan Poe", 1843, "United States Saturday Post/The Black Cat", "For the most wild"),
    ("The Tell-Tale Heart", "Edgar Allan Poe", 1843, "Poe's Tales of Mystery and Imagination/The Tell-Tale Heart", "True!"),
    ("The Masque of the Red Death", "Edgar Allan Poe", 1842,
     "Poe's Tales of Mystery and Imagination/The Masque of the Red Death", "The “Red Death”"),
    ("Dagon", "H. P. Lovecraft", 1919, "Weird Tales/Volume 2/Issue 3/Dagon", "I am writing", "The window!"),
    ("The Call of Cthulhu", "H. P. Lovecraft", 1928, "Weird Tales/Volume 11/Issue 2/The Call of Cthulhu",
     '"Of such great powers', "meets no other eye."),
]
SKIP = {"ws-noexport", "headertemplate", "footertemplate", "pagenum", "reference", "references", "mw-cite-backlink",
        "Z3988", "mw-heading", "mw-html-heading", "mw-editsection"}
VOID = {"br", "img", "hr", "meta", "link", "input", "wbr", "col", "area", "source"}


class Text(HTMLParser):
    """Paragraph and verse text, minus Wikisource's headers, page numbers and footnotes."""

    def __init__(self):
        super().__init__()
        self.out, self.skip = [], 0

    def handle_starttag(self, tag, attrs):
        if tag in VOID:
            if tag == "br" and not self.skip:
                self.out.append("\n")
            return
        if self.skip or SKIP & set((dict(attrs).get("class") or "").split()) or tag in ("style", "script"):
            self.skip += 1

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if self.skip:
            self.skip -= 1
        elif tag in ("p", "div"):
            self.out.append("\n\n")

    def handle_data(self, data):
        if not self.skip:
            self.out.append(data.replace("\n", " "))

    def text(self):
        raw = re.sub(r"[\ufeff\u200b]", "", "".join(self.out))  # BOMs / zero-width spaces in verse
        lines = [" ".join(l.split()) for l in raw.split("\n")]
        return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()


def fetch(page, lang="fr"):
    q = urllib.parse.urlencode({"action": "parse", "page": page, "prop": "text", "format": "json", "formatversion": 2})
    req = urllib.request.Request(f"{API.format(lang)}?{q}", headers={"User-Agent": "pluton-builder/0.1 (personal project)"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                html = json.load(r)["parse"]["text"]
            break
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 3:
                raise
            time.sleep(int(e.headers.get("Retry-After") or 10))  # Wikimedia rate limit: wait as asked
    time.sleep(1)  # be polite between pages
    t = Text()
    t.feed(html)
    return t.text()


def main():
    shelf = []
    for lang, entries in (("fr", SHELF), ("en", BRIDGE)):
        for title, author, year, page, *start in entries:
            body = fetch(page, lang)
            if start:
                body = body[body.index(start[0]):]  # drop book and magazine headers before the story
            if len(start) > 1:
                body = body[: body.index(start[1]) + len(start[1])]  # and promos or licence text after it
            words = len(body.split())
            print(f"{words:6} words  [{lang}] {title}")
            assert words > 80, f"{title}: only {words} words, check the page"
            shelf.append({"title": title, "author": author, "year": year, "lang": lang, "body": body,
                          "source": f"https://{lang}.wikisource.org/wiki/" + urllib.parse.quote(page.replace(" ", "_"))})
    OUT.write_text(json.dumps(shelf, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
