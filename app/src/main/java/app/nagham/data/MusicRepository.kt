package app.nagham.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.time.LocalDate
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

class MusicRepository(private val dao: MusicDao, private val prefs: SharedPreferences) {

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

    private suspend fun audiusSearch(query: String): List<Track> {
        val tracks = withApi { it.search(query) }.data.filter { it.isStreamable }.map { it.toTrack() }
        dao.upsertTracks(tracks)
        return tracks
    }

    /** بحث موحّد: Jamendo (إن أُضيف المفتاح) + Audius، بنتائج متداخلة. */
    suspend fun search(query: String): List<Track> = coroutineScope {
        val a = async { runCatching { audiusSearch(query) } }
        val j = if (hasJamendo) async { runCatching { jamendo(search = query) } } else null
        val ra = a.await()
        val rj = j?.await()
        val lists = listOfNotNull(rj?.getOrNull(), ra.getOrNull())
        if (lists.isEmpty()) throw (rj?.exceptionOrNull() ?: ra.exceptionOrNull() ?: IOException("لا نتائج"))
        val out = ArrayList<Track>()
        val max = lists.maxOf { it.size }
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        out.distinctBy { it.id }
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

    // ---------- Jamendo ----------

    private val _sourcesVersion = MutableStateFlow(0)
    val sourcesVersion: StateFlow<Int> = _sourcesVersion.asStateFlow()

    val jamendoId: String get() = prefs.getString("jamendo_id", "")?.trim().orEmpty()
    val hasJamendo: Boolean get() = jamendoId.isNotEmpty()

    fun setJamendoId(value: String) {
        prefs.edit().putString("jamendo_id", value.trim()).apply()
        _sourcesVersion.value += 1
    }

    private val jamendoApi: JamendoApi by lazy { retrofit("https://api.jamendo.com/").create(JamendoApi::class.java) }

    private suspend fun jamendo(
        search: String? = null,
        order: String? = null,
        lang: String? = null,
        dateBetween: String? = null,
        limit: Int = 30,
    ): List<Track> {
        val r = jamendoApi.tracks(
            clientId = jamendoId, limit = limit, search = search,
            order = order, lang = lang, dateBetween = dateBetween,
        )
        val h = r.headers
        if (h != null && h.status.isNotEmpty() && h.status != "success") {
            throw IllegalStateException("Jamendo: " + h.errorMessage.ifBlank { "طلب مرفوض (تحقق من المفتاح)" })
        }
        val tracks = r.results.filter { !it.audio.isNullOrBlank() }.map { it.toTrack() }
        dao.upsertTracks(tracks)
        return tracks
    }

    /** الأكثر رواجاً بين الإصدارات الحديثة (آخر 4 أشهر). */
    suspend fun jamendoLatest(): List<Track> {
        val to = LocalDate.now()
        return jamendo(order = "popularity_week", dateBetween = "${to.minusDays(120)}_$to")
    }

    suspend fun jamendoArabic(): List<Track> = jamendo(lang = "ar", order = "popularity_month")

    fun streamUrl(track: Track): String = track.streamUrl ?: "$host/v1/tracks/${track.id}/stream?app_name=$APP_NAME"

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
