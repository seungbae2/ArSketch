# Epic 4: AR Foundation

## 개요
- **목표**: ARCore 세션 관리 및 OpenGL ES 렌더링 파이프라인 구축
- **예상 작업량**: 높음 (기술적 복잡도 높음)
- **의존성**: Epic 1 완료

---

## 기술 배경

### ARCore 기본 개념
- **Session**: AR 기능의 진입점, 카메라와 AR 상태 관리
- **Frame**: 매 프레임마다 카메라 이미지와 AR 상태 제공
- **Plane**: 감지된 수평/수직 평면
- **HitResult**: Raycast 결과 (화면 좌표 → 월드 좌표)
- **Trackable**: 추적 가능한 AR 객체 (Plane, Point 등)

### OpenGL ES 렌더링 파이프라인
1. AR 카메라 배경 렌더링
2. AR 객체 (스트로크) 렌더링
3. UI 오버레이

---

## 작업 목록

### Task 4.1: AR Session Manager

#### ARSessionManager.kt
**파일**: `ar/core/ARSessionManager.kt`

```kotlin
package com.sb.arsketch.ar.core

import android.app.Activity
import android.view.Surface
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AR 세션 상태
 */
sealed class ARSessionState {
    object NotInitialized : ARSessionState()
    object Initializing : ARSessionState()
    object Ready : ARSessionState()
    data class Error(val message: String) : ARSessionState()
}

/**
 * AR 추적 상태
 */
sealed class ARTrackingState {
    object NotTracking : ARTrackingState()
    object Tracking : ARTrackingState()
    object Paused : ARTrackingState()
}

/**
 * ARCore 세션 관리자
 */
@Singleton
class ARSessionManager @Inject constructor() {

    private var session: Session? = null
    private var isInstallRequested = false

    private val _sessionState = MutableStateFlow<ARSessionState>(ARSessionState.NotInitialized)
    val sessionState: StateFlow<ARSessionState> = _sessionState.asStateFlow()

    private val _trackingState = MutableStateFlow<ARTrackingState>(ARTrackingState.NotTracking)
    val trackingState: StateFlow<ARTrackingState> = _trackingState.asStateFlow()

    /**
     * ARCore 가용성 확인 및 세션 초기화
     * @return 세션이 준비되었으면 true
     */
    fun checkAndInitialize(activity: Activity): Boolean {
        _sessionState.value = ARSessionState.Initializing

        // ARCore 설치 상태 확인
        val availability = ArCoreApk.getInstance().checkAvailability(activity)

        if (availability.isTransient) {
            // 아직 확인 중, 나중에 다시 확인 필요
            return false
        }

        if (availability.isSupported) {
            return tryCreateSession(activity)
        } else {
            _sessionState.value = ARSessionState.Error("ARCore를 지원하지 않는 기기입니다")
            return false
        }
    }

    private fun tryCreateSession(activity: Activity): Boolean {
        return try {
            // ARCore 설치 요청 (필요한 경우)
            when (ArCoreApk.getInstance().requestInstall(activity, !isInstallRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    isInstallRequested = true
                    false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    createSession(activity)
                    true
                }
            }
        } catch (e: Exception) {
            handleSessionCreationError(e)
            false
        }
    }

    private fun createSession(activity: Activity) {
        session = Session(activity).also { newSession ->
            // 세션 설정
            val config = Config(newSession).apply {
                // 평면 감지 활성화
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

                // 깊이 모드 (지원 시)
                if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }

                // 라이트 추정
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR

                // 포커스 모드
                focusMode = Config.FocusMode.AUTO

                // 업데이트 모드
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }

            newSession.configure(config)
            _sessionState.value = ARSessionState.Ready
            Timber.d("ARCore 세션 생성 완료")
        }
    }

    private fun handleSessionCreationError(e: Exception) {
        val errorMessage = when (e) {
            is UnavailableArcoreNotInstalledException -> "ARCore가 설치되어 있지 않습니다"
            is UnavailableApkTooOldException -> "ARCore 업데이트가 필요합니다"
            is UnavailableSdkTooOldException -> "앱 업데이트가 필요합니다"
            is UnavailableDeviceNotCompatibleException -> "이 기기는 ARCore를 지원하지 않습니다"
            else -> "AR 세션 생성 실패: ${e.message}"
        }
        _sessionState.value = ARSessionState.Error(errorMessage)
        Timber.e(e, "ARCore 세션 생성 오류")
    }

    /**
     * 세션 재개
     */
    fun resume() {
        session?.let { activeSession ->
            try {
                activeSession.resume()
                Timber.d("AR 세션 재개")
            } catch (e: CameraNotAvailableException) {
                _sessionState.value = ARSessionState.Error("카메라를 사용할 수 없습니다")
                Timber.e(e, "카메라 사용 불가")
            }
        }
    }

    /**
     * 세션 일시 중지
     */
    fun pause() {
        session?.pause()
        _trackingState.value = ARTrackingState.Paused
        Timber.d("AR 세션 일시 중지")
    }

    /**
     * 세션 종료 및 리소스 해제
     */
    fun destroy() {
        session?.close()
        session = null
        _sessionState.value = ARSessionState.NotInitialized
        _trackingState.value = ARTrackingState.NotTracking
        Timber.d("AR 세션 종료")
    }

    /**
     * 디스플레이 방향 설정
     */
    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        session?.setDisplayGeometry(rotation, width, height)
    }

    /**
     * 현재 프레임 업데이트
     * @return 업데이트된 Frame, 또는 null
     */
    fun update(): Frame? {
        return try {
            session?.update()?.also { frame ->
                // 추적 상태 업데이트
                val camera = frame.camera
                _trackingState.value = when (camera.trackingState) {
                    TrackingState.TRACKING -> ARTrackingState.Tracking
                    TrackingState.PAUSED -> ARTrackingState.Paused
                    else -> ARTrackingState.NotTracking
                }
            }
        } catch (e: CameraNotAvailableException) {
            Timber.e(e, "프레임 업데이트 실패")
            null
        }
    }

    /**
     * 세션이 준비되었는지 확인
     */
    fun isReady(): Boolean = session != null && _sessionState.value == ARSessionState.Ready

    /**
     * 현재 세션 반환
     */
    fun getSession(): Session? = session
}
```

