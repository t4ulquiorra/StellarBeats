package com.stellarbeats.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stellarbeats.database.entities.SearchHistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /**
     * Insert or update a search query.
     * If the query already exists, updates the timestamp (moves to top).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntry)

    /**
     * Convenience: record a search with just the query string.
     */
    @Query("""
        INSERT OR REPLACE INTO search_history (query, filter, date_searched)
        VALUES (:query, :filter, :timestamp)
    """)
    suspend fun recordSearch(
        query: String,
        filter: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    )

    /** Get all recent searches, newest first. */
    @Query("SELECT * FROM search_history ORDER BY date_searched DESC LIMIT :limit")
    fun observeAll(limit: Int = 20): Flow<List<SearchHistoryEntry>>

    @Query("SELECT * FROM search_history ORDER BY date_searched DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 20): List<SearchHistoryEntry>

    /** Delete a single entry. */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    /** Clear all search history. */
    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    /** Remove entries older than the given timestamp. */
    @Query("DELETE FROM search_history WHERE date_searched < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)

    /** Check if a query exists in history. */
    @Query("SELECT EXISTS(SELECT 1 FROM search_history WHERE query = :query)")
    suspend fun contains(query: String): Boolean
}
