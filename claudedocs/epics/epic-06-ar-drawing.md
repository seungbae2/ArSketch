# Epic 6: AR Drawing 구현

## 개요
- **목표**: 터치 입력 → AR 월드 좌표 변환 → 실시간 스트로크 렌더링 파이프라인 구현
- **예상 작업량**: 높음 (핵심 기능)
- **의존성**: Epic 4 (AR Foundation), Epic 5 (Presentation) 완료

---

## 핵심 파이프라인

```
┌─────────────────────────────────────────────────────────────────┐
│                    AR Drawing Pipeline                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Touch Event                                                     │
│      │                                                          │
│      ▼                                                          │
│  ┌─────────────────┐                                            │
│  │ Screen 좌표     │ (픽셀)                                     │
│  │ (x, y)         │                                            │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────────┐                │
│  │            Hit Test / Projection            │                │
│  │  ┌─────────────┐    ┌─────────────────────┐ │                │
│  │  │ SURFACE     │    │ AIR Mode            │ │                │
│  │  │ Raycast →   │ or │ Camera Forward      │ │                │
│  │  │ Plane Hit   │    │ Projection (1.5m)   │ │                │
│  │  └─────────────┘    └─────────────────────┘ │                │
│  └────────┬────────────────────────────────────┘                │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ World 좌표      │ Point3D(x, y, z)                           │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────────┐                │
│  │         Distance-based Resampling           │                │
│  │  (마지막 점과 거리 > 5mm 인 경우만 추가)    │                │
│  └────────┬────────────────────────────────────┘                │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ Stroke에 점 추가 │                                            │
│  └────────┬────────┘                                            │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────────┐                │
│  │         Throttled Mesh Update               │                │
│  │  (최대 60fps로 제한)                        │                │
│  └────────┬────────────────────────────────────┘                │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │ OpenGL Render   │ LineStripMesh → Screen                     │
│  └─────────────────┘                                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 작업 목록

### Task 6.1: AR GLSurfaceView Wrapper

#### ARGLSurfaceView.kt
**파일**: `ar/core/ARGLSurfaceView.kt`

```kotlin
package com.sb.arsketch.ar.core

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.sb.arsketch.ar.renderer.ARRenderer
import timber.log.Timber

/**
 * AR용 GLSurfaceView 래퍼
 * 터치 이벤트 처리 및 렌더러 연결
 */