---

### Task 4.2: Hit Test Helper

#### HitTestHelper.kt
**파일**: `ar/util/HitTestHelper.kt`

```kotlin
package com.sb.arsketch.ar.util

import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.sb.arsketch.domain.model.Point3D
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 히트 테스트 결과
 */
sealed class HitTestResult {
    data class PlaneHit(val point: Point3D, val plane: Plane) : HitTestResult()
    object NoHit : HitTestResult()
}

/**
 * ARCore Hit Test 헬퍼
 */
@Singleton
class HitTestHelper @Inject constructor() {

    /**
     * 화면 좌표로 히트 테스트 수행
     * @param frame 현재 AR 프레임
     * @param x 화면 x 좌표 (픽셀)
     * @param y 화면 y 좌표 (픽셀)
     * @return HitTestResult
     */
    fun performHitTest(frame: Frame, x: Float, y: Float): HitTestResult {
        // 추적 중이 아니면 히트 테스트 불가
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return HitTestResult.NoHit
        }

        val hitResults = frame.hitTest(x, y)

        // 평면 히트 결과 찾기
        for (hit in hitResults) {
            val trackable = hit.trackable

            // Plane인 경우만 처리
            if (trackable is Plane &&
                trackable.isPoseInPolygon(hit.hitPose) &&
                trackable.trackingState == TrackingState.TRACKING
            ) {
                val pose = hit.hitPose
                val point = Point3D(
                    x = pose.tx(),
                    y = pose.ty(),
                    z = pose.tz()
                )
                Timber.v("히트 테스트 성공: $point")
                return HitTestResult.PlaneHit(point, trackable)
            }
        }

        return HitTestResult.NoHit
    }

    /**
     * 여러 후보 중 가장 가까운 히트 결과 반환
     */
    fun findClosestHit(frame: Frame, x: Float, y: Float): HitResult? {
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return null
        }

        return frame.hitTest(x, y)
            .filter { hit ->
                val trackable = hit.trackable
                trackable is Plane &&
                        trackable.isPoseInPolygon(hit.hitPose) &&
                        trackable.trackingState == TrackingState.TRACKING
            }
            .minByOrNull { it.distance }
    }
}
```

---

### Task 4.3: Air Drawing Projector

#### AirDrawingProjector.kt
**파일**: `ar/util/AirDrawingProjector.kt`

