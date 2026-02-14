package com.sb.arsketch.ar.util

import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.sb.arsketch.domain.model.Point3D
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class HitTestResult {
    data class PlaneHit(
        val point: Point3D,
        val plane: Plane,
        val hitResult: HitResult  // Anchor 생성용
    ) : HitTestResult()
    data object NoHit : HitTestResult()
}

@Singleton
class HitTestHelper @Inject constructor() {

    fun performHitTest(frame: Frame, x: Float, y: Float): HitTestResult {
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return HitTestResult.NoHit
        }

        val hitResults = frame.hitTest(x, y)

        for (hit in hitResults) {
            val trackable = hit.trackable

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
                return HitTestResult.PlaneHit(point, trackable, hit)
            }
        }

        return HitTestResult.NoHit
    }

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
