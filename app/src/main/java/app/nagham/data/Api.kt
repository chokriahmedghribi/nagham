package app.nagham.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Query

const val APP_NAME = "Nagham"

/** Audius: كتالوج موسيقى مفتوح ومجاني بترخيص من الفنانين أنفسهم. */
interface AudiusApi {
    @GET("v1/tracks/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("limit") limit: Int = 30,
        @Query("app_name") app: String = APP_NAME,
    ): TrackListDto

    @GET("v1/tracks/trending")
    suspend fun trending(
        @Query("genre") genre: String? = null,
        @Query("time") time: String = "week",
        @Query("limit") limit: Int = 30,
        @Query("app_name") app: String = APP_NAME,
    ): TrackListDto
}

/** LRCLIB: كلمات أغاني مجانية، متزامنة بصيغة LRC. */
interface LrclibApi {
    @GET("api/get")
    suspend fun get(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
        @Query("duration") duration: Int,
    ): LrcDto
}

/** Jamendo: موسيقى كاملة بترخيص Creative Commons. يلزم client_id مجاني من devportal.jamendo.com */
interface JamendoApi {
    @GET("v3.0/tracks/")
    suspend fun tracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 30,
        @Query("search") search: String? = null,
        @Query("order") order: String? = null,
        @Query("lang") lang: String? = null,
        @Query("datebetween") dateBetween: String? = null,
        @Query("type", encoded = true) type: String = "single+albumtrack",
        @Query("audioformat") audioFormat: String = "mp32",
        @Query("imagesize") imageSize: Int = 300,
        @Query("include") include: String = "musicinfo",
    ): JamendoResp
}

@Serializable
data class JamendoResp(val headers: JamendoHeaders? = null, val results: List<JamendoTrackDto> = emptyList())

@Serializable
data class JamendoHeaders(
    val status: String = "",
    val code: Int = 0,
    @SerialName("error_message") val errorMessage: String = "",
)

@Serializable
data class JamendoTrackDto(
    val id: String,
    val name: String = "",
    val duration: Int = 0,
    @SerialName("artist_name") val artistName: String = "",
    val image: String? = null,
    val audio: String? = null,
    @SerialName("audiodownload_allowed") val downloadAllowed: Boolean = false,
    val musicinfo: JsonElement? = null,
) {
    fun toTrack(): Track {
        val genres = ((musicinfo as? JsonObject)?.get("tags") as? JsonObject)?.get("genres") as? JsonArray
        return Track(
            id = "jm_$id",
            title = name,
            artist = artistName,
            artworkUrl = image?.takeIf { it.isNotBlank() },
            durationSec = duration,
            genre = genres?.firstOrNull()?.jsonPrimitive?.contentOrNull?.replaceFirstChar { it.uppercase() },
            mood = null,
            source = "jamendo",
            streamUrl = audio,
            downloadable = downloadAllowed,
        )
    }
}

@Serializable
data class HostsDto(val data: List<String> = emptyList())

@Serializable
data class TrackListDto(val data: List<TrackDto> = emptyList())

@Serializable
data class TrackDto(
    val id: String,
    val title: String = "",
    val duration: Int = 0,
    val genre: String? = null,
    val mood: String? = null,
    val artwork: ArtworkDto? = null,
    val user: UserDto? = null,
    @SerialName("is_streamable") val isStreamable: Boolean = true,
    @SerialName("is_downloadable") val isDownloadable: Boolean = false,
) {
    fun toTrack() = Track(
        id = id,
        title = title,
        artist = user?.name.orEmpty(),
        artworkUrl = artwork?.medium ?: artwork?.small ?: artwork?.large,
        durationSec = duration,
        genre = genre?.takeIf { it.isNotBlank() },
        mood = mood?.takeIf { it.isNotBlank() },
        downloadable = isDownloadable,
    )
}

@Serializable
data class ArtworkDto(
    @SerialName("150x150") val small: String? = null,
    @SerialName("480x480") val medium: String? = null,
    @SerialName("1000x1000") val large: String? = null,
)

@Serializable
data class UserDto(val name: String = "")

@Serializable
data class LrcDto(val syncedLyrics: String? = null, val plainLyrics: String? = null)