class ARGLSurfaceView(
    context: Context,
    private val arSessionManager: ARSessionManager
) : GLSurfaceView(context) {

    private val arRenderer: ARRenderer

    // 터치 이벤트 콜백
    var onTouchDown: ((Float, Float) -> Unit)? = null
    var onTouchMove: ((Float, Float) -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    init {
        // OpenGL ES 3.0 사용
        setEGLContextClientVersion(3)

        // 렌더러 생성 및 설정
        arRenderer = ARRenderer(context, arSessionManager)
        setRenderer(arRenderer)

        // 연속 렌더링 모드
        renderMode = RENDERMODE_CONTINUOUSLY

        Timber.d("ARGLSurfaceView 초기화 완료")
    }

    fun getARRenderer(): ARRenderer = arRenderer

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onTouchDown?.invoke(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                onTouchMove?.invoke(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onTouchUp?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        arSessionManager.resume()
    }

    override fun onPause() {
        super.onPause()
        arSessionManager.pause()
    }

    fun release() {
        arRenderer.release()
    }
}
```

---

### Task 6.2: 터치 → 월드 좌표 변환기

#### TouchToWorldConverter.kt
**파일**: `ar/util/TouchToWorldConverter.kt`

```kotlin
package com.sb.arsketch.ar.util

import com.google.ar.core.Frame
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 터치 좌표 → 월드 좌표 변환
 */
@Singleton
class TouchToWorldConverter @Inject constructor(
    private val hitTestHelper: HitTestHelper,
    private val airDrawingProjector: AirDrawingProjector
) {

    /**
     * 화면 터치 좌표를 월드 좌표로 변환
     *
     * @param frame 현재 AR 프레임
     * @param screenX 화면 X 좌표 (픽셀)
     * @param screenY 화면 Y 좌표 (픽셀)
     * @param viewportWidth 뷰포트 너비
     * @param viewportHeight 뷰포트 높이
     * @param mode 드로잉 모드
     * @return 월드 좌표 Point3D, 또는 null
     */
    fun convert(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        mode: DrawingMode
    ): Point3D? {
        return when (mode) {
            DrawingMode.SURFACE -> convertSurfaceMode(frame, screenX, screenY)
            DrawingMode.AIR -> convertAirMode(frame, screenX, screenY, viewportWidth, viewportHeight)
        }
    }

    private fun convertSurfaceMode(
        frame: Frame,
        screenX: Float,
        screenY: Float
    ): Point3D? {
        return when (val result = hitTestHelper.performHitTest(frame, screenX, screenY)) {
            is HitTestResult.PlaneHit -> {
                Timber.v("Surface hit at: ${result.point}")
                result.point
            }
            is HitTestResult.NoHit -> {
                Timber.v("No surface hit")
                null
            }
        }
    }

    private fun convertAirMode(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int
    ): Point3D? {
        // 정규화된 좌표로 변환 (0.0 ~ 1.0)
        val normalizedX = screenX / viewportWidth
        val normalizedY = screenY / viewportHeight

        return airDrawingProjector.projectToWorld(
            frame = frame,
            screenX = normalizedX,
            screenY = normalizedY
        )
    }
}
```

---

### Task 6.3: 드로잉 컨트롤러

#### DrawingController.kt
**파일**: `ar/core/DrawingController.kt`

```kotlin
package com.sb.arsketch.ar.core

import com.google.ar.core.Frame
import com.sb.arsketch.ar.util.TouchToWorldConverter
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 드로잉 컨트롤러
 * 터치 이벤트를 월드 좌표로 변환하고 콜백 전달
 */
@Singleton
class DrawingController @Inject constructor(
    private val touchToWorldConverter: TouchToWorldConverter
) {
    // 현재 AR 프레임 (렌더 루프에서 업데이트)
    @Volatile
    private var currentFrame: Frame? = null

    // 뷰포트 크기
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0

    // 드로잉 모드
    @Volatile
    private var drawingMode: DrawingMode = DrawingMode.SURFACE

    // 콜백
    var onStrokeStart: ((Point3D) -> Unit)? = null
    var onStrokePoint: ((Point3D) -> Unit)? = null
    var onStrokeEnd: (() -> Unit)? = null

    // 드로잉 상태
    private var isDrawing = false

    /**
     * 프레임 업데이트 (렌더 루프에서 호출)
     */
    fun updateFrame(frame: Frame) {
        currentFrame = frame
    }

    /**
     * 뷰포트 크기 설정
     */
    fun setViewportSize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    /**
     * 드로잉 모드 설정
     */
    fun setDrawingMode(mode: DrawingMode) {
        drawingMode = mode
    }

    /**
     * 터치 다운 처리
     */
    fun onTouchDown(screenX: Float, screenY: Float) {
        val frame = currentFrame ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        val point = touchToWorldConverter.convert(
            frame = frame,
            screenX = screenX,
            screenY = screenY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            mode = drawingMode
        )

        if (point != null) {
            isDrawing = true
            onStrokeStart?.invoke(point)
            Timber.d("드로잉 시작: $point")
        }
    }

    /**
     * 터치 이동 처리
     */
    fun onTouchMove(screenX: Float, screenY: Float) {
        if (!isDrawing) return

        val frame = currentFrame ?: return

        val point = touchToWorldConverter.convert(
            frame = frame,
            screenX = screenX,
            screenY = screenY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            mode = drawingMode
        )

        if (point != null) {
            onStrokePoint?.invoke(point)
        }
    }

    /**
     * 터치 업 처리
     */
    fun onTouchUp() {
        if (isDrawing) {
            isDrawing = false
            onStrokeEnd?.invoke()
            Timber.d("드로잉 종료")
        }
    }

    /**
     * 현재 드로잉 중인지 확인
     */
    fun isCurrentlyDrawing(): Boolean = isDrawing
}
```

---

### Task 6.4: Drawing Screen (메인 화면)

#### DrawingScreen.kt
**파일**: `presentation/screen/drawing/DrawingScreen.kt`

```kotlin
package com.sb.arsketch.presentation.screen.drawing

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sb.arsketch.ar.core.ARGLSurfaceView
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.ARSessionState
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.component.ActionToolbar
import com.sb.arsketch.presentation.component.BrushToolbar
import com.sb.arsketch.presentation.component.SaveSessionDialog
import com.sb.arsketch.presentation.component.TrackingStatusIndicator
import com.sb.arsketch.presentation.state.ARState
import timber.log.Timber
import javax.inject.Inject

/**
 * 메인 드로잉 화면
 */
@Composable
fun DrawingScreen(
    viewModel: DrawingViewModel = hiltViewModel(),
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    onNavigateToSessions: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()

    // 카메라 권한 상태
    var hasCameraPermission by remember { mutableStateOf(false) }

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted && activity != null) {
            arSessionManager.checkAndInitialize(activity)
        }
    }

    // GLSurfaceView 참조
    var glSurfaceView: ARGLSurfaceView? by remember { mutableStateOf(null) }

    // 권한 요청
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // AR 세션 상태 관찰
    LaunchedEffect(arSessionManager) {
        arSessionManager.sessionState.collect { state ->
            when (state) {
                is ARSessionState.Ready -> viewModel.updateARState(ARState.Searching)
                is ARSessionState.Error -> viewModel.updateARState(ARState.Error(state.message))
                else -> {}
            }
        }
    }

    // 추적 상태 관찰
    LaunchedEffect(arSessionManager) {
        arSessionManager.trackingState.collect { state ->
            when (state) {
                is com.sb.arsketch.ar.core.ARTrackingState.Tracking ->
                    viewModel.updateARState(ARState.Tracking)
                is com.sb.arsketch.ar.core.ARTrackingState.NotTracking ->
                    viewModel.updateARState(ARState.Searching)
                is com.sb.arsketch.ar.core.ARTrackingState.Paused ->
                    viewModel.updateARState(ARState.Paused)
            }
        }
    }

    // 드로잉 컨트롤러 콜백 설정
    LaunchedEffect(drawingController) {
        drawingController.onStrokeStart = { point ->
            viewModel.onTouchStart(point)
        }
        drawingController.onStrokePoint = { point ->
            viewModel.onTouchMove(point)
        }
        drawingController.onStrokeEnd = {
            viewModel.onTouchEnd()
        }
    }

    // 드로잉 모드 동기화
    LaunchedEffect(uiState.drawingMode) {
        drawingController.setDrawingMode(uiState.drawingMode)
    }

    // 라이프사이클 관찰
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView?.release()
        }
    }

    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        // AR GLSurfaceView
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    ARGLSurfaceView(ctx, arSessionManager).also { view ->
                        glSurfaceView = view

                        // 터치 이벤트 연결
                        view.onTouchDown = { x, y ->
                            drawingController.onTouchDown(x, y)
                        }
                        view.onTouchMove = { x, y ->
                            drawingController.onTouchMove(x, y)
                        }
                        view.onTouchUp = {
                            drawingController.onTouchUp()
                        }

                        // 프레임 콜백 설정
                        view.getARRenderer().onFrameUpdate = { frame ->
                            drawingController.updateFrame(frame)

                            // 렌더러에 스트로크 데이터 전달
                            val (strokes, currentStroke) = viewModel.getStrokesForRendering()
                            view.getARRenderer().updateStrokes(strokes, currentStroke)
                        }

                        // 뷰포트 크기 설정 (레이아웃 후)
                        view.post {
                            drawingController.setViewportSize(view.width, view.height)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 상단 상태 표시
        TrackingStatusIndicator(
            arState = uiState.arState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        )

        // 하단 툴바
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            BrushToolbar(
                brushSettings = uiState.brushSettings,
                onColorSelected = viewModel::setColor,
                onThicknessSelected = viewModel::setThickness
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionToolbar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = viewModel::clearAll,
                onSave = viewModel::showSaveDialog
            )
        }

        // 에러 메시지
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(error)
            }
        }

        // 저장 다이얼로그
        if (uiState.showSaveDialog) {
            SaveSessionDialog(
                sessionName = uiState.sessionName,
                onSessionNameChange = viewModel::updateSessionName,
                onDismiss = viewModel::dismissSaveDialog,
                onConfirm = viewModel::saveSession
            )
        }
    }
}
```

---

### Task 6.5: Hilt AR Module

#### ARModule.kt
**파일**: `di/ARModule.kt`

```kotlin
package com.sb.arsketch.di

