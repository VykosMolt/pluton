package io.github.vykosmolt.pluton

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Runs the real Activity on the JVM: assets decode, SQLite opens, Compose draws, tabs navigate. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        // A fresh install shows the one-question onboarding before Home; later tests reuse the saved answer.
        rule.waitUntil(timeoutMillis = 30_000) {
            rule.onAllNodesWithText("Start with 10 minutes").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Start a run", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("Start with 10 minutes").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Start with 10 minutes").performClick()
        }
        rule.waitUntil(timeoutMillis = 90_000) {
            rule.onAllNodesWithText("Start a run", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeLoadsTheDeckAndShowsItsTabs() {
        waitForHome()
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("Start a run", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Read").assertExists()
        rule.onNodeWithText("Speak").assertExists()
    }

    @Test
    fun theDailyPlanOffersAllFourStrands() {
        waitForHome()
        rule.onNodeWithText("Today").assertExists()
        rule.onNodeWithText("Warm up").assertExists()
        rule.onNodeWithText("Read something").assertExists()
        rule.onNodeWithText("Speak aloud").assertExists()
        rule.onNodeWithText("Write a little").assertExists()
    }

    @Test
    fun readTabListsTheStory() {
        waitForHome()
        rule.onNodeWithText("Read").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Chapitre 1 · Le Mur", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Chapitre 1 · Le Mur", substring = true).assertExists()
        rule.onNodeWithText("La Parure", substring = true).assertExists()
        rule.onNodeWithText("Norman Bridge").assertExists()
        rule.onNodeWithText("The Black Cat", substring = true).assertExists()
        // Your texts section renders with its add action, even when empty.
        rule.onNodeWithText("Your texts").assertExists()
        rule.onNodeWithText("+ Add").assertExists()
    }

    @Test
    fun normanBridgeOpensAnEnglishText() {
        waitForHome()
        rule.onNodeWithText("Read").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("The Black Cat", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("The Black Cat", substring = true).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Norman Bridge").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Weave what I know").assertExists()
        rule.onNodeWithText("Weave everything").assertExists()
        rule.onNodeWithText("came that way", substring = true).assertExists()
    }

    @Test
    fun moreTabShowsSettings() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Settings").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("New words per run").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("New words per run").assertExists()
        rule.onNodeWithText("Download backup").assertExists()
        rule.onNodeWithText("Lexique 3.83", substring = true).assertExists()
        rule.onNodeWithText("AI tutor", substring = true).assertExists()
        rule.onNodeWithText("Test & save").assertExists()
    }

    @Test
    fun talkWithoutAKeyPointsToSettings() {
        waitForHome()
        rule.onNodeWithText("Speak").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Talk").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Talk").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Pluton needs a key first").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Open Settings").assertExists()
        rule.onNodeWithText("Practice").performClick()
        rule.onNodeWithText("Hear it").assertExists()
    }

    @Test
    fun speakOffersFluencyAndSounds() {
        waitForHome()
        rule.onNodeWithText("Speak").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Practice").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Fluency").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Shadowing").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("4 / 3 / 2").assertExists()
        rule.onNodeWithText("Sounds").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Les nasales").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Les nasales").assertExists()
    }

    @Test
    fun writingWithoutAKeyPointsToSettings() {
        waitForHome()
        rule.onNodeWithText("Speak").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Write").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Write").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Writing correction uses an AI model", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Open Settings").assertExists()
    }

    @Test
    fun moreTabShowsProgressAndCanDos() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Progress").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Progress").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Vocabulary estimate").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Vocabulary estimate").assertExists()
        rule.onNodeWithText("Last 7 days").assertExists()
        rule.onNodeWithText("Cabinet").assertExists()
        rule.onNodeWithText("ticked", substring = true).assertExists()
        rule.onNodeWithText("Milestones").performScrollTo().assertExists()
        rule.onNodeWithText("Can you…?", substring = true).assertExists()
    }

    @Test
    fun moreTabShowsThePath() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Path").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Path").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Le Chemin").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Le Mur · The Wall").assertExists()
        rule.onNodeWithText("Learn 75 words", substring = true).assertExists()
    }

    @Test
    fun moreTabShowsTheDeckBrowser() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Words").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Words").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Search French or English…").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Search French or English…").assertExists()
        rule.onNodeWithText("in your deck", substring = true).assertExists()
    }

    @Test
    fun moreTabShowsConjugationTables() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Verbs").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Verbs").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("être").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("être").performClick()
        rule.onNodeWithText("suis").assertExists()
        rule.onNodeWithText("présent").assertExists()
    }

    @Test
    fun moreTabShowsGrammarExercises() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Grammar").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Grammar").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("object pronouns").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("object pronouns").performClick()
        assertTrue(rule.onAllNodesWithText("Show answer").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aPracticedTableShowsUpInTheRun() {
        waitForHome()
        rule.onNodeWithText("More").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Verbs").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Verbs").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("être").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("être").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Practice").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Practice")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertTrue(
            rule.onRoot().printToString(),
            rule.onAllNodesWithText("queued for your runs").fetchSemanticsNodes().isNotEmpty(),
        )
        rule.onNodeWithText("Home").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Start a run", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Start a run", substring = true).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("CONJUGATION", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("CONJUGATION", substring = true).assertExists()
    }

    @Test
    fun userTextsGetReadAlongWithTheDeviceVoice() {
        waitForHome()
        rule.onNodeWithText("Read").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Chapitre 1 · Le Mur", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("+ Add").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Add a text").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Text in French").performTextInput("Bonjour. Ça va bien ?")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Bonjour. Ça va bien ?", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Bonjour. Ça va bien ?", substring = true).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Read & listen").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Read & listen").assertExists()
    }

    @Test
    fun readerOffersAStoryGenerator() {
        waitForHome()
        rule.onNodeWithText("Read").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Chapitre 1 · Le Mur", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Story").performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("A story at your level", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Open Settings").assertExists()
    }

    @Test
    fun classicsOfferRetellButStoryChaptersDoNot() {
        waitForHome()
        rule.onNodeWithText("Read").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("La Parure", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("La Parure", substring = true).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Did you understand it?").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Open Settings").assertExists()
        rule.onNodeWithText("← Library").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Chapitre 1 · Le Mur", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Chapitre 1 · Le Mur", substring = true).performScrollTo().performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("← Library").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            rule.onRoot().printToString(),
            rule.onAllNodesWithText("Did you understand it?").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun listenTabOffersMediaAndSubtitles() {
        waitForHome()
        rule.onNodeWithText("Listen").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("Choose audio or video", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Choose audio or video", substring = true).assertExists()
        rule.onNodeWithText("Choose subtitles (.srt / .vtt)", substring = true).assertExists()
    }
}
