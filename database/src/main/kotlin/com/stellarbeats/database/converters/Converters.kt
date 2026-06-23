package com.stellarbeats.database.converters

import androidx.room.TypeConverter
import com.stellarbeats.database.entities.ArtistRef
import com.stellarbeats.database.entities.DownloadState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

    @TypeConverter
    fun fromArtistList(artists: List<ArtistRef>): String = json.encodeToString(artists)

    @TypeConverter
    fun toArtistList(value: String): List<ArtistRef> = try { json.decodeFromString(value) } catch (_: Exception) { emptyList() }

    @TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name

    @TypeConverter
    fun toDownloadState(value: String): DownloadState = try { DownloadState.valueOf(value) } catch (_: Exception) { DownloadState.QUEUED }
}
