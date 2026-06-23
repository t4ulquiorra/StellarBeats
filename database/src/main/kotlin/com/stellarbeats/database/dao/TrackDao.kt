package com.stellarbeats.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.stellarbeats.database.entities.LocalTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    // ── Insert / Update ──────────────────────

    /**
     * Insert a track. If it already exists (same trackId), replace it.
     *
     * Using REPLACE instead of UPSERT because we want to fully overwrite
     * on conflict — the source data might have updated metadata.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: LocalTrack)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<LocalTrack>)

    /**
     * Update specific fields without touching the rest.
     * Used for incrementing play count, updating dominant color, etc.
     */
    @Query("""
        UPDATE local_tracks SET
            dominant_color = :dominantColor,
            liked = :liked,
            local_play_count = :localPlayCount,
            date_played = :datePlayed
        WHERE track_id = :trackId
    """)
    suspend fun updatePlaybackState(
        trackId: String,
        dominantColor: Int? = null,
        liked: Boolean? = null,
        localPlayCount: Int? = null,
        datePlayed: Long? = null,
    )

    @Query("UPDATE local_tracks SET liked = NOT liked WHERE track_id = :trackId")
    suspend fun toggleLike(trackId: String)

    @Query("UPDATE local_tracks SET dominant_color = :color WHERE track_id = :trackId")
    suspend fun updateDominantColor(trackId: String, color: Int)

    @Query("""
        UPDATE local_tracks SET
            local_play_count = local_play_count + 1,
            date_played = :timestamp
        WHERE track_id = :trackId
    """)
    suspend fun incrementPlayCount(trackId: String, timestamp: Long = System.currentTimeMillis())

    // ── Delete ───────────────────────────────

    /**
     * Remove a track from the library.
     *
     * CASCADE will also remove it from all playlist_entries and downloads.
     * The caller is responsible for deleting the actual download file from disk.
     */
    @Query("DELETE FROM local_tracks WHERE track_id = :trackId")
    suspend fun delete(trackId: String)

    /**
     * Remove tracks that aren't in any playlist, aren't liked, and
     * have no download. Called as cleanup.
     */
    @Query("""
        DELETE FROM local_tracks
        WHERE track_id NOT IN (SELECT track_id FROM playlist_entries)
          AND liked = 0
          AND track_id NOT IN (SELECT track_id FROM downloads)
    """)
    suspend fun pruneOrphanedTracks()

    // ── Fetch single ─────────────────────────

    @Query("SELECT * FROM local_tracks WHERE track_id = :trackId")
    suspend fun get(trackId: String): LocalTrack?

    @Query("SELECT * FROM local_tracks WHERE track_id = :trackId")
    fun observe(trackId: String): Flow<LocalTrack?>

    // ── Fetch lists ──────────────────────────

    @Query("SELECT * FROM local_tracks WHERE liked = 1 ORDER BY date_added DESC")
    fun observeLiked(): Flow<List<LocalTrack>>

    @Query("""
        SELECT * FROM local_tracks
        WHERE local_play_count > 0
        ORDER BY local_play_count DESC, date_played DESC
        LIMIT :limit
    """)
    fun observeMostPlayed(limit: Int = 50): Flow<List<LocalTrack>>

    @Query("""
        SELECT * FROM local_tracks
        WHERE date_played IS NOT NULL
        ORDER BY date_played DESC
        LIMIT :limit
    """)
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<LocalTrack>>

    /** All tracks in the library, newest first. */
    @Query("SELECT * FROM local_tracks ORDER BY date_added DESC")
    fun observeAll(): Flow<List<LocalTrack>>

    /** All tracks from a specific source. */
    @Query("SELECT * FROM local_tracks WHERE source = :source ORDER BY title ASC")
    fun observeBySource(source: String): Flow<List<LocalTrack>>

    // ── Search ───────────────────────────────

    @Query("""
        SELECT * FROM local_tracks
        WHERE title LIKE '%' || :query || '%'
           OR artists_json LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN title LIKE :query || '%' THEN 0
                 WHEN title LIKE '%' || :query || '%' THEN 1
                 WHEN artists_json LIKE '%' || :query || '%' THEN 2
                 ELSE 3
            END,
            title ASC
        LIMIT :limit
    """)
    suspend fun search(query: String, limit: Int = 50): List<LocalTrack>

    // ── Counters ─────────────────────────────

    @Query("SELECT COUNT(*) FROM local_tracks")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM local_tracks WHERE liked = 1")
    suspend fun likedCount(): Int

    @Query("SELECT COUNT(*) FROM local_tracks WHERE source = :source")
    suspend fun countBySource(source: String): Int

    // ── Exists check ─────────────────────────

    @Query("SELECT EXISTS(SELECT 1 FROM local_tracks WHERE track_id = :trackId)")
    suspend fun exists(trackId: String): Boolean

    // ── Raw query for complex filters ────────

    @RawQuery
    suspend fun rawQuery(query: SupportSQLiteQuery): List<LocalTrack>
}
