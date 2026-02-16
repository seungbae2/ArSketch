package com.sb.arsketch.streaming

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.sb.arsketch.streaming.api.ViewerConnectionState
import com.sb.arsketch.streaming.api.ViewerStreamingClient
import timber.log.Timber

/**
 * Viewer용 LiveKit 연결 관리자.
 *
 * - 카메라/마이크 발행 없이 Room에 접속
 * - 원격 비디오 트랙 구독
 * - DataChannel 수신 → StrokeEventReceiver로 전달
 */
class ViewerConnectionManager(
    private val context: Context,
    private val strokeEventReceiver: StrokeEventReceiver
) : ViewerStreamingClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var room: Room? = null
    private var isDestroyed = false

    private val _connectionState = MutableStateFlow<ViewerConnectionState>(ViewerConnectionState.Disconnected)
    override val connectionState: StateFlow<ViewerConnectionState> = _connectionState.asStateFlow()

    private val _participantCount = MutableStateFlow(0)
    override val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

    override fun connect(serverUrl: String, token: String) {
        if (_connectionState.value !is ViewerConnectionState.Disconnected) return
        if (isDestroyed) return

        _connectionState.value = ViewerConnectionState.Connecting

        scope.launch {
            try {
                Timber.d("Viewer connecting to: $serverUrl")

                room = LiveKit.create(appContext = context)
                room?.connect(serverUrl, token)

                Timber.d("Viewer connected to room: ${room?.name}")

                _connectionState.value = ViewerConnectionState.Connected(
                    roomName = room?.name ?: ""
                )

                observeRoomEvents()
                updateParticipantCount()

            } catch (e: Exception) {
                Timber.e(e, "Viewer connection failed")
                _connectionState.value = ViewerConnectionState.Error(
                    e.message ?: "Connection failed"
                )
                cleanup()
            }
        }
    }

    private fun observeRoomEvents() {
        scope.launch {
            room?.events?.collect { event ->
                when (event) {
                    is RoomEvent.DataReceived -> {
                        if (event.topic == StreamingConstants.DATA_TOPIC_AR_DRAWING) {
                            strokeEventReceiver.onDataReceived(event.data)
                        }
                    }
                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected -> {
                        updateParticipantCount()
                    }
                    is RoomEvent.Disconnected -> {
                        _connectionState.value = ViewerConnectionState.Disconnected
                    }
                    else -> {}
                }
            }
        }
    }

    private fun updateParticipantCount() {
        _participantCount.value = (room?.remoteParticipants?.size ?: 0) + 1
    }

    override fun disconnect() {
        if (isDestroyed) return
        scope.launch {
            cleanup()
            _connectionState.value = ViewerConnectionState.Disconnected
        }
    }

    private suspend fun cleanup() {
        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting viewer room")
        }
        room = null
        strokeEventReceiver.clear()
    }

    override fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        try {
            room?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error on viewer destroy")
        }
        room = null
        scope.cancel()
    }
}
