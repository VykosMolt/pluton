package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextTest {
    private val forms = mapOf(
        "homme" to "homme", "il" to "il", "voir" to "voir", "voit" to "voir", "aujourd'hui" to "aujourd'hui",
        "dire" to "dire", "dit" to "dire",
        "a" to "avoir", "à" to "à", "ou" to "ou", "où" to "où", "es" to "être", "est" to "être", "suis" to "être",
        "parle" to "parler", "parles" to "parler", "coeur" to "coeur", "mon" to "mon", "le" to "le", "chat" to "chat",
        "dort" to "dormir", "va" to "aller", "paris" to "pari",
    )

    @Test
    fun tokenizesElisionCompoundsHyphensNamesAndOffsets() {
        val text = "L'homme qu’il voit aujourd'hui, dit-il à Paul. Mon cœur"
        val toks = tokenize(text, forms, FRENCH_ELISION)
        assertEquals(
            listOf("le", "homme", "que", "il", "voir", "aujourd'hui", "dire", "il", "à", "paul", "mon", "coeur"),
            toks.map { it.lemma },
        )
        assertEquals(listOf("Paul"), toks.filter { it.name }.map { it.text })
        for (t in toks) assertEquals(t.text, text.substring(t.start, t.end).replace('’', '\''))
    }

    @Test
    fun midSentenceCapitalsAreNames() {
        val names = { s: String -> tokenize(s, forms, FRENCH_ELISION).filter { it.name }.map { it.text } }
        assertEquals(listOf("Paris"), names("Il va à Paris. « Le chat dort. »")) // not the plural of "pari"
        assertEquals(listOf("Italie", "Marie", "Paul"), names("Il dit : « Le chat ! » L'Italie, Marie et Paul."))
        assertEquals(listOf("où", "aller", "il"), tokenize("Où va-t-il ?", forms, FRENCH_ELISION).map { it.lemma })
    }

    @Test
    fun coverageAndUnlock() {
        val toks = tokenize("le chat dort le chat dort homme Paul", forms, FRENCH_ELISION)
        val known = setOf("le", "chat")
        val st = { l: String -> if (l in known) WordStatus.Known else WordStatus.New }
        val c = coverage(toks, st)
        assertEquals(listOf(c.known, c.newWords, c.total), listOf(4, 3, 7)) // Paul is a name, not counted
        assertEquals(listOf("dormir", "homme"), unlockList(toks, st, 0.95))
        assertEquals(listOf("dormir"), unlockList(toks, st, 0.8))
        assertEquals(emptyList(), unlockList(toks, { WordStatus.Known }))
        val queued = { l: String -> if (l in known) WordStatus.Known else WordStatus.Queued }
        assertEquals(4.0 / 7, coverage(toks, queued).pct)
        assertEquals(listOf("dormir", "homme"), unlockList(toks, queued, 0.95))
    }

    @Test
    fun sentenceAroundATokenIsCropped() {
        val text = "Il pleut. Le chat dort ici ! Fin"
        assertEquals("Le chat dort ici !", sentenceAt(text, text.indexOf("chat")))
        assertEquals("Fin", sentenceAt(text, text.indexOf("Fin")))
        val long = "mot ".repeat(100) + "cible " + "mot ".repeat(100)
        val s = sentenceAt(long, long.indexOf("cible"))
        assertTrue(s.contains("cible") && s.length < 170, s)
    }

    @Test
    fun sentencesSkipLeadingQuotesAndNumbersOnly() {
        val body = "« Bonjour ! » dit-il.\nIl fait beau... 42\n— Tu viens ?"
        val spans = sentencesOf(body)
        assertEquals(
            listOf("Bonjour !", "dit-il.", "Il fait beau...", "Tu viens ?"),
            spans.map { body.substring(it.start, it.end) },
        )
        assertEquals(emptyList(), sentencesOf("42\n— 7"))
        assertEquals(emptyList(), sentencesOf(""))
    }

    @Test
    fun blankFindsWholeWords() {
        val (a, b, c) = blank("Il me donne des coups de pied !", "de")
        assertEquals("de", b)
        assertTrue(a.endsWith("coups "))
        assertEquals(" pied !", c)
        assertEquals("dort", blank("Le chat dort sur le mur.", "dort").second)
        assertEquals("", blank("Le chat dort.", "chien").second)
    }
}
