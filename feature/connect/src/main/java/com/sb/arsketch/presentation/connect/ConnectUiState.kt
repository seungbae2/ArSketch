package com.sb.arsketch.presentation.connect

import com.sb.arsketch.domain.model.RoomRole

data class ConnectUiState(
    val serverUrl: String = "",
    val token: String = "",
    val role: RoomRole = RoomRole.HOST,
    val error: String? = null
)
