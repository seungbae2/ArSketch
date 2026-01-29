package com.sb.arsketch.ar.core

import android.annotation.SuppressLint
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
    private val arSessionManager: ARSessionManager,
    private val anchorManager: AnchorManager
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
        arRenderer = ARRenderer(context, arSessionManager, anchorManager)
        setRenderer(arRenderer)

        // 연속 렌더링 모드
        renderMode = RENDERMODE_CONTINUOUSLY

        Timber.d("ARGLSurfaceView 초기화 완료")
    }

    fun getARRenderer(): ARRenderer = arRenderer

    @SuppressLint("ClickableViewAccessibility")
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
