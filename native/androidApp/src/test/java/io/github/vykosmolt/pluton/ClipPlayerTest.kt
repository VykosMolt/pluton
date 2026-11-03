package io.github.vykosmolt.pluton

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The APK does not bundle deck word clips; a missing asset must be reportable so the UI can speak it instead. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipPlayerTest {
    @Test
    fun missingAndNullClipsReportFailure() {
        val player = ClipPlayer(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(player.play(null))
        assertFalse(player.play("000000000000"))
        player.release()
    }
}
