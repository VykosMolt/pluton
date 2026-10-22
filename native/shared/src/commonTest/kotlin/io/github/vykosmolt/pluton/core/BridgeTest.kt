package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeTest {
    private val cognates = mapOf(
        "abandon" to listOf("abandonner", ""),
        "library" to listOf("librairie", "faux:bookshop"),
        "forest" to listOf("forêt", "circumflex"),
        "strange" to listOf("étrange", "es"),
        "abbreviation" to listOf("abréviation", "é>ed"),
    )

    @Test
    fun lookupTriesInflectionsAndFlagsFalseFriends() {
        assertEquals("abandonner", enLookup("abandoned", cognates)?.first)
        assertEquals("abandonner", enLookup("abandoning", cognates)?.first)
        assertEquals("abandonner", enLookup("abandons", cognates)?.first)
        val faux = enLookup("library", cognates)!!
        assertEquals("librairie", faux.first)
        assertTrue(faux.second.startsWith("faux:"))
        assertEquals(null, enLookup("house", cognates))
        assertEquals(null, enLookup("cat", cognates)) // too short for the inflection fallback
        assertEquals("abandon", enLookup("abandon's", cognates)?.third)
    }

    @Test
    fun tokensCarryOffsetsAndFrenchWords() {
        val text = "The library was abandoned near the forest."
        val tokens = englishTokens(text, cognates)
        val library = tokens.single { it.text == "library" }
        assertEquals("librairie", library.fr)
        assertTrue(library.faux)
        assertEquals(text.substring(library.start, library.end), library.text)
        val abandoned = tokens.single { it.text == "abandoned" }
        assertEquals("abandonner", abandoned.fr)
        assertTrue(!abandoned.faux)
        assertEquals(null, tokens.single { it.text == "near" }.fr)
    }

    @Test
    fun notesExplainSoundChangesAndFalseFriends() {
        val forest = englishTokens("forest", cognates).single()
        assertEquals("The circumflex in forêt marks an s French later dropped and English kept: forêt → forest.", bridgeNote(forest))
        val strange = englishTokens("strange", cognates).single()
        assertEquals("Old French words in es- lost the s and gained é-; English kept the old form: étrange → strange.", bridgeNote(strange))
        val library = englishTokens("library", cognates).single()
        assertEquals("Faux ami: librairie means “bookshop”, not “library”.", bridgeNote(library))
        val abbreviation = englishTokens("abbreviation", cognates).single()
        assertEquals("French -é often became English -ed: abréviation → abbreviation.", bridgeNote(abbreviation))
    }

    @Test
    fun decodesTheGeneratedCognateMap() {
        val map = decodeCognates("""{"forest":["forêt","circumflex"],"library":["librairie","faux:bookshop"]}""")
        assertEquals(listOf("forêt", "circumflex"), map["forest"])
        assertEquals("faux:bookshop", map["library"]?.get(1))
    }
}
