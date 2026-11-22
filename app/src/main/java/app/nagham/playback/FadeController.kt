package app.nagham.playback

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
import kotlin.math.min

/**
 * الانتقال المتلاشي بين الأغاني (تلاشي خروج ثم تلاشي دخول) + دمج معامل مؤقت النوم.
 * ملاحظة: هذا تلاشٍ متتابع وليس تداخلاً حقيقياً بين مشغّلين.
 */
class FadeController(
    private val prefs: SharedPreferences,
    private val sleep: SleepTimerController,
) {
    private val _crossfadeSec = MutableStateFlow(prefs.getInt("crossfade", 0))
    val crossfadeSec: StateFlow<Int> = _crossfadeSec.asStateFlow()

    private var job: Job? = null
    private var player: ExoPlayer? = null
    private var inStart = 0L

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            inStart = SystemClock.elapsedRealtime()
        }
    }

    fun setCrossfade(sec: Int) {
        _crossfadeSec.value = sec
        prefs.edit().putInt("crossfade", sec).apply()
    }

    fun attach(p: ExoPlayer, scope: CoroutineScope) {
        detach()
        player = p
        p.addListener(listener)
        job = scope.launch {
            while (isActive) {
                delay(150)
                apply(p)
            }
        }
    }

    fun detach() {
        job?.cancel()
        job = null
        player?.removeListener(listener)
        player = null
    }

    private fun apply(p: ExoPlayer) {
        var v = 1f
        val fadeMs = _crossfadeSec.value * 1000L
        if (fadeMs > 0 && p.isPlaying) {
            val dur = p.duration
            if (dur != C.TIME_UNSET && p.hasNextMediaItem() && p.repeatMode != Player.REPEAT_MODE_ONE) {
                val rem = dur - p.currentPosition
                if (rem < fadeMs) v = min(v, (rem.toFloat() / fadeMs).coerceIn(0f, 1f))
            }
            val since = SystemClock.elapsedRealtime() - inStart
            if (since < fadeMs) v = min(v, (since.toFloat() / fadeMs).coerceIn(0f, 1f))
        }
        v *= sleep.volumeFactor()
        val out = v.coerceIn(0f, 1f)
        if (p.volume != out) p.volume = out
    }
}
