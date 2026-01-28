# Epic 9: 테스트 및 최적화

## 개요
- **목표**: 단위 테스트 작성, 통합 테스트, 성능 최적화
- **예상 작업량**: 중간
- **의존성**: 모든 이전 Epic 완료

---

## 테스트 전략

### 테스트 피라미드

```
         /\
        /  \
       /E2E \     ← 수동 테스트 (AR 기기 테스트)
      /------\
     /        \
    / 통합     \   ← Room DB, Repository
   /------------\
  /              \
 /   단위 테스트  \  ← Domain UseCase, ViewModel
/------------------\
```

### 테스트 범위

| 레이어 | 테스트 유형 | 도구 |
|--------|-----------|------|
| Domain | 단위 테스트 | JUnit, MockK |
| Data | 통합 테스트 | Room Testing, Coroutines Test |
| Presentation | 단위 테스트 | JUnit, Turbine (Flow 테스트) |
| AR | 수동 테스트 | 실제 ARCore 기기 |

---

## Task 9.1: 테스트 의존성 추가

### libs.versions.toml 업데이트

```toml
[versions]
# 테스트
mockk = "1.13.9"
turbine = "1.0.0"
coroutinesTest = "1.8.0"
archCoreTesting = "2.2.0"
roomTesting = "2.6.1"

[libraries]
# 테스트
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
mockk-android = { module = "io.mockk:mockk-android", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
arch-core-testing = { module = "androidx.arch.core:core-testing", version.ref = "archCoreTesting" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
```

### app/build.gradle.kts 업데이트

```kotlin
dependencies {
    // 테스트
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.arch.core.testing)

    // Android 테스트
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
```

---

## Task 9.2: Domain Use Case 단위 테스트

### AddPointToStrokeUseCaseTest.kt
**파일**: `app/src/test/java/com/sb/arsketch/domain/usecase/stroke/AddPointToStrokeUseCaseTest.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class AddPointToStrokeUseCaseTest {

    private lateinit var useCase: AddPointToStrokeUseCase

    @Before
    fun setup() {
        useCase = AddPointToStrokeUseCase()
    }

    private fun createStroke(points: List<Point3D> = listOf(Point3D.ZERO)): Stroke {
        return Stroke(
            id = "test-stroke",
            points = points,
            color = BrushSettings.DEFAULT.color,
            thickness = BrushSettings.DEFAULT.thickness.value,
            mode = DrawingMode.SURFACE
        )
    }

    @Test
    fun `새 점이 최소 거리 이상이면 추가됨`() {
        // Given
        val stroke = createStroke(listOf(Point3D(0f, 0f, 0f)))
        val newPoint = Point3D(0.01f, 0f, 0f)  // 10mm > 5mm

        // When
        val result = useCase(stroke, newPoint)

        // Then
        assertEquals(2, result.points.size)
        assertEquals(newPoint, result.points.last())
    }

    @Test
    fun `새 점이 최소 거리 미만이면 무시됨`() {
        // Given
        val stroke = createStroke(listOf(Point3D(0f, 0f, 0f)))
        val newPoint = Point3D(0.003f, 0f, 0f)  // 3mm < 5mm

        // When
        val result = useCase(stroke, newPoint)

        // Then
        assertSame(stroke, result)  // 같은 객체 반환
        assertEquals(1, result.points.size)
    }

    @Test
    fun `최대 점 개수 초과 시 추가 무시`() {
        // Given
        val maxPoints = AddPointToStrokeUseCase.MAX_POINTS
        val points = (0 until maxPoints).map { Point3D(it.toFloat() * 0.01f, 0f, 0f) }
        val stroke = createStroke(points)
        val newPoint = Point3D(maxPoints.toFloat() * 0.01f, 0f, 0f)

        // When
        val result = useCase(stroke, newPoint)

        // Then
        assertSame(stroke, result)
        assertEquals(maxPoints, result.points.size)
    }
}
```

### UndoRedoUseCaseTest.kt
**파일**: `app/src/test/java/com/sb/arsketch/domain/usecase/stroke/UndoRedoUseCaseTest.kt`

