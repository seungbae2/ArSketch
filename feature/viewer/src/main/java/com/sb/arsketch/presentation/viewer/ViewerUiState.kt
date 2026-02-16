package com.sb.arsketch.presentation.viewer

import com.sb.arsketch.domain.model.ViewerStroke
import com.sb.arsketch.streaming.api.ConnectionState

data class ViewerUiState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val strokes: List<ViewerStroke> = emptyList(),
    val participantCount: Int = 0
)
