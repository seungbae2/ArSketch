package com.sb.arsketch.streaming.api

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val roomName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
