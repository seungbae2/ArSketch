package com.sb.arsketch.presentation.host

sealed interface HostEvent {
    data class Error(val message: String) : HostEvent
    data object Disconnected : HostEvent
}
