package app.nagham.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.nagham.data.MusicRepository
import app.nagham.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val current: Track? = null,
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Track> = emptyList(),
)

/** واجهة التطبيق للمشغّل الذي يعمل داخل الخدمة (عبر MediaController). */
class PlayerController(
    private val ctx: Context,
    private val repo: MusicRepository,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(ctx))

        scope.launch {
            while (true) {
                delay(400)
                val c = controller ?: continue
                if (c.isPlaying) {
                    _state.update {
                        it.copy(positionMs = c.currentPosition, durationMs = durationOf(c, it.current))
                    }
                }
            }
        }
    }

    private fun durationOf(c: MediaController, cur: Track?): Long {
        val d = c.duration
        return if (d != C.TIME_UNSET && d > 0) d else (cur?.durationSec ?: 0) * 1000L
    }

    private fun refresh() {
        val c = controller ?: return
        val cur = c.currentMediaItem?.toTrack()
        _state.value = PlayerUiState(
            current = cur,
            index = c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition,
            durationMs = durationOf(c, cur),
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            queue = List(c.mediaItemCount) { c.getMediaItemAt(it).toTrack() },
        )
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.addLast(block)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem(repo.streamUrl(it)) }
        withController { c ->
            c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playNext(track: Track) = withController { c ->
        val item = track.toMediaItem(repo.streamUrl(track))
        if (c.mediaItemCount == 0) {
            c.setMediaItem(item); c.prepare(); c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, item)
        }
    }

    fun enqueue(track: Track) = withController { c ->
        val item = track.toMediaItem(repo.streamUrl(track))
        val wasEmpty = c.mediaItemCount == 0
        c.addMediaItem(item)
        if (wasEmpty) { c.prepare(); c.play() }
    }

    fun togglePlay() = withController { c -> if (c.isPlaying) c.pause() else c.play() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun skipTo(index: Int) = withController { it.seekTo(index, 0L) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
