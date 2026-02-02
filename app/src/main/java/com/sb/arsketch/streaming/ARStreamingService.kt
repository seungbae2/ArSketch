package com.sb.arsketch.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.video.BitmapFrameCapturer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * AR 합성 프레임 스트리밍을 위한 Foreground Service.
 * BitmapFrameCapturer를 사용하여 OpenGL에서 캡처한 프레임을 LiveKit으로 전송합니다.
 */
class ARStreamingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null
    private var capturer: BitmapFrameCapturer? = null
    private var videoTrack: LocalVideoTrack? = null

    // 스트리밍 상태
    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    // 프레임 통계
    private val frameCount = AtomicLong(0)
    private var lastFpsTime = System.currentTimeMillis()
    private var currentFps = 0f

    inner class LocalBinder : Binder() {
        fun getService(): ARStreamingService = this@ARStreamingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    /**
     * LiveKit 연결 및 BitmapFrameCapturer 초기화
     *
     * @param url LiveKit 서버 URL
     * @param token 인증 토큰
     * @param width 스트림 너비
     * @param height 스트림 높이
     * @param fps 목표 프레임레이트
     */
    fun connect(
        url: String,
        token: String,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (_streamingState.value != StreamingState.Idle) {
            onError(IllegalStateException("Already connected or connecting"))
            return
        }

        _streamingState.value = StreamingState.Connecting

        serviceScope.launch {
            try {
                Timber.d("Connecting to LiveKit: $url")

                // 1. LiveKit Room 생성 및 연결
                room = LiveKit.create(appContext = applicationContext)
                room?.connect(url, token)

                Timber.d("Connected to room: ${room?.name}")

                // 2. BitmapFrameCapturer 생성
                capturer = BitmapFrameCapturer()
                capturer?.startCapture(width, height, fps)

                Timber.d("BitmapFrameCapturer started: ${width}x${height} @ ${fps}fps")

                // 3. VideoTrack 생성 및 발행
                videoTrack = room?.localParticipant?.createVideoTrack(
                    name = "ar_composited",
                    capturer = capturer!!,
                    options = LocalVideoTrackOptions()
                )

                room?.localParticipant?.publishVideoTrack(videoTrack!!)

                Timber.d("VideoTrack published: ${videoTrack?.name}")

                _streamingState.value = StreamingState.Streaming(
                    roomName = room?.name ?: "",
                    resolution = "${width}x${height}",
                    fps = fps
                )

                onSuccess()

            } catch (e: Exception) {
                Timber.e(e, "Failed to connect")
                _streamingState.value = StreamingState.Error(e.message ?: "Connection failed")
                cleanup()
                onError(e)
            }
        }
    }

    /**
     * 프레임 전송.
     * GLThread에서 호출됩니다.
     *
     * @param bitmap 전송할 비트맵 (ARGB_8888)
     * @param rotationDegrees 회전 각도 (0, 90, 180, 270)
     */
    fun pushFrame(bitmap: Bitmap, rotationDegrees: Int = 0) {
        if (_streamingState.value !is StreamingState.Streaming) {
            return
        }

        try {
            capturer?.pushBitmap(bitmap, rotationDegrees)
            updateFps()
        } catch (e: Exception) {
            Timber.e(e, "Error pushing frame")
        }
    }

    /**
     * 연결 해제 및 리소스 정리
     */
    fun disconnect() {
        Timber.d("Disconnecting")
        serviceScope.launch {
            cleanup()
            _streamingState.value = StreamingState.Idle
            stopSelf()
        }
    }

    private fun cleanup() {
        try {
            capturer?.stopCapture()
            capturer?.dispose()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping capturer")
        }
        capturer = null

        try {
            videoTrack?.let { room?.localParticipant?.unpublishTrack(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error unpublishing track")
        }
        videoTrack = null

        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room")
        }
        room = null

        frameCount.set(0)
        currentFps = 0f
    }

    private fun updateFps() {
        val count = frameCount.incrementAndGet()
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTime

        if (elapsed >= 1000) {
            currentFps = count * 1000f / elapsed
            frameCount.set(0)
            lastFpsTime = now
            Timber.v("Streaming FPS: %.1f", currentFps)
        }
    }

    /**
     * 현재 FPS 반환
     */
    fun getCurrentFps(): Float = currentFps

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AR 스트리밍",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AR Drawing 화면을 스트리밍 중입니다"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR Drawing 스트리밍 중")
            .setContentText("AR 화면을 상대방과 공유하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ar_streaming_channel"
        private const val NOTIFICATION_ID = 1002
    }
}

/**
 * AR 스트리밍 상태
 */
sealed class StreamingState {
    /** 대기 상태 */
    data object Idle : StreamingState()

    /** 연결 중 */
    data object Connecting : StreamingState()

    /** 스트리밍 중 */
    data class Streaming(
        val roomName: String,
        val resolution: String,
        val fps: Int
    ) : StreamingState()

    /** 오류 발생 */
    data class Error(val message: String) : StreamingState()
}
