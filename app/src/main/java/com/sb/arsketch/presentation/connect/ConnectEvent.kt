package com.sb.arsketch.presentation.connect

sealed interface ConnectEvent {
    data class NavigateToHost(val serverUrl: String, val token: String) : ConnectEvent
    data class NavigateToViewer(val serverUrl: String, val token: String) : ConnectEvent
}
