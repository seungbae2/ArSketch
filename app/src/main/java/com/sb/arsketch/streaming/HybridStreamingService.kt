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
import com.sb.arsketch.domain.model.StrokeEvent
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Hybrid 스트리밍 서비스.
 *
 * 카메라 영상은 LiveKit VideoTrack으로,
 * AR 드로잉 데이터는 DataChannel(JSON)로 전송합니다.
 */
class HybridStreamingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null

    private val json = Json { ignoreUnknownKeys = true }

    // 스트리밍 상태
    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): HybridStreamingService = this@HybridStreamingService
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
     * LiveKit 연결 및 카메라 트랙 활성화
     *
     * @param url LiveKit 서버 URL
     * @param token 인증 토큰
     */
    fun connect(
        url: String,
        token: String,
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

                // 1. Room 생성 및 연결
                room = LiveKit.create(appContext = applicationContext)
                room?.connect(url, token)

                Timber.d("Connected to room: ${room?.name}")

                // 2. 후면 카메라 활성화 (LiveKit이 카메라 트랙 생성 및 발행 처리)
                room?.localParticipant?.setCameraEnabled(true)

                Timber.d("Camera track enabled")

                _streamingState.value = StreamingState.Streaming(
                    roomName = room?.name ?: ""
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
     * StrokeEvent를 DataChannel로 전송
     *
     * @param event 전송할 StrokeEvent
     */
    fun publishStrokeEvent(event: StrokeEvent) {
        if (_streamingState.value !is StreamingState.Streaming) return

        serviceScope.launch {
            try {
                val jsonString = json.encodeToString(event)
                val data = jsonString.toByteArray(Charsets.UTF_8)

                room?.localParticipant?.publishData(
                    data = data,
                    reliability = DataPublishReliability.RELIABLE,
                    topic = DATA_TOPIC_AR_DRAWING
                )
            } catch (e: Exception) {
                Timber.e(e, "Error publishing stroke event")
            }
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

    private suspend fun cleanup() {
        try {
            room?.localParticipant?.setCameraEnabled(false)
        } catch (e: Exception) {
            Timber.e(e, "Error disabling camera")
        }

        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room")
        }
        room = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // 동기적으로 정리 (coroutine scope 취소 전)
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
                "AR 스트리밍",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AR Drawing을 스트리밍 중입니다"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AR Drawing 스트리밍 중")
            .setContentText("카메라와 AR 드로잉을 공유하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hybrid_streaming_channel"
        private const val NOTIFICATION_ID = 1003
        const val DATA_TOPIC_AR_DRAWING = "ar_drawing"
    }
}

/**
 * Hybrid 스트리밍 상태
 */
sealed class StreamingState {
    data object Idle : StreamingState()
    data object Connecting : StreamingState()
    data class Streaming(val roomName: String) : StreamingState()
    data class Error(val message: String) : StreamingState()
}
