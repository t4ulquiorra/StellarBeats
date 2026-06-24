package com.stellarbeats.app.repository

import com.stellarbeats.database.dao.DownloadDao
import com.stellarbeats.database.dao.PlaylistDao
import com.stellarbeats.database.dao.SearchHistoryDao
import com.stellarbeats.database.dao.TrackDao
import com.stellarbeats.database.entities.ArtistRef
import com.stellarbeats.database.entities.LocalTrack
import com.stellarbeats.database.entities.Playlist
import com.stellarbeats.innertube.Innertube
import com.stellarbeats.innertube.SearchFilter
import com.stellarbeats.innertube.SearchItem
import com.stellarbeats.jiosaavn.JioSaavnClient
import com.stellarbeats.jiosaavn.ParsedSong
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MusicRepository(
    val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val downloadDao: DownloadDao,
    private val searchHistoryDao: SearchHistoryDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun search(query: String, filter: SearchFilter? = null): List<LocalTrack> {
        searchHistoryDao.recordSearch(query, filter?.name?.lowercase())
        val ytResults = Innertube.search(query, filter).getOrNull()
            ?.items?.filterIsInstance<SearchItem.Song>()
            ?.map { it.toLocalTrack() }
        if (!ytResults.isNullOrEmpty()) return ytResults
        return JioSaavnClient.search(query).getOrNull()
            ?.songs?.map { it.toLocalTrack() }.orEmpty()
    }

    suspend fun homeSections(): HomeData {
        val ytHome = Innertube.home().getOrNull()
        val sections = ytHome?.sections?.map { section ->
            HomeSection(
                title = section.title,
                tracks = section.items.filterIsInstance<SearchItem.Song>().map { it.toLocalTrack() },
            )
        }
        if (!sections.isNullOrEmpty()) return HomeData(sections)
        val jsHome = JioSaavnClient.home().getOrNull()
        return HomeData(
            listOf(
                HomeSection("Trending", jsHome?.trending?.map { it.toLocalTrack() }.orEmpty()),
                HomeSection("New Releases", jsHome?.newAlbums?.flatMap { it.songs.map { s -> s.toLocalTrack() } }.orEmpty()),
            )
        )
    }

    suspend fun resolveStreamUrl(track: LocalTrack): String {
        downloadDao.getFilePath(track.trackId)?.let { return "file://$it" }
        return when (track.source) {
            "youtube" -> {
                val response = Innertube.player(track.sourceId).getOrNull()
                    ?: throw Exception("Failed to get player response for ${track.sourceId}")
                response.streams.firstOrNull()?.url
                    ?: throw Exception("No audio streams for ${track.sourceId}")
            }
            "jiosaavn" -> {
                track.jsStreamUrl ?: run {
                    val song = JioSaavnClient.song(track.sourceId).getOrNull()
                        ?: throw Exception("Failed to re-fetch JioSaavn song ${track.sourceId}")
                    song.streamUrl ?: throw Exception("No stream URL from JioSaavn")
                }
            }
            else -> throw Exception("Unknown source: ${track.source}")
        }
    }

    fun observeLiked(): Flow<List<LocalTrack>> = trackDao.observeLiked()
    fun observeRecentlyPlayed(): Flow<List<LocalTrack>> = trackDao.observeRecentlyPlayed()
    fun observeMostPlayed(): Flow<List<LocalTrack>> = trackDao.observeMostPlayed()
    fun observeAllTracks(): Flow<List<LocalTrack>> = trackDao.observeAll()
    fun observeDownloadedTracks(): Flow<List<LocalTrack>> = downloadDao.observeCompletedTracks()

    suspend fun toggleLike(trackId: String) { trackDao.toggleLike(trackId) }

    suspend fun addToPlaylist(playlistId: String, track: LocalTrack) {
        trackDao.insert(track)
        playlistDao.addTrack(playlistId, track.trackId)
    }

    suspend fun incrementPlayCount(trackId: String) { trackDao.incrementPlayCount(trackId) }

    suspend fun updateDominantColor(trackId: String, color: Int) {
        trackDao.updateDominantColor(trackId, color)
    }

    suspend fun createPlaylist(name: String, description: String? = null): Playlist {
        val playlist = Playlist(
            playlistId = "user:${System.currentTimeMillis()}",
            name = name,
            description = description,
        )
        playlistDao.insertPlaylist(playlist)
        return playlist
    }

    fun observePlaylists(): Flow<List<Playlist>> = playlistDao.observeAllPlaylists()
    fun observePlaylistTracks(playlistId: String): Flow<List<LocalTrack>> =
        playlistDao.observePlaylistTracks(playlistId)

    suspend fun removeFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeTrack(playlistId, trackId)
    }

    fun observeDownloads(): Flow<List<LocalTrack>> = downloadDao.observeCompletedTracks()
    suspend fun isDownloaded(trackId: String): Boolean = downloadDao.isDownloaded(trackId)
    fun observeSearchHistory() = searchHistoryDao.observeAll()
    suspend fun clearSearchHistory() = searchHistoryDao.clearAll()

    private fun SearchItem.Song.toLocalTrack(): LocalTrack {
        val trackId = "yt:$id"
        return LocalTrack(
            trackId = trackId,
            title = title,
            artistsJson = json.encodeToString(artists.map { ArtistRef(it.title, it.id) }),
            album = album?.title,
            albumId = album?.id,
            durationMs = duration?.let { it * 1000L },
            year = null,
            language = null,
            thumbnailUrl = thumbnail?.url,
            dominantColor = null,
            source = "youtube",
            sourceId = id,
            jsStreamUrl = null,
            jsStreamQuality = null,
            explicit = false,
            hasLyrics = false,
            lyricsId = null,
            playCount = 0L,
        )
    }

    internal fun ParsedSong.toLocalTrack(): LocalTrack {
        val trackId = "js:$id"
        return LocalTrack(
            trackId = trackId,
            title = title,
            artistsJson = json.encodeToString(artists),
            album = album,
            albumId = albumId,
            durationMs = duration?.let { it * 1000L },
            year = year,
            language = language,
            thumbnailUrl = thumbnailUrl,
            dominantColor = null,
            source = "jiosaavn",
            sourceId = id,
            jsStreamUrl = streamUrl,
            jsStreamQuality = streamQuality,
            explicit = explicit,
            hasLyrics = hasLyrics,
            lyricsId = lyricsId,
            playCount = playCount ?: 0L,
        )
    }
}

data class HomeData(val sections: List<HomeSection>)
data class HomeSection(val title: String, val tracks: List<LocalTrack>)
