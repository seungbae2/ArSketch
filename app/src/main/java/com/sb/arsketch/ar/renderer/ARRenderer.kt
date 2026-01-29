package com.sb.arsketch.ar.renderer

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.domain.model.Stroke
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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

    @Volatile
    private var strokes: List<Stroke> = emptyList()

    @Volatile
    private var currentStroke: Stroke? = null

    @Volatile
    private var isTextureSet = false

    var onFrameUpdate: ((Frame) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("OpenGL Surface 생성")

        isTextureSet = false

        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        backgroundRenderer.initialize(context)
        strokeRenderer.initialize(context)

        arSessionManager.getSession()?.let { session ->
            session.setCameraTextureName(backgroundRenderer.getTextureId())
            isTextureSet = true
            Timber.d("카메라 텍스처 설정 완료: ${backgroundRenderer.getTextureId()}")
        }
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

        backgroundRenderer.draw(frame)

        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            camera.getViewMatrix(viewMatrix, 0)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

            strokeRenderer.updateStrokes(strokes, currentStroke)
            strokeRenderer.draw(strokes, currentStroke, viewMatrix, projectionMatrix)
        }

        onFrameUpdate?.invoke(frame)
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

    fun release() {
        isTextureSet = false
        backgroundRenderer.release()
        strokeRenderer.release()
    }
}
