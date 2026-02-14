package com.sb.arsketch.streaming.api

sealed class ViewerConnectionState {
    data object Disconnected : ViewerConnectionState()
    data object Connecting : ViewerConnectionState()
    data class Connected(val roomName: String) : ViewerConnectionState()
    data class Error(val message: String) : ViewerConnectionState()
}
