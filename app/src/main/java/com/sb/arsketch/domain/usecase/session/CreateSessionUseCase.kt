package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.repository.SessionRepository
import javax.inject.Inject

class CreateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(name: String): DrawingSession {
        return sessionRepository.createSession(name)
    }
}
