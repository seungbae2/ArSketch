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
    val errorMessage: String? = null,

    // AR 스트리밍 상태
    val streamingState: StreamingUiState = StreamingUiState.Idle
)

/**
 * 스트리밍 UI 상태
 */
sealed class StreamingUiState {
    /** 대기 상태 */
    data object Idle : StreamingUiState()

    /** 연결 중 */
    data object Connecting : StreamingUiState()

    /** 스트리밍 중 */
    data class Streaming(
        val roomName: String = "",
        val resolution: String = "",
        val fps: Float = 0f
    ) : StreamingUiState()

    /** 오류 발생 */
    data class Error(val message: String) : StreamingUiState()
}

sealed class ARState {
    object Initializing : ARState()
    object Searching : ARState()
    object Tracking : ARState()
    object Paused : ARState()
    data class Error(val message: String) : ARState()
}
