package com.sb.arsketch.streaming.api

import android.opengl.GLSurfaceView
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.StrokeEvent
import kotlinx.coroutines.flow.StateFlow

interface HostStreamingController {
    val streamingState: StateFlow<StreamingState>
    val participantCount: StateFlow<Int>
    var onRemoteTouchReceived: ((RemoteTouchEvent) -> Unit)?

    fun connect(url: String, token: String, onSuccess: () -> Unit, onError: (Exception) -> Unit)
    fun setARSurfaceView(surfaceView: GLSurfaceView)
    fun publishStrokeEvent(event: StrokeEvent)
    fun disconnect()
}
