# Epic 2: Domain Layer

## 개요
- **목표**: 순수 Kotlin으로 도메인 모델, Repository 인터페이스, Use Case 정의
- **예상 작업량**: 중간
- **의존성**: Epic 1 완료

---

## 아키텍처 원칙

### Clean Architecture에서 Domain Layer의 역할
- **Android 의존성 없음**: 순수 Kotlin 코드만 사용
- **비즈니스 로직 캡슐화**: Use Case에 모든 비즈니스 규칙 포함
- **인터페이스 정의**: Repository 인터페이스로 데이터 계층과 분리
- **테스트 용이성**: 외부 의존성 없이 단위 테스트 가능

---

## 작업 목록

### Task 2.1: 도메인 모델 생성

#### Point3D.kt
**파일**: `domain/model/Point3D.kt`

```kotlin
package com.sb.arsketch.domain.model

import kotlinx.serialization.Serializable

/**
 * 3D 공간의 한 점을 나타내는 데이터 클래스
 * ARCore 월드 좌표계 기준 (미터 단위)
 */
@Serializable
data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    /**
     * 두 점 사이의 거리 계산
     */
    fun distanceTo(other: Point3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        val ZERO = Point3D(0f, 0f, 0f)
    }
}
```

#### DrawingMode.kt
**파일**: `domain/model/DrawingMode.kt`

```kotlin
package com.sb.arsketch.domain.model

/**
 * 드로잉 모드
 * - SURFACE: 감지된 평면 위에 그리기
 * - AIR: 공중에 그리기 (카메라 기준 고정 깊이)
 */
enum class DrawingMode {
    SURFACE,
    AIR
}
```

#### BrushSettings.kt
**파일**: `domain/model/BrushSettings.kt`

```kotlin
package com.sb.arsketch.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 브러시 설정
 * @param color 스트로크 색상 (ARGB Int)
 * @param thickness 스트로크 두께 (1: 얇음, 2: 보통, 3: 두꺼움)
 */
data class BrushSettings(
    val color: Int,
    val thickness: Thickness
) {
    enum class Thickness(val value: Float) {
        THIN(0.003f),      // 3mm
        MEDIUM(0.006f),    // 6mm
        THICK(0.012f)      // 12mm
    }

    companion object {
        // 기본 색상 팔레트
        val COLORS = listOf(
            0xFFFF0000.toInt(), // 빨강
            0xFFFF8800.toInt(), // 주황
            0xFFFFFF00.toInt(), // 노랑
            0xFF00FF00.toInt(), // 초록
            0xFF0088FF.toInt(), // 파랑
            0xFF8800FF.toInt(), // 보라
            0xFFFFFFFF.toInt(), // 흰색
            0xFF000000.toInt()  // 검정
        )

        val DEFAULT = BrushSettings(
            color = COLORS[4], // 파랑
            thickness = Thickness.MEDIUM
        )
    }
}
```

#### Stroke.kt
**파일**: `domain/model/Stroke.kt`

```kotlin
package com.sb.arsketch.domain.model

import java.util.UUID

/**
 * 하나의 스트로크(획)를 나타내는 데이터 클래스
 * 터치 시작부터 끝까지의 연속된 점들의 집합
 */
data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Point3D>,
    val color: Int,
    val thickness: Float,
    val mode: DrawingMode,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 스트로크에 새로운 점 추가 (불변 객체 유지)
     */
    fun addPoint(point: Point3D): Stroke {
        return copy(points = points + point)
    }

    /**
     * 유효한 스트로크인지 확인 (최소 2개 이상의 점)
     */
    fun isValid(): Boolean = points.size >= 2

    companion object {
        /**
         * 새 스트로크 생성
         */
        fun create(
            startPoint: Point3D,
            brush: BrushSettings,
            mode: DrawingMode
        ): Stroke {
            return Stroke(
                points = listOf(startPoint),
                color = brush.color,
                thickness = brush.thickness.value,
                mode = mode
            )
        }
    }
}
```

