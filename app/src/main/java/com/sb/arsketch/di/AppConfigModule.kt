package com.sb.arsketch.di

import com.sb.arsketch.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Named("defaultServerUrl")
    fun provideDefaultServerUrl(): String = BuildConfig.LIVEKIT_URL

    @Provides
    @Named("defaultToken")
    fun provideDefaultToken(): String = BuildConfig.LIVEKIT_HOST_TOKEN
}
