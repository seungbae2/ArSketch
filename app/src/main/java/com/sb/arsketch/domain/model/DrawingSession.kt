package com.sb.arsketch.domain.model

import java.util.UUID

data class DrawingSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val strokes: List<Stroke> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun addStroke(stroke: Stroke): DrawingSession {
        return copy(
            strokes = strokes + stroke,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun removeLastStroke(): Pair<DrawingSession, Stroke?> {
        if (strokes.isEmpty()) return this to null
        val removed = strokes.last()
        return copy(
            strokes = strokes.dropLast(1),
            updatedAt = System.currentTimeMillis()
        ) to removed
    }

    fun clearStrokes(): DrawingSession {
        return copy(
            strokes = emptyList(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
