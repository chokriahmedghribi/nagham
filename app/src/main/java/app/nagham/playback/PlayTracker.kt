package app.nagham.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.nagham.data.PlayEvent

/** يقيس مدة الاستماع الفعلية لكل أغنية ويسجلها محلياً (للتوصيات والملخص السنوي). */
class PlayTracker(
    private val player: Player,
    private val onEvent: (PlayEvent) -> Unit,
) : Player.Listener {

    private var current: MediaItem? = null
    private var accumMs = 0L
    private var resumedAt = 0L

    private fun now() = SystemClock.elapsedRealtime()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (resumedAt == 0L) resumedAt = now()
        } else if (resumedAt != 0L) {
            accumMs += now() - resumedAt
            resumedAt = 0L
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        flush()
        current = mediaItem
        accumMs = 0L
        resumedAt = if (player.isPlaying) now() else 0L
    }

    fun flush() {
        val item = current ?: return
        var total = accumMs
        if (resumedAt != 0L) total += now() - resumedAt
        val sec = (total / 1000).toInt()
        if (sec >= 3) {
            val t = item.toTrack()
            onEvent(
                PlayEvent(
                    trackId = t.id, title = t.title, artist = t.artist, artworkUrl = t.artworkUrl,
                    genre = t.genre, mood = t.mood, durationSec = t.durationSec,
                    listenedSec = sec, playedAt = System.currentTimeMillis(),
                )
            )
        }
        accumMs = 0L
        resumedAt = if (player.isPlaying) now() else 0L
    }
}