```kotlin
package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoRedoUseCaseTest {

    private lateinit var undoUseCase: UndoStrokeUseCase
    private lateinit var redoUseCase: RedoStrokeUseCase

    @Before
    fun setup() {
        undoUseCase = UndoStrokeUseCase()
        redoUseCase = RedoStrokeUseCase()
    }

    private fun createStroke(id: String): Stroke {
        return Stroke(
            id = id,
            points = listOf(Point3D.ZERO, Point3D(0.1f, 0f, 0f)),
            color = BrushSettings.DEFAULT.color,
            thickness = BrushSettings.DEFAULT.thickness.value,
            mode = DrawingMode.SURFACE
        )
    }

    @Test
    fun `Undo 시 마지막 스트로크가 undoneStrokes로 이동`() {
        // Given
        val stroke1 = createStroke("stroke-1")
        val stroke2 = createStroke("stroke-2")
        val strokes = listOf(stroke1, stroke2)
        val undoneStrokes = emptyList<Stroke>()

        // When
        val (newStrokes, newUndoneStrokes) = undoUseCase(strokes, undoneStrokes)

        // Then
        assertEquals(1, newStrokes.size)
        assertEquals(stroke1, newStrokes[0])
        assertEquals(1, newUndoneStrokes.size)
        assertEquals(stroke2, newUndoneStrokes[0])
    }

    @Test
    fun `빈 strokes에서 Undo 시 변화 없음`() {
        // Given
        val strokes = emptyList<Stroke>()
        val undoneStrokes = emptyList<Stroke>()

        // When
        val (newStrokes, newUndoneStrokes) = undoUseCase(strokes, undoneStrokes)

        // Then
        assertTrue(newStrokes.isEmpty())
        assertTrue(newUndoneStrokes.isEmpty())
    }

    @Test
    fun `Redo 시 undoneStrokes에서 strokes로 복원`() {
        // Given
        val stroke1 = createStroke("stroke-1")
        val stroke2 = createStroke("stroke-2")
        val strokes = listOf(stroke1)
        val undoneStrokes = listOf(stroke2)

        // When
        val (newStrokes, newUndoneStrokes) = redoUseCase(strokes, undoneStrokes)

        // Then
        assertEquals(2, newStrokes.size)
        assertEquals(stroke2, newStrokes[1])
        assertTrue(newUndoneStrokes.isEmpty())
    }

    @Test
    fun `Undo 후 Redo 시 원래 상태로 복원`() {
        // Given
        val stroke = createStroke("stroke-1")
        val strokes = listOf(stroke)
        val undoneStrokes = emptyList<Stroke>()

        // When
        val (afterUndo, afterUndoUndone) = undoUseCase(strokes, undoneStrokes)
        val (afterRedo, afterRedoUndone) = redoUseCase(afterUndo, afterUndoUndone)

        // Then
        assertEquals(1, afterRedo.size)
        assertEquals(stroke, afterRedo[0])
        assertTrue(afterRedoUndone.isEmpty())
    }
}
```

---

## Task 9.3: ViewModel 테스트

### DrawingViewModelTest.kt
**파일**: `app/src/test/java/com/sb/arsketch/presentation/screen/drawing/DrawingViewModelTest.kt`

