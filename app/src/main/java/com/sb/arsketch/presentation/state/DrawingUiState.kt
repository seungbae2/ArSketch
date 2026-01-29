package com.sb.arsketch.presentation.state

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Stroke

data class DrawingUiState(
    val arState: ARState = ARState.Initializing,
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val undoneStrokes: List<Stroke> = emptyList(),
    val brushSettings: BrushSettings = BrushSettings.DEFAULT,
    val drawingMode: DrawingMode = DrawingMode.SURFACE,
    val airDrawingDepth: Float = 1.5f,  // Air Drawing 깊이 (미터)
    val showPlanes: Boolean = true,     // 평면 시각화 표시 여부
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showSaveDialog: Boolean = false,
    val sessionName: String = "",
    val errorMessage: String? = null
)

sealed class ARState {
    object Initializing : ARState()
    object Searching : ARState()
    object Tracking : ARState()
    object Paused : ARState()
    data class Error(val message: String) : ARState()
}
