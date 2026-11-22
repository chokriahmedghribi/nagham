package app.nagham

import android.content.Context
import androidx.room.Room
import app.nagham.data.MusicRepository
import app.nagham.data.MIGRATION_2_3
import app.nagham.data.NaghamDb
import app.nagham.data.DownloadManager
import app.nagham.playback.EqualizerController
import app.nagham.playback.FadeController
import app.nagham.playback.PlayerController
import app.nagham.playback.SleepTimerController
import app.nagham.reco.ModeManager
import app.nagham.reco.RecommendationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** حاوية بسيطة للاعتماديات (بدون Hilt لإبقاء المشروع خفيفاً). */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val db: NaghamDb = Room.databaseBuilder(app, NaghamDb::class.java, "nagham.db")
        .addMigrations(MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()
    val dao get() = db.dao()
    private val prefs = app.getSharedPreferences("nagham", Context.MODE_PRIVATE)

    val repo = MusicRepository(db.dao(), prefs)
    val downloads = DownloadManager(app, repo, db.dao(), scope)
    val recommender = RecommendationEngine(repo, db.dao())

    val eq = EqualizerController(prefs)
    val sleep = SleepTimerController()
    val fade = FadeController(prefs, sleep)

    val player = PlayerController(app, repo, scope)
    val modes = ModeManager(repo, player, sleep, scope)

    init {
        repo.observeDownloads(scope)
    }
}
