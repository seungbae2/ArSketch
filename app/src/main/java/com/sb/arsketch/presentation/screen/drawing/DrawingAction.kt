package com.sb.arsketch.presentation.screen.drawing

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.presentation.state.ARState

/**
 * 드로잉 화면의 모든 사용자 액션을 정의하는 sealed interface
 * ViewModel의 단일 진입점(onAction)을 통해 처리됨
 */
sealed interface DrawingAction {
    // AR 상태 업데이트
    data class UpdateARState(val state: ARState) : DrawingAction

    // 터치 이벤트
    data class TouchStart(val point: Point3D, val anchorId: String? = null) : DrawingAction
    data class TouchMove(val point: Point3D) : DrawingAction
    data object TouchEnd : DrawingAction

    // Undo/Redo/Clear
    data object Undo : DrawingAction
    data object Redo : DrawingAction
    data object ClearAll : DrawingAction

    // 브러시 설정
    data class SetColor(val color: Int) : DrawingAction
    data class SetThickness(val thickness: BrushSettings.Thickness) : DrawingAction

    // 드로잉 모드
    data class SetDrawingMode(val mode: DrawingMode) : DrawingAction
    data class SetAirDrawingDepth(val depth: Float) : DrawingAction
    data object ToggleShowPlanes : DrawingAction

    // 세션 관리
    data object ShowSaveDialog : DrawingAction
    data object DismissSaveDialog : DrawingAction
    data class UpdateSessionName(val name: String) : DrawingAction
    data object SaveSession : DrawingAction
    data object StartNewSession : DrawingAction
    data class LoadSession(val sessionId: String) : DrawingAction

    // 에러 처리
    data object ClearError : DrawingAction

    // AR 스트리밍
    data class StartStreaming(
        val url: String,
        val token: String,
        val width: Int = 1280,
        val height: Int = 720
    ) : DrawingAction
    data object StopStreaming : DrawingAction
}
