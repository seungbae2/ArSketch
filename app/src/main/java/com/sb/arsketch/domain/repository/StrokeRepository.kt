package com.sb.arsketch.domain.repository

import com.sb.arsketch.domain.model.Stroke
import kotlinx.coroutines.flow.Flow

interface StrokeRepository {
    suspend fun saveStroke(stroke: Stroke, sessionId: String)
    suspend fun saveStrokes(strokes: List<Stroke>, sessionId: String)
    suspend fun getStrokesForSession(sessionId: String): List<Stroke>
    fun observeStrokesForSession(sessionId: String): Flow<List<Stroke>>
    suspend fun deleteStroke(strokeId: String)
    suspend fun deleteAllStrokesForSession(sessionId: String)
}
