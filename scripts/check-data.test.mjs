import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync, unlinkSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const validator = fileURLToPath(new URL('./check-data.py', import.meta.url))
const clip = 'a'.repeat(12)

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'pluton-data-'))
  t.after(() => rmSync(directory, { recursive: true, force: true }))
  const data = {
    words: [{ lemma: 'être', pos: 'verb', display: 'être', gloss: 'to be', sentence: 'Il est ici.',
      translation: 'He is here.', form: 'est', audio_word: clip, audio_sentence: clip }],
    forms: { est: 'être' },
    conj: { être: { aux: 'avoir', pp: 'été', t: { 'ind:pre': ['suis', 'es', 'est', 'sommes', 'êtes', 'sont'] },
      audio: { 'ind:pre': Array(6).fill(clip) } } },
    cognates: { being: ['être', ''] },
    story: [{ title: 'Bonjour', body: 'Bonjour.', sentences: [[0, 8, clip]],
      questions: [{ q: 'Un mot ?', a: ['Oui', 'Non', 'Peut-être'], ok: 0, why: 'Un seul mot.' }] }],
    shelf: [{ title: 'A story', body: 'An English text.', lang: 'en', author: 'Author', source: 'https://example.test/story' }],
    grammar: [{ id: 'test', name: 'Test', level: 'A1', how: 'Complete the sentence.', items: [{ q: 'Je ___ ici.', a: ['suis'] }] }],
  }
  const save = (name) => writeFileSync(join(directory, `${name}.json`), JSON.stringify(data[name]))
  for (const name of Object.keys(data)) save(name)
  mkdirSync(join(directory, 'audio'))
  const audio = join(directory, 'audio', `${clip}.mp3`)
  writeFileSync(audio, 'audio fixture')
  return { data, save, audio, directory, run: () => spawnSync('python3', [validator, directory], { encoding: 'utf8' }) }
}

test('complete generated data validates without the builder dependencies', (t) => {
  const result = fixture(t).run()
  assert.equal(result.status, 0, result.stderr)
  assert.match(result.stdout, /1 words, 1 referenced audio clips/)
})

test('missing lazy-loaded data fails packaging', (t) => {
  const f = fixture(t)
  unlinkSync(join(f.directory, 'cognates.json'))
  const result = f.run()
  assert.equal(result.status, 1)
  assert.match(result.stderr, /cognates.json/)
})

test('wrong JSON shapes fail packaging', (t) => {
  const f = fixture(t)
  f.data.forms = []
  f.save('forms')
  const result = f.run()
  assert.equal(result.status, 1)
  assert.match(result.stderr, /forms.json: expected a nonempty dict/)
})

test('missing and empty referenced audio fails packaging', (t) => {
  const f = fixture(t)
  unlinkSync(f.audio)
  assert.match(f.run().stderr, /1 missing or empty audio clips/)
  writeFileSync(f.audio, '')
  const result = f.run()
  assert.equal(result.status, 1)
  assert.match(result.stderr, /1 missing or empty audio clips/)
})

test('out-of-range read-along offsets fail packaging', (t) => {
  const f = fixture(t)
  f.data.story[0].sentences[0][1] = 999
  f.save('story')
  const result = f.run()
  assert.equal(result.status, 1)
  assert.match(result.stderr, /invalid sentence offsets/)
})

test('cognates use meanings, excluding inflection metadata and parenthetical usage labels', () => {
  const builder = fileURLToPath(new URL('../data/fr/build.py', import.meta.url))
  const result = spawnSync('python3', ['-c', `
import importlib.util, json, tempfile
from pathlib import Path
spec = importlib.util.spec_from_file_location("builder", ${JSON.stringify(builder)})
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)
entries = [
    {"word": "sourd", "pos": "adj", "senses": [{"glosses": ["deaf (person, animal)"]}, {"glosses": ["muffled (sound)"]}]},
    {"word": "forme", "pos": "noun", "senses": [{"glosses": ["shape (physical appearance)"]}, {"glosses": ["form"]}]},
    {"word": "forme", "pos": "verb", "senses": [{"glosses": ["inflection of former:"], "form_of": [{"word": "former"}]}]},
    {"word": "longe", "pos": "verb", "senses": [{"glosses": ["inflection of longer:"], "tags": ["form-of"]}]},
    {"word": "horror", "pos": "noun", "senses": [{"glosses": ["alternative spelling of horreur"], "alt_of": [{"word": "horreur"}]}]},
    {"word": "horreur", "pos": "noun", "senses": [{"glosses": ["horror (emotion)"]}]},
    {"word": "sonde", "pos": "verb", "senses": [{"glosses": ["to sound; to probe"]}]},
    {"word": "allonger", "pos": "verb", "senses": [{"glosses": ["to make longer"]}]},
]
with tempfile.TemporaryDirectory() as directory:
    b.RAW = Path(directory)
    (b.RAW / "kaikki.org-dictionary-French.jsonl").write_text("\\n".join(json.dumps(e) for e in entries))
    glosses, meanings, loans = b.load_glosses({"sourd": "adj", "forme": "noun", "longe": "verb", "horror": "noun", "horreur": "noun", "sonde": "verb", "allonger": "verb"})
    assert meanings["sourd"] == {"deaf", "muffled"}, meanings
    assert meanings["forme"] == {"shape", "form"}, meanings
    assert not meanings["longe"] and not meanings["horror"], meanings
    assert glosses["sourd"] == "deaf (person, animal); muffled (sound)"  # display gloss keeps useful qualifiers
    assert b.meaning_words("muffled (a sound (especially music))") == {"muffled"}
    rank = {w: i for i, w in enumerate(["sourd", "forme", "longe", "horror", "horreur", "sonde", "allonger"])}
    cognates = b.cognates(rank, meanings, loans, set(rank))
    assert "sound" not in cognates and "former" not in cognates and "longer" not in cognates, cognates
    assert cognates["horror"][0] == "horreur", cognates
`], { encoding: 'utf8' })
  assert.equal(result.status, 0, result.stderr)
})
