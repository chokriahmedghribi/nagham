package app.nagham.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

private const val FALLBACK_HOST = "https://discoveryprovider.audius.co"

class MusicRepository(private val dao: MusicDao) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var api: AudiusApi? = null

    @Volatile
    var host: String = FALLBACK_HOST
        private set

    private fun retrofit(base: String) = Retrofit.Builder()
        .baseUrl(base)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val lrclib: LrclibApi by lazy { retrofit("https://lrclib.net/").create(LrclibApi::class.java) }

    // ---------- الشبكة ----------

    private suspend fun discoverHost(): String = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("https://api.audius.co").build()).execute().use { r ->
                json.decodeFromString<HostsDto>(r.body?.string().orEmpty()).data.randomOrNull()
            }
        }.getOrNull()?.trimEnd('/') ?: FALLBACK_HOST
    }

    private suspend fun api(): AudiusApi = mutex.withLock {
        api ?: run {
            host = discoverHost()
            retrofit("$host/").create(AudiusApi::class.java).also { api = it }
        }
    }

    private suspend fun <T> withApi(block: suspend (AudiusApi) -> T): T = try {
        block(api())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // قد يكون الخادم الموزّع متعطلاً: نعيد الاكتشاف ونحاول مرة واحدة
        mutex.withLock { api = null }
        block(api())
    }

    suspend fun search(query: String): List<Track> {
        val tracks = withApi { it.search(query) }.data.filter { it.isStreamable }.map { it.toTrack() }
        dao.upsertTracks(tracks)
        return tracks
    }

    suspend fun trending(genre: String? = null): List<Track> {
        val tracks = withApi { it.trending(genre = genre) }.data.filter { it.isStreamable }.map { it.toTrack() }
        dao.upsertTracks(tracks)
        return tracks
    }

    /** مزيج حسب المزاج لأوضاع الجري/النوم. */
    suspend fun moodMix(genres: List<String>, moods: Set<String>): List<Track> = coroutineScope {
        val all = genres
            .map { g -> async { runCatching { trending(g) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten().distinctBy { it.id }
        val filtered = all.filter { moods.contains(it.mood ?: "") }
        (if (filtered.size >= 10) filtered else all).shuffled()
    }

    fun streamUrl(track: Track): String = "$host/v1/tracks/${track.id}/stream?app_name=$APP_NAME"

    suspend fun lyrics(track: Track): Lyrics? = runCatching {
        val d = lrclib.get(track.artist, track.title, track.durationSec)
        val synced = d.syncedLyrics?.let(::parseLrc).orEmpty()
        if (synced.isEmpty() && d.plainLyrics.isNullOrBlank()) null else Lyrics(synced, d.plainLyrics)
    }.getOrNull()

    private val lrcRegex = Regex("""\[(\d+):(\d+(?:\.\d+)?)](.*)""")

    private fun parseLrc(text: String): List<LyricLine> = text.lines().mapNotNull { line ->
        val m = lrcRegex.matchEntire(line.trim()) ?: return@mapNotNull null
        val (min, sec, content) = m.destructured
        LyricLine(((min.toLong() * 60 + sec.toDouble()) * 1000).toLong(), content.trim())
    }.sortedBy { it.timeMs }

    // ---------- قوائم التشغيل والمفضلة ----------

    fun playlists(): Flow<List<Playlist>> = dao.playlists()
    fun playlistTracks(id: Long): Flow<List<Track>> = dao.playlistTracks(id)
    fun favoriteTracks(): Flow<List<Track>> = dao.favoriteTracks()
    fun favoriteIds(): Flow<List<String>> = dao.favoriteIds()

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(Playlist(name = name.trim()))

    suspend fun deletePlaylist(id: Long) {
        dao.clearPlaylist(id)
        dao.deletePlaylist(id)
    }

    suspend fun addToPlaylist(playlistId: Long, track: Track) {
        dao.upsertTracks(listOf(track))
        dao.addToPlaylist(PlaylistTrack(playlistId, track.id, dao.nextPosition(playlistId) ?: 0))
    }

    suspend fun removeFromPlaylist(playlistId: Long, trackId: String) =
        dao.removeFromPlaylist(playlistId, trackId)

    suspend fun setFavorite(track: Track, favorite: Boolean) {
        if (favorite) {
            dao.upsertTracks(listOf(track))
            dao.addFavorite(Favorite(track.id))
        } else {
            dao.removeFavorite(track.id)
        }
    }
}
