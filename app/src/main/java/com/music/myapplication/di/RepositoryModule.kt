package com.music.myapplication.di

import com.music.myapplication.data.repository.LocalLibraryRepositoryImpl
import com.music.myapplication.data.repository.OnlineMusicRepositoryImpl
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
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
    abstract fun bindOnlineMusicRepository(
        impl: OnlineMusicRepositoryImpl
    ): OnlineMusicRepository

    @Binds
    @Singleton
    abstract fun bindLocalLibraryRepository(
        impl: LocalLibraryRepositoryImpl
    ): LocalLibraryRepository
}
