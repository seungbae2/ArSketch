package com.sb.arsketch.ar.core

import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ARCore Anchor 관리자
 * 스트로크를 실제 세계에 고정하기 위한 Anchor 생성 및 관리
 */
@Singleton
class AnchorManager @Inject constructor() {

    private val anchors = ConcurrentHashMap<String, Anchor>()

    /**
     * HitResult로부터 Anchor 생성 (Surface 모드)
     * @return 생성된 Anchor의 ID
     */
    fun createAnchor(hitResult: HitResult): String? {
        return try {
            val anchor = hitResult.createAnchor()
            val anchorId = UUID.randomUUID().toString()
            anchors[anchorId] = anchor
            Timber.d("Anchor 생성 (HitResult): $anchorId")
            anchorId
        } catch (e: Exception) {
            Timber.e(e, "Anchor 생성 실패")
            null
        }
    }

    /**
     * Pose로부터 Anchor 생성 (Air 모드)
     * @param session AR 세션
     * @param pose Anchor를 생성할 위치의 Pose
     * @return 생성된 Anchor의 ID
     */
    fun createAnchorFromPose(session: Session, pose: Pose): String? {
        return try {
            val anchor = session.createAnchor(pose)
            val anchorId = UUID.randomUUID().toString()
            anchors[anchorId] = anchor
            Timber.d("Anchor 생성 (Pose): $anchorId at ${pose.tx()}, ${pose.ty()}, ${pose.tz()}")
            anchorId
        } catch (e: Exception) {
            Timber.e(e, "Anchor 생성 실패 (Pose)")
            null
        }
    }

    /**
     * Anchor의 현재 Pose 가져오기
     * @return Pose 또는 null (Anchor가 없거나 트래킹 실패 시)
     */
    fun getAnchorPose(anchorId: String): Pose? {
        val anchor = anchors[anchorId] ?: return null

        return if (anchor.trackingState == TrackingState.TRACKING) {
            anchor.pose
        } else {
            Timber.w("Anchor 트래킹 안됨: $anchorId, 상태: ${anchor.trackingState}")
            null
        }
    }

    /**
     * Anchor가 유효한지 (트래킹 중인지) 확인
     */
    fun isAnchorValid(anchorId: String): Boolean {
        val anchor = anchors[anchorId] ?: return false
        return anchor.trackingState == TrackingState.TRACKING
    }

    /**
     * Anchor의 model matrix 가져오기
     */
    fun getAnchorModelMatrix(anchorId: String, matrix: FloatArray, offset: Int = 0): Boolean {
        val pose = getAnchorPose(anchorId) ?: return false
        pose.toMatrix(matrix, offset)
        return true
    }

    /**
     * 특정 Anchor 해제
     */
    fun releaseAnchor(anchorId: String) {
        anchors.remove(anchorId)?.let { anchor ->
            anchor.detach()
            Timber.d("Anchor 해제: $anchorId")
        }
    }

    /**
     * 여러 Anchor 해제
     */
    fun releaseAnchors(anchorIds: Collection<String>) {
        anchorIds.forEach { releaseAnchor(it) }
    }

    /**
     * 모든 Anchor 해제
     */
    fun releaseAll() {
        anchors.forEach { (id, anchor) ->
            anchor.detach()
            Timber.d("Anchor 해제: $id")
        }
        anchors.clear()
    }

    /**
     * 현재 관리 중인 Anchor 개수
     */
    fun getAnchorCount(): Int = anchors.size
}
