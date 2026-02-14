package com.sb.arsketch.streaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.opengl.GLSurfaceView
import android.os.IBinder
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.StrokeEvent
import com.sb.arsketch.streaming.api.HostStreamingController
import com.sb.arsketch.streaming.api.HostStreamingSession
import com.sb.arsketch.streaming.api.StreamingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class HostStreamingSessionImpl(
    private val context: Context
) : HostStreamingSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controller: HostStreamingController? = null
    private var isServiceBound = false
    private var pendingSurfaceView: GLSurfaceView? = null
    private var remoteTouchHandler: ((RemoteTouchEvent) -> Unit)? = null

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    override val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _participantCount = MutableStateFlow(0)
    override val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

    private var pendingConnect: PendingConnect? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HybridStreamingService.LocalBinder
            controller = binder.getController()
            isServiceBound = true
            Timber.d("HybridStreamingService connected")

            pendingSurfaceView?.let { controller?.setARSurfaceView(it) }

            controller?.onRemoteTouchReceived = { event ->
                remoteTouchHandler?.invoke(event)
            }

            observeControllerState()

            pendingConnect?.let { pending ->
                controller?.connect(
                    url = pending.url,
                    token = pending.token,
                    onSuccess = pending.onSuccess,
                    onError = pending.onError
                )
                pendingConnect = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            controller = null
            isServiceBound = false
            _streamingState.value = StreamingState.Idle
        }
    }

    override fun connect(
        url: String,
        token: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        _streamingState.value = StreamingState.Connecting
        pendingConnect = PendingConnect(url, token, onSuccess, onError)

        val serviceIntent = Intent(context, HybridStreamingService::class.java)
        context.startForegroundService(serviceIntent)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun setARSurfaceView(surfaceView: GLSurfaceView) {
        pendingSurfaceView = surfaceView
        controller?.setARSurfaceView(surfaceView)
    }

    override fun publishStrokeEvent(event: StrokeEvent) {
        controller?.publishStrokeEvent(event)
    }

    override fun setRemoteTouchHandler(handler: ((RemoteTouchEvent) -> Unit)?) {
        remoteTouchHandler = handler
        controller?.onRemoteTouchReceived = handler?.let { h ->
            { event: RemoteTouchEvent -> h(event) }
        }
    }

    override fun disconnect() {
        controller?.onRemoteTouchReceived = null
        controller?.disconnect()

        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Timber.e(e, "Error unbinding service")
            }
            isServiceBound = false
        }

        controller = null
        pendingConnect = null
        _streamingState.value = StreamingState.Idle
        _participantCount.value = 0
        scope.cancel()
    }

    private fun observeControllerState() {
        scope.launch {
            controller?.streamingState?.collect { state ->
                _streamingState.value = state
            }
        }
        scope.launch {
            controller?.participantCount?.collect { count ->
                _participantCount.value = count
            }
        }
    }

    private data class PendingConnect(
        val url: String,
        val token: String,
        val onSuccess: () -> Unit,
        val onError: (Exception) -> Unit
    )
}
