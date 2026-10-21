package io.github.vykosmolt.pluton.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GradeTest {
    private val forms = mapOf(
        "a" to "avoir", "à" to "à", "ou" to "ou", "où" to "où",
        "dort" to "dormir", "dors" to "dormir", "élève" to "élève", "eleve" to "élève",
    )

    @Test
    fun exactMatchIgnoresCaseAndSpace() {
        assertEquals("right", grade(" Dort ", "dort", "dormir", forms).result)
    }

    @Test
    fun accentOnlyIsAlmost() {
        val g = grade("eleve", "élève", "élève", forms)
        assertEquals("almost", g.result)
        assertEquals("accent", g.why)
    }

    @Test
    fun aRealWordForAnotherLemmaIsWrong() {
        val g = grade("a", "à", "à", forms)
        assertEquals("wrong", g.result)
        assertEquals("other", g.why)
        assertEquals("avoir", g.other)
    }

    @Test
    fun rightLemmaWrongFormIsAlmost() {
        val g = grade("dors", "dort", "dormir", forms)
        assertEquals("almost", g.result)
        assertEquals("form", g.why)
    }

    @Test
    fun oneCharacterTypoIsAlmost() {
        val g = grade("dore", "dort", "dormir", forms)
        assertEquals("almost", g.result)
        assertEquals("typo", g.why)
    }

    @Test
    fun unrelatedAnswerIsWrong() {
        assertEquals("wrong", grade("chien", "dort", "dormir", forms).result)
    }

    @Test
    fun oeFoldsToOe() {
        assertEquals("right", grade("coeur", "cœur", "coeur", forms).result)
    }

    @Test
    fun quizPassesAtTwoThirds() {
        val qs = listOf(
            QuizQuestion(listOf("a", "b"), 1),
            QuizQuestion(listOf("a", "b"), 1),
            QuizQuestion(listOf("a", "b"), 0),
        )
        assertEquals(QuizResult(2, 3, true), gradeQuiz(qs, listOf(1, 0, 0)))
        assertEquals(QuizResult(1, 3, false), gradeQuiz(qs, listOf(0, 1, 1)))
    }

    @Test
    fun dictationAlignmentAndMarks() {
        val s = "Je n’ai pas vu le chat, d'accord ?"
        val marks = { r: DicteeResult -> r.marks.joinToString(" ") { it.mark } }
        assertEquals("right", gradeDictee("je n'ai pas vu le chat d'accord", s).result)
        val one = gradeDictee("je n'ai pas vue le chat d'accord", s)
        assertEquals("almost", one.result)
        assertEquals("ok ok ok bad ok ok ok", marks(one))
        val missing = gradeDictee("je n'ai vu chat d'accord", s)
        assertEquals("wrong", missing.result)
        assertEquals("ok ok bad ok bad ok ok", marks(missing))
        val acc = gradeDictee("Il était là, déjà.", "Il etait la, deja.")
        assertEquals("almost", acc.result)
        assertEquals("accent", acc.why)
        assertEquals("wrong", gradeDictee("", s).result)
        val m = gradeDictee("x", "Bon, ça va.").marks[1]
        assertEquals("ça", "Bon, ça va.".substring(m.start, m.end))
    }

    @Test
    fun conjugationCellsAllowPronounsAndAgreement() {
        assertEquals("right", gradeConj("je suis", "suis").result)
        assertEquals("right", gradeConj("qu'il soit", "soit").result)
        assertEquals("almost", gradeConj("etre", "être").result)
        assertEquals("wrong", gradeConj("es", "suis").result)
        // être-verbs: participle agreement isn't tested (elle est allée ~ allé).
        assertEquals("right", gradeConj("elle est allée", "est allée", agrees = true).result)
        assertEquals("right", gradeConj("elle est allé", "est allée", agrees = true).result)
        assertEquals("wrong", gradeConj("elle est allé", "est allée").result)
        assertEquals("wrong", gradeConj("", "suis").result)
    }

    @Test
    fun grammarAnswersAcceptAlternativesAndLooseApostrophes() {
        assertEquals("right", gradeGram("la", listOf("la")).result)
        assertEquals("right", gradeGram("l'autre", listOf("l'autre")).result)
        assertEquals("almost", gradeGram("lautre", listOf("l'autre")).result)
        assertEquals("wrong", gradeGram("l autre", listOf("l'autre")).result) // a space is not a lost apostrophe
        assertEquals("almost", gradeGram("etre", listOf("être")).result)
        assertEquals("wrong", gradeGram("le", listOf("la")).result)
        assertEquals("wrong", gradeGram("", listOf("la")).result)
    }

    @Test
    fun productionExpectsTheRightArticle() {
        val house = Word("maison", pos = "noun", display = "la maison", gloss = "house", gender = "f", form = "maison")
        assertEquals("la maison", produceAnswer(house))
        assertEquals("dormir", produceAnswer(Word("dormir", pos = "verb")))
        assertEquals("right", gradeProduce("la maison", house, emptyMap()).result)
        assertEquals("right", gradeProduce("une maison", house, emptyMap()).result)
        assertEquals("almost", gradeProduce("maison", house, emptyMap()).result)
        assertEquals("article", gradeProduce("maison", house, emptyMap()).why)
        assertEquals("almost", gradeProduce("le maison", house, emptyMap()).result)
        assertEquals("gender", gradeProduce("le maison", house, emptyMap()).why)
        assertEquals("almost", gradeProduce("un maison", house, emptyMap()).result)
        assertEquals("right", gradeProduce("l' maison", house, emptyMap()).result) // l' carries no gender
        assertEquals("wrong", gradeProduce("chien", house, emptyMap()).result)
        val verb = Word("dormir", pos = "verb")
        assertEquals("right", gradeProduce("dormir", verb, emptyMap()).result)
        assertEquals("almost", gradeProduce("dormi", verb, emptyMap()).result)
    }

    @Test
    fun exerciseModesFollowTheRepCount() {
        val word = Word("maison", pos = "noun", display = "la maison", gloss = "house", gender = "f", audioSentence = "clip")
        fun card(reps: Int, state: State = State.Review, stability: Double = 3.0) =
            createEmptyCard(1).copy(reps = reps, state = state, stability = stability)
        assertEquals("cloze", modeFor(card(0, State.Learning, 0.0), word))
        assertEquals("dictation", modeFor(card(1, State.Learning, 0.0), word))
        assertEquals("produce", modeFor(card(2), word))
        assertEquals("dictee", modeFor(card(3, stability = KNOWN_DAYS), word))
        assertEquals("cloze", modeFor(card(2, State.Learning), word)) // not in Review yet
        assertEquals("dictation", modeFor(card(3, stability = KNOWN_DAYS), word.copy(audioSentence = null)))
        assertEquals("cloze", modeFor(card(2), word.copy(gloss = "")))
    }

    @Test
    fun recognizerMatchScoreWeightsAccentSlips() {
        val sentence = "Le chat dort sur le mur."
        assertEquals(100, dicteeScore(gradeDictee(sentence, sentence)))
        assertEquals(70, dicteeScore(gradeDictee("ete", "été")))
        assertEquals(0, dicteeScore(gradeDictee("", sentence)))
        assertEquals(0, dicteeScore(DicteeResult("wrong", null, emptyList())))
        // Three words right, one accent slip: (3 + 0.7) / 4 = 92.5, which rounds to 92 here.
        assertEquals(92, dicteeScore(gradeDictee("Le chat est la", "Le chat est là.")))
    }

    @Test
    fun levenshteinBasics() {
        assertEquals(0, levenshtein("a", "a"))
        assertEquals(1, levenshtein("dort", "dore"))
        assertEquals(3, levenshtein("", "abc"))
    }
}
