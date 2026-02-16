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
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    fun provideHostStreamingSession(
        @ApplicationContext context: Context
    ): HostStreamingSession = HostStreamingSessionImpl(context)
}

@Module
@InstallIn(ViewModelComponent::class)
object ViewerStreamingModule {

    @Provides
    @ViewModelScoped
    fun provideStrokeEventReceiver(): StrokeEventReceiver = StrokeEventReceiver()

    @Provides
    @ViewModelScoped
    fun provideStrokeEventSource(receiver: StrokeEventReceiver): StrokeEventSource = receiver

    @Provides
    @ViewModelScoped
    fun provideViewerStreamingClient(
        @ApplicationContext context: Context,
        receiver: StrokeEventReceiver
    ): ViewerStreamingClient = ViewerConnectionManager(context, receiver)
}
