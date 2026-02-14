package com.sb.arsketch.presentation.viewer

sealed interface ViewerAction {
    data object Disconnect : ViewerAction
}
