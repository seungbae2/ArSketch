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

    // 스트리밍 활성화 여부
    @Volatile
    var isStreamingEnabled: Boolean = false
        private set

    // FBO 해상도 (720p 기본)
    private var fboWidth = 1280
    private var fboHeight = 720

    // 스트리밍 프레임 콜백
    var onFrameComposited: ((Bitmap) -> Unit)? = null

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
        Timber.d("CompositeFramebuffer initialized: ${fboWidth}x${fboHeight}")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("OpenGL Surface 크기 변경: ${width}x${height}")

        viewportWidth = width
        viewportHeight = height
        GLES30.glViewport(0, 0, width, height)

        arSessionManager.setDisplayGeometry(0, width, height)
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

        // Bitmap으로 읽어서 콜백
        fbo.readToBitmap()?.let { bitmap ->
            onFrameComposited?.invoke(bitmap)
        }

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
     * @param width FBO 너비 (선택사항, 기본 1280)
     * @param height FBO 높이 (선택사항, 기본 720)
     */
    fun setStreamingEnabled(enabled: Boolean, width: Int = 1280, height: Int = 720) {
        if (enabled && (width != fboWidth || height != fboHeight)) {
            fboWidth = width
            fboHeight = height
            // 해상도 변경 시 FBO 재생성
            initializeCompositeFbo()
        }
        isStreamingEnabled = enabled
        Timber.d("Streaming ${if (enabled) "enabled" else "disabled"}: ${fboWidth}x${fboHeight}")
    }

    /**
     * 현재 FBO 해상도 반환
     */
    fun getStreamingResolution(): Pair<Int, Int> = fboWidth to fboHeight

    fun release() {
        isTextureSet = false
        isStreamingEnabled = false
        lastFrame = null

        compositeFbo?.destroy()
        compositeFbo = null

        backgroundRenderer.release()
        planeRenderer.release()
        strokeRenderer.release()
    }
}
