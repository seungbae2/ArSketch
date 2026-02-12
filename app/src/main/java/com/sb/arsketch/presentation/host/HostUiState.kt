package com.sb.arsketch.presentation.host

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Stroke

data class HostUiState(
    val arState: ARState = ARState.Initializing,
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val undoneStrokes: List<Stroke> = emptyList(),
    val brushSettings: BrushSettings = BrushSettings.DEFAULT,
    val drawingMode: DrawingMode = DrawingMode.SURFACE,
    val airDrawingDepth: Float = 1.5f,
    val showPlanes: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val errorMessage: String? = null,
    val streamingState: StreamingUiState = StreamingUiState.Idle,
    val participantCount: Int = 0
)

sealed class StreamingUiState {
    data object Idle : StreamingUiState()
    data object Connecting : StreamingUiState()
    data class Streaming(val roomName: String = "") : StreamingUiState()
    data class Error(val message: String) : StreamingUiState()
}

sealed class ARState {
    data object Initializing : ARState()
    data object Searching : ARState()
    data object Tracking : ARState()
    data object Paused : ARState()
    data class Error(val message: String) : ARState()
}
