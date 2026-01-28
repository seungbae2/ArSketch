package com.sb.arsketch.domain.model

import kotlinx.serialization.Serializable

/**
 * 스트로크 이벤트
 * 원격 전송을 위한 직렬화 가능한 이벤트 클래스
 */
@Serializable
sealed class StrokeEvent {

    /**
     * 스트로크 시작 이벤트
     */
    @Serializable
    data class Started(
        val strokeId: String,
        val startPoint: Point3D,
        val color: Int,
        val thickness: Float,
        val mode: DrawingMode,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 스트로크에 점 추가 이벤트
     */
    @Serializable
    data class PointAdded(
        val strokeId: String,
        val point: Point3D,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 스트로크 종료 이벤트
     */
    @Serializable
    data class Ended(
        val strokeId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 스트로크 삭제 이벤트 (Undo)
     */
    @Serializable
    data class Deleted(
        val strokeId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 모든 스트로크 삭제 이벤트
     */
    @Serializable
    data class AllCleared(
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()
}
