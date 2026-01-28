package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(sessionId: String) {
        strokeRepository.deleteAllStrokesForSession(sessionId)
        sessionRepository.deleteSession(sessionId)
    }
}
