package io.github.vykosmolt.pluton

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.vykosmolt.pluton.core.Chunk
import io.github.vykosmolt.pluton.core.Token
import io.github.vykosmolt.pluton.core.Word
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The word sheet is the only chunk entry point; exercise its Add callback, not just the core data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WordSheetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun offersChunksForTheTappedWordAndAddsTheChosenOne() {
        var added: Chunk? = null
        rule.setContent {
            MaterialTheme {
                WordSheet(
                    token = Token(0, 5, "avoir", "avoir", false),
                    word = Word("avoir", display = "avoir", gloss = "to have"),
                    sentence = "Je vais avoir besoin de ton aide.",
                    card = null,
                    onAdd = {},
                    onAddChunk = { added = it },
                    onKnown = {},
                    onUndo = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Chunks with this word").assertIsDisplayed()
        rule.onAllNodesWithText("Add")[0].performClick()
        assertNotNull("Add should hand the chunk to the caller", added)
        assertTrue(added!!.lemmas.contains("avoir"))
    }
}
