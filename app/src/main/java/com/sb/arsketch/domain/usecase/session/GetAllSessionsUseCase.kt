package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<List<DrawingSession>> {
        return sessionRepository.getAllSessions()
    }
}
