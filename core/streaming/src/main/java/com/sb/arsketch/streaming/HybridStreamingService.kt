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
import android.view.SurfaceView
import androidx.core.app.NotificationCompat
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.StrokeEvent
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.DataPublishReliability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Hybrid 스트리밍 서비스.
 *
 * AR 렌더링 영상은 ARFrameCapturer(PixelCopy)를 통해 LiveKit VideoTrack으로,
 * AR 드로잉 데이터는 DataChannel(JSON)로 전송합니다.
 */
class HybridStreamingService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null
    private val arFrameCapturer = ARFrameCapturer()
    private var pendingSurfaceView: SurfaceView? = null

    private val json = Json { ignoreUnknownKeys = true }

    // 리모트 터치 이벤트를 Flow로 발행
    private val _remoteTouchEvents = MutableSharedFlow<RemoteTouchEvent>(extraBufferCapacity = 64)
    val remoteTouchEvents: Flow<RemoteTouchEvent> = _remoteTouchEvents.asSharedFlow()

    // 연결 상태
    private val _connectionState = MutableStateFlow<com.sb.arsketch.streaming.api.ConnectionState>(
        com.sb.arsketch.streaming.api.ConnectionState.Idle
    )
    val connectionState: StateFlow<com.sb.arsketch.streaming.api.ConnectionState> = _connectionState.asStateFlow()

    // 참가자 수
    private val _participantCount = MutableStateFlow(0)
    val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

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
     * LiveKit 연결 (비디오 트랙은 setARSurfaceView에서 시작)
     */
    suspend fun connect(url: String, token: String) {
        if (_connectionState.value != com.sb.arsketch.streaming.api.ConnectionState.Idle) {
            throw IllegalStateException("Already connected or connecting")
        }

        _connectionState.value = com.sb.arsketch.streaming.api.ConnectionState.Connecting

        try {
            Timber.d("Connecting to LiveKit: $url")

            room = LiveKit.create(appContext = applicationContext)
            room?.connect(url, token)

            Timber.d("Connected to room: ${room?.name}")

            _connectionState.value = com.sb.arsketch.streaming.api.ConnectionState.Connected(
                roomName = room?.name ?: ""
            )

            observeRoomEvents()
            tryStartCapture()

        } catch (e: Exception) {
            Timber.e(e, "Failed to connect")
            _connectionState.value = com.sb.arsketch.streaming.api.ConnectionState.Error(
                e.message ?: "Connection failed"
            )
            cleanup()
            throw e
        }
    }

    /**
     * AR SurfaceView를 설정합니다.
     * Room이 이미 연결되어 있으면 즉시 캡처를 시작하고,
     * 아직 연결 전이면 연결 완료 시 자동으로 시작합니다.
     */
    fun setARSurfaceView(surfaceView: SurfaceView) {
        Timber.d("setARSurfaceView called, view: ${surfaceView.width}x${surfaceView.height}")
        pendingSurfaceView = surfaceView
        tryStartCapture()
    }

    /**
     * Room과 SurfaceView가 모두 준비되면 AR 프레임 캡처를 시작합니다.
     */
    private fun tryStartCapture() {
        val currentRoom = room ?: run {
            Timber.d("tryStartCapture: room is null, waiting")
            return
        }
        val view = pendingSurfaceView ?: run {
            Timber.d("tryStartCapture: surfaceView is null, waiting")
            return
        }

        if (_connectionState.value !is com.sb.arsketch.streaming.api.ConnectionState.Connected) {
            Timber.d("tryStartCapture: not connected yet, waiting")
            return
        }

        Timber.d("tryStartCapture: all ready, starting AR capture")
        pendingSurfaceView = null

        serviceScope.launch {
            try {
                arFrameCapturer.stop()
                arFrameCapturer.start(currentRoom, view)
                Timber.d("AR frame capture started successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start AR frame capture")
            }
        }
    }

    private fun observeRoomEvents() {
        serviceScope.launch {
            room?.events?.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected -> {
                        _participantCount.value = (room?.remoteParticipants?.size ?: 0) + 1
                    }
                    is RoomEvent.DataReceived -> {
                        if (event.topic == StreamingConstants.DATA_TOPIC_REMOTE_TOUCH) {
                            try {
                                val jsonString = event.data.toString(Charsets.UTF_8)
                                val touchEvent = json.decodeFromString<RemoteTouchEvent>(jsonString)
                                _remoteTouchEvents.tryEmit(touchEvent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to deserialize RemoteTouchEvent")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        _participantCount.value = (room?.remoteParticipants?.size ?: 0) + 1
    }

    /**
     * StrokeEvent를 DataChannel로 전송
     */
    fun publishStrokeEvent(event: StrokeEvent) {
        if (_connectionState.value !is com.sb.arsketch.streaming.api.ConnectionState.Connected) return

        serviceScope.launch {
            try {
                val jsonString = json.encodeToString(event)
                val data = jsonString.toByteArray(Charsets.UTF_8)

                room?.localParticipant?.publishData(
                    data = data,
                    reliability = DataPublishReliability.RELIABLE,
                    topic = StreamingConstants.DATA_TOPIC_AR_DRAWING
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
            _connectionState.value = com.sb.arsketch.streaming.api.ConnectionState.Idle
            stopSelf()
        }
    }

    private suspend fun cleanup() {
        try {
            arFrameCapturer.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping AR frame capture")
        }

        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room")
        }
        room = null
        pendingSurfaceView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        arFrameCapturer.stop()
        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room on destroy")
        }
        room = null
        pendingSurfaceView = null
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
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
    }
}
