package com.sb.arsketch.di

import com.sb.arsketch.data.repository.SessionRepositoryImpl
import com.sb.arsketch.data.repository.StrokeRepositoryImpl
import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
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
    abstract fun bindStrokeRepository(
        impl: StrokeRepositoryImpl
    ): StrokeRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository
}