```kotlin
package com.sb.arsketch.ar.util

import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.sb.arsketch.domain.model.Point3D
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Air Drawing 모드용 좌표 투영기
 * 화면 터치 좌표를 카메라 전방 고정 깊이의 월드 좌표로 변환
 */
@Singleton
class AirDrawingProjector @Inject constructor() {

    companion object {
        // 기본 깊이 (1.5미터)
        const val DEFAULT_DEPTH = 1.5f
    }

    /**
     * 화면 좌표를 월드 좌표로 투영
     * @param frame 현재 AR 프레임
     * @param screenX 화면 x 좌표 (0.0 ~ 1.0 정규화)
     * @param screenY 화면 y 좌표 (0.0 ~ 1.0 정규화)
     * @param depth 카메라로부터의 깊이 (미터)
     * @return 월드 좌표 Point3D, 또는 null (추적 불가 시)
     */
    fun projectToWorld(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        depth: Float = DEFAULT_DEPTH
    ): Point3D? {
        val camera = frame.camera

        if (camera.trackingState != TrackingState.TRACKING) {
            return null
        }

        // 카메라 포즈 가져오기
        val cameraPose = camera.pose

        // 카메라 좌표계에서의 방향 계산
        // 화면 중앙이 (0, 0), 좌상단이 (-0.5, -0.5)
        val normalizedX = screenX - 0.5f
        val normalizedY = screenY - 0.5f

        // View Matrix와 Projection Matrix 사용하여 ray 계산
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

        // 간단한 구현: 카메라 전방 방향으로 depth만큼 이동
        // 더 정확한 구현은 inverse projection 필요

        // 카메라 전방 벡터 (Z축 방향)
        val forward = floatArrayOf(
            -viewMatrix[2],  // Forward X
            -viewMatrix[6],  // Forward Y
            -viewMatrix[10]  // Forward Z
        )

        // 카메라 우측 벡터 (X축 방향)
        val right = floatArrayOf(
            viewMatrix[0],
            viewMatrix[4],
            viewMatrix[8]
        )

        // 카메라 상단 벡터 (Y축 방향)
        val up = floatArrayOf(
            viewMatrix[1],
            viewMatrix[5],
            viewMatrix[9]
        )

        // 화면 좌표를 월드 좌표로 변환
        // FOV 기반 offset 계산 (대략적인 값)
        val horizontalOffset = normalizedX * depth * 1.2f  // 약 60도 FOV 가정
        val verticalOffset = -normalizedY * depth * 1.2f

        val worldX = cameraPose.tx() +
                forward[0] * depth +
                right[0] * horizontalOffset +
                up[0] * verticalOffset

        val worldY = cameraPose.ty() +
                forward[1] * depth +
                right[1] * horizontalOffset +
                up[1] * verticalOffset

        val worldZ = cameraPose.tz() +
                forward[2] * depth +
                right[2] * horizontalOffset +
                up[2] * verticalOffset

        return Point3D(worldX, worldY, worldZ)
    }
}
```

---

### Task 4.4: OpenGL Shader Utilities

#### ShaderUtil.kt
**파일**: `ar/renderer/ShaderUtil.kt`

```kotlin
package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * OpenGL 셰이더 유틸리티
 */
object ShaderUtil {

    /**
     * 셰이더 프로그램 로드 및 컴파일
     */
    fun loadGLShader(
        context: Context,
        type: Int,
        filename: String
    ): Int {
        val code = readShaderFile(context, filename)
        return compileShader(type, code)
    }

    /**
     * 셰이더 프로그램 생성
     */
    fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES30.glCreateProgram()
        checkGLError("glCreateProgram")

        GLES30.glAttachShader(program, vertexShader)
        checkGLError("glAttachShader vertex")

        GLES30.glAttachShader(program, fragmentShader)
        checkGLError("glAttachShader fragment")

        GLES30.glLinkProgram(program)
        checkGLError("glLinkProgram")

        // 링크 상태 확인
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("프로그램 링크 실패: $error")
        }

        return program
    }

    private fun readShaderFile(context: Context, filename: String): String {
        return context.assets.open("shaders/$filename").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        checkGLError("glCreateShader")

        GLES30.glShaderSource(shader, code)
        checkGLError("glShaderSource")

        GLES30.glCompileShader(shader)
        checkGLError("glCompileShader")

        // 컴파일 상태 확인
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES30.GL_TRUE) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("셰이더 컴파일 실패: $error")
        }

        return shader
    }

    fun checkGLError(operation: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            Timber.e("OpenGL 오류 ($operation): $error")
            throw RuntimeException("OpenGL 오류: $operation, code=$error")
        }
    }
}
```

