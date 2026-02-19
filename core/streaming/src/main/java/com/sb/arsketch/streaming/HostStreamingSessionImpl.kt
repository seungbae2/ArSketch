package com.sb.arsketch.streaming

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.SurfaceView
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.StrokeEvent
import com.sb.arsketch.streaming.api.ConnectionState
import com.sb.arsketch.streaming.api.HostStreamingSession
import kotlinx.coroutines.CancellableContinuation
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
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HostStreamingSessionImpl(
    private val context: Context
) : HostStreamingSession {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var service: HybridStreamingService? = null
    private var isServiceBound = false
    private var pendingSurfaceView: SurfaceView? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _participantCount = MutableStateFlow(0)
    override val participantCount: StateFlow<Int> = _participantCount.asStateFlow()

    private val _remoteTouchEvents = MutableSharedFlow<RemoteTouchEvent>(extraBufferCapacity = 64)
    override val remoteTouchEvents: Flow<RemoteTouchEvent> = _remoteTouchEvents.asSharedFlow()

    private var pendingConnectContinuation: CancellableContinuation<Unit>? = null
    private var pendingConnectParams: ConnectParams? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as HybridStreamingService.LocalBinder
            service = localBinder.getService()
            isServiceBound = true
            Timber.d("HybridStreamingService connected")

            pendingSurfaceView?.let { service?.setARSurfaceView(it) }
            observeServiceState()
            observeRemoteTouchEvents()

            pendingConnectContinuation?.let { continuation ->
                scope.launch {
                    try {
                        service?.connect(
                            url = pendingConnectParams!!.url,
                            token = pendingConnectParams!!.token
                        )
                        continuation.resume(Unit)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                    pendingConnectContinuation = null
                    pendingConnectParams = null
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isServiceBound = false
            _connectionState.value = ConnectionState.Idle

            scope.cancel()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
    }

    override suspend fun connect(url: String, token: String) {
        _connectionState.value = ConnectionState.Connecting

        return suspendCancellableCoroutine { continuation ->
            pendingConnectContinuation = continuation
            pendingConnectParams = ConnectParams(url, token)

            val serviceIntent = Intent(context, HybridStreamingService::class.java)
            context.startForegroundService(serviceIntent)
            context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

            continuation.invokeOnCancellation {
                pendingConnectContinuation = null
                pendingConnectParams = null
                service?.disconnect()
                unbindServiceSafely()
                service = null
                _connectionState.value = ConnectionState.Idle
            }
        }
    }

    override fun setARSurfaceView(surfaceView: SurfaceView) {
        pendingSurfaceView = surfaceView
        service?.setARSurfaceView(surfaceView)
    }

    override fun publishStrokeEvent(event: StrokeEvent) {
        service?.publishStrokeEvent(event)
    }

    override fun disconnect() {
        service?.disconnect()
        unbindServiceSafely()

        service = null
        pendingSurfaceView = null
        pendingConnectContinuation = null
        pendingConnectParams = null
        _connectionState.value = ConnectionState.Idle
        _participantCount.value = 0

        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private fun unbindServiceSafely() {
        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Timber.e(e, "Error unbinding service")
            }
            isServiceBound = false
        }
    }

    private fun observeServiceState() {
        scope.launch {
            service?.connectionState?.collect { state ->
                _connectionState.value = state
            }
        }
        scope.launch {
            service?.participantCount?.collect { count ->
                _participantCount.value = count
            }
        }
    }

    private fun observeRemoteTouchEvents() {
        scope.launch {
            service?.remoteTouchEvents?.collect { event ->
                _remoteTouchEvents.tryEmit(event)
            }
        }
    }

    private data class ConnectParams(val url: String, val token: String)
}
