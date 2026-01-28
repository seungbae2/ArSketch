package com.sb.arsketch.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class StrokeEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val color: Int,
    val thickness: Float,
    val mode: String,
    val pointsJson: String,
    val createdAt: Long
)
