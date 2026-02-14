package com.sb.arsketch.di

import android.content.Context
import com.sb.arsketch.streaming.HostStreamingSessionImpl
import com.sb.arsketch.streaming.StrokeEventReceiver
import com.sb.arsketch.streaming.ViewerConnectionManager
import com.sb.arsketch.streaming.api.HostStreamingSession
import com.sb.arsketch.streaming.api.StrokeEventSource
import com.sb.arsketch.streaming.api.ViewerStreamingClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    @Singleton
    fun provideStrokeEventReceiver(): StrokeEventReceiver = StrokeEventReceiver()

    @Provides
    @Singleton
    fun provideStrokeEventSource(receiver: StrokeEventReceiver): StrokeEventSource = receiver

    @Provides
    @Singleton
    fun provideViewerStreamingClient(
        @ApplicationContext context: Context,
        receiver: StrokeEventReceiver
    ): ViewerStreamingClient = ViewerConnectionManager(context, receiver)

    @Provides
    fun provideHostStreamingSession(
        @ApplicationContext context: Context
    ): HostStreamingSession = HostStreamingSessionImpl(context)
}
