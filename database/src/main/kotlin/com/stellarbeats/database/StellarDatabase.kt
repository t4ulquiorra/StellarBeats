package com.stellarbeats.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.stellarbeats.database.converters.Converters
import com.stellarbeats.database.dao.DownloadDao
import com.stellarbeats.database.dao.PlaylistDao
import com.stellarbeats.database.dao.SearchHistoryDao
import com.stellarbeats.database.dao.TrackDao
import com.stellarbeats.database.entities.DownloadEntity
import com.stellarbeats.database.entities.LocalTrack
import com.stellarbeats.database.entities.Playlist
import com.stellarbeats.database.entities.PlaylistEntry
import com.stellarbeats.database.entities.SearchHistoryEntry

/**
 * Room database — the local persistence layer for StellarBeats.
 *
 * ## Schema overview
 * ```
 * local_tracks          ← Central track table
 *     │
 *     ├── playlist_entries (N:M junction with playlists)
 *     │       └── playlists
 *     │
 *     └── downloads (1:1 with tracks)
 *
 * search_history        ← Independent, no FK
 * ```
 *
 * ## Migration strategy
 * Schemas are exported to `database/schemas/` via KSP arg.
 * For a FOSS app distributed as APK (not Play Store), destructive
 * migrations (`fallbackToDestructiveMigration`) are acceptable.
 * For Play Store distribution, implement proper migrations.
 *
 * ## Thread safety
 * Room handles thread safety internally. All suspend DAO methods
 * run on the calling coroutine's dispatcher. Flow-based methods
 * are already asynchronous.
 */
@Database(
    entities = [
        LocalTrack::class,
        Playlist::class,
        PlaylistEntry::class,
        DownloadEntity::class,
        SearchHistoryEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StellarDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
