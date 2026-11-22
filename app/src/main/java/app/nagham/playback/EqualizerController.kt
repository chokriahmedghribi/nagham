package app.nagham.playback

import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(val index: Int, val centerHz: Int, val level: Int)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevel: Int = 0,
    val maxLevel: Int = 0,
    val bassSupported: Boolean = false,
    val bassStrength: Int = 0,
    val preset: String = "مسطّح",
)

class EqualizerController(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null

    /** منحنيات جاهزة: الإدخال موضع النطاق من 0 (جهير) إلى 1 (حاد)، والناتج -1..1 من المدى. */
    private val presets: Map<String, (Double) -> Double> = mapOf(
        "مسطّح" to { _ -> 0.0 },
        "جهير قوي" to { f -> 0.8 * (1 - f) * (1 - f) - 0.1 * f },
        "صوت بشري" to { f -> 0.6 * kotlin.math.exp(-((f - 0.55) * (f - 0.55)) / 0.04) - 0.2 },
        "حدّة عالية" to { f -> 0.7 * f * f - 0.1 * (1 - f) },
    )
    val presetNames: List<String> get() = presets.keys.toList()

    fun attach(sessionId: Int) {
        release()
        if (sessionId <= 0) return
        runCatching {
            val e = Equalizer(0, sessionId)
            eq = e
            bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
            val n = e.numberOfBands.toInt()
            for (i in 0 until n) {
                val saved = prefs.getInt("eq_band_$i", Int.MIN_VALUE)
                if (saved != Int.MIN_VALUE) e.setBandLevel(i.toShort(), saved.toShort())
            }
            e.enabled = prefs.getBoolean("eq_on", false)
            bass?.let {
                if (it.strengthSupported) it.setStrength(prefs.getInt("bass", 0).toShort())
                it.enabled = prefs.getBoolean("eq_on", false)
            }
            publish(prefs.getString("eq_preset", "مسطّح") ?: "مسطّح")
        }
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        eq = null
        bass = null
        _state.value = EqState()
    }

    private fun publish(preset: String = _state.value.preset) {
        val e = eq ?: return
        val range = e.bandLevelRange
        val bands = (0 until e.numberOfBands.toInt()).map {
            EqBand(it, e.getCenterFreq(it.toShort()) / 1000, e.getBandLevel(it.toShort()).toInt())
        }
        _state.value = EqState(
            available = true,
            enabled = e.enabled,
            bands = bands,
            minLevel = range[0].toInt(),
            maxLevel = range[1].toInt(),
            bassSupported = bass?.strengthSupported == true,
            bassStrength = bass?.roundedStrength?.toInt() ?: 0,
            preset = preset,
        )
    }

    fun setEnabled(on: Boolean) {
        eq?.enabled = on
        bass?.enabled = on
        prefs.edit().putBoolean("eq_on", on).apply()
        publish()
    }

    fun setBand(index: Int, level: Int) {
        eq?.setBandLevel(index.toShort(), level.toShort())
        prefs.edit().putInt("eq_band_$index", level).putString("eq_preset", "مخصّص").apply()
        publish("مخصّص")
    }

    fun applyPreset(name: String) {
        val e = eq ?: return
        val curve = presets[name] ?: return
        val n = e.numberOfBands.toInt()
        val min = e.bandLevelRange[0].toInt()
        val max = e.bandLevelRange[1].toInt()
        val editor = prefs.edit()
        for (i in 0 until n) {
            val f = if (n > 1) i.toDouble() / (n - 1) else 0.0
            val v = curve(f).coerceIn(-1.0, 1.0)
            val level = (if (v >= 0) v * max else -v * min).toInt().coerceIn(min, max)
            e.setBandLevel(i.toShort(), level.toShort())
            editor.putInt("eq_band_$i", level)
        }
        editor.putString("eq_preset", name).apply()
        publish(name)
    }

    fun setBass(strength: Int) {
        val b = bass ?: return
        if (!b.strengthSupported) return
        b.setStrength(strength.toShort())
        prefs.edit().putInt("bass", strength).apply()
        publish()
    }
}
