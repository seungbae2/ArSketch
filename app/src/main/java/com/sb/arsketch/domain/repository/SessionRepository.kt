package com.sb.arsketch.domain.repository

import com.sb.arsketch.domain.model.DrawingSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun createSession(name: String): DrawingSession
    suspend fun getSession(id: String): DrawingSession?
    fun getAllSessions(): Flow<List<DrawingSession>>
    suspend fun updateSession(session: DrawingSession)
    suspend fun deleteSession(id: String)
    suspend fun getLatestSession(): DrawingSession?
}
