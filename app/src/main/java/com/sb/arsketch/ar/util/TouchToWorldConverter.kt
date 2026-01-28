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
