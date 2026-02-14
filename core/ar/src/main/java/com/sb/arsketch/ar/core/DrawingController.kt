package com.sb.arsketch.ar.core

import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.sb.arsketch.ar.util.ConversionResult
import com.sb.arsketch.ar.util.TouchToWorldConverter
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 리모트 브러시 정보 (웹 뷰어에서 수신)
 */
data class RemoteBrushInfo(
    val color: Int,
    val thickness: Float,
    val mode: DrawingMode,
    val senderId: String
)

/**
 * 스트로크 시작 정보 (Anchor 포함)
 */
data class StrokeStartInfo(
    val localPoint: Point3D,  // Anchor 기준 로컬 좌표
    val anchorId: String?,    // Anchor ID (Surface/Air 모드 공통)
    val remoteBrush: RemoteBrushInfo? = null  // null = 로컬 터치
)

/**
 * 드로잉 컨트롤러
 * 터치 이벤트를 월드 좌표로 변환하고 콜백 전달
 */
@Singleton
class DrawingController @Inject constructor(
    private val touchToWorldConverter: TouchToWorldConverter,
    private val anchorManager: AnchorManager,
    private val arSessionManager: ARSessionManager
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

    // 콜백 (Anchor 정보 포함)
    var onStrokeStartWithAnchor: ((StrokeStartInfo) -> Unit)? = null
    var onStrokePoint: ((Point3D) -> Unit)? = null
    var onStrokeEnd: (() -> Unit)? = null

    // 기존 콜백 (호환성 유지)
    @Deprecated("Use onStrokeStartWithAnchor instead")
    var onStrokeStart: ((Point3D) -> Unit)? = null

    // 드로잉 상태
    @Volatile
    private var isDrawing = false

    // 현재 스트로크의 모드 (down~up 동안 유지)
    private var currentStrokeMode: DrawingMode? = null

    // 리모트 브러시 정보 (콜백 전달용)
    private var pendingRemoteBrush: RemoteBrushInfo? = null

    // 현재 스트로크의 Anchor 정보
    private var currentAnchorId: String? = null
    private var currentAnchorPose: Pose? = null

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
        if (isDrawing) return  // 동시 스트로크 방지
        val frame = currentFrame ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        currentStrokeMode = drawingMode

        val result = touchToWorldConverter.convertWithDetails(
            frame = frame,
            screenX = screenX,
            screenY = screenY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            mode = drawingMode
        )

        when (result) {
            is ConversionResult.SurfaceHit -> {
                // Surface 모드: Anchor 생성 후 로컬 좌표로 변환
                val anchorId = anchorManager.createAnchor(result.hitResult)
                if (anchorId != null) {
                    val anchorPose = anchorManager.getAnchorPose(anchorId)
                    if (anchorPose != null) {
                        currentAnchorId = anchorId
                        currentAnchorPose = anchorPose

                        // 첫 번째 점은 Anchor 위치이므로 로컬 좌표는 원점
                        val localPoint = Point3D.ZERO

                        isDrawing = true
                        onStrokeStartWithAnchor?.invoke(
                            StrokeStartInfo(localPoint, anchorId, pendingRemoteBrush)
                        )
                        onStrokeStart?.invoke(localPoint)
                        Timber.d("드로잉 시작 (Surface): anchorId=$anchorId, localPoint=$localPoint")
                    } else {
                        anchorManager.releaseAnchor(anchorId)
                    }
                }
            }
            is ConversionResult.AirPoint -> {
                // Air 모드: Pose로부터 Anchor 생성
                val session = arSessionManager.getSession()
                if (session != null) {
                    val anchorId = anchorManager.createAnchorFromPose(session, result.pose)
                    if (anchorId != null) {
                        val anchorPose = anchorManager.getAnchorPose(anchorId)
                        if (anchorPose != null) {
                            currentAnchorId = anchorId
                            currentAnchorPose = anchorPose

                            // 첫 번째 점은 Anchor 위치이므로 로컬 좌표는 원점
                            val localPoint = Point3D.ZERO

                            isDrawing = true
                            onStrokeStartWithAnchor?.invoke(
                                StrokeStartInfo(localPoint, anchorId, pendingRemoteBrush)
                            )
                            onStrokeStart?.invoke(localPoint)
                            Timber.d("드로잉 시작 (Air): anchorId=$anchorId, localPoint=$localPoint")
                        } else {
                            anchorManager.releaseAnchor(anchorId)
                        }
                    }
                } else {
                    Timber.w("AR 세션이 없어 Air 모드 드로잉 불가")
                }
            }
            is ConversionResult.NoHit -> {
                Timber.v("드로잉 시작 실패: 히트 없음")
            }
        }
    }

    /**
     * 터치 이동 처리
     */
    fun onTouchMove(screenX: Float, screenY: Float) {
        if (!isDrawing) return

        val frame = currentFrame ?: return
        val activeMode = currentStrokeMode ?: drawingMode

        val result = touchToWorldConverter.convertWithDetails(
            frame = frame,
            screenX = screenX,
            screenY = screenY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            mode = activeMode
        )

        when (result) {
            is ConversionResult.SurfaceHit -> {
                // Surface 모드: Anchor 기준 로컬 좌표로 변환
                val anchorPose = currentAnchorPose
                if (anchorPose != null) {
                    val localPoint = touchToWorldConverter.worldToLocal(result.worldPoint, anchorPose)
                    onStrokePoint?.invoke(localPoint)
                }
            }
            is ConversionResult.AirPoint -> {
                // Air 모드: Anchor 기준 로컬 좌표로 변환
                val anchorPose = currentAnchorPose
                if (anchorPose != null) {
                    val localPoint = touchToWorldConverter.worldToLocal(result.worldPoint, anchorPose)
                    onStrokePoint?.invoke(localPoint)
                }
            }
            is ConversionResult.NoHit -> {
                // 히트 없음 - 무시
            }
        }
    }

    /**
     * 터치 업 처리
     */
    fun onTouchUp() {
        if (isDrawing) {
            isDrawing = false
            currentStrokeMode = null
            pendingRemoteBrush = null
            currentAnchorId = null
            currentAnchorPose = null
            onStrokeEnd?.invoke()
            Timber.d("드로잉 종료")
        }
    }

    /**
     * 리모트 터치 다운 처리 (웹 뷰어에서 수신)
     * drawingMode를 변경하지 않고 mode를 직접 전달하여 좌표 변환
     */
    fun onRemoteTouchDown(screenX: Float, screenY: Float, remoteBrush: RemoteBrushInfo) {
        if (isDrawing) return  // 동시 드로잉 방지
        val frame = currentFrame ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        pendingRemoteBrush = remoteBrush

        var result = touchToWorldConverter.convertWithDetails(
            frame = frame,
            screenX = screenX,
            screenY = screenY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            mode = remoteBrush.mode
        )

        // SURFACE NoHit → AIR fallback
        if (result is ConversionResult.NoHit && remoteBrush.mode == DrawingMode.SURFACE) {
            result = touchToWorldConverter.convertWithDetails(
                frame = frame,
                screenX = screenX,
                screenY = screenY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                mode = DrawingMode.AIR
            )
        }

        when (result) {
            is ConversionResult.SurfaceHit -> {
                val anchorId = anchorManager.createAnchor(result.hitResult)
                if (anchorId != null) {
                    val anchorPose = anchorManager.getAnchorPose(anchorId)
                    if (anchorPose != null) {
                        currentAnchorId = anchorId
                        currentAnchorPose = anchorPose
                        currentStrokeMode = DrawingMode.SURFACE
                        isDrawing = true
                        onStrokeStartWithAnchor?.invoke(
                            StrokeStartInfo(Point3D.ZERO, anchorId, remoteBrush)
                        )
                        Timber.d("리모트 드로잉 시작 (Surface): anchorId=$anchorId, sender=${remoteBrush.senderId}")
                    } else {
                        anchorManager.releaseAnchor(anchorId)
                        pendingRemoteBrush = null
                    }
                } else {
                    pendingRemoteBrush = null
                }
            }
            is ConversionResult.AirPoint -> {
                val session = arSessionManager.getSession()
                if (session != null) {
                    val anchorId = anchorManager.createAnchorFromPose(session, result.pose)
                    if (anchorId != null) {
                        val anchorPose = anchorManager.getAnchorPose(anchorId)
                        if (anchorPose != null) {
                            currentAnchorId = anchorId
                            currentAnchorPose = anchorPose
                            currentStrokeMode = DrawingMode.AIR
                            isDrawing = true
                            onStrokeStartWithAnchor?.invoke(
                                StrokeStartInfo(Point3D.ZERO, anchorId, remoteBrush)
                            )
                            Timber.d("리모트 드로잉 시작 (Air): anchorId=$anchorId, sender=${remoteBrush.senderId}")
                        } else {
                            anchorManager.releaseAnchor(anchorId)
                            pendingRemoteBrush = null
                        }
                    } else {
                        pendingRemoteBrush = null
                    }
                } else {
                    pendingRemoteBrush = null
                    Timber.w("AR 세션이 없어 리모트 드로잉 불가")
                }
            }
            is ConversionResult.NoHit -> {
                pendingRemoteBrush = null
                Timber.v("리모트 드로잉 시작 실패: 히트 없음")
            }
        }
    }

    /**
     * 현재 드로잉 중인지 확인
     */
    fun isCurrentlyDrawing(): Boolean = isDrawing

    /**
     * 현재 스트로크의 Anchor ID
     */
    fun getCurrentAnchorId(): String? = currentAnchorId

    /**
     * 뷰포트 크기 getter (리모트 터치 좌표 변환용)
     */
    fun getViewportWidth(): Int = viewportWidth
    fun getViewportHeight(): Int = viewportHeight
}
