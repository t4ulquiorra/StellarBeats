package com.stellarbeats.app.di

import com.stellarbeats.app.repository.MusicRepository
import com.stellarbeats.database.dao.DownloadDao
import com.stellarbeats.database.dao.PlaylistDao
import com.stellarbeats.database.dao.SearchHistoryDao
import com.stellarbeats.database.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMusicRepository(
        trackDao: TrackDao,
        playlistDao: PlaylistDao,
        downloadDao: DownloadDao,
        searchHistoryDao: SearchHistoryDao,
    ): MusicRepository {
        return MusicRepository(
            trackDao = trackDao,
            playlistDao = playlistDao,
            downloadDao = downloadDao,
            searchHistoryDao = searchHistoryDao,
        )
    }
}
