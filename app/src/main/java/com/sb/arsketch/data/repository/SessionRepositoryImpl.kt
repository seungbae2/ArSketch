package com.sb.arsketch.data.repository

import com.sb.arsketch.data.local.db.SessionDao
import com.sb.arsketch.data.mapper.EntityMapper.toDomain
import com.sb.arsketch.data.mapper.EntityMapper.toEntity
import com.sb.arsketch.data.mapper.EntityMapper.toSessionDomainList
import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {

    override suspend fun createSession(name: String): DrawingSession {
        val session = DrawingSession(
            id = UUID.randomUUID().toString(),
            name = name
        )
        sessionDao.insert(session.toEntity())
        return session
    }

    override suspend fun getSession(id: String): DrawingSession? {
        return sessionDao.getById(id)?.toDomain()
    }

    override fun getAllSessions(): Flow<List<DrawingSession>> {
        return sessionDao.getAllFlow().map { it.toSessionDomainList() }
    }

    override suspend fun updateSession(session: DrawingSession) {
        sessionDao.update(session.toEntity())
    }

    override suspend fun deleteSession(id: String) {
        sessionDao.deleteById(id)
    }

    override suspend fun getLatestSession(): DrawingSession? {
        return sessionDao.getLatest()?.toDomain()
    }
}