#### DrawingSession.kt
**파일**: `domain/model/DrawingSession.kt`

```kotlin
package com.sb.arsketch.domain.model

import java.util.UUID

/**
 * 드로잉 세션
 * 저장/불러오기 단위
 */
data class DrawingSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val strokes: List<Stroke> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 세션에 스트로크 추가
     */
    fun addStroke(stroke: Stroke): DrawingSession {
        return copy(
            strokes = strokes + stroke,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 마지막 스트로크 제거 (Undo)
     */
    fun removeLastStroke(): Pair<DrawingSession, Stroke?> {
        if (strokes.isEmpty()) return this to null
        val removed = strokes.last()
        return copy(
            strokes = strokes.dropLast(1),
            updatedAt = System.currentTimeMillis()
        ) to removed
    }

    /**
     * 모든 스트로크 제거
     */
    fun clearStrokes(): DrawingSession {
        return copy(
            strokes = emptyList(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
```

---

### Task 2.2: Repository 인터페이스 정의

#### StrokeRepository.kt
**파일**: `domain/repository/StrokeRepository.kt`

```kotlin
package com.sb.arsketch.domain.repository

import com.sb.arsketch.domain.model.Stroke
import kotlinx.coroutines.flow.Flow

/**
 * 스트로크 데이터 저장소 인터페이스
 */
interface StrokeRepository {

    /**
     * 세션에 스트로크 저장
     */
    suspend fun saveStroke(stroke: Stroke, sessionId: String)

    /**
     * 여러 스트로크 일괄 저장
     */
    suspend fun saveStrokes(strokes: List<Stroke>, sessionId: String)

    /**
     * 세션의 모든 스트로크 조회
     */
    suspend fun getStrokesForSession(sessionId: String): List<Stroke>

    /**
     * 세션의 스트로크를 Flow로 관찰
     */
    fun observeStrokesForSession(sessionId: String): Flow<List<Stroke>>

    /**
     * 특정 스트로크 삭제
     */
    suspend fun deleteStroke(strokeId: String)

    /**
     * 세션의 모든 스트로크 삭제
     */
    suspend fun deleteAllStrokesForSession(sessionId: String)
}
```

#### SessionRepository.kt
**파일**: `domain/repository/SessionRepository.kt`

```kotlin
package com.sb.arsketch.domain.repository

import com.sb.arsketch.domain.model.DrawingSession
import kotlinx.coroutines.flow.Flow

/**
 * 드로잉 세션 저장소 인터페이스
 */
interface SessionRepository {

    /**
     * 새 세션 생성
     */
    suspend fun createSession(name: String): DrawingSession

    /**
     * 세션 조회
     */
    suspend fun getSession(id: String): DrawingSession?

    /**
     * 모든 세션 목록 (Flow로 관찰)
     */
    fun getAllSessions(): Flow<List<DrawingSession>>

    /**
     * 세션 업데이트
     */
    suspend fun updateSession(session: DrawingSession)

    /**
     * 세션 삭제 (연관된 스트로크도 함께 삭제)
     */
    suspend fun deleteSession(id: String)

    /**
     * 가장 최근 세션 조회
     */
    suspend fun getLatestSession(): DrawingSession?
}
```

---

### Task 2.3: Stroke 관련 Use Case

#### CreateStrokeUseCase.kt
**파일**: `domain/usecase/stroke/CreateStrokeUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

/**
 * 새 스트로크 생성 Use Case
 */
class CreateStrokeUseCase @Inject constructor() {

    operator fun invoke(
        startPoint: Point3D,
        brush: BrushSettings,
        mode: DrawingMode
    ): Stroke {
        return Stroke.create(startPoint, brush, mode)
    }
}
```

