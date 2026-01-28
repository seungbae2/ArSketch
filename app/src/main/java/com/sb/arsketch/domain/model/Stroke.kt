package com.sb.arsketch.domain.model

import java.util.UUID

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Point3D>,
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
            mode: DrawingMode
        ): Stroke {
            return Stroke(
                points = listOf(startPoint),
                color = brush.color,
                thickness = brush.thickness.value,
                mode = mode
            )
        }
    }
}