---

### Task 4.5: Background Renderer

#### BackgroundRenderer.kt
**파일**: `ar/renderer/BackgroundRenderer.kt`

```kotlin
package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * AR 카메라 배경 렌더러
 * ARCore 카메라 이미지를 화면 배경으로 렌더링
 */
class BackgroundRenderer {

    companion object {
        private const val COORDS_PER_VERTEX = 2

        // NDC 좌표 (화면 전체)
        private val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f,  // 좌하단
            +1.0f, -1.0f,  // 우하단
            -1.0f, +1.0f,  // 좌상단
            +1.0f, +1.0f   // 우상단
        )

        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec2 a_Position;
            layout(location = 1) in vec2 a_TexCoord;
            out vec2 v_TexCoord;
            void main() {
                gl_Position = vec4(a_Position, 0.0, 1.0);
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(u_Texture, v_TexCoord);
            }
        """
    }

    private var program = 0
    private var textureId = 0
    private var quadPositionBuffer: FloatBuffer
    private var quadTexCoordBuffer: FloatBuffer

    init {
        // 위치 버퍼 생성
        quadPositionBuffer = ByteBuffer
            .allocateDirect(QUAD_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_COORDS)
                position(0)
            }

        // 텍스처 좌표 버퍼 (프레임마다 업데이트)
        quadTexCoordBuffer = ByteBuffer
            .allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    /**
     * 렌더러 초기화 (GL 컨텍스트에서 호출)
     */
    fun initialize(context: Context) {
        // 셰이더 프로그램 생성
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = ShaderUtil.createProgram(vertexShader, fragmentShader)

        // 텍스처 생성
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MIN_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_MAG_FILTER,
            GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_S,
            GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES30.GL_TEXTURE_WRAP_T,
            GLES30.GL_CLAMP_TO_EDGE
        )
    }

    fun getTextureId(): Int = textureId

    /**
     * 배경 렌더링
     */
    fun draw(frame: Frame) {
        // 뒷면 컬링 비활성화
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        // 텍스처 좌표 업데이트
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadPositionBuffer,
            Coordinates2d.TEXTURE_NORMALIZED,
            quadTexCoordBuffer
        )

        // 셰이더 프로그램 사용
        GLES30.glUseProgram(program)

        // 텍스처 바인딩
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // 위치 어트리뷰트
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, quadPositionBuffer)

        // 텍스처 좌표 어트리뷰트
        GLES30.glEnableVertexAttribArray(1)
        quadTexCoordBuffer.position(0)
        GLES30.glVertexAttribPointer(1, COORDS_PER_VERTEX, GLES30.GL_FLOAT, false, 0, quadTexCoordBuffer)

        // 그리기
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // 정리
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        return shader
    }

    fun release() {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }
}
```

---

### Task 4.6: Line Strip Mesh

#### LineStripMesh.kt
**파일**: `ar/geometry/LineStripMesh.kt`

```kotlin
package com.sb.arsketch.ar.geometry

import android.opengl.GLES30
import com.sb.arsketch.domain.model.Point3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Line Strip 메쉬
 * 연속된 점들을 Line Strip으로 렌더링
 */
class LineStripMesh {

    companion object {
        private const val COORDS_PER_VERTEX = 3  // x, y, z
        private const val BYTES_PER_FLOAT = 4
    }

    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private var vboId = 0
    private var isDirty = true

    /**
     * 점 리스트로 메쉬 업데이트
     */
    fun updatePoints(points: List<Point3D>) {
        if (points.size < 2) {
            vertexCount = 0
            return
        }

        val vertexData = FloatArray(points.size * COORDS_PER_VERTEX)
        points.forEachIndexed { index, point ->
            val offset = index * COORDS_PER_VERTEX
            vertexData[offset] = point.x
            vertexData[offset + 1] = point.y
            vertexData[offset + 2] = point.z
        }

        vertexBuffer = ByteBuffer
            .allocateDirect(vertexData.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }

        vertexCount = points.size
        isDirty = true
    }

    /**
     * VBO 업데이트 (GL 컨텍스트에서 호출)
     */
    fun uploadToGPU() {
        if (!isDirty || vertexBuffer == null) return

        if (vboId == 0) {
            val vbos = IntArray(1)
            GLES30.glGenBuffers(1, vbos, 0)
            vboId = vbos[0]
        }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexCount * COORDS_PER_VERTEX * BYTES_PER_FLOAT,
            vertexBuffer,
            GLES30.GL_DYNAMIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        isDirty = false
    }

    /**
     * 메쉬 렌더링
     */
    fun draw() {
        if (vertexCount < 2 || vboId == 0) return

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0,
            COORDS_PER_VERTEX,
            GLES30.GL_FLOAT,
            false,
            0,
            0
        )

        GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun getVertexCount(): Int = vertexCount

    fun release() {
        if (vboId != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vboId), 0)
            vboId = 0
        }
        vertexBuffer = null
        vertexCount = 0
    }
}
```

