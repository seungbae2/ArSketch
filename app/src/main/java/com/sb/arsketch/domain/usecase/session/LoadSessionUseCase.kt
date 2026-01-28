package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

class LoadSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(sessionId: String): Pair<DrawingSession, List<Stroke>>? {
        val session = sessionRepository.getSession(sessionId) ?: return null
        val strokes = strokeRepository.getStrokesForSession(sessionId)
        return session to strokes
    }
}
