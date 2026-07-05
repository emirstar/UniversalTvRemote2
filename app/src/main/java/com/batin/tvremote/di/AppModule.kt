package com.batin.tvremote.di

import android.content.Context
import androidx.room.Room
import com.batin.tvremote.data.local.db.TvDeviceDao
import com.batin.tvremote.data.local.db.TvRemoteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** A process-lifetime coroutine scope for work that must outlive any single screen (e.g. observing transport events). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TvRemoteDatabase =
        Room.databaseBuilder(context, TvRemoteDatabase::class.java, TvRemoteDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDeviceDao(database: TvRemoteDatabase): TvDeviceDao = database.tvDeviceDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
