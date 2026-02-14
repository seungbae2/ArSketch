package com.sb.arsketch.presentation.viewer

import com.sb.arsketch.domain.model.ViewerStroke
import com.sb.arsketch.streaming.ViewerConnectionState

data class ViewerUiState(
    val connectionState: ViewerConnectionState = ViewerConnectionState.Disconnected,
    val strokes: List<ViewerStroke> = emptyList(),
    val participantCount: Int = 0
)
