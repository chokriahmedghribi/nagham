package app.nagham.spotify

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import app.nagham.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class SpotifyNow(val title: String, val artist: String, val artworkUrl: String?, val isPlaying: Boolean)

/**
 * ربط حساب Spotify (OAuth PKCE). البحث عبر Web API والتشغيل عبر تطبيق Spotify نفسه (Connect)،
 * فيلزم Premium وتطبيق Spotify مثبّتاً على الهاتف.
 */
class SpotifyController(
    private val ctx: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
) {
    companion object {
        const val REDIRECT = "nagham://spotify-callback"
        private const val SCOPES = "user-read-playback-state user-modify-playback-state"
        private const val API = "https://api.spotify.com/v1"
    }

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    val clientId: String get() = prefs.getString("sp_client", "")?.trim().orEmpty()

    private val _connected = MutableStateFlow(prefs.getString("sp_refresh", null) != null)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    private val _now = MutableStateFlow<SpotifyNow?>(null)
    val nowPlaying: StateFlow<SpotifyNow?> = _now.asStateFlow()

    private var accessToken: String? = null
    private var expiresAt = 0L
    private var pollJob: Job? = null

    fun setClientId(v: String) {
        prefs.edit().putString("sp_client", v.trim()).apply()
    }

    // ---------- تسجيل الدخول ----------

    fun login() {
        if (clientId.isEmpty()) { _message.value = "أدخل Spotify Client ID أولاً"; return }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val rnd = SecureRandom()
        val verifier = (1..64).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        prefs.edit().putString("sp_verifier", verifier).apply()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun logout() {
        prefs.edit().remove("sp_refresh").apply()
        accessToken = null
        pollJob?.cancel()
        _now.value = null
        _connected.value = false
    }

    fun handleRedirect(uri: Uri) {
        if (uri.scheme != "nagham" || uri.host != "spotify-callback") return
        val code = uri.getQueryParameter("code")
        if (code == null) {
            _message.value = "لم يكتمل الربط: " + (uri.getQueryParameter("error") ?: "ملغى")
            return
        }
        scope.launch {
            val ok = tokenRequest(
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to REDIRECT,
                    "client_id" to clientId,
                    "code_verifier" to prefs.getString("sp_verifier", "").orEmpty(),
                )
            )
            if (ok) _message.value = "تم ربط Spotify"
        }
    }

    private suspend fun tokenRequest(form: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            http.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build())
                .execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        _message.value = "فشل الربط: " + text.take(140)
                        return@withContext false
                    }
                    val o = json.parseToJsonElement(text).jsonObject
                    accessToken = o["access_token"]?.jsonPrimitive?.contentOrNull
                    val ttl = o["expires_in"]?.jsonPrimitive?.int ?: 3600
                    expiresAt = System.currentTimeMillis() + ttl * 1000L - 60_000
                    o["refresh_token"]?.jsonPrimitive?.contentOrNull?.let {
                        prefs.edit().putString("sp_refresh", it).apply()
                    }
                    _connected.value = prefs.getString("sp_refresh", null) != null
                    true
                }
        } catch (e: Exception) {
            _message.value = "تعذّر الاتصال بـ Spotify"
            false
        }
    }

    private suspend fun token(forceRefresh: Boolean = false): String? {
        val t = accessToken
        if (!forceRefresh && t != null && System.currentTimeMillis() < expiresAt) return t
        val refresh = prefs.getString("sp_refresh", null) ?: return null
        val ok = tokenRequest(
            mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId)
        )
        return if (ok) accessToken else null
    }

    // ---------- الطلبات ----------

    private suspend fun call(method: String, url: String, jsonBody: String? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            var tok = token() ?: throw IllegalStateException("Spotify غير مرتبط")
            repeat(2) { attempt ->
                val body = when {
                    jsonBody != null -> jsonBody.toRequestBody("application/json".toMediaType())
                    method == "GET" -> null
                    else -> ByteArray(0).toRequestBody(null)
                }
                val req = Request.Builder().url(url).header("Authorization", "Bearer $tok").method(method, body).build()
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (r.code == 401 && attempt == 0) {
                        tok = token(forceRefresh = true) ?: throw IllegalStateException("انتهت صلاحية الربط")
                    } else {
                        return@withContext r.code to text
                    }
                }
            }
            throw IllegalStateException("فشل الطلب")
        }

    // ---------- البحث ----------

    suspend fun search(query: String, limit: Int = 10): List<Track> {
        val q = URLEncoder.encode(query, "UTF-8")
        val (code, text) = call("GET", "$API/search?q=$q&type=track&limit=$limit")
        if (code != 200) throw IllegalStateException("Spotify $code")
        val items = json.parseToJsonElement(text).jsonObject["tracks"]?.jsonObject?.get("items")?.jsonArray
            ?: return emptyList()
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val images = o["album"]?.jsonObject?.get("images")?.jsonArray
            Track(
                id = "sp_$id",
                title = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                artist = o["artists"]?.jsonArray?.joinToString(", ") {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }.orEmpty(),
                artworkUrl = (images?.getOrNull(1) ?: images?.getOrNull(0))
                    ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                durationSec = (o["duration_ms"]?.jsonPrimitive?.int ?: 0) / 1000,
                genre = null,
                mood = null,
                source = "spotify",
                streamUrl = "spotify:track:$id",
                downloadable = false,
            )
        }
    }

    /** أقسام الصفحة الرئيسية: عدة بحوث صغيرة (الحد الأقصى 10 لكل طلب). */
    suspend fun discover(queries: List<String>): List<Track> =
        queries.flatMap { runCatching { search(it) }.getOrDefault(emptyList()) }.distinctBy { it.id }

    // ---------- التشغيل عبر تطبيق Spotify ----------

    fun playUris(uris: List<String>) {
        scope.launch {
            try {
                val body = buildJsonObject { put("uris", JsonArray(uris.take(50).map { JsonPrimitive(it) })) }.toString()
                var (code, text) = call("PUT", "$API/me/player/play", body)
                if (code == 404) {
                    val deviceId = pickDevice()
                    if (deviceId == null) {
                        _message.value = "افتح تطبيق Spotify على هاتفك مرة واحدة ثم أعد المحاولة"
                        return@launch
                    }
                    val r = call("PUT", "$API/me/player/play?device_id=$deviceId", body)
                    code = r.first; text = r.second
                }
                when {
                    code in 200..204 -> startPolling()
                    code == 403 -> _message.value = "التشغيل عبر Spotify يتطلب حساب Premium"
                    else -> _message.value = "تعذّر التشغيل عبر Spotify ($code)"
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "تعذّر الاتصال بـ Spotify"
            }
        }
    }

    private suspend fun pickDevice(): String? {
        val (code, text) = call("GET", "$API/me/player/devices")
        if (code != 200) return null
        val devices = json.parseToJsonElement(text).jsonObject["devices"]?.jsonArray ?: return null
        val list = devices.mapNotNull { it as? JsonObject }
        val chosen = list.firstOrNull { it["is_active"]?.jsonPrimitive?.boolean == true }
            ?: list.firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "Smartphone" }
            ?: list.firstOrNull()
        return chosen?.get("id")?.jsonPrimitive?.contentOrNull
    }

    fun togglePlay() {
        scope.launch {
            runCatching {
                if (_now.value?.isPlaying == true) call("PUT", "$API/me/player/pause")
                else call("PUT", "$API/me/player/play")
                delay(400)
                poll()
            }
        }
    }

    fun next() { scope.launch { runCatching { call("POST", "$API/me/player/next"); delay(500); poll() } } }
    fun previous() { scope.launch { runCatching { call("POST", "$API/me/player/previous"); delay(500); poll() } } }

    fun addToQueue(uri: String) {
        scope.launch {
            runCatching { call("POST", "$API/me/player/queue?uri=" + URLEncoder.encode(uri, "UTF-8")) }
        }
    }

    /** يُستدعى قبل تشغيل أغنية من مصدر آخر كي لا يعملا معاً. */
    fun pauseIfActive() {
        if (_now.value?.isPlaying == true) {
            scope.launch { runCatching { call("PUT", "$API/me/player/pause"); delay(300); poll() } }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            var empty = 0
            while (isActive && empty < 5) {
                val got = poll()
                empty = if (got) 0 else empty + 1
                delay(3000)
            }
        }
    }

    private suspend fun poll(): Boolean {
        return try {
            val (code, text) = call("GET", "$API/me/player")
            if (code != 200) { _now.value = null; return false }
            val o = json.parseToJsonElement(text).jsonObject
            val item = o["item"] as? JsonObject
            if (item == null) { _now.value = null; return false }
            val images = item["album"]?.jsonObject?.get("images")?.jsonArray
            _now.value = SpotifyNow(
                title = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                artist = item["artists"]?.jsonArray?.joinToString(", ") {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }.orEmpty(),
                artworkUrl = (images?.getOrNull(1) ?: images?.getOrNull(0))
                    ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                isPlaying = o["is_playing"]?.jsonPrimitive?.boolean == true,
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
