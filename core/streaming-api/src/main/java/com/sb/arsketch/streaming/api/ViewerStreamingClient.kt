package com.sb.arsketch.streaming.api

import com.sb.arsketch.streaming.ViewerConnectionState
import kotlinx.coroutines.flow.StateFlow

interface ViewerStreamingClient {
    val connectionState: StateFlow<ViewerConnectionState>
    val participantCount: StateFlow<Int>
    fun connect(serverUrl: String, token: String)
    fun disconnect()
    fun destroy()
}
