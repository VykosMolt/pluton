package io.github.vykosmolt.pluton

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vykosmolt.pluton.core.ErrorDrill
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The error loop has to carry the correction, the grade and the reason, not just render a list. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ErrorReviewScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val drills = listOf(
        ErrorDrill(1, "je suis alle", "je suis allé", "Le participe s'accorde.", "grammar", 0, 0),
        ErrorDrill(2, "les chats", "les chat", "Un seul chat.", "grammar", 0, 0),
    )

    @Test
    fun checkingAnAnswerShowsTheFixAndReasonThenAdvances() {
        var graded: Pair<Long, String>? = null
        rule.setContent {
            MaterialTheme {
                ErrorReviewScreen(drills, hasCorrections = true, onGraded = { drill, result -> graded = drill.id to result.result }, onBack = {})
            }
        }
        rule.onNodeWithText("je suis alle").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("je suis allé")
        rule.onNodeWithText("Check").performScrollTo().performClick()
        rule.onNodeWithText("Exactly.").assertExists()
        rule.onNodeWithText("The fix: je suis allé").assertExists()
        rule.onNodeWithText("Le participe s'accorde.").assertExists()
        assertEquals(1L to "right", graded)
        rule.onNodeWithText("Next").performScrollTo().performClick()
        rule.onNodeWithText("les chats").assertExists()
    }

    @Test
    fun startOverResetsTheSessionScoreOnASingleDrill() {
        rule.setContent { MaterialTheme { ErrorReviewScreen(listOf(drills.first()), hasCorrections = true, onGraded = { _, _ -> }, onBack = {}) } }
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("je suis allé")
        rule.onNodeWithText("Check").performScrollTo().performClick()
        rule.onNodeWithText("1 right", substring = true).assertExists()
        rule.onNodeWithText("Start over").performScrollTo().performClick()
        rule.onNodeWithText("0 right", substring = true).assertExists()
    }

    @Test
    fun aParentRecompositionDoesNotSwapTheSession() {
        val upcoming = mutableStateOf(listOf(drills.first()))
        rule.setContent {
            MaterialTheme {
                ErrorReviewScreen(upcoming.value, hasCorrections = true, onGraded = { _, _ -> }, onBack = {})
            }
        }
        rule.onNodeWithText("je suis alle").assertExists()
        rule.onNodeWithText("1 / 1", substring = true).assertExists()
        rule.runOnIdle { upcoming.value = drills }
        rule.onNodeWithText("je suis alle").assertExists()
        rule.onNodeWithText("1 / 1", substring = true).assertExists()
    }

    @Test
    fun emptyStateExplainsHowCorrectionsArrive() {
        rule.setContent { MaterialTheme { ErrorReviewScreen(emptyList(), hasCorrections = false, onGraded = { _, _ -> }, onBack = {}) } }
        rule.onNodeWithText("Nothing to review yet").assertIsDisplayed()
        rule.onNodeWithText("Speak → Write", substring = true).assertIsDisplayed()
    }
}
