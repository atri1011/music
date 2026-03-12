package com.music.myapplication.di

import com.music.myapplication.data.repository.LocalLibraryRepositoryImpl
import com.music.myapplication.data.repository.NeteaseAccountRepositoryImpl
import com.music.myapplication.data.repository.OnlineMusicRepositoryImpl
import com.music.myapplication.data.repository.RecommendationRepositoryImpl
import com.music.myapplication.domain.repository.LocalLibraryRepository
import com.music.myapplication.domain.repository.NeteaseAccountRepository
import com.music.myapplication.domain.repository.OnlineMusicRepository
import com.music.myapplication.domain.repository.RecommendationRepository
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

    @Binds
    @Singleton
    abstract fun bindNeteaseAccountRepository(
        impl: NeteaseAccountRepositoryImpl
    ): NeteaseAccountRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(
        impl: RecommendationRepositoryImpl
    ): RecommendationRepository
}