import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.ar.util.AirDrawingProjector
import com.sb.arsketch.ar.util.HitTestHelper
import com.sb.arsketch.ar.util.TouchToWorldConverter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ARModule {

    @Provides
    @Singleton
    fun provideARSessionManager(): ARSessionManager {
        return ARSessionManager()
    }

    @Provides
    @Singleton
    fun provideHitTestHelper(): HitTestHelper {
        return HitTestHelper()
    }

    @Provides
    @Singleton
    fun provideAirDrawingProjector(): AirDrawingProjector {
        return AirDrawingProjector()
    }

    @Provides
    @Singleton
    fun provideTouchToWorldConverter(
        hitTestHelper: HitTestHelper,
        airDrawingProjector: AirDrawingProjector
    ): TouchToWorldConverter {
        return TouchToWorldConverter(hitTestHelper, airDrawingProjector)
    }

    @Provides
    @Singleton
    fun provideDrawingController(
        touchToWorldConverter: TouchToWorldConverter
    ): DrawingController {
        return DrawingController(touchToWorldConverter)
    }
}
```

---

### Task 6.6: MainActivity 업데이트

#### MainActivity.kt (업데이트)
**파일**: `MainActivity.kt`

```kotlin
package com.sb.arsketch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.navigation.ArSketchNavGraph
import com.sb.arsketch.ui.theme.ArSketchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var arSessionManager: ARSessionManager

    @Inject
    lateinit var drawingController: DrawingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // AR 세션 초기화는 권한 획득 후 DrawingScreen에서 수행

        setContent {
            ArSketchTheme {
                ArSketchNavGraph()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        arSessionManager.destroy()
    }
}
```

---

## 성능 최적화 전략

### 1. 거리 기반 리샘플링
```kotlin
// AddPointToStrokeUseCase.kt
const val MIN_DISTANCE = 0.005f  // 5mm