---

### Task 4.7: Stroke Renderer

#### StrokeRenderer.kt
**파일**: `ar/renderer/StrokeRenderer.kt`

```kotlin
package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import com.sb.arsketch.ar.geometry.LineStripMesh
import com.sb.arsketch.domain.model.Stroke
import java.util.concurrent.ConcurrentHashMap

/**
 * 스트로크 렌더러
 * 모든 스트로크를 OpenGL로 렌더링
 */
class StrokeRenderer {

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 a_Position;
            uniform mat4 u_ModelViewProjection;
            void main() {
                gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            uniform vec4 u_Color;
            out vec4 fragColor;
            void main() {
                fragColor = u_Color;
            }
        """
    }

    private var program = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    // 스트로크 ID → 메쉬 매핑
    private val strokeMeshes = ConcurrentHashMap<String, LineStripMesh>()

    // 현재 그리는 중인 스트로크 메쉬
    private var currentStrokeMesh: LineStripMesh? = null

    /**
     * 렌더러 초기화
     */
    fun initialize(context: Context) {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = ShaderUtil.createProgram(vertexShader, fragmentShader)

        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "u_ModelViewProjection")
        colorHandle = GLES30.glGetUniformLocation(program, "u_Color")
    }

    /**
     * 스트로크 목록 업데이트
     */
    fun updateStrokes(strokes: List<Stroke>, currentStroke: Stroke?) {
        // 완료된 스트로크 메쉬 업데이트
        val currentIds = strokes.map { it.id }.toSet()

        // 삭제된 스트로크 정리
        strokeMeshes.keys.filter { it !in currentIds }.forEach { id ->
            strokeMeshes.remove(id)?.release()
        }

        // 새로운/업데이트된 스트로크 처리
        strokes.forEach { stroke ->
            val mesh = strokeMeshes.getOrPut(stroke.id) { LineStripMesh() }
            mesh.updatePoints(stroke.points)
        }

        // 현재 그리는 중인 스트로크
        if (currentStroke != null && currentStroke.points.size >= 2) {
            if (currentStrokeMesh == null) {
                currentStrokeMesh = LineStripMesh()
            }
            currentStrokeMesh?.updatePoints(currentStroke.points)
        } else {
            currentStrokeMesh?.release()
            currentStrokeMesh = null
        }
    }

    /**
     * 모든 스트로크 렌더링
     */
    fun draw(
        strokes: List<Stroke>,
        currentStroke: Stroke?,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (program == 0) return

        GLES30.glUseProgram(program)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        val mvpMatrix = FloatArray(16)

        // 완료된 스트로크 렌더링
        strokes.forEach { stroke ->
            strokeMeshes[stroke.id]?.let { mesh ->
                mesh.uploadToGPU()
                drawStroke(mesh, stroke, viewMatrix, projectionMatrix, mvpMatrix)
            }
        }

        // 현재 그리는 중인 스트로크 렌더링
        currentStroke?.let { stroke ->
            currentStrokeMesh?.let { mesh ->
                mesh.uploadToGPU()
                drawStroke(mesh, stroke, viewMatrix, projectionMatrix, mvpMatrix)
            }
        }
    }

    private fun drawStroke(
        mesh: LineStripMesh,
        stroke: Stroke,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        mvpMatrix: FloatArray
    ) {
        if (mesh.getVertexCount() < 2) return

        // MVP 행렬 계산 (Model = Identity)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 색상 설정
        val color = floatArrayOf(
            ((stroke.color shr 16) and 0xFF) / 255f,  // R
            ((stroke.color shr 8) and 0xFF) / 255f,   // G
            (stroke.color and 0xFF) / 255f,           // B
            ((stroke.color shr 24) and 0xFF) / 255f   // A
        )
        GLES30.glUniform4fv(colorHandle, 1, color, 0)

        // 선 두께 설정 (OpenGL ES에서는 제한적)
        GLES30.glLineWidth(stroke.thickness * 500f)  // 미터 → 픽셀 대략 변환

        mesh.draw()
    }

    /**
     * 모든 스트로크 삭제
     */
    fun clearAll() {
        strokeMeshes.values.forEach { it.release() }
        strokeMeshes.clear()
        currentStrokeMesh?.release()
        currentStrokeMesh = null
    }

    fun release() {
        clearAll()
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }

    private fun compileShader(type: Int, code: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, code)
        GLES30.glCompileShader(shader)
        return shader
    }
}
```

