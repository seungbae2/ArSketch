package com.sb.arsketch.presentation.connect

import com.sb.arsketch.domain.model.RoomRole

sealed interface ConnectAction {
    data class UpdateUrl(val url: String) : ConnectAction
    data class UpdateToken(val token: String) : ConnectAction
    data class SetRole(val role: RoomRole) : ConnectAction
    data object Connect : ConnectAction
    data object ClearError : ConnectAction
}
