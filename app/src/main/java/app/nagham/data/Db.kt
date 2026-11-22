package app.nagham.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<Track>)

    // قوائم التشغيل
    @Insert
    suspend fun insertPlaylist(p: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<Playlist>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToPlaylist(pt: PlaylistTrack)

    @Query("SELECT MAX(position) + 1 FROM playlist_tracks WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int?

    @Query(
        "SELECT t.* FROM tracks t JOIN playlist_tracks p ON t.id = p.trackId " +
            "WHERE p.playlistId = :id ORDER BY p.position"
    )
    fun playlistTracks(id: Long): Flow<List<Track>>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :pid AND trackId = :tid")
    suspend fun removeFromPlaylist(pid: Long, tid: String)

    // المفضلة
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: Favorite)

    @Query("DELETE FROM favorites WHERE trackId = :id")
    suspend fun removeFavorite(id: String)

    @Query("SELECT trackId FROM favorites")
    fun favoriteIds(): Flow<List<String>>

    @Query(
        "SELECT t.* FROM tracks t JOIN favorites f ON t.id = f.trackId ORDER BY f.addedAt DESC"
    )
    fun favoriteTracks(): Flow<List<Track>>

    // سجل الاستماع
    @Insert
    suspend fun insertEvent(e: PlayEvent)

    @Query("SELECT * FROM play_events WHERE playedAt >= :since ORDER BY playedAt DESC")
    suspend fun eventsSince(since: Long): List<PlayEvent>
}

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class, Favorite::class, PlayEvent::class],
    version = 1,
    exportSchema = false,
)
abstract class NaghamDb : RoomDatabase() {
    abstract fun dao(): MusicDao
}
