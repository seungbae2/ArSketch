package com.sb.arsketch.presentation.connect

import com.sb.arsketch.BuildConfig
import com.sb.arsketch.domain.model.RoomRole

data class ConnectUiState(
    val serverUrl: String = BuildConfig.LIVEKIT_URL,
    val token: String = BuildConfig.LIVEKIT_HOST_TOKEN,
    val role: RoomRole = RoomRole.HOST,
    val error: String? = null
)
