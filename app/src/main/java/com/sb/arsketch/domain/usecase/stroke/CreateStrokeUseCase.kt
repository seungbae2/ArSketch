package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

class CreateStrokeUseCase @Inject constructor() {
    operator fun invoke(
        startPoint: Point3D,
        brush: BrushSettings,
        mode: DrawingMode
    ): Stroke {
        return Stroke.create(startPoint, brush, mode)
    }
}