#### AddPointToStrokeUseCase.kt
**파일**: `domain/usecase/stroke/AddPointToStrokeUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

/**
 * 스트로크에 점 추가 Use Case
 * 거리 기반 리샘플링 적용
 */
class AddPointToStrokeUseCase @Inject constructor() {

    companion object {
        // 최소 거리 (5mm) - 이보다 가까운 점은 무시
        const val MIN_DISTANCE = 0.005f
        // 최대 점 개수
        const val MAX_POINTS = 10000
    }

    /**
     * @return 점이 추가된 새 Stroke, 또는 조건 미충족 시 원본 Stroke
     */
    operator fun invoke(stroke: Stroke, newPoint: Point3D): Stroke {
        // 최대 점 개수 초과 시 무시
        if (stroke.points.size >= MAX_POINTS) {
            return stroke
        }

        // 마지막 점과의 거리 확인
        val lastPoint = stroke.points.lastOrNull()
        if (lastPoint != null && lastPoint.distanceTo(newPoint) < MIN_DISTANCE) {
            return stroke
        }

        return stroke.addPoint(newPoint)
    }
}
```

#### SaveStrokeUseCase.kt
**파일**: `domain/usecase/stroke/SaveStrokeUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

/**
 * 스트로크 저장 Use Case
 */
class SaveStrokeUseCase @Inject constructor(
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(stroke: Stroke, sessionId: String) {
        if (stroke.isValid()) {
            strokeRepository.saveStroke(stroke, sessionId)
        }
    }
}
```

#### UndoStrokeUseCase.kt
**파일**: `domain/usecase/stroke/UndoStrokeUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

/**
 * Undo 작업 Use Case
 * 스택 기반 Undo/Redo 로직
 */
class UndoStrokeUseCase @Inject constructor() {

    /**
     * @param strokes 현재 스트로크 목록
     * @param undoneStrokes 실행 취소된 스트로크 스택
     * @return 업데이트된 (strokes, undoneStrokes) 쌍
     */
    operator fun invoke(
        strokes: List<Stroke>,
        undoneStrokes: List<Stroke>
    ): Pair<List<Stroke>, List<Stroke>> {
        if (strokes.isEmpty()) {
            return strokes to undoneStrokes
        }

        val removedStroke = strokes.last()
        val newStrokes = strokes.dropLast(1)
        val newUndoneStrokes = undoneStrokes + removedStroke

        return newStrokes to newUndoneStrokes
    }
}
```

#### RedoStrokeUseCase.kt
**파일**: `domain/usecase/stroke/RedoStrokeUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

/**
 * Redo 작업 Use Case
 */
class RedoStrokeUseCase @Inject constructor() {

    /**
     * @param strokes 현재 스트로크 목록
     * @param undoneStrokes 실행 취소된 스트로크 스택
     * @return 업데이트된 (strokes, undoneStrokes) 쌍
     */
    operator fun invoke(
        strokes: List<Stroke>,
        undoneStrokes: List<Stroke>
    ): Pair<List<Stroke>, List<Stroke>> {
        if (undoneStrokes.isEmpty()) {
            return strokes to undoneStrokes
        }

        val restoredStroke = undoneStrokes.last()
        val newStrokes = strokes + restoredStroke
        val newUndoneStrokes = undoneStrokes.dropLast(1)

        return newStrokes to newUndoneStrokes
    }
}
```

#### ClearAllStrokesUseCase.kt
**파일**: `domain/usecase/stroke/ClearAllStrokesUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import javax.inject.Inject

/**
 * 모든 스트로크 삭제 Use Case
 */
class ClearAllStrokesUseCase @Inject constructor() {

    /**
     * @return 비어있는 스트로크 리스트들 (strokes, undoneStrokes)
     */
    operator fun invoke(): Pair<List<Nothing>, List<Nothing>> {
        return emptyList<Nothing>() to emptyList()
    }
}
```

---

### Task 2.4: Session 관련 Use Case

