package com.sb.arsketch.streaming.api

import android.view.SurfaceView
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.StrokeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface HostStreamingSession {
    val connectionState: StateFlow<ConnectionState>
    val participantCount: StateFlow<Int>
    val remoteTouchEvents: Flow<RemoteTouchEvent>

    suspend fun connect(url: String, token: String)
    fun setARSurfaceView(surfaceView: SurfaceView)
    fun publishStrokeEvent(event: StrokeEvent)
    fun disconnect()
}
