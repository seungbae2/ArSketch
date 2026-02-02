package com.sb.arsketch.ar.renderer

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.sb.arsketch.ar.core.AnchorManager
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.streaming.AdaptiveQualityController
import com.sb.arsketch.streaming.Resolution
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARRenderer(
    private val context: Context,
    private val arSessionManager: ARSessionManager,
    private val anchorManager: AnchorManager
) : GLSurfaceView.Renderer {

    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val strokeRenderer = StrokeRenderer()

    // FBO 오프스크린 렌더링
    private var compositeFbo: CompositeFramebuffer? = null

    // PBO 비동기 픽셀 읽기
    private var asyncPixelReader: AsyncPixelReader? = null

    // 적응형 품질 컨트롤러
    private val qualityController = AdaptiveQualityController(
        targetFps = 30,
        initialResolution = Resolution.FHD_1080  // 1080p로 시작
    )

    // 스트리밍 활성화 여부
    @Volatile
    var isStreamingEnabled: Boolean = false
        private set

    // PBO 사용 여부 (성능 최적화)
    private var usePboAsync = true

    // FBO 해상도 (기기 방향에 따라 조정)
    private var fboWidth = 1080
    private var fboHeight = 1920  // 기본값: 세로 모드
    private var isPortraitMode = true

    // 스트리밍 프레임 콜백
    var onFrameComposited: ((Bitmap) -> Unit)? = null

    // 해상도 변경 콜백
    var onResolutionChanged: ((Resolution) -> Unit)? = null

    // 평면 시각화 활성화 여부
    @Volatile
    var showPlanes: Boolean = true

    private var viewportWidth = 0
    private var viewportHeight = 0

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    // FBO용 별도 행렬 (해상도가 다를 수 있음)
    private val fboProjectionMatrix = FloatArray(16)

    @Volatile
    private var strokes: List<Stroke> = emptyList()

    @Volatile
    private var currentStroke: Stroke? = null

    @Volatile
    private var isTextureSet = false

    // 마지막 유효 프레임 저장 (FBO 렌더링용)
    private var lastFrame: Frame? = null

    var onFrameUpdate: ((Frame) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("OpenGL Surface 생성")

        isTextureSet = false

        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        backgroundRenderer.initialize(context)
        planeRenderer.initialize(context)
        strokeRenderer.initialize(context)

        // FBO 초기화
        initializeCompositeFbo()

        arSessionManager.getSession()?.let { session ->
            session.setCameraTextureName(backgroundRenderer.getTextureId())
            isTextureSet = true
            Timber.d("카메라 텍스처 설정 완료: ${backgroundRenderer.getTextureId()}")
        }
    }

    private fun initializeCompositeFbo() {
        compositeFbo?.destroy()
        compositeFbo = CompositeFramebuffer(fboWidth, fboHeight).apply {
            initialize()
        }

        // PBO 비동기 읽기 초기화
        if (usePboAsync) {
            asyncPixelReader?.destroy()
            asyncPixelReader = AsyncPixelReader(fboWidth, fboHeight).apply {
                initialize()
            }
        }

        // 적응형 품질 컨트롤러 콜백 설정
        qualityController.onResolutionChanged = { resolution ->
            // 현재 화면 방향에 맞춰 해상도 설정
            val (newWidth, newHeight) = if (isPortraitMode) {
                resolution.toPortrait()
            } else {
                resolution.toLandscape()
            }
            Timber.i("해상도 변경 요청: ${newWidth}x${newHeight} (${if (isPortraitMode) "portrait" else "landscape"})")
            fboWidth = newWidth
            fboHeight = newHeight
            // GL 컨텍스트에서 재초기화 필요 (다음 프레임에서 처리)
            onResolutionChanged?.invoke(resolution)
        }

        Timber.d("CompositeFramebuffer initialized: ${fboWidth}x${fboHeight}, PBO: $usePboAsync")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("OpenGL Surface 크기 변경: ${width}x${height}")

        viewportWidth = width
        viewportHeight = height
        GLES30.glViewport(0, 0, width, height)

        // 화면 방향 감지 및 FBO 해상도 조정
        val newPortraitMode = height > width
        if (newPortraitMode != isPortraitMode) {
            isPortraitMode = newPortraitMode
            updateFboResolutionForOrientation()
        }

        arSessionManager.setDisplayGeometry(0, width, height)
    }

    /**
     * 화면 방향에 맞춰 FBO 해상도 업데이트
     */
    private fun updateFboResolutionForOrientation() {
        val resolution = qualityController.currentResolution
        val (newWidth, newHeight) = if (isPortraitMode) {
            resolution.toPortrait()
        } else {
            resolution.toLandscape()
        }

        if (newWidth != fboWidth || newHeight != fboHeight) {
            fboWidth = newWidth
            fboHeight = newHeight
            Timber.d("FBO 해상도 변경 (${if (isPortraitMode) "세로" else "가로"}): ${fboWidth}x${fboHeight}")

            // 스트리밍 중이면 FBO 재초기화
            if (isStreamingEnabled) {
                initializeCompositeFbo()
            }
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 텍스처가 설정되지 않았으면 스킵
        if (!isTextureSet) {
            return
        }

        val frame = arSessionManager.update() ?: return
        lastFrame = frame

        // 1. 화면에 렌더링 (사용자가 보는 화면)
        renderScene(frame, viewportWidth, viewportHeight, projectionMatrix)

        // 2. 스트리밍용 FBO 렌더링
        if (isStreamingEnabled) {
            renderToFbo(frame)
        }

        onFrameUpdate?.invoke(frame)
    }

    /**
     * 씬 렌더링 (화면 또는 FBO에 공통 사용)
     */
    private fun renderScene(
        frame: Frame,
        width: Int,
        height: Int,
        projMatrix: FloatArray
    ) {
        backgroundRenderer.draw(frame)

        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

            // 감지된 평면 렌더링
            if (showPlanes) {
                val planes = arSessionManager.getSession()
                    ?.getAllTrackables(Plane::class.java)
                    ?: emptyList()
                planeRenderer.draw(planes, viewMatrix, projMatrix)
            }

            // 스트로크 렌더링
            strokeRenderer.updateStrokes(strokes, currentStroke)
            strokeRenderer.draw(
                strokes = strokes,
                currentStroke = currentStroke,
                viewMatrix = viewMatrix,
                projectionMatrix = projMatrix,
                anchorModelMatrixProvider = { anchorId, matrix, offset ->
                    anchorManager.getAnchorModelMatrix(anchorId, matrix, offset)
                }
            )
        }
    }

    /**
     * FBO에 렌더링하고 Bitmap으로 추출
     */
    private fun renderToFbo(frame: Frame) {
        val fbo = compositeFbo ?: return
        if (!fbo.isReady()) return

        // FBO 바인딩
        fbo.bind()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // FBO 해상도에 맞는 프로젝션 행렬 계산
        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            // FBO 해상도의 종횡비로 프로젝션 행렬 재계산
            camera.getProjectionMatrix(fboProjectionMatrix, 0, 0.1f, 100f)
        }

        // FBO에 씬 렌더링
        renderScene(frame, fboWidth, fboHeight, fboProjectionMatrix)

        // Bitmap 추출 (PBO 또는 동기 방식)
        val bitmap = if (usePboAsync) {
            asyncPixelReader?.let { reader ->
                // 비동기 읽기 시작 (현재 프레임)
                reader.readPixelsAsync()
                // 이전 프레임 결과 가져오기 (1프레임 지연)
                reader.getLastFrameBitmap()
            }
        } else {
            // 동기 방식 폴백
            fbo.readToBitmap()
        }

        // 콜백으로 전달
        bitmap?.let { onFrameComposited?.invoke(it) }

        // 품질 컨트롤러에 프레임 완료 알림
        qualityController.onFrameRendered()

        // 원래 화면으로 복원
        fbo.unbind(viewportWidth, viewportHeight)
    }

    fun updateStrokes(completedStrokes: List<Stroke>, activeStroke: Stroke?) {
        this.strokes = completedStrokes
        this.currentStroke = activeStroke
    }

    fun clearStrokes() {
        strokes = emptyList()
        currentStroke = null
        strokeRenderer.clearAll()
    }

    /**
     * 스트리밍 활성화/비활성화
     *
     * @param enabled 스트리밍 활성화 여부
     * @param resolution 해상도 (기본 1080p)
     */
    fun setStreamingEnabled(enabled: Boolean, resolution: Resolution = Resolution.FHD_1080) {
        if (enabled) {
            // 현재 화면 방향에 맞춰 해상도 설정
            val (newWidth, newHeight) = if (isPortraitMode) {
                resolution.toPortrait()
            } else {
                resolution.toLandscape()
            }

            if (newWidth != fboWidth || newHeight != fboHeight) {
                fboWidth = newWidth
                fboHeight = newHeight
                // 해상도 변경 시 FBO 재생성
                initializeCompositeFbo()
            }
            qualityController.forceResolution(resolution)
            qualityController.reset()
        }
        isStreamingEnabled = enabled
        Timber.d("Streaming ${if (enabled) "enabled" else "disabled"}: ${fboWidth}x${fboHeight} (${if (isPortraitMode) "portrait" else "landscape"})")
    }

    /**
     * 스트리밍 활성화/비활성화 (width/height 직접 지정)
     *
     * @param enabled 스트리밍 활성화 여부
     * @param width FBO 너비
     * @param height FBO 높이
     */
    fun setStreamingEnabled(enabled: Boolean, width: Int, height: Int) {
        val resolution = when {
            width >= 1920 -> Resolution.FHD_1080
            width >= 1280 -> Resolution.HD_720
            else -> Resolution.SD_480
        }
        setStreamingEnabled(enabled, resolution)
    }

    /**
     * 현재 FBO 해상도 반환
     */
    fun getStreamingResolution(): Pair<Int, Int> = fboWidth to fboHeight

    /**
     * 현재 스트리밍 해상도 (Resolution enum)
     */
    fun getCurrentResolution(): Resolution = qualityController.currentResolution

    /**
     * 측정된 FPS 반환
     */
    fun getMeasuredFps(): Float = qualityController.getMeasuredFps()

    /**
     * PBO 사용 여부 설정
     */
    fun setUsePboAsync(use: Boolean) {
        if (usePboAsync != use) {
            usePboAsync = use
            if (isStreamingEnabled) {
                initializeCompositeFbo()
            }
            Timber.d("PBO async mode: $usePboAsync")
        }
    }

    fun release() {
        isTextureSet = false
        isStreamingEnabled = false
        lastFrame = null

        asyncPixelReader?.destroy()
        asyncPixelReader = null

        compositeFbo?.destroy()
        compositeFbo = null

        backgroundRenderer.release()
        planeRenderer.release()
        strokeRenderer.release()
    }
}
