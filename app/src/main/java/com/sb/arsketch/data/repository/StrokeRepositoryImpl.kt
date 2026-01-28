package com.sb.arsketch.data.repository

import com.sb.arsketch.data.local.db.StrokeDao
import com.sb.arsketch.data.mapper.EntityMapper.toDomain
import com.sb.arsketch.data.mapper.EntityMapper.toDomainList
import com.sb.arsketch.data.mapper.EntityMapper.toEntity
import com.sb.arsketch.data.mapper.EntityMapper.toEntityList
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.StrokeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrokeRepositoryImpl @Inject constructor(
    private val strokeDao: StrokeDao
) : StrokeRepository {

    override suspend fun saveStroke(stroke: Stroke, sessionId: String) {
        strokeDao.insert(stroke.toEntity(sessionId))
    }

    override suspend fun saveStrokes(strokes: List<Stroke>, sessionId: String) {
        strokeDao.insertAll(strokes.toEntityList(sessionId))
    }

    override suspend fun getStrokesForSession(sessionId: String): List<Stroke> {
        return strokeDao.getBySessionId(sessionId).toDomainList()
    }

    override fun observeStrokesForSession(sessionId: String): Flow<List<Stroke>> {
        return strokeDao.observeBySessionId(sessionId).map { it.toDomainList() }
    }

    override suspend fun deleteStroke(strokeId: String) {
        strokeDao.deleteById(strokeId)
    }

    override suspend fun deleteAllStrokesForSession(sessionId: String) {
        strokeDao.deleteBySessionId(sessionId)
    }
}
