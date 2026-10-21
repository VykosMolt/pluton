package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReferenceTest {
    @Test
    fun decodesConjugationTablesWithMissingCells() {
        val conj = decodeConj(
            """{"être":{"aux":"avoir","pp":"été",
               "t":{"ind:pre":["suis","es","est","sommes","êtes","sont"]},
               "audio":{"ind:pre":["a","b","c","d","e","f"]}},
               "dire":{"aux":"avoir","pp":"dit",
               "t":{"ind:fut":["dirai","diras","dira","dirons","direz",null]},
               "audio":{"ind:fut":["g","h","i","j","k",null]}}}""",
        )
        assertEquals(setOf("être", "dire"), conj.keys)
        assertEquals("été", conj.getValue("être").pp)
        assertEquals(listOf("suis", "es", "est", "sommes", "êtes", "sont"), conj.getValue("être").t.getValue("ind:pre"))
        assertNull(conj.getValue("dire").t.getValue("ind:fut")[5])
        assertEquals("f", conj.getValue("être").audio.getValue("ind:pre")[5])
    }

    @Test
    fun decodesGrammarTopicsAndKeys() {
        val topics = decodeGrammar(
            """[{"id":"objets","level":"A2","name":"le, la, les · lui, leur","en":"object pronouns",
               "how":"They go before the verb.",
               "items":[{"q":"Tu vois Marie ? — Oui, je ___ vois.","a":["la"]},
                        {"q":"Tu leur parles ?","a":["oui"],"ask":"Réponds"}]}]""",
        )
        assertEquals(listOf("objets"), topics.map { it.id })
        assertEquals("A2", topics[0].level)
        assertEquals(listOf("la"), topics[0].items[0].a)
        assertEquals("Réponds", topics[0].items[1].ask)
        assertEquals("conj/être/ind:pre", conjKey("être", "ind:pre"))
        assertEquals("être" to "ind:pre", parseConj(conjKey("être", "ind:pre")))
        assertNull(parseConj("gram/objets"))
        assertEquals("gram/objets", gramKey("objets"))
    }

    @Test
    fun subjectsMatchTheWebApp() {
        assertEquals("je", subject(0, "ind:pre"))
        assertEquals("nous", subject(3, "pc"))
        assertEquals("que je", subject(0, "sub:pre"))
        assertEquals("qu'il", subject(2, "sub:pre"))
        assertEquals("qu'ils", subject(5, "sub:pre"))
        assertEquals(6, FRENCH_TENSES.size)
        assertEquals("ind:pre", FRENCH_TENSES.first().id)
    }
}
