package com.sb.arsketch.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sb.arsketch.data.local.entity.SessionEntity
import com.sb.arsketch.data.local.entity.StrokeEntity

@Database(
    entities = [
        SessionEntity::class,
        StrokeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ArSketchDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun strokeDao(): StrokeDao

    companion object {
        const val DATABASE_NAME = "arsketch_database"
    }
}
