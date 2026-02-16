package com.sb.arsketch.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.opengl.GLSurfaceView
import android.os.Binder
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.sb.arsketch.streaming.api.HostStreamingController
import com.sb.arsketch.streaming.api.ConnectionState
import timber.log.Timber

/**
 * Hybrid 스트리밍 서비스.
 *
 * AR 렌더링 영상은 ARFrameCapturer(PixelCopy)를 통해 LiveKit VideoTrack으로,
 * AR 드로잉 데이터는 DataChannel(JSON)로 전송합니다.
 */
class HybridStreamingService : Service(), HostStreamingController {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null
    private val arFrameCapturer = ARFrameCapturer()
    private var pendingSurfaceView: GLSurfaceView? = null

    private val json = Json { ignoreUnknownKeys = true }

    // 리모트 터치 이벤트 수신 콜백
    override var onRemoteTouchReceived: ((RemoteTouchEvent) -> Unit)? = null

    // 스트리밍 상태
    private val _streamingState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val streamingState: StateFlow<ConnectionState> = _streamingState.asStateFlow()

    // 참가자 수
    private val _participantCount = MutableStateFlow(0)
    override val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getController(): HostStreamingController = this@HybridStreamingService
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
     *
     * @param url LiveKit 서버 URL
     * @param token 인증 토큰
     */
    override fun connect(
        url: String,
        token: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (_streamingState.value != ConnectionState.Idle) {
            onError(IllegalStateException("Already connected or connecting"))
            return
        }

        _streamingState.value = ConnectionState.Connecting

        serviceScope.launch {
            try {
                Timber.d("Connecting to LiveKit: $url")

                // Room 생성 및 연결
                room = LiveKit.create(appContext = applicationContext)
                room?.connect(url, token)

                Timber.d("Connected to room: ${room?.name}")

                _streamingState.value = ConnectionState.Connected(
                    roomName = room?.name ?: ""
                )

                // 참가자 수 추적
                observeRoomEvents()

                // GLSurfaceView가 이미 설정되어 있으면 캡처 시작
                tryStartCapture()

                onSuccess()

            } catch (e: Exception) {
                Timber.e(e, "Failed to connect")
                _streamingState.value = ConnectionState.Error(e.message ?: "Connection failed")
                cleanup()
                onError(e)
            }
        }
    }

    /**
     * AR GLSurfaceView를 설정합니다.
     * Room이 이미 연결되어 있으면 즉시 캡처를 시작하고,
     * 아직 연결 전이면 연결 완료 시 자동으로 시작합니다.
     */
    override fun setARSurfaceView(surfaceView: GLSurfaceView) {
        Timber.d("setARSurfaceView called, view: ${surfaceView.width}x${surfaceView.height}")
        pendingSurfaceView = surfaceView
        tryStartCapture()
    }

    /**
     * Room과 GLSurfaceView가 모두 준비되면 AR 프레임 캡처를 시작합니다.
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

        if (_streamingState.value !is ConnectionState.Connected) {
            Timber.d("tryStartCapture: not streaming yet, waiting")
            return
        }

        Timber.d("tryStartCapture: all ready, starting AR capture")
        pendingSurfaceView = null // 중복 시작 방지

        serviceScope.launch {
            try {
                // 기존 캡처 중지 (화면 회전 등으로 뷰가 바뀐 경우)
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
                                onRemoteTouchReceived?.invoke(touchEvent)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to deserialize RemoteTouchEvent")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        // 초기값 설정
        _participantCount.value = (room?.remoteParticipants?.size ?: 0) + 1
    }

    /**
     * StrokeEvent를 DataChannel로 전송
     *
     * @param event 전송할 StrokeEvent
     */
    override fun publishStrokeEvent(event: StrokeEvent) {
        if (_streamingState.value !is ConnectionState.Connected) return

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
    override fun disconnect() {
        Timber.d("Disconnecting")
        serviceScope.launch {
            cleanup()
            _streamingState.value = ConnectionState.Idle
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
    }

    override fun onDestroy() {
        super.onDestroy()
        // 동기적으로 정리 (coroutine scope 취소 전)
        arFrameCapturer.stop()
        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting room on destroy")
        }
        room = null
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
