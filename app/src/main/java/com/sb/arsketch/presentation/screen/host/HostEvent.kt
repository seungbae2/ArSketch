package com.sb.arsketch.presentation.screen.host

sealed interface HostEvent {
    data class Error(val message: String) : HostEvent
    data object Disconnected : HostEvent
}
