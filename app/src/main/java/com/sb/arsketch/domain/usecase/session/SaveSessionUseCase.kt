package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

class SaveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        name: String,
        strokes: List<Stroke>
    ) {
        val existingSession = sessionRepository.getSession(sessionId)
        val session = existingSession?.copy(
            name = name,
            strokes = strokes,
            updatedAt = System.currentTimeMillis()
        ) ?: DrawingSession(
            id = sessionId,
            name = name,
            strokes = strokes
        )

        sessionRepository.updateSession(session)
        strokeRepository.deleteAllStrokesForSession(sessionId)
        strokeRepository.saveStrokes(strokes, sessionId)
    }
}
