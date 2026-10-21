package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DataTest {
    @Test
    fun decodesWordsAndForms() {
        val w = decodeWords(
            """[{"lemma":"chat","pos":"noun","display":"le chat","gloss":"cat","gender":"m",
               "sentence":"Le chat dort.","translation":"The cat sleeps.","form":"chat",
               "audio_word":"a","audio_sentence":"b","extra":true}]""",
        ).single()
        assertEquals("le chat", w.display)
        assertEquals("m", w.gender)
        assertEquals("chat", w.form)
        assertEquals("a", w.audioWord)
        assertEquals("b", w.audioSentence)
        assertEquals(mapOf("dort" to "dormir"), decodeForms("""{"dort":"dormir"}"""))
    }

    @Test
    fun decodesStoryQuestions() {
        val story = decodeStory(
            """[{"title":"Le Mur","body":"Il fait noir.","sentences":[[0,13,"abc"]],
               "questions":[{"q":"Where?","a":["A wall","A boat"],"ok":0,"why":"Le mur."}]}]""",
        ).single()
        assertEquals("Le Mur", story.title)
        assertEquals(1, story.questions.size)
        assertEquals("Where?", story.questions[0].question)
        assertEquals(listOf("A wall", "A boat"), story.questions[0].options)
        assertEquals(0, story.questions[0].correct)
        assertEquals(listOf(StorySentence(0, 13, "abc")), story.audioSentences())
    }

    @Test
    fun decodesTheClassicsShelf() {
        val shelf = decodeShelf(
            """[{"title":"La Parure","author":"Guy de Maupassant","year":1884,"lang":"fr",
               "body":"Elle était jolie.","source":"https://example","sentences":[[0,15,"xyz"]]}]""",
        ).single()
        assertEquals("La Parure", shelf.title)
        assertEquals("Guy de Maupassant", shelf.author)
        assertEquals(1884, shelf.year)
        assertEquals("fr", shelf.lang)
        assertEquals(listOf(StorySentence(0, 15, "xyz")), shelf.audioSentences())
    }
}
