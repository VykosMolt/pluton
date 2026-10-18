"""Fetch the built-in reading shelf (public-domain French classics) from French Wikisource into data/fr/shelf.json.

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
API = "https://fr.wikisource.org/w/api.php"
SHELF = [  # (title, author, year, Wikisource page[, phrase the text starts at])
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


def fetch(page):
    q = urllib.parse.urlencode({"action": "parse", "page": page, "prop": "text", "format": "json", "formatversion": 2})
    req = urllib.request.Request(f"{API}?{q}", headers={"User-Agent": "pluton-builder/0.1 (personal project)"})
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
    for title, author, year, page, *start in SHELF:
        body = fetch(page)
        if start:
            body = body[body.index(start[0]):]  # drop book headers before the story
        words = len(body.split())
        print(f"{words:6} words  {title}")
        assert words > 80, f"{title}: only {words} words, check the page"
        shelf.append({"title": title, "author": author, "year": year, "body": body,
                      "source": "https://fr.wikisource.org/wiki/" + urllib.parse.quote(page.replace(" ", "_"))})
    OUT.write_text(json.dumps(shelf, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
