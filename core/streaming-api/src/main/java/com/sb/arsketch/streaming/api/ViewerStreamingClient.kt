package com.sb.arsketch.streaming.api

import kotlinx.coroutines.flow.StateFlow

interface ViewerStreamingClient {
    val connectionState: StateFlow<ConnectionState>
    val participantCount: StateFlow<Int>
    suspend fun connect(serverUrl: String, token: String)
    fun disconnect()
}
