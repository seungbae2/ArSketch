package com.sb.arsketch.streaming

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class StreamingUiState(
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StreamingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(StreamingUiState())
    val uiState: StateFlow<StreamingUiState> = _uiState.asStateFlow()

    private var screenCaptureService: ScreenCaptureService? = null
    private var pendingMediaProjectionData: Intent? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenCaptureService.LocalBinder
            screenCaptureService = binder.getService()
            isBound = true

            Timber.d("Service connected")

            // 서비스 연결 후 스트리밍 시작
            pendingMediaProjectionData?.let { data ->
                startStreamingWithService(data)
                pendingMediaProjectionData = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenCaptureService = null
            isBound = false
            Timber.d("Service disconnected")
        }
    }

    /**
     * MediaProjection 권한 요청을 위한 Intent 생성
     */
    fun createScreenCaptureIntent(): Intent {
        val mediaProjectionManager = context.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    /**
     * 권한 획득 후 스트리밍 시작
     */
    fun onScreenCapturePermissionGranted(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            _uiState.update { it.copy(error = "화면 캡처 권한이 거부되었습니다") }
            return
        }

        Timber.d("Screen capture permission granted")
        _uiState.update { it.copy(isConnecting = true) }
        pendingMediaProjectionData = data

        // Foreground Service 시작 및 바인딩
        val serviceIntent = Intent(context, ScreenCaptureService::class.java)
        context.startForegroundService(serviceIntent)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startStreamingWithService(mediaProjectionData: Intent) {
        viewModelScope.launch {
            screenCaptureService?.startStreaming(
                url = LIVEKIT_URL,
                token = LIVEKIT_TOKEN,
                mediaProjectionData = mediaProjectionData,
                onSuccess = {
                    Timber.d("Streaming started successfully")
                    _uiState.update {
                        it.copy(
                            isStreaming = true,
                            isConnecting = false,
                            error = null
                        )
                    }
                },
                onError = { e ->
                    Timber.e(e, "Streaming failed")
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            isConnecting = false,
                            error = e.message ?: "스트리밍 시작 실패"
                        )
                    }
                }
            )
        }
    }

    /**
     * 스트리밍 중지
     */
    fun stopStreaming() {
        screenCaptureService?.stopStreaming()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }

        _uiState.update {
            it.copy(
                isStreaming = false,
                isConnecting = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
        }
    }

    companion object {
        // LiveKit Cloud URL
        private const val LIVEKIT_URL = "wss://ardrawing-xabqpgun.livekit.cloud"

        // Android용 토큰 (1주일 유효 - 2025-02-05까지)
        // Room: ar-drawing, Identity: android-user
        private const val LIVEKIT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzAyNzU2MjYsImlkZW50aXR5IjoiYW5kcm9pZC11c2VyIiwiaXNzIjoiQVBJb3dMNkNRdjM4M21BIiwibmFtZSI6ImFuZHJvaWQtdXNlciIsIm5iZiI6MTc2OTY3MDgyNiwic3ViIjoiYW5kcm9pZC11c2VyIiwidmlkZW8iOnsicm9vbSI6ImFyLWRyYXdpbmciLCJyb29tSm9pbiI6dHJ1ZX19.7C0kTuLq12iFkX88BtqDSkn2hzfweHikXCWFOUatH-g"
    }
}
