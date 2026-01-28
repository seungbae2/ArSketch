package com.sb.arsketch.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sb.arsketch.data.local.entity.SessionEntity
import com.sb.arsketch.data.local.entity.StrokeEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class StrokeDaoTest {

    private lateinit var database: ArSketchDatabase
    private lateinit var sessionDao: SessionDao
    private lateinit var strokeDao: StrokeDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ArSketchDatabase::class.java
        ).allowMainThreadQueries().build()

        sessionDao = database.sessionDao()
        strokeDao = database.strokeDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndRetrieveStroke() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            name = "Test Session",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.insert(session)

        val stroke = StrokeEntity(
            id = "stroke-1",
            sessionId = "session-1",
            color = 0xFFFF0000.toInt(),
            thickness = 0.006f,
            mode = "SURFACE",
            pointsJson = "[{\"x\":0,\"y\":0,\"z\":0}]",
            createdAt = System.currentTimeMillis()
        )

        // When
        strokeDao.insert(stroke)
        val strokes = strokeDao.getBySessionId("session-1")

        // Then
        assertEquals(1, strokes.size)
        assertEquals("stroke-1", strokes[0].id)
    }

    @Test
    fun deleteBySessionIdRemovesAllStrokes() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            name = "Test Session",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.insert(session)

        repeat(5) { i ->
            strokeDao.insert(
                StrokeEntity(
                    id = "stroke-$i",
                    sessionId = "session-1",
                    color = 0xFFFF0000.toInt(),
                    thickness = 0.006f,
                    mode = "SURFACE",
                    pointsJson = "[]",
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        // When
        strokeDao.deleteBySessionId("session-1")
        val strokes = strokeDao.getBySessionId("session-1")

        // Then
        assertTrue(strokes.isEmpty())
    }

    @Test
    fun cascadeDeleteRemovesStrokesWhenSessionDeleted() = runTest {
        // Given
        val session = SessionEntity(
            id = "session-1",
            name = "Test Session",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.insert(session)

        strokeDao.insert(
            StrokeEntity(
                id = "stroke-1",
                sessionId = "session-1",
                color = 0xFFFF0000.toInt(),
                thickness = 0.006f,
                mode = "SURFACE",
                pointsJson = "[]",
                createdAt = System.currentTimeMillis()
            )
        )

        // When
        sessionDao.deleteById("session-1")
        val strokes = strokeDao.getBySessionId("session-1")

        // Then
        assertTrue(strokes.isEmpty())
    }
}
