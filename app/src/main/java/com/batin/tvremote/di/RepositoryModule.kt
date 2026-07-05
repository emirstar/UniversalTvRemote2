package com.batin.tvremote.di

import com.batin.tvremote.data.repository.RemoteControlRepository
import com.batin.tvremote.data.repository.RemoteControlRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRemoteControlRepository(impl: RemoteControlRepositoryImpl): RemoteControlRepository
}
