package com.sb.arsketch.streaming.api

import android.opengl.GLSurfaceView
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.StrokeEvent
import kotlinx.coroutines.flow.StateFlow

interface HostStreamingSession {
    val streamingState: StateFlow<ConnectionState>
    val participantCount: StateFlow<Int>

    fun connect(url: String, token: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun setARSurfaceView(surfaceView: GLSurfaceView)
    fun publishStrokeEvent(event: StrokeEvent)
    fun setRemoteTouchHandler(handler: ((RemoteTouchEvent) -> Unit)?)
    fun disconnect()
}
