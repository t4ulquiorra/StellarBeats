package com.stellarbeats.app.di

import android.content.Context
import androidx.room.Room
import com.stellarbeats.database.StellarDatabase
import com.stellarbeats.database.dao.DownloadDao
import com.stellarbeats.database.dao.PlaylistDao
import com.stellarbeats.database.dao.SearchHistoryDao
import com.stellarbeats.database.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StellarDatabase {
        return Room.databaseBuilder(
            context,
            StellarDatabase::class.java,
            "stellarbeats.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideTrackDao(db: StellarDatabase): TrackDao = db.trackDao()

    @Provides fun providePlaylistDao(db: StellarDatabase): PlaylistDao = db.playlistDao()

    @Provides fun provideDownloadDao(db: StellarDatabase): DownloadDao = db.downloadDao()

    @Provides fun provideSearchHistoryDao(db: StellarDatabase): SearchHistoryDao = db.searchHistoryDao()
}
