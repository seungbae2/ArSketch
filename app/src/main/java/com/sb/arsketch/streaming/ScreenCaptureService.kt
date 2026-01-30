package com.sb.arsketch.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Screen Capture를 위한 Foreground Service
 * Android 10+ 에서는 MediaProjection을 Foreground Service에서 실행해야 함
 */
class ScreenCaptureService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    /**
     * LiveKit 연결 및 Screen Capture 시작
     */
    fun startStreaming(
        url: String,
        token: String,
        mediaProjectionData: Intent,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        serviceScope.launch {
            try {
                Timber.d("Connecting to LiveKit: $url")

                // 1. LiveKit Room 생성 및 연결
                room = LiveKit.create(appContext = applicationContext)
                room?.connect(url, token)

                Timber.d("Connected to room: ${room?.name}")

                // 2. ScreenCaptureParams 생성 및 화면 공유 시작
                val screenCaptureParams = ScreenCaptureParams(
                    mediaProjectionPermissionResultData = mediaProjectionData,
                    notificationId = NOTIFICATION_ID,
                    notification = createNotification()
                )

                val success = room?.localParticipant?.setScreenShareEnabled(
                    enabled = true,
                    screenCaptureParams = screenCaptureParams
                )

                Timber.d("Screen share enabled: $success")

                onSuccess()

            } catch (e: Exception) {
                Timber.e(e, "Failed to start streaming")
                onError(e)
            }
        }
    }

    /**
     * 스트리밍 중지
     */
    fun stopStreaming() {
        Timber.d("Stopping streaming")
        serviceScope.launch {
            try {
                room?.localParticipant?.setScreenShareEnabled(false)
            } catch (e: Exception) {
                Timber.e(e, "Error disabling screen share")
            }
            room?.disconnect()
            room = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 동기적으로 정리 (coroutine scope가 취소되기 전에)
        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room on destroy")
        }
        room = null
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AR Drawing 스트리밍",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AR Drawing 화면을 공유 중입니다"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR Drawing 공유 중")
            .setContentText("화면을 상대방과 공유하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