---

### Task 4.8: Main AR Renderer

#### ARRenderer.kt
**파일**: `ar/renderer/ARRenderer.kt`

```kotlin
package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.domain.model.Stroke
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 메인 AR 렌더러
 * GLSurfaceView.Renderer 구현
 */
class ARRenderer(
    private val context: Context,
    private val arSessionManager: ARSessionManager
) : GLSurfaceView.Renderer {

    private val backgroundRenderer = BackgroundRenderer()
    private val strokeRenderer = StrokeRenderer()

    private var viewportWidth = 0
    private var viewportHeight = 0

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    // 렌더링할 스트로크 (외부에서 업데이트)
    @Volatile
    private var strokes: List<Stroke> = emptyList()

    @Volatile
    private var currentStroke: Stroke? = null

    // 프레임 콜백
    var onFrameUpdate: ((Frame) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("OpenGL Surface 생성")

        // 배경색 설정
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // 렌더러 초기화
        backgroundRenderer.initialize(context)
        strokeRenderer.initialize(context)

        // ARCore 세션에 텍스처 ID 전달
        arSessionManager.getSession()?.setCameraTextureName(backgroundRenderer.getTextureId())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("OpenGL Surface 크기 변경: ${width}x${height}")

        viewportWidth = width
        viewportHeight = height
        GLES30.glViewport(0, 0, width, height)

        // ARCore 디스플레이 지오메트리 업데이트
        arSessionManager.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 화면 클리어
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // ARCore 프레임 업데이트
        val frame = arSessionManager.update() ?: return

        // 배경 렌더링
        backgroundRenderer.draw(frame)

        // 카메라 추적 중인 경우에만 스트로크 렌더링
        val camera = frame.camera
        if (camera.trackingState == com.google.ar.core.TrackingState.TRACKING) {
            // View/Projection 행렬 가져오기
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            // 스트로크 렌더링
            strokeRenderer.updateStrokes(strokes, currentStroke)
            strokeRenderer.draw(strokes, currentStroke, viewMatrix, projectionMatrix)
        }

        // 프레임 콜백 호출
        onFrameUpdate?.invoke(frame)
    }

    /**
     * 렌더링할 스트로크 업데이트
     */
    fun updateStrokes(completedStrokes: List<Stroke>, activeStroke: Stroke?) {
        this.strokes = completedStrokes
        this.currentStroke = activeStroke
    }

    /**
     * 모든 스트로크 클리어
     */
    fun clearStrokes() {
        strokes = emptyList()
        currentStroke = null
        strokeRenderer.clearAll()
    }

    /**
     * 리소스 해제
     */
    fun release() {
        backgroundRenderer.release()
        strokeRenderer.release()
    }
}
```

---

## 완료 조건

- [ ] ARSessionManager 구현 완료
- [ ] HitTestHelper 구현 완료
- [ ] AirDrawingProjector 구현 완료
- [ ] BackgroundRenderer 구현 완료
- [ ] LineStripMesh 구현 완료
- [ ] StrokeRenderer 구현 완료
- [ ] ARRenderer 구현 완료
- [ ] `./gradlew assembleDebug` 빌드 성공
- [ ] AR 카메라 배경이 정상 렌더링되는지 확인

---

## 테스트 계획

### 수동 테스트
1. AR 세션 초기화 및 카메라 배경 렌더링
2. 평면 감지 동작 확인
3. 히트 테스트로 월드 좌표 획득 확인
4. 간단한 스트로크 렌더링 테스트

---

## 다음 단계

→ [Epic 5: Presentation Layer](epic-05-presentation-layer.md)
