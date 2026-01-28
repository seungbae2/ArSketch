# Epic 3: Data Layer

## 개요
- **목표**: Room 데이터베이스 설정, Repository 구현체 생성
- **예상 작업량**: 중간
- **의존성**: Epic 2 완료

---

## Room 데이터베이스 설계

### ERD (Entity Relationship Diagram)

```
┌─────────────────────────────────────┐
│              sessions               │
├─────────────────────────────────────┤
│ id: String (PK)                     │
│ name: String                        │
│ createdAt: Long                     │
│ updatedAt: Long                     │
└─────────────────────────────────────┘
                │
                │ 1:N
                ▼
┌─────────────────────────────────────┐
│              strokes                │
├─────────────────────────────────────┤
│ id: String (PK)                     │
│ sessionId: String (FK → sessions)   │
│ color: Int                          │
│ thickness: Float                    │
│ mode: String                        │
│ pointsJson: String                  │
│ createdAt: Long                     │
└─────────────────────────────────────┘
```

---

## 작업 목록

### Task 3.1: Room Entity 생성

#### SessionEntity.kt
**파일**: `data/local/entity/SessionEntity.kt`

```kotlin
package com.sb.arsketch.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 드로잉 세션 Room Entity
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
```

#### StrokeEntity.kt
**파일**: `data/local/entity/StrokeEntity.kt`

```kotlin
package com.sb.arsketch.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 스트로크 Room Entity
 * points는 JSON 문자열로 저장
 */
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
    val mode: String,          // DrawingMode.name
    val pointsJson: String,    // JSON serialized List<Point3D>
    val createdAt: Long
)
```

---

### Task 3.2: DAO 생성

#### SessionDao.kt
**파일**: `data/local/db/SessionDao.kt`

```kotlin
package com.sb.arsketch.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sb.arsketch.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
```

#### StrokeDao.kt
**파일**: `data/local/db/StrokeDao.kt`

```kotlin
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
```

---

### Task 3.3: Room Database 생성

#### ArSketchDatabase.kt
**파일**: `data/local/db/ArSketchDatabase.kt`

```kotlin
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
```

---

### Task 3.4: Entity ↔ Domain 매퍼

#### EntityMapper.kt
**파일**: `data/mapper/EntityMapper.kt`

```kotlin
package com.sb.arsketch.data.mapper

import com.sb.arsketch.data.local.entity.SessionEntity
import com.sb.arsketch.data.local.entity.StrokeEntity
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Entity ↔ Domain 모델 변환 매퍼
 */
object EntityMapper {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ==================== Session ====================

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

    // ==================== Stroke ====================

    fun StrokeEntity.toDomain(): Stroke {
        val points = try {
            json.decodeFromString<List<Point3D>>(pointsJson)
        } catch (e: Exception) {
            emptyList()
        }

        return Stroke(
            id = id,
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
            color = color,
            thickness = thickness,
            mode = mode.name,
            pointsJson = json.encodeToString(points),
            createdAt = createdAt
        )
    }

    // ==================== List Extensions ====================

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
```

---

### Task 3.5: Repository 구현

#### StrokeRepositoryImpl.kt
**파일**: `data/repository/StrokeRepositoryImpl.kt`

```kotlin
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
```

#### SessionRepositoryImpl.kt
**파일**: `data/repository/SessionRepositoryImpl.kt`

```kotlin
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
```

---

### Task 3.6: Hilt Module 생성

#### DataModule.kt
**파일**: `di/DataModule.kt`

```kotlin
package com.sb.arsketch.di

import android.content.Context
import androidx.room.Room
import com.sb.arsketch.data.local.db.ArSketchDatabase
import com.sb.arsketch.data.local.db.SessionDao
import com.sb.arsketch.data.local.db.StrokeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ArSketchDatabase {
        return Room.databaseBuilder(
            context,
            ArSketchDatabase::class.java,
            ArSketchDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSessionDao(database: ArSketchDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideStrokeDao(database: ArSketchDatabase): StrokeDao {
        return database.strokeDao()
    }
}
```

#### RepositoryModule.kt
**파일**: `di/RepositoryModule.kt`

```kotlin
package com.sb.arsketch.di

import com.sb.arsketch.data.repository.SessionRepositoryImpl
import com.sb.arsketch.data.repository.StrokeRepositoryImpl
import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStrokeRepository(
        impl: StrokeRepositoryImpl
    ): StrokeRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: SessionRepositoryImpl
    ): SessionRepository
}
```

---

## 완료 조건

- [ ] Room Entity 클래스 생성 완료
- [ ] DAO 인터페이스 정의 완료
- [ ] Database 클래스 생성 완료
- [ ] Entity ↔ Domain 매퍼 구현 완료
- [ ] Repository 구현체 완료
- [ ] Hilt Module 설정 완료
- [ ] `./gradlew assembleDebug` 빌드 성공
- [ ] KSP Room 컴파일러 정상 동작

---

## 테스트 계획

### 단위 테스트 (Repository)
```kotlin
@Test
fun `saveStroke and getStrokesForSession returns saved stroke`() = runTest {
    // Given
    val stroke = Stroke.create(Point3D.ZERO, BrushSettings.DEFAULT, DrawingMode.SURFACE)
    val sessionId = "test-session"

    // When
    repository.saveStroke(stroke, sessionId)
    val result = repository.getStrokesForSession(sessionId)

    // Then
    assertEquals(1, result.size)
    assertEquals(stroke.id, result[0].id)
}
```

### 통합 테스트 (Room)
```kotlin
@Test
fun `cascade delete removes strokes when session is deleted`() = runTest {
    // Given
    val session = sessionRepository.createSession("Test")
    strokeRepository.saveStroke(stroke, session.id)

    // When
    sessionRepository.deleteSession(session.id)

    // Then
    val strokes = strokeRepository.getStrokesForSession(session.id)
    assertTrue(strokes.isEmpty())
}
```

---

## 다음 단계

→ [Epic 4: AR Foundation](epic-04-ar-foundation.md)
