package com.stellarbeats.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.stellarbeats.database.entities.LocalTrack
import com.stellarbeats.database.entities.Playlist
import com.stellarbeats.database.entities.PlaylistEntry
import kotlinx.coroutines.flow.Flow

/**
 * Playlist data access with transactional safety for position management.
 *
 * All mutations that touch positions use @Transaction to prevent
 * inconsistent state if something fails mid-operation.
 */
@Dao
interface PlaylistDao {

    // ── Playlist CRUD ────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :name, description = :description, date_modified = :now WHERE playlist_id = :playlistId")
    suspend fun updatePlaylist(playlistId: String, name: String, description: String? = null, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM playlists WHERE playlist_id = :playlistId AND is_system = 0")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT * FROM playlists ORDER BY CASE WHEN is_system = 1 THEN 0 ELSE 1 END, date_modified DESC")
    fun observeAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    suspend fun getPlaylist(playlistId: String): Playlist?

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    fun observePlaylist(playlistId: String): Flow<Playlist?>

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE playlist_id = :playlistId)")
    suspend fun playlistExists(playlistId: String): Boolean

    // ── Track management ─────────────────────

    /**
     * Add a track to a playlist at the end.
     * If already present, moves it to the end.
     */
    @Transaction
    suspend fun addTrack(playlistId: String, trackId: String) {
        // Remove if already in playlist (will be re-added at end)
        removeTrack(playlistId, trackId)

        // Get current max position
        val maxPos = getMaxPosition(playlistId)

        // Insert at next position
        val entry = PlaylistEntry(
            playlistId = playlistId,
            trackId = trackId,
            position = (maxPos + 1).coerceAtLeast(0),
        )
        insertEntry(entry)

        // Update cached count
        updateTrackCount(playlistId)
    }

    /**
     * Add multiple tracks to a playlist, maintaining order.
     */
    @Transaction
    suspend fun addTracks(playlistId: String, trackIds: List<String>) {
        val startPos = (getMaxPosition(playlistId) + 1).coerceAtLeast(0)
        val entries = trackIds.mapIndexed { index, trackId ->
            PlaylistEntry(
                playlistId = playlistId,
                trackId = trackId,
                position = startPos + index,
            )
        }
        insertEntries(entries)
        updateTrackCount(playlistId)
    }

    /**
     * Remove a track from a playlist and close the position gap.
     */
    @Transaction
    suspend fun removeTrack(playlistId: String, trackId: String) {
        deleteEntry(playlistId, trackId)
        // Close the gap: shift all entries after the removed one up by 1
        shiftPositionsUp(playlistId)
        updateTrackCount(playlistId)
    }

    /**
     * Move a track within a playlist from [fromPosition] to [toPosition].
     * Handles both upward and downward moves correctly.
     */
    @Transaction
    suspend fun moveTrack(playlistId: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return

        // Temporarily move the track to -1 to avoid unique constraint issues
        setEntryPosition(playlistId, fromPosition, -1)

        if (fromPosition < toPosition) {
            // Moving down: shift tracks between from+1..to up by 1
            shiftRangeUp(playlistId, fromPosition + 1, toPosition)
        } else {
            // Moving up: shift tracks between to..from-1 down by 1
            shiftRangeDown(playlistId, toPosition, fromPosition - 1)
        }

        // Place the track at its final position
        setEntryPosition(playlistId, -1, toPosition)
    }

    // ── Query playlist tracks ────────────────

    /**
     * Get all tracks in a playlist, ordered by position.
     * JOINs with local_tracks to get full track data.
     */
    @Query("""
        SELECT lt.* FROM local_tracks lt
        INNER JOIN playlist_entries pe ON lt.track_id = pe.track_id
        WHERE pe.playlist_id = :playlistId
        ORDER BY pe.position ASC
    """)
    fun observePlaylistTracks(playlistId: String): Flow<List<LocalTrack>>

    @Query("""
        SELECT lt.* FROM local_tracks lt
        INNER JOIN playlist_entries pe ON lt.track_id = pe.track_id
        WHERE pe.playlist_id = :playlistId
        ORDER BY pe.position ASC
    """)
    suspend fun getPlaylistTracks(playlistId: String): List<LocalTrack>

    /** Check if a track is in a specific playlist. */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM playlist_entries
            WHERE playlist_id = :playlistId AND track_id = :trackId
        )
    """)
    suspend fun isTrackInPlaylist(playlistId: String, trackId: String): Boolean

    /** Get all playlist IDs that contain a track. */
    @Query("SELECT playlist_id FROM playlist_entries WHERE track_id = :trackId")
    suspend fun getPlaylistIdsForTrack(trackId: String): List<String>

    // ── Internal entry operations ────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: PlaylistEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<PlaylistEntry>)

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun deleteEntry(playlistId: String, trackId: String)

    @Query("SELECT MAX(position) FROM playlist_entries WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int

    @Query("""
        UPDATE playlist_entries SET position = position - 1
        WHERE playlist_id = :playlistId AND position > -1
        ORDER BY position ASC
    """)
    suspend fun shiftPositionsUp(playlistId: String)

    @Query("""
        UPDATE playlist_entries SET position = position - 1
        WHERE playlist_id = :playlistId
          AND position >= :start AND position <= :end
    """)
    suspend fun shiftRangeUp(playlistId: String, start: Int, end: Int)

    @Query("""
        UPDATE playlist_entries SET position = position + 1
        WHERE playlist_id = :playlistId
          AND position >= :start AND position <= :end
    """)
    suspend fun shiftRangeDown(playlistId: String, start: Int, end: Int)

    @Query("""
        UPDATE playlist_entries SET position = :newPosition
        WHERE playlist_id = :playlistId AND position = :oldPosition
    """)
    suspend fun setEntryPosition(playlistId: String, oldPosition: Int, newPosition: Int)

    /**
     * Recalculate and cache the track count for a playlist.
     */
    @Query("""
        UPDATE playlists SET
            track_count = (SELECT COUNT(*) FROM playlist_entries WHERE playlist_id = :playlistId),
            total_duration_ms = COALESCE(
                (SELECT SUM(lt.duration_ms) FROM local_tracks lt
                 INNER JOIN playlist_entries pe ON lt.track_id = pe.track_id
                 WHERE pe.playlist_id = :playlistId),
                0
            ),
            date_modified = :now
        WHERE playlist_id = :playlistId
    """)
    suspend fun updateTrackCount(playlistId: String, now: Long = System.currentTimeMillis())

    /**
     * Refresh cached counts for all playlists.
     * Call after batch operations or import.
     */
    @Query("""
        UPDATE playlists SET
            track_count = (SELECT COUNT(*) FROM playlist_entries WHERE playlist_entries.playlist_id = playlists.playlist_id),
            total_duration_ms = COALESCE(
                (SELECT SUM(lt.duration_ms) FROM local_tracks lt
                 INNER JOIN playlist_entries pe ON lt.track_id = pe.track_id
                 WHERE pe.playlist_id = playlists.playlist_id),
                0
            )
    """)
    suspend fun refreshAllCounts()
}
