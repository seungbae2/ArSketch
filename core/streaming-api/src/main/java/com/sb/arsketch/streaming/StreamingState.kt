package com.sb.arsketch.streaming

sealed class StreamingState {
    data object Idle : StreamingState()
    data object Connecting : StreamingState()
    data class Streaming(val roomName: String) : StreamingState()
    data class Error(val message: String) : StreamingState()
}
