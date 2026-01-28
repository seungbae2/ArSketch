package com.sb.arsketch.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sb.arsketch.data.local.entity.StrokeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrokeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(strokes: List<StrokeEntity>)

    @Query("SELECT * FROM strokes WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getBySessionId(sessionId: String): List<StrokeEntity>

    @Query("SELECT * FROM strokes WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySessionId(sessionId: String): Flow<List<StrokeEntity>>

    @Query("DELETE FROM strokes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM strokes WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("SELECT COUNT(*) FROM strokes WHERE sessionId = :sessionId")
    suspend fun getCountBySessionId(sessionId: String): Int
}
