package app.nagham.reco

import app.nagham.data.MusicDao
import app.nagham.data.MusicRepository
import app.nagham.data.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.pow
import kotlin.random.Random

private const val DAY = 24L * 60 * 60 * 1000

/**
 * توصيات تُحسب بالكامل على الهاتف من سجل استماعك: لا حساب ولا تتبّع ولا إرسال بيانات.
 * كل استماع يُوزن بحداثته (نصف الوزن كل 14 يوماً) وبنسبة الإكمال، والتخطي السريع يعطي وزناً سالباً.
 */
class RecommendationEngine(
    private val repo: MusicRepository,
    private val dao: MusicDao,
) {
    suspend fun forYou(limit: Int = 30): List<Track> = coroutineScope {
        val now = System.currentTimeMillis()
        val events = dao.eventsSince(now - 60 * DAY)
        if (events.size < 3) return@coroutineScope repo.trending()

        val genreW = HashMap<String, Double>()
        val artistW = HashMap<String, Double>()
        val moodW = HashMap<String, Double>()
        for (e in events) {
            val ageDays = (now - e.playedAt).toDouble() / DAY
            val recency = 0.5.pow(ageDays / 14.0)
            val completion = if (e.durationSec > 0) {
                (e.listenedSec.toDouble() / e.durationSec).coerceIn(0.0, 1.0)
            } else 0.5
            val w = recency * (if (e.listenedSec < 10) -0.6 else completion)
            e.genre?.let { genreW.merge(it, w) { a, b -> a + b } }
            e.mood?.let { moodW.merge(it, w) { a, b -> a + b } }
            if (e.artist.isNotBlank()) artistW.merge(e.artist, w) { a, b -> a + b }
        }

        val topGenres = genreW.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(3).map { it.key }
        val topArtists = artistW.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(2).map { it.key }

        val fromGenres = topGenres.map { g -> async { runCatching { repo.trending(g) }.getOrDefault(emptyList()) } }
        val fromArtists = topArtists.map { a -> async { runCatching { repo.search(a) }.getOrDefault(emptyList()) } }
        val candidates = (fromGenres + fromArtists).awaitAll().flatten().distinctBy { it.id }
        if (candidates.isEmpty()) return@coroutineScope repo.trending()

        val recentlyPlayed = events
            .filter { now - it.playedAt < 2 * DAY && it.listenedSec >= 30 }
            .map { it.trackId }.toSet()

        fun score(t: Track): Double =
            (t.genre?.let { genreW[it] } ?: 0.0) * 1.0 +
                (artistW[t.artist] ?: 0.0) * 1.5 +
                (t.mood?.let { moodW[it] } ?: 0.0) * 0.5 +
                Random.nextDouble() * 0.3 // لمسة عشوائية كي لا تتكرر القائمة نفسها

        val perArtist = HashMap<String, Int>()
        candidates
            .filter { it.id !in recentlyPlayed }
            .map { it to score(it) }
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { perArtist.merge(it.artist, 1) { a, b -> a + b }!! <= 3 } // تنويع الفنانين
            .take(limit)
            .ifEmpty { candidates.take(limit) }
    }
}
