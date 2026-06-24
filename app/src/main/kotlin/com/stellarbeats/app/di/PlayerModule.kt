package com.stellarbeats.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideOkHttpDataSourceFactory(okHttpClient: OkHttpClient): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideDefaultDataSourceFactory(
        @ApplicationContext context: Context,
        okHttpDataSourceFactory: OkHttpDataSource.Factory
    ): DefaultDataSource.Factory {
        return DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        dataSourceFactory: DefaultDataSource.Factory
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(dataSourceFactory)
            )
            .build()
    }
}
