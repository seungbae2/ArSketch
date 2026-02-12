package com.sb.arsketch.presentation.host

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D

sealed interface HostAction {
    // AR 상태
    data class UpdateARState(val state: ARState) : HostAction

    // 터치 이벤트
    data class TouchStart(val point: Point3D, val anchorId: String? = null) : HostAction
    data class TouchMove(val point: Point3D) : HostAction
    data object TouchEnd : HostAction

    // Undo/Redo/Clear
    data object Undo : HostAction
    data object Redo : HostAction
    data object ClearAll : HostAction

    // 브러시 설정
    data class SetColor(val color: Int) : HostAction
    data class SetThickness(val thickness: BrushSettings.Thickness) : HostAction

    // 드로잉 모드
    data class SetDrawingMode(val mode: DrawingMode) : HostAction
    data class SetAirDrawingDepth(val depth: Float) : HostAction
    data object ToggleShowPlanes : HostAction

    // 연결 해제
    data object Disconnect : HostAction

    // 에러
    data object ClearError : HostAction
}
