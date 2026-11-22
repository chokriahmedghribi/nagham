package app.nagham.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    // التنزيلات
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(d: Download)

    @Query("DELETE FROM downloads WHERE trackId = :id")
    suspend fun removeDownload(id: String)

    @Query("SELECT * FROM downloads")
    fun downloads(): Flow<List<Download>>

    @Query("SELECT trackId FROM downloads")
    fun downloadedIds(): Flow<List<String>>

    @Query("SELECT t.* FROM tracks t JOIN downloads d ON t.id = d.trackId ORDER BY d.downloadedAt DESC")
    fun downloadedTracks(): Flow<List<Track>>

    // سجل الاستماع
    @Insert
    suspend fun insertEvent(e: PlayEvent)

    @Query("SELECT * FROM play_events WHERE playedAt >= :since ORDER BY playedAt DESC")
    suspend fun eventsSince(since: Long): List<PlayEvent>
}

@Database(
    entities = [Track::class, Playlist::class, PlaylistTrack::class, Favorite::class, PlayEvent::class, Download::class],
    version = 3,
    exportSchema = false,
)
abstract class NaghamDb : RoomDatabase() {
    abstract fun dao(): MusicDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN downloadable INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS downloads (trackId TEXT NOT NULL, path TEXT NOT NULL, " +
                "bytes INTEGER NOT NULL, downloadedAt INTEGER NOT NULL, PRIMARY KEY(trackId))"
        )
    }
}
