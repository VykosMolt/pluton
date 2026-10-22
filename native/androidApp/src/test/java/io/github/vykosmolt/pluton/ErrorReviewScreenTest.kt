package io.github.vykosmolt.pluton

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vykosmolt.pluton.core.ErrorDrill
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
        ErrorDrill("je suis alle", "je suis allé", "Le participe s'accorde.", "grammar"),
        ErrorDrill("les chats", "les chat", "Un seul chat.", "grammar"),
    )

    @Test
    fun checkingAnAnswerShowsTheFixAndReasonThenAdvances() {
        rule.setContent { MaterialTheme { ErrorReviewScreen(drills, onBack = {}) } }
        rule.onNodeWithText("je suis alle").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("je suis allé")
        rule.onNodeWithText("Check").performScrollTo().performClick()
        rule.onNodeWithText("Exactly.").assertExists()
        rule.onNodeWithText("The fix: je suis allé").assertExists()
        rule.onNodeWithText("Le participe s'accorde.").assertExists()
        rule.onNodeWithText("Next").performScrollTo().performClick()
        rule.onNodeWithText("les chats").assertExists()
    }

    @Test
    fun emptyStateExplainsHowCorrectionsArrive() {
        rule.setContent { MaterialTheme { ErrorReviewScreen(emptyList(), onBack = {}) } }
        rule.onNodeWithText("Nothing to review yet").assertIsDisplayed()
        rule.onNodeWithText("Speak → Write", substring = true).assertIsDisplayed()
    }
}
