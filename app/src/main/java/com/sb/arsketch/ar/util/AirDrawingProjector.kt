package com.sb.arsketch.ar.util

import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.sb.arsketch.domain.model.Point3D
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Air 모드 프로젝션 결과
 */
data class AirProjectionResult(
    val worldPoint: Point3D,
    val pose: Pose  // Anchor 생성용
)

@Singleton
class AirDrawingProjector @Inject constructor() {

    companion object {
        const val DEFAULT_DEPTH = 1.5f
    }

    /**
     * 화면 좌표를 월드 좌표로 프로젝션 (기존 호환용)
     */
    fun projectToWorld(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        depth: Float = DEFAULT_DEPTH
    ): Point3D? {
        return projectToWorldWithPose(frame, screenX, screenY, depth)?.worldPoint
    }

    /**
     * 화면 좌표를 월드 좌표와 Pose로 프로젝션
     */
    fun projectToWorldWithPose(
        frame: Frame,
        screenX: Float,
        screenY: Float,
        depth: Float = DEFAULT_DEPTH
    ): AirProjectionResult? {
        val camera = frame.camera

        if (camera.trackingState != TrackingState.TRACKING) {
            return null
        }

        val cameraPose = camera.pose

        val normalizedX = screenX - 0.5f
        val normalizedY = screenY - 0.5f

        val viewMatrix = FloatArray(16)
        camera.getViewMatrix(viewMatrix, 0)

        val forward = floatArrayOf(
            -viewMatrix[2],
            -viewMatrix[6],
            -viewMatrix[10]
        )

        val right = floatArrayOf(
            viewMatrix[0],
            viewMatrix[4],
            viewMatrix[8]
        )

        val up = floatArrayOf(
            viewMatrix[1],
            viewMatrix[5],
            viewMatrix[9]
        )

        val horizontalOffset = normalizedX * depth * 1.2f
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

        val worldPoint = Point3D(worldX, worldY, worldZ)

        // Pose 생성 (카메라 방향 유지)
        val pose = Pose(
            floatArrayOf(worldX, worldY, worldZ),
            cameraPose.rotationQuaternion
        )

        return AirProjectionResult(worldPoint, pose)
    }
}
