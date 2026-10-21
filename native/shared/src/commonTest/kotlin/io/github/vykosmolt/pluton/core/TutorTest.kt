package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TutorTest {
    @Test
    fun levelFollowsVocabularySize() {
        assertEquals("A1", levelOf(0).code)
        assertEquals("A1", levelOf(149).code)
        assertEquals("A2", levelOf(150).code)
        assertEquals("A2", levelOf(599).code)
        assertEquals("B1", levelOf(600).code)
        assertEquals("B1", levelOf(1999).code)
        assertEquals("B2", levelOf(2000).code)
        assertEquals("C1", levelOf(4000).code)
        assertEquals("C1", levelOf(1_000_000).code)
        assertEquals(150, levelOf(150).words)
    }

    @Test
    fun chatPromptCarriesLevelAndCurrentWords() {
        val prompt = chatSystem(levelOf(200), listOf("chat", "maison"))
        assertTrue(prompt.contains("treat them as A2"))
        assertTrue(prompt.contains("roughly 200 French words"))
        assertTrue(prompt.contains("chat, maison"))
        assertTrue(!chatSystem(levelOf(200), emptyList()).contains("Words they are learning"))
        assertTrue(prompt.contains("✏️"))
    }

    @Test
    fun writingPromptsFollowTheLevel() {
        assertEquals(listOf("A1", "A2"), suggestedWritingPrompts(levelOf(0)).map { it.level }.distinct())
        assertTrue(suggestedWritingPrompts(levelOf(0)).all { it.level == "A1" || it.level == "A2" })
        assertTrue(suggestedWritingPrompts(levelOf(5000)).all { it.level == "B2" || it.level == "C1" })
        assertTrue(writeSystem(levelOf(700), "Raconte.").contains("about B1"))
        assertTrue(writeSystem(levelOf(700), "Raconte.").contains("Raconte."))
    }

    @Test
    fun writingJsonIsParsedThroughProseAndFences() {
        val reply = """
            Here is the correction:
            ```json
            {"corrected":"Je suis allé au marché.","errors":[{"original":"je suis allé au marché","fix":"Je suis allé au marché.","why":"Capital letter and full stop.","kind":"spelling"}],"feedback":"Good past tense. Watch punctuation.","level":"A2"}
            ```
        """.trimIndent()
        val result = parseWriting(reply)!!
        assertEquals("Je suis allé au marché.", result.corrected)
        assertEquals("Capital letter and full stop.", result.errors.single().why)
        assertEquals("A2", result.level)
        assertEquals(null, parseWriting("no json here"))
        assertEquals(null, parseWriting("""{"corrected":"   "}"""))
        assertEquals(null, parseWriting("""{"corrected": """))
    }

    @Test
    fun storyPromptAndParsing() {
        val prompt = readerSystem(levelOf(900), listOf("chat", "maison"), listOf("chien"))
        assertTrue(prompt.contains("learner is B1"))
        assertTrue(prompt.contains("chat, maison"))
        assertTrue(prompt.contains("chien"))
        assertTrue(!readerSystem(levelOf(0)).contains("Words they already know:"))
        val body = "Léa ouvre la porte. ".repeat(6)
        val story = parseStory("""Here: {"title":"Le matin","body":"$body"}""")!!
        assertEquals("Le matin", story.title)
        assertTrue(story.body.startsWith("Léa"))
        assertEquals(null, parseStory("""{"title":"Court","body":"Trop court."}"""))
        assertEquals(null, parseStory("""{"title":"","body":"$body"}"""))
        assertEquals(null, parseStory("nope"))
    }

    @Test
    fun retellFeedbackIsValidated() {
        val ok = parseRetell("""{"score":4,"feedback":"You got the main idea; you missed the ending."}""")!!
        assertEquals(4, ok.score)
        assertTrue(ok.feedback.contains("main idea"))
        assertEquals(null, parseRetell("""{"score":7,"feedback":"Great"}"""))
        assertEquals(null, parseRetell("""{"score":3,"feedback":"  "}"""))
        assertEquals(null, parseRetell("""{"score":"four"}"""))
        assertEquals(null, parseRetell("not json"))
    }

    @Test
    fun correctionLineIsSplitOut() {
        val withFix = splitCorrection("✏️ Je suis allé au marché — past participle agrees with être.\nBonjour ! Comment ça va ?")
        assertEquals("Je suis allé au marché", withFix.fix)
        assertEquals("past participle agrees with être.", withFix.why)
        assertEquals("Bonjour ! Comment ça va ?", withFix.body)
        val noWhy = splitCorrection("✏️ Je suis allé.\nOn continue ?")
        assertEquals("Je suis allé.", noWhy.fix)
        assertEquals("", noWhy.why)
        assertEquals("On continue ?", noWhy.body)
        val plain = splitCorrection("Salut ! Ça va ?")
        assertEquals(null, plain.fix)
        assertEquals("Salut ! Ça va ?", plain.body)
    }
}
