package app.nagham.playback

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class SleepState(
    val active: Boolean = false,
    val remainingMs: Long = 0,
    val endOfTrack: Boolean = false,
    val smart: Boolean = false,
)

/**
 * مؤقت النوم. في الوضع «الذكي» يخفض الصوت تدريجياً خلال آخر 5 دقائق إلى 35%،
 * ثم يتلاشى بالكامل في آخر 30 ثانية قبل الإيقاف، فلا ينقطع الصوت فجأة.
 */
class SleepTimerController {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var job: Job? = null
    private var endAt = 0L

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                _state.value = SleepState()
            }
        }
    }

    fun attach(p: ExoPlayer, scope: CoroutineScope) {
        detach()
        player = p
        p.addListener(listener)
        job = scope.launch {
            while (isActive) {
                delay(500)
                tick()
            }
        }
    }

    fun detach() {
        job?.cancel()
        job = null
        player?.removeListener(listener)
        player = null
    }

    fun start(minutes: Int, smart: Boolean = false) {
        player?.setPauseAtEndOfMediaItems(false)
        endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.value = SleepState(active = true, remainingMs = minutes * 60_000L, smart = smart)
    }

    fun startEndOfTrack() {
        player?.setPauseAtEndOfMediaItems(true)
        _state.value = SleepState(active = true, endOfTrack = true)
    }

    fun cancel() {
        player?.setPauseAtEndOfMediaItems(false)
        _state.value = SleepState()
    }

    private fun tick() {
        val s = _state.value
        if (!s.active || s.endOfTrack) return
        val rem = endAt - SystemClock.elapsedRealtime()
        if (rem <= 0) {
            player?.pause()
            _state.value = SleepState()
        } else {
            _state.value = s.copy(remainingMs = rem)
        }
    }

    /** معامل خفض الصوت (0..1) يُضرب في مستوى الصوت النهائي. */
    fun volumeFactor(): Float {
        val s = _state.value
        if (!s.active || s.endOfTrack) return 1f
        val rem = endAt - SystemClock.elapsedRealtime()
        val fadeOut = 30_000L
        val ramp = 5 * 60_000L
        return when {
            rem <= 0 -> 0f
            rem <= fadeOut -> (if (s.smart) 0.35f else 1f) * (rem.toFloat() / fadeOut)
            s.smart && rem < ramp -> 1f - 0.65f * ((ramp - rem).toFloat() / (ramp - fadeOut))
            else -> 1f
        }
    }
}
