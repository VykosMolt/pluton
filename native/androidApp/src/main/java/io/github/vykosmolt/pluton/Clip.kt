package io.github.vykosmolt.pluton

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Plays one bundled read-along clip at a time; a new call stops the previous clip. */
internal class ClipPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    /** Starts a bundled clip. Returns false when the clip is absent, so callers can use TTS instead. */
    fun play(id: String?): Boolean {
        if (id == null) return false
        player?.release()
        player = null
        return runCatching {
            val mp = MediaPlayer()
            try {
                context.assets.openFd("fr/audio/$id.mp3").use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                mp.prepare()
                mp.start()
            } catch (e: Exception) {
                mp.release()
                throw e
            }
            player = mp
            true
        }.getOrDefault(false)
    }

    fun release() {
        player?.release()
        player = null
    }
}

@Composable
internal fun rememberClipPlayer(): ClipPlayer {
    val context = LocalContext.current
    val player = remember { ClipPlayer(context) }
    OnAppBackground { player.release() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    return player
}
