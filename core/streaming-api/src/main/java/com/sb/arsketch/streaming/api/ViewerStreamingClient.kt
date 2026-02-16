package com.sb.arsketch.streaming.api

import kotlinx.coroutines.flow.StateFlow

interface ViewerStreamingClient {
    val connectionState: StateFlow<ConnectionState>
    val participantCount: StateFlow<Int>
    fun connect(serverUrl: String, token: String)
    fun disconnect()
    fun destroy()
}