```kotlin
package com.sb.arsketch.presentation.screen.drawing

import app.cash.turbine.test
import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.usecase.session.CreateSessionUseCase
import com.sb.arsketch.domain.usecase.session.SaveSessionUseCase
import com.sb.arsketch.domain.usecase.stroke.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingViewModelTest {

    private lateinit var viewModel: DrawingViewModel
    private val testDispatcher = StandardTestDispatcher()

    // Use Cases
    private val createStrokeUseCase = CreateStrokeUseCase()
    private val addPointToStrokeUseCase = AddPointToStrokeUseCase()
    private val undoStrokeUseCase = UndoStrokeUseCase()
    private val redoStrokeUseCase = RedoStrokeUseCase()
    private val clearAllStrokesUseCase = ClearAllStrokesUseCase()
    private val saveSessionUseCase: SaveSessionUseCase = mockk()
    private val createSessionUseCase: CreateSessionUseCase = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DrawingViewModel(
            createStrokeUseCase = createStrokeUseCase,
            addPointToStrokeUseCase = addPointToStrokeUseCase,
            undoStrokeUseCase = undoStrokeUseCase,
            redoStrokeUseCase = redoStrokeUseCase,
            clearAllStrokesUseCase = clearAllStrokesUseCase,
            saveSessionUseCase = saveSessionUseCase,
            createSessionUseCase = createSessionUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `터치 시작 시 currentStroke 생성`() = runTest {
        // Given
        val startPoint = Point3D(1f, 0f, 0f)

        // When
        viewModel.onTouchStart(startPoint)

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.currentStroke)
        assertEquals(1, state.currentStroke?.points?.size)
        assertEquals(startPoint, state.currentStroke?.points?.first())
    }

    @Test
    fun `터치 이동 시 currentStroke에 점 추가`() = runTest {
        // Given
        val startPoint = Point3D(0f, 0f, 0f)
        val movePoint = Point3D(0.1f, 0f, 0f)  // 10cm

        viewModel.onTouchStart(startPoint)

        // When
        viewModel.onTouchMove(movePoint)

        // Then
        val state = viewModel.uiState.value
        assertEquals(2, state.currentStroke?.points?.size)
    }

    @Test
    fun `터치 종료 시 currentStroke가 strokes로 이동`() = runTest {
        // Given
        val startPoint = Point3D(0f, 0f, 0f)
        val endPoint = Point3D(0.1f, 0f, 0f)

        viewModel.onTouchStart(startPoint)
        viewModel.onTouchMove(endPoint)

        // When
        viewModel.onTouchEnd()

        // Then
        val state = viewModel.uiState.value
        assertNull(state.currentStroke)
        assertEquals(1, state.strokes.size)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Undo 시 canUndo와 canRedo 상태 업데이트`() = runTest {
        // Given - 스트로크 하나 생성
        viewModel.onTouchStart(Point3D(0f, 0f, 0f))
        viewModel.onTouchMove(Point3D(0.1f, 0f, 0f))
        viewModel.onTouchEnd()

        // When
        viewModel.undo()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.strokes.isEmpty())
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test
    fun `색상 변경 시 brushSettings 업데이트`() = runTest {
        // Given
        val newColor = 0xFFFF0000.toInt()

        // When
        viewModel.setColor(newColor)

        // Then
        assertEquals(newColor, viewModel.uiState.value.brushSettings.color)
    }

    @Test
    fun `clearAll 시 모든 스트로크 삭제`() = runTest {
        // Given
        repeat(3) { i ->
            viewModel.onTouchStart(Point3D(i.toFloat(), 0f, 0f))
            viewModel.onTouchMove(Point3D(i.toFloat() + 0.1f, 0f, 0f))
            viewModel.onTouchEnd()
        }

        // When
        viewModel.clearAll()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.strokes.isEmpty())
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }
}
```

---

## Task 9.4: Room Database 통합 테스트

### StrokeDaoTest.kt
**파일**: `app/src/androidTest/java/com/sb/arsketch/data/local/db/StrokeDaoTest.kt`

```kotlin
package com.sb.arsketch.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sb.arsketch.data.local.entity.SessionEntity
import com.sb.arsketch.data.local.entity.StrokeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
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
```

---

## Task 9.5: 성능 최적화

### 1. 메모리 최적화

```kotlin
// LineStripMesh.kt - VBO 재사용
class LineStripMesh {
    private var vboId = 0
    private var vboCapacity = 0
    private val INITIAL_CAPACITY = 1000

    fun uploadToGPU() {
        val requiredSize = vertexCount * COORDS_PER_VERTEX * BYTES_PER_FLOAT

        if (vboId == 0) {
            // VBO 생성
            val vbos = IntArray(1)
            GLES30.glGenBuffers(1, vbos, 0)
            vboId = vbos[0]
            vboCapacity = 0
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)

        if (requiredSize > vboCapacity) {
            // 용량 부족 시 재할당 (2배 증가)
            vboCapacity = maxOf(requiredSize, vboCapacity * 2)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                vboCapacity,
                null,
                GLES30.GL_DYNAMIC_DRAW
            )
        }

        // 데이터만 업데이트 (재할당 없이)
        GLES30.glBufferSubData(
            GLES30.GL_ARRAY_BUFFER,
            0,
            requiredSize,
            vertexBuffer
        )
    }
}
```

### 2. 렌더링 최적화

```kotlin
// ARRenderer.kt - 변경 감지 기반 업데이트
class ARRenderer {
    private var lastStrokesHash = 0