if (lastPoint.distanceTo(newPoint) < MIN_DISTANCE) {
    return stroke  // 점 추가하지 않음
}
```

### 2. 메쉬 업데이트 쓰로틀링
```kotlin
// 프레임마다 업데이트하지 않고 변경이 있을 때만
private var lastMeshUpdateTime = 0L
private const val MESH_UPDATE_INTERVAL_MS = 16L  // ~60fps

fun updateMeshIfNeeded() {
    val now = System.currentTimeMillis()
    if (now - lastMeshUpdateTime >= MESH_UPDATE_INTERVAL_MS) {
        updateMesh()
        lastMeshUpdateTime = now
    }
}
```

### 3. 포인트 최대 개수 제한
```kotlin
const val MAX_POINTS = 10000

if (stroke.points.size >= MAX_POINTS) {
    return stroke  // 더 이상 점 추가하지 않음
}
```

---

## 완료 조건

- [ ] ARGLSurfaceView 구현 완료
- [ ] TouchToWorldConverter 구현 완료
- [ ] DrawingController 구현 완료
- [ ] DrawingScreen Compose UI 구현 완료
- [ ] AR Module (Hilt) 설정 완료
- [ ] MainActivity 업데이트 완료
- [ ] 실제 기기에서 AR 드로잉 동작 확인
- [ ] Surface 모드에서 평면 위 드로잉 동작 확인
- [ ] Undo/Redo 동작 확인
- [ ] 색상/두께 변경 동작 확인

---

## 테스트 계획

### 수동 테스트 체크리스트
1. 앱 실행 시 카메라 권한 요청
2. AR 세션 초기화 및 평면 감지
3. 평면 터치 시 스트로크 시작
4. 드래그하며 스트로크 그리기
5. 손 떼면 스트로크 완료
6. Undo 버튼으로 마지막 스트로크 제거
7. Redo 버튼으로 복원
8. 색상 변경 후 새 스트로크
9. 두께 변경 후 새 스트로크

---

## 다음 단계

→ [Epic 7: 저장/불러오기](epic-07-save-load.md)
