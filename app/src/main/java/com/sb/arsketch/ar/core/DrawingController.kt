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
