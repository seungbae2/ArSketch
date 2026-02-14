package com.sb.arsketch.domain.model

import java.util.UUID

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val anchorId: String? = null,  // Anchor 기반 고정용
    val points: List<Point3D>,     // Anchor 기준 로컬 좌표
    val color: Int,
    val thickness: Float,
    val mode: DrawingMode,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun addPoint(point: Point3D): Stroke {
        return copy(points = points + point)
    }

    fun isValid(): Boolean = points.size >= 2

    companion object {
        fun create(
            startPoint: Point3D,
            brush: BrushSettings,
            mode: DrawingMode,
            anchorId: String? = null
        ): Stroke {
            return Stroke(
                anchorId = anchorId,
                points = listOf(startPoint),
                color = brush.color,
                thickness = brush.thickness.value,
                mode = mode
            )
        }
    }
}