    fun updateStrokes(strokes: List<Stroke>, currentStroke: Stroke?) {
        val newHash = strokes.hashCode() + (currentStroke?.hashCode() ?: 0)

        if (newHash != lastStrokesHash) {
            strokeRenderer.updateStrokes(strokes, currentStroke)
            lastStrokesHash = newHash
        }
    }
}
```

### 3. 터치 이벤트 쓰로틀링

```kotlin
// DrawingController.kt - 터치 이벤트 쓰로틀링
class DrawingController {
    private var lastTouchTime = 0L
    private val TOUCH_THROTTLE_MS = 8L  // ~120fps

    fun onTouchMove(screenX: Float, screenY: Float) {
        val now = System.currentTimeMillis()
        if (now - lastTouchTime < TOUCH_THROTTLE_MS) {
            return  // 쓰로틀링
        }
        lastTouchTime = now

        // 실제 처리...
    }
}
```

---

## Task 9.6: 수동 테스트 체크리스트

### AR 기능 테스트
- [ ] 앱 실행 시 카메라 권한 요청
- [ ] ARCore 세션 초기화 성공
- [ ] 평면 감지 동작 (바닥, 테이블)
- [ ] 추적 상태 인디케이터 정확히 표시

### 드로잉 테스트
- [ ] Surface 모드에서 평면 위 드로잉
- [ ] Air 모드에서 공중 드로잉
- [ ] 색상 변경 후 새 스트로크에 반영
- [ ] 두께 변경 후 새 스트로크에 반영
- [ ] 빠른 드로잉 시 부드러운 렌더링

### Undo/Redo 테스트
- [ ] Undo로 마지막 스트로크 제거
- [ ] 연속 Undo 동작
- [ ] Redo로 복원
- [ ] Clear All 후 Undo 불가

### 저장/불러오기 테스트
- [ ] 저장 다이얼로그 표시
- [ ] 이름 입력 후 저장 성공
- [ ] 세션 목록에 저장된 항목 표시
- [ ] 저장된 세션 불러오기
- [ ] 불러온 스트로크 AR에 렌더링
- [ ] 세션 삭제 동작

### 성능 테스트
- [ ] 10+ 스트로크에서 프레임 드랍 없음
- [ ] 긴 스트로크(1000+ 점)에서 안정적
- [ ] 저장/불러오기 시 UI 멈춤 없음

### 엣지 케이스
- [ ] 카메라 권한 거부 시 적절한 메시지
- [ ] ARCore 미지원 기기에서 에러 처리
- [ ] 앱 백그라운드/포그라운드 전환
- [ ] 화면 회전 시 상태 유지

---

## 완료 조건

### 단위 테스트
- [ ] Domain UseCase 테스트 작성 완료
- [ ] ViewModel 테스트 작성 완료
- [ ] 테스트 커버리지 60% 이상

### 통합 테스트
- [ ] Room DAO 테스트 작성 완료
- [ ] Repository 테스트 작성 완료

### 성능 최적화
- [ ] VBO 재사용 구현
- [ ] 변경 감지 기반 업데이트 구현
- [ ] 터치 이벤트 쓰로틀링 구현

### 수동 테스트
- [ ] 모든 체크리스트 항목 통과

---

## 빌드 및 테스트 명령어

```bash
# 전체 단위 테스트 실행
./gradlew testDebugUnitTest

# 특정 테스트 클래스 실행
./gradlew test --tests "com.sb.arsketch.domain.usecase.stroke.AddPointToStrokeUseCaseTest"

# Android 테스트 실행 (기기/에뮬레이터 필요)
./gradlew connectedAndroidTest

# 테스트 커버리지 리포트
./gradlew testDebugUnitTestCoverage

# 린트 검사
./gradlew lintDebug
```

---

## 프로젝트 완료!

모든 Epic이 완료되면 AirSketch AR 앱은 다음 기능을 갖추게 됩니다:

1. ARCore 기반 평면 감지
2. Surface/Air 모드 드로잉
3. 실시간 Line Strip 렌더링
4. 색상/두께 설정
5. Undo/Redo
6. 세션 저장/불러오기
7. Clean Architecture + MVVM

향후 WebRTC 통합을 통해 원격 협업 AR 드로잉으로 확장할 수 있는 기반이 마련됩니다.
