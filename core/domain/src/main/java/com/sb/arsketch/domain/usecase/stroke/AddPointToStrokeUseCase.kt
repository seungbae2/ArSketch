package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

class AddPointToStrokeUseCase @Inject constructor() {
    companion object {
        const val MIN_DISTANCE = 0.005f
        const val MAX_POINTS = 10000
    }

    operator fun invoke(stroke: Stroke, newPoint: Point3D): Stroke {
        if (stroke.points.size >= MAX_POINTS) {
            return stroke
        }

        val lastPoint = stroke.points.lastOrNull()
        if (lastPoint != null && lastPoint.distanceTo(newPoint) < MIN_DISTANCE) {
            return stroke
        }

        return stroke.addPoint(newPoint)
    }
}
