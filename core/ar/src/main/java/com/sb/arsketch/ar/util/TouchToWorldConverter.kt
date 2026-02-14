package com.sb.arsketch.ar.util

import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Pose
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 터치 좌표 변환 결과
 */
sealed class ConversionResult {
    /**
     * Surface 모드: 평면 히트 + Anchor 생성용 HitResult
     */
    data class SurfaceHit(
        val worldPoint: Point3D,
        val hitResult: HitResult
    ) : ConversionResult()

    /**
     * Air 모드: 월드 좌표 + Anchor 생성용 Pose
     */
    data class AirPoint(
        val worldPoint: Point3D,
        val pose: Pose  // Anchor 생성용
    ) : ConversionResult()

    data object NoHit : ConversionResult()
}

/**
 * 터치 좌표 → 월드 좌표 변환
 */
@Singleton
class TouchToWorldConverter @Inject constructor(
    private val hitTestHelper: HitTestHelper,
    private val airDrawingProjector: AirDrawingProjector
) {

    /**
     * 화면 터치 좌표를 월드 좌표로 변환 (기존 호환용)
     */
    fun convert(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        mode: DrawingMode
    ): Point3D? {
        return when (val result = convertWithDetails(frame, screenX, screenY, viewportWidth, viewportHeight, mode)) {
            is ConversionResult.SurfaceHit -> result.worldPoint
            is ConversionResult.AirPoint -> result.worldPoint
            is ConversionResult.NoHit -> null
        }
    }

    /**
     * 화면 터치 좌표를 변환 (상세 정보 포함)
     */
    fun convertWithDetails(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
        mode: DrawingMode
    ): ConversionResult {
        return when (mode) {
            DrawingMode.SURFACE -> convertSurfaceModeWithDetails(frame, screenX, screenY)
            DrawingMode.AIR -> convertAirModeWithDetails(frame, screenX, screenY, viewportWidth, viewportHeight)
        }
    }

    private fun convertSurfaceModeWithDetails(
        frame: Frame,
        screenX: Float,
        screenY: Float
    ): ConversionResult {
        return when (val result = hitTestHelper.performHitTest(frame, screenX, screenY)) {
            is HitTestResult.PlaneHit -> {
                Timber.v("Surface hit at: ${result.point}")
                ConversionResult.SurfaceHit(result.point, result.hitResult)
            }
            is HitTestResult.NoHit -> {
                Timber.v("No surface hit")
                ConversionResult.NoHit
            }
        }
    }

    private fun convertAirModeWithDetails(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        viewportWidth: Int,
        viewportHeight: Int
    ): ConversionResult {
        val normalizedX = screenX / viewportWidth
        val normalizedY = screenY / viewportHeight

        val result = airDrawingProjector.projectToWorldWithPose(
            frame = frame,
            screenX = normalizedX,
            screenY = normalizedY
        )

        return if (result != null) {
            ConversionResult.AirPoint(result.worldPoint, result.pose)
        } else {
            ConversionResult.NoHit
        }
    }

    /**
     * 월드 좌표를 Anchor 기준 로컬 좌표로 변환
     */
    fun worldToLocal(worldPoint: Point3D, anchorPose: Pose): Point3D {
        val anchorMatrix = FloatArray(16)
        anchorPose.toMatrix(anchorMatrix, 0)

        // Anchor의 역행렬 계산
        val inverseMatrix = FloatArray(16)
        android.opengl.Matrix.invertM(inverseMatrix, 0, anchorMatrix, 0)

        // 월드 좌표를 로컬 좌표로 변환
        val worldVec = floatArrayOf(worldPoint.x, worldPoint.y, worldPoint.z, 1f)
        val localVec = FloatArray(4)
        android.opengl.Matrix.multiplyMV(localVec, 0, inverseMatrix, 0, worldVec, 0)

        return Point3D(localVec[0], localVec[1], localVec[2])
    }
}
