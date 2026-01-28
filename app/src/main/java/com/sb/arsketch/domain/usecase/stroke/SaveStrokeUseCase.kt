package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

class SaveStrokeUseCase @Inject constructor(
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(stroke: Stroke, sessionId: String) {
        if (stroke.isValid()) {
            strokeRepository.saveStroke(stroke, sessionId)
        }
    }
}
