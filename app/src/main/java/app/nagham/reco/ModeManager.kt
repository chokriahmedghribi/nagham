package app.nagham.reco

import app.nagham.data.MusicRepository
import app.nagham.playback.PlayerController
import app.nagham.playback.SleepTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ListeningMode(val label: String) {
    NORMAL("عادي"),
    RUN("جري"),
    SLEEP("نوم"),
}

/** أوضاع الاستماع: تختار موسيقى مناسبة للسياق، والنوم يفعّل مؤقتاً ذكياً تلقائياً. */
class ModeManager(
    private val repo: MusicRepository,
    private val player: PlayerController,
    private val sleep: SleepTimerController,
    private val scope: CoroutineScope,
) {
    private val _mode = MutableStateFlow(ListeningMode.NORMAL)
    val mode: StateFlow<ListeningMode> = _mode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val runMoods = setOf("Energizing", "Upbeat", "Aggressive", "Fiery", "Excited", "Empowering", "Rowdy", "Defiant")
    private val sleepMoods = setOf("Peaceful", "Serenity", "Easygoing", "Sentimental", "Yearning")

    fun start(mode: ListeningMode) {
        _mode.value = mode
        _error.value = null
        if (mode == ListeningMode.NORMAL) {
            sleep.cancel()
            return
        }
        scope.launch {
            try {
                val tracks = when (mode) {
                    ListeningMode.RUN ->
                        repo.moodMix(listOf("Electronic", "Hip-Hop/Rap", "Pop", "Rock"), runMoods)
                    else ->
                        repo.moodMix(listOf("Ambient", "Lo-Fi", "Classical", "Acoustic"), sleepMoods)
                }
                if (tracks.isEmpty()) {
                    _error.value = "لم أجد موسيقى مناسبة الآن"
                    return@launch
                }
                player.playTracks(tracks.take(40))
                if (mode == ListeningMode.SLEEP) sleep.start(30, smart = true) else sleep.cancel()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "تعذّر الاتصال بالإنترنت"
            }
        }
    }
}
