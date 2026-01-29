package com.sb.arsketch.data.mapper

import com.sb.arsketch.data.local.entity.SessionEntity
import com.sb.arsketch.data.local.entity.StrokeEntity
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object EntityMapper {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Session mappings
    fun SessionEntity.toDomain(strokes: List<Stroke> = emptyList()): DrawingSession {
        return DrawingSession(
            id = id,
            name = name,
            strokes = strokes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun DrawingSession.toEntity(): SessionEntity {
        return SessionEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // Stroke mappings
    fun StrokeEntity.toDomain(): Stroke {
        val points = try {
            json.decodeFromString<List<Point3D>>(pointsJson)
        } catch (e: Exception) {
            emptyList()
        }

        return Stroke(
            id = id,
            anchorId = anchorId,
            points = points,
            color = color,
            thickness = thickness,
            mode = DrawingMode.valueOf(mode),
            createdAt = createdAt
        )
    }

    fun Stroke.toEntity(sessionId: String): StrokeEntity {
        return StrokeEntity(
            id = id,
            sessionId = sessionId,
            anchorId = anchorId,
            color = color,
            thickness = thickness,
            mode = mode.name,
            pointsJson = json.encodeToString(points),
            createdAt = createdAt
        )
    }

    // List extensions
    fun List<StrokeEntity>.toDomainList(): List<Stroke> {
        return map { it.toDomain() }
    }

    fun List<Stroke>.toEntityList(sessionId: String): List<StrokeEntity> {
        return map { it.toEntity(sessionId) }
    }

    fun List<SessionEntity>.toSessionDomainList(): List<DrawingSession> {
        return map { it.toDomain() }
    }
}
