package com.sb.arsketch.domain.model

import kotlinx.serialization.Serializable

/**
 * 웹 뷰어에서 수신하는 리모트 터치 이벤트
 * DataChannel (topic: "remote_touch")을 통해 전송됨
 */
@Serializable
sealed class RemoteTouchEvent {

    @Serializable
    data class TouchDown(
        val normalizedX: Float,  // 0.0-1.0 (비디오 기준)
        val normalizedY: Float,
        val color: Int,          // ARGB
        val thickness: Float,    // 0.003, 0.006, 0.012
        val mode: DrawingMode,   // SURFACE or AIR
        val senderId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteTouchEvent()

    @Serializable
    data class TouchMove(
        val normalizedX: Float,
        val normalizedY: Float,
        val senderId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteTouchEvent()

    @Serializable
    data class TouchUp(
        val senderId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : RemoteTouchEvent()
}
