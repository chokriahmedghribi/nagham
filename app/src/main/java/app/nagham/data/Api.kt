package app.nagham.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
) {
    fun toTrack() = Track(
        id = id,
        title = title,
        artist = user?.name.orEmpty(),
        artworkUrl = artwork?.medium ?: artwork?.small ?: artwork?.large,
        durationSec = duration,
        genre = genre?.takeIf { it.isNotBlank() },
        mood = mood?.takeIf { it.isNotBlank() },
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