#### CreateSessionUseCase.kt
**파일**: `domain/usecase/session/CreateSessionUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 새 세션 생성 Use Case
 */
class CreateSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(name: String): DrawingSession {
        return sessionRepository.createSession(name)
    }
}
```

#### SaveSessionUseCase.kt
**파일**: `domain/usecase/session/SaveSessionUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

/**
 * 세션과 스트로크 저장 Use Case
 */
class SaveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        name: String,
        strokes: List<Stroke>
    ) {
        // 세션 업데이트 또는 생성
        val existingSession = sessionRepository.getSession(sessionId)
        val session = existingSession?.copy(
            name = name,
            strokes = strokes,
            updatedAt = System.currentTimeMillis()
        ) ?: DrawingSession(
            id = sessionId,
            name = name,
            strokes = strokes
        )

        sessionRepository.updateSession(session)

        // 스트로크 저장
        strokeRepository.deleteAllStrokesForSession(sessionId)
        strokeRepository.saveStrokes(strokes, sessionId)
    }
}
```

#### LoadSessionUseCase.kt
**파일**: `domain/usecase/session/LoadSessionUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

/**
 * 세션 불러오기 Use Case
 */
class LoadSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val strokeRepository: StrokeRepository
) {
    /**
     * @return 세션과 스트로크, 세션이 없으면 null
     */
    suspend operator fun invoke(sessionId: String): Pair<DrawingSession, List<Stroke>>? {
        val session = sessionRepository.getSession(sessionId) ?: return null
        val strokes = strokeRepository.getStrokesForSession(sessionId)
        return session to strokes
    }
}
```

#### GetAllSessionsUseCase.kt
**파일**: `domain/usecase/session/GetAllSessionsUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 모든 세션 목록 조회 Use Case
 */
class GetAllSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(): Flow<List<DrawingSession>> {
        return sessionRepository.getAllSessions()
    }
}
```

#### DeleteSessionUseCase.kt
**파일**: `domain/usecase/session/DeleteSessionUseCase.kt`

```kotlin
package com.sb.arsketch.domain.usecase.session

import com.sb.arsketch.domain.repository.SessionRepository
import com.sb.arsketch.domain.repository.StrokeRepository
import javax.inject.Inject

/**
 * 세션 삭제 Use Case
 */
class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val strokeRepository: StrokeRepository
) {
    suspend operator fun invoke(sessionId: String) {
        // 스트로크 먼저 삭제
        strokeRepository.deleteAllStrokesForSession(sessionId)
        // 세션 삭제
        sessionRepository.deleteSession(sessionId)
    }
}
```

---

## 완료 조건

- [ ] 모든 도메인 모델 파일 생성
- [ ] Repository 인터페이스 정의
- [ ] 모든 Use Case 구현
- [ ] 컴파일 오류 없음
- [ ] Android 의존성 없이 순수 Kotlin만 사용 (BrushSettings의 Color 제외 - 선택적 제거 가능)

---

## 의존성 다이어그램

```
UseCase
   │
   ├── CreateStrokeUseCase ──────────────> (독립)
   ├── AddPointToStrokeUseCase ──────────> (독립)
   ├── SaveStrokeUseCase ────────────────> StrokeRepository
   ├── UndoStrokeUseCase ────────────────> (독립)
   ├── RedoStrokeUseCase ────────────────> (독립)
   ├── ClearAllStrokesUseCase ───────────> (독립)
   │
   ├── CreateSessionUseCase ─────────────> SessionRepository
   ├── SaveSessionUseCase ───────────────> SessionRepository, StrokeRepository
   ├── LoadSessionUseCase ───────────────> SessionRepository, StrokeRepository
   ├── GetAllSessionsUseCase ────────────> SessionRepository
   └── DeleteSessionUseCase ─────────────> SessionRepository, StrokeRepository
```

---

## 다음 단계

→ [Epic 3: Data Layer](epic-03-data-layer.md)
