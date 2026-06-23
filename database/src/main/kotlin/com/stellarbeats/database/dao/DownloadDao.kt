package com.stellarbeats.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stellarbeats.database.entities.DownloadEntity
import com.stellarbeats.database.entities.DownloadState
import com.stellarbeats.database.entities.LocalTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    // ── Insert / Update ──────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Query("""
        UPDATE downloads SET
            state = :state,
            progress = :progress,
            error_message = :errorMessage
        WHERE track_id = :trackId
    """)
    suspend fun updateState(
        trackId: String,
        state: DownloadState,
        progress: Int = 0,
        errorMessage: String? = null,
    )

    @Query("""
        UPDATE downloads SET
            state = :state,
            file_path = :filePath,
            file_extension = :fileExtension,
            file_size_bytes = :fileSizeBytes,
            mime_type = :mimeType,
            bitrate = :bitrate,
            quality_label = :qualityLabel,
            progress = 100,
            date_completed = :now
        WHERE track_id = :trackId
    """)
    suspend fun markCompleted(
        trackId: String,
        filePath: String,
        fileExtension: String,
        fileSizeBytes: Long,
        mimeType: String,
        bitrate: Int,
        qualityLabel: String?,
        state: DownloadState = DownloadState.COMPLETED,
        now: Long = System.currentTimeMillis(),
    )

    // ── Delete ───────────────────────────────

    /**
     * Remove a download record.
     * Caller MUST delete the actual file from disk before/after this.
     */
    @Query("DELETE FROM downloads WHERE track_id = :trackId")
    suspend fun delete(trackId: String)

    /** Remove all downloads that aren't COMPLETED (stale queued/failed/paused). */
    @Query("DELETE FROM downloads WHERE state != 'COMPLETED'")
    suspend fun clearIncomplete()

    // ── Fetch ────────────────────────────────

    @Query("SELECT * FROM downloads WHERE track_id = :trackId")
    suspend fun get(trackId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE track_id = :trackId")
    fun observe(trackId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads ORDER BY date_created DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    /** Get all completed downloads with their track metadata. */
    @Query("""
        SELECT lt.* FROM local_tracks lt
        INNER JOIN downloads d ON lt.track_id = d.track_id
        WHERE d.state = 'COMPLETED'
        ORDER BY lt.title ASC
    """)
    fun observeCompletedTracks(): Flow<List<LocalTrack>>

    // ── State queries ────────────────────────

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE track_id = :trackId AND state = 'COMPLETED')")
    suspend fun isDownloaded(trackId: String): Boolean

    @Query("SELECT file_path FROM downloads WHERE track_id = :trackId AND state = 'COMPLETED'")
    suspend fun getFilePath(trackId: String): String?

    /** Get the next download in queue that isn't started yet. */
    @Query("""
        SELECT * FROM downloads
        WHERE state = 'QUEUED'
        ORDER BY date_created ASC
        LIMIT 1
    """)
    suspend fun getNextQueued(): DownloadEntity?

    /** Count of completed downloads. */
    @Query("SELECT COUNT(*) FROM downloads WHERE state = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    /** Total size of all downloaded files in bytes. */
    @Query("SELECT COALESCE(SUM(file_size_bytes), 0) FROM downloads WHERE state = 'COMPLETED'")
    fun observeTotalSize(): Flow<Long>
}
