package com.sb.arsketch.streaming

import timber.log.Timber

/**
 * 적응형 스트리밍 품질 컨트롤러.
 *
 * 실시간 FPS를 모니터링하고 디바이스 성능에 맞춰
 * 해상도를 자동으로 조절합니다.
 *
 * @param targetFps 목표 FPS (기본 30)
 * @param initialResolution 초기 해상도 (기본 HD_720)
 */
class AdaptiveQualityController(
    private val targetFps: Int = 30,
    initialResolution: Resolution = Resolution.HD_720
) {
    var currentResolution: Resolution = initialResolution
        private set

    private var frameCount = 0
    private var lastFpsCheckTime = System.currentTimeMillis()
    private var measuredFps = 0f

    // 품질 변경 콜백
    var onResolutionChanged: ((Resolution) -> Unit)? = null

    // 연속 저성능/고성능 카운터 (급격한 변경 방지)
    private var lowPerformanceCount = 0
    private var highPerformanceCount = 0

    // 품질 변경 쿨다운 (초)
    private var lastQualityChangeTime = 0L
    private val qualityChangeCooldown = 5000L  // 5초

    /**
     * 프레임 렌더링 완료 시 호출.
     * FPS를 측정하고 필요 시 품질을 조절합니다.
     */
    fun onFrameRendered() {
        frameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsCheckTime

        if (elapsed >= 1000) {  // 1초마다 측정
            measuredFps = frameCount * 1000f / elapsed
            frameCount = 0
            lastFpsCheckTime = now

            adjustQuality(now)

            Timber.v("Streaming FPS: %.1f, Resolution: %s", measuredFps, currentResolution.name)
        }
    }

    private fun adjustQuality(now: Long) {
        // 쿨다운 체크
        if (now - lastQualityChangeTime < qualityChangeCooldown) {
            return
        }

        val fpsRatio = measuredFps / targetFps

        when {
            // 성능 부족: FPS가 목표의 80% 미만
            fpsRatio < 0.8f -> {
                lowPerformanceCount++
                highPerformanceCount = 0

                // 3회 연속 저성능이면 해상도 낮춤
                if (lowPerformanceCount >= 3) {
                    val lowerResolution = currentResolution.lower()
                    if (lowerResolution != currentResolution) {
                        Timber.i("품질 저하: $currentResolution → $lowerResolution (FPS: %.1f)", measuredFps)
                        currentResolution = lowerResolution
                        onResolutionChanged?.invoke(currentResolution)
                        lastQualityChangeTime = now
                        lowPerformanceCount = 0
                    }
                }
            }

            // 성능 여유: FPS가 목표의 95% 이상
            fpsRatio > 0.95f -> {
                highPerformanceCount++
                lowPerformanceCount = 0

                // 5회 연속 고성능이면 해상도 높임
                if (highPerformanceCount >= 5) {
                    val higherResolution = currentResolution.higher()
                    if (higherResolution != currentResolution) {
                        Timber.i("품질 향상: $currentResolution → $higherResolution (FPS: %.1f)", measuredFps)
                        currentResolution = higherResolution
                        onResolutionChanged?.invoke(currentResolution)
                        lastQualityChangeTime = now
                        highPerformanceCount = 0
                    }
                }
            }

            // 정상 범위
            else -> {
                lowPerformanceCount = 0
                highPerformanceCount = 0
            }
        }
    }

    /**
     * 현재 측정된 FPS 반환.
     */
    fun getMeasuredFps(): Float = measuredFps

    /**
     * 특정 해상도로 강제 설정.
     */
    fun forceResolution(resolution: Resolution) {
        if (resolution != currentResolution) {
            currentResolution = resolution
            onResolutionChanged?.invoke(resolution)
            Timber.d("해상도 강제 설정: $resolution")
        }
    }

    /**
     * 상태 리셋.
     */
    fun reset() {
        frameCount = 0
        measuredFps = 0f
        lowPerformanceCount = 0
        highPerformanceCount = 0
        lastFpsCheckTime = System.currentTimeMillis()
        lastQualityChangeTime = 0L
    }
}

/**
 * 스트리밍 해상도 프리셋.
 * 가로/세로 모드 모두 지원합니다.
 */
enum class Resolution(val width: Int, val height: Int) {
    /** 480p - 저사양 기기용 */
    SD_480(854, 480),

    /** 720p - 기본값 */
    HD_720(1280, 720),

    /** 1080p - 고사양 기기용 */
    FHD_1080(1920, 1080);

    /** 한 단계 낮은 해상도 */
    fun lower(): Resolution = entries.getOrElse(ordinal - 1) { this }

    /** 한 단계 높은 해상도 */
    fun higher(): Resolution = entries.getOrElse(ordinal + 1) { this }

    /** 세로 모드용 너비 */
    val portraitWidth: Int get() = height

    /** 세로 모드용 높이 */
    val portraitHeight: Int get() = width

    /** 세로 모드 해상도 반환 */
    fun toPortrait(): Pair<Int, Int> = portraitWidth to portraitHeight

    /** 가로 모드 해상도 반환 */
    fun toLandscape(): Pair<Int, Int> = width to height

    override fun toString(): String = "${width}x${height}"
}
