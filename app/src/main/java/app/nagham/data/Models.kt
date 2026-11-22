package app.nagham.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSec: Int,
    val genre: String?,
    val mood: String?,
    /** audius أو jamendo */
    val source: String = "audius",
    /** رابط بث مباشر (Jamendo)؛ فارغ لـ Audius لأن مضيفه يُكتشف وقت التشغيل */
    val streamUrl: String? = null,
    /** يسمح صاحب العمل بتحميله */
    val downloadable: Boolean = false,
)

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val trackId: String,
    val path: String,
    val bytes: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** سجل استماع محلي: أساس التوصيات والملخص السنوي. لا يغادر الجهاز أبداً. */
@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val genre: String?,
    val mood: String?,
    val durationSec: Int,
    val listenedSec: Int,
    val playedAt: Long,
)

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: List<LyricLine>, val plain: String?)
