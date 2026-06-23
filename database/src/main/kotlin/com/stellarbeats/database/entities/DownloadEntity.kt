package com.stellarbeats.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a downloaded track's file on disk.
 *
 * Separated from [LocalTrack] because:
 * 1. A track can exist in the library without being downloaded
 * 2. Download state (progress, path, quality) is orthogonal to metadata
 * 3. Downloads can be retried without touching the track metadata
 * 4. The download manager needs fast queries on state without JOINs
 *
 * The actual audio file lives in:
 * `context.filesDir/downloads/{trackId}.{ext}`
 *
 * When a download is deleted, this row is removed AND the file is deleted.
 */
@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = LocalTrack::class,
            parentColumns = ["track_id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["track_id"], name = "idx_downloads_track_id", unique = true),
        Index(value = ["state"], name = "idx_downloads_state"),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    // ── File info ───────────────────────────

    /**
     * Absolute path to the downloaded file on disk.
     * Null if download hasn't completed (still in progress or failed).
     */
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,

    /** File extension without dot: "m4a", "opus", "webm" */
    @ColumnInfo(name = "file_extension")
    val fileExtension: String? = null,

    /** File size in bytes. Null until download completes. */
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long? = null,

    // ── Stream info ─────────────────────────

    /** Audio MIME type from the stream: "audio/mp4", "audio/webm", "audio/opus" */
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    /** Bitrate of the downloaded stream in bps. */
    @ColumnInfo(name = "bitrate")
    val bitrate: Int? = null,

    /**
     * Quality label for display: "320kbps", "256kbps", "160kbps", etc.
     * For YouTube streams, computed from bitrate. For JioSaavn, from API.
     */
    @ColumnInfo(name = "quality_label")
    val qualityLabel: String? = null,

    // ── State ───────────────────────────────

    @ColumnInfo(name = "state")
    val state: DownloadState = DownloadState.QUEUED,

    /** 0..100. Only meaningful when [state] is DOWNLOADING. */
    @ColumnInfo(name = "progress")
    val progress: Int = 0,

    /** Human-readable error message when [state] is FAILED. */
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    // ── Timestamps ──────────────────────────

    @ColumnInfo(name = "date_created")
    val dateCreated: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "date_completed")
    val dateCompleted: Long? = null,
)

enum class DownloadState {
    /** Queued but not yet started. */
    QUEUED,

    /** Actively downloading. Check [DownloadEntity.progress]. */
    DOWNLOADING,

    /** Download finished successfully. [DownloadEntity.filePath] is set. */
    COMPLETED,

    /** Download failed. Check [DownloadEntity.errorMessage]. */
    FAILED,

    /** Paused by user. Can be resumed. */
    PAUSED,
}
