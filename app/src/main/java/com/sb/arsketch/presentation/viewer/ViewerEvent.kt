package com.sb.arsketch.presentation.viewer

sealed interface ViewerEvent {
    data class Error(val message: String) : ViewerEvent
    data object Disconnected : ViewerEvent
}
