package com.sb.arsketch.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.SurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.video.BitmapFrameCapturer
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ARCore SurfaceView에서 렌더링된 프레임을 캡처하여
 * LiveKit VideoTrack으로 스트리밍합니다.
 *
 * PixelCopy API를 사용하여 SurfaceView의 렌더링 결과를
 * Bitmap으로 캡처한 후, BitmapFrameCapturer를 통해
 * LiveKit에 전달합니다.
 *
 * 더블 버퍼링으로 PixelCopy(비동기)와 pushBitmap 간 레이스 컨디션을 방지합니다.
 */
class ARFrameCapturer {

    private var bitmapCapturer: BitmapFrameCapturer? = null
    private var videoTrack: LocalVideoTrack? = null
    private var captureHandler: Handler? = null
    private var captureThread: HandlerThread? = null
    private var pixelCopyHandler: Handler? = null
    private var pixelCopyThread: HandlerThread? = null

    @Volatile
    private var isCapturing = false

    private var activeSurfaceView: SurfaceView? = null

    // 더블 버퍼링: PixelCopy 대상과 pushBitmap 대상을 분리
    private var bufferA: Bitmap? = null
    private var bufferB: Bitmap? = null
    private var currentCaptureBuffer = 0 // 0 = A, 1 = B

    // PixelCopy 진행 중 플래그 — 완료 전에 다음 캡처 요청 방지
    private val copyInProgress = AtomicBoolean(false)

    // 축소된 출력 비트맵 (원본 해상도가 큰 경우)
    private var scaledBitmap: Bitmap? = null
    private var scaleMatrix: Matrix? = null
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var outputWidth = 0
    private var outputHeight = 0

    /**
     * AR 프레임 캡처를 시작합니다.
     *
     * @param room LiveKit Room
     * @param surfaceView AR 렌더링이 이루어지는 SurfaceView
     * @param fps 캡처 프레임 레이트 (기본 15fps)
     */
    suspend fun start(
        room: Room,
        surfaceView: SurfaceView,
        fps: Int = DEFAULT_FPS
    ) {
        if (isCapturing) {
            Timber.w("ARFrameCapturer already capturing")
            return
        }

        val srcWidth = surfaceView.width
        val srcHeight = surfaceView.height

        if (srcWidth <= 0 || srcHeight <= 0) {
            Timber.e("Invalid surface view dimensions: ${srcWidth}x${srcHeight}")
            return
        }

        // 출력 해상도 결정: 긴 변이 MAX_DIMENSION 이하가 되도록 축소
        val scale = (MAX_DIMENSION.toFloat() / maxOf(srcWidth, srcHeight)).coerceAtMost(1f)
        outputWidth = (srcWidth * scale).toInt()
        outputHeight = (srcHeight * scale).toInt()

        Timber.d("AR frame capture: src=${srcWidth}x${srcHeight} → out=${outputWidth}x${outputHeight} @ ${fps}fps")

        // 캡처용 스레드
        captureThread = HandlerThread("ARFrameCapture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)

        pixelCopyThread = HandlerThread("ARPixelCopy").also { it.start() }
        pixelCopyHandler = Handler(pixelCopyThread!!.looper)

        // 더블 버퍼 — PixelCopy용 (원본 해상도)
        bufferA = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)
        bufferB = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888)

        // 축소가 필요한 경우 축소 비트맵과 매트릭스 준비
        if (scale < 1f) {
            scaledBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            scaleMatrix = Matrix().apply { setScale(scale, scale) }
        }

        // BitmapFrameCapturer
        bitmapCapturer = BitmapFrameCapturer()

        // LiveKit VideoTrack 생성
        videoTrack = room.localParticipant.createVideoTrack(
            name = "arcore-stream",
            capturer = bitmapCapturer!!,
            options = LocalVideoTrackOptions(
                isScreencast = true,
                captureParams = VideoCaptureParameter(
                    width = outputWidth,
                    height = outputHeight,
                    maxFps = fps,
                    adaptOutputToDimensions = false
                )
            )
        )

        // startCapture()를 호출해야 BitmapFrameCapturer의 프레임 리스너가 활성화됨
        videoTrack!!.startCapture()

        room.localParticipant.publishVideoTrack(videoTrack!!)

        Timber.d("Video track published: ${videoTrack?.name}")

        // 캡처 루프 시작
        isCapturing = true
        activeSurfaceView = surfaceView
        val intervalMs = 1000L / fps
        captureHandler?.post(object : Runnable {
            override fun run() {
                if (!isCapturing) return
                val view = activeSurfaceView ?: return
                captureFrame(view)
                captureHandler?.postDelayed(this, intervalMs)
            }
        })
    }

    private fun captureFrame(surfaceView: SurfaceView) {
        // 이전 PixelCopy가 아직 진행 중이면 이번 프레임 스킵
        if (!copyInProgress.compareAndSet(false, true)) return

        val captureBuffer = if (currentCaptureBuffer == 0) bufferA else bufferB
        if (captureBuffer == null) {
            copyInProgress.set(false)
            return
        }
        val handler = pixelCopyHandler ?: run {
            copyInProgress.set(false)
            return
        }

        // 다음 캡처에서 사용할 버퍼 전환
        currentCaptureBuffer = 1 - currentCaptureBuffer

        try {
            PixelCopy.request(
                surfaceView,
                captureBuffer,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        val output = maybeScale(captureBuffer)
                        bitmapCapturer?.pushBitmap(output, 0)
                    } else {
                        Timber.w("PixelCopy failed: $copyResult")
                    }
                    copyInProgress.set(false)
                },
                handler
            )
        } catch (e: Exception) {
            Timber.e(e, "Error capturing AR frame")
            copyInProgress.set(false)
        }
    }

    /** 원본 비트맵을 축소된 출력 비트맵으로 변환. 축소 불필요 시 원본 반환. */
    private fun maybeScale(source: Bitmap): Bitmap {
        val scaled = scaledBitmap ?: return source
        val matrix = scaleMatrix ?: return source

        val canvas = Canvas(scaled)
        canvas.drawBitmap(source, matrix, scalePaint)
        return scaled
    }

    /**
     * 캡처를 중지하고 리소스를 정리합니다.
     */
    fun stop() {
        Timber.d("Stopping AR frame capture")
        isCapturing = false
        activeSurfaceView = null

        captureHandler?.removeCallbacksAndMessages(null)
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        pixelCopyThread?.quitSafely()
        pixelCopyThread = null
        pixelCopyHandler = null

        try {
            bitmapCapturer?.stopCapture()
            bitmapCapturer?.dispose()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping BitmapFrameCapturer")
        }
        bitmapCapturer = null

        bufferA?.recycle()
        bufferA = null
        bufferB?.recycle()
        bufferB = null
        scaledBitmap?.recycle()
        scaledBitmap = null
        scaleMatrix = null

        videoTrack = null
    }

    companion object {
        private const val DEFAULT_FPS = 15
        private const val MAX_DIMENSION = 1080
    }
}
