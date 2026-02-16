package com.sb.arsketch.presentation.host

import android.view.SurfaceView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.ar.core.RemoteBrushInfo
import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.RemoteTouchEvent
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.model.StrokeEvent
import com.sb.arsketch.domain.usecase.stroke.AddPointToStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.ClearAllStrokesUseCase
import com.sb.arsketch.domain.usecase.stroke.CreateStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.RedoStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.UndoStrokeUseCase
import com.sb.arsketch.streaming.api.ConnectionState
import com.sb.arsketch.streaming.api.HostStreamingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class HostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val streamingSession: HostStreamingSession,
    private val drawingController: DrawingController,
    private val createStrokeUseCase: CreateStrokeUseCase,
    private val addPointToStrokeUseCase: AddPointToStrokeUseCase,
    private val undoStrokeUseCase: UndoStrokeUseCase,
    private val redoStrokeUseCase: RedoStrokeUseCase,
    private val clearAllStrokesUseCase: ClearAllStrokesUseCase
) : ViewModel() {

    private val serverUrl: String = URLDecoder.decode(
        savedStateHandle["serverUrl"] ?: "", "UTF-8"
    )
    private val token: String = URLDecoder.decode(
        savedStateHandle["token"] ?: "", "UTF-8"
    )

    private val _uiState = MutableStateFlow(HostUiState())
    val uiState: StateFlow<HostUiState> = _uiState.asStateFlow()

    private val _events = Channel<HostEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // DataChannel throttling
    private var lastEventTime = 0L
    private val eventThrottleMs = 16L

    init {
        observeRemoteTouchEvents()
        observeStreamingState()

        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            startStreaming()
        }
    }

    fun onAction(action: HostAction) {
        when (action) {
            is HostAction.UpdateARState -> _uiState.update { it.copy(arState = action.state) }
            is HostAction.TouchStart -> onTouchStart(action.point, action.anchorId)
            is HostAction.TouchMove -> onTouchMove(action.point)
            is HostAction.TouchEnd -> onTouchEnd()
            is HostAction.Undo -> undo()
            is HostAction.Redo -> redo()
            is HostAction.ClearAll -> clearAll()
            is HostAction.SetColor -> _uiState.update {
                it.copy(brushSettings = it.brushSettings.copy(color = action.color))
            }
            is HostAction.SetThickness -> _uiState.update {
                it.copy(brushSettings = it.brushSettings.copy(thickness = action.thickness))
            }
            is HostAction.SetDrawingMode -> _uiState.update { it.copy(drawingMode = action.mode) }
            is HostAction.SetAirDrawingDepth -> _uiState.update { it.copy(airDrawingDepth = action.depth) }
            is HostAction.ToggleShowPlanes -> _uiState.update { it.copy(showPlanes = !it.showPlanes) }
            is HostAction.RemoteTouchStart -> onRemoteTouchStart(
                action.point, action.anchorId, action.color, action.thickness, action.mode
            )
            is HostAction.Disconnect -> disconnect()
            is HostAction.ClearError -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun onTouchStart(point: Point3D, anchorId: String?) {
        val state = _uiState.value
        val stroke = createStrokeUseCase(
            startPoint = point,
            brush = state.brushSettings,
            mode = state.drawingMode,
            anchorId = anchorId
        )

        _uiState.update {
            it.copy(currentStroke = stroke, undoneStrokes = emptyList(), canRedo = false)
        }

        publishStrokeEvent(
            StrokeEvent.Started(
                strokeId = stroke.id,
                startPoint = point,
                color = state.brushSettings.color,
                thickness = state.brushSettings.thickness.value,
                mode = state.drawingMode
            )
        )
    }

    private fun onTouchMove(point: Point3D) {
        val currentStroke = _uiState.value.currentStroke ?: return
        val updatedStroke = addPointToStrokeUseCase(currentStroke, point)

        if (updatedStroke !== currentStroke) {
            _uiState.update { it.copy(currentStroke = updatedStroke) }

            val now = System.currentTimeMillis()
            if (now - lastEventTime >= eventThrottleMs) {
                lastEventTime = now
                publishStrokeEvent(
                    StrokeEvent.PointAdded(strokeId = currentStroke.id, point = point)
                )
            }
        }
    }

    private fun onTouchEnd() {
        val currentStroke = _uiState.value.currentStroke ?: return

        if (currentStroke.isValid()) {
            _uiState.update {
                it.copy(
                    strokes = it.strokes + currentStroke,
                    currentStroke = null,
                    canUndo = true
                )
            }
            publishStrokeEvent(StrokeEvent.Ended(strokeId = currentStroke.id))
        } else {
            _uiState.update { it.copy(currentStroke = null) }
        }
    }

    private fun onRemoteTouchStart(
        point: Point3D, anchorId: String?, color: Int, thickness: Float, mode: DrawingMode
    ) {
        val brush = BrushSettings(
            color = color,
            thickness = BrushSettings.Thickness.entries.minByOrNull {
                kotlin.math.abs(it.value - thickness)
            } ?: BrushSettings.Thickness.MEDIUM
        )
        val stroke = createStrokeUseCase(
            startPoint = point, brush = brush, mode = mode, anchorId = anchorId
        )
        _uiState.update {
            it.copy(currentStroke = stroke, undoneStrokes = emptyList(), canRedo = false)
        }
        publishStrokeEvent(
            StrokeEvent.Started(
                strokeId = stroke.id,
                startPoint = point,
                color = color,
                thickness = thickness,
                mode = mode
            )
        )
    }

    fun handleRemoteTouchEvent(event: RemoteTouchEvent) {
        val vpWidth = drawingController.getViewportWidth()
        val vpHeight = drawingController.getViewportHeight()
        if (vpWidth <= 0 || vpHeight <= 0) return

        when (event) {
            is RemoteTouchEvent.TouchDown -> {
                val screenX = event.normalizedX * vpWidth
                val screenY = event.normalizedY * vpHeight
                drawingController.onRemoteTouchDown(
                    screenX, screenY,
                    RemoteBrushInfo(event.color, event.thickness, event.mode, event.senderId)
                )
            }
            is RemoteTouchEvent.TouchMove -> {
                val screenX = event.normalizedX * vpWidth
                val screenY = event.normalizedY * vpHeight
                drawingController.onTouchMove(screenX, screenY)
            }
            is RemoteTouchEvent.TouchUp -> {
                drawingController.onTouchUp()
            }
        }
    }

    private fun undo() {
        val state = _uiState.value
        val lastStroke = state.strokes.lastOrNull()

        val (newStrokes, newUndoneStrokes) = undoStrokeUseCase(
            strokes = state.strokes,
            undoneStrokes = state.undoneStrokes
        )

        _uiState.update {
            it.copy(
                strokes = newStrokes,
                undoneStrokes = newUndoneStrokes,
                canUndo = newStrokes.isNotEmpty(),
                canRedo = newUndoneStrokes.isNotEmpty()
            )
        }

        lastStroke?.let {
            publishStrokeEvent(StrokeEvent.Deleted(strokeId = it.id))
        }
    }

    private fun redo() {
        val state = _uiState.value
        val restoredStroke = state.undoneStrokes.lastOrNull()

        val (newStrokes, newUndoneStrokes) = redoStrokeUseCase(
            strokes = state.strokes,
            undoneStrokes = state.undoneStrokes
        )

        _uiState.update {
            it.copy(
                strokes = newStrokes,
                undoneStrokes = newUndoneStrokes,
                canUndo = newStrokes.isNotEmpty(),
                canRedo = newUndoneStrokes.isNotEmpty()
            )
        }

        restoredStroke?.let {
            publishStrokeEvent(StrokeEvent.Restored(strokeId = it.id))
        }
    }

    private fun clearAll() {
        val (newStrokes, newUndoneStrokes) = clearAllStrokesUseCase()

        _uiState.update {
            it.copy(
                strokes = newStrokes,
                undoneStrokes = newUndoneStrokes,
                currentStroke = null,
                canUndo = false,
                canRedo = false
            )
        }

        publishStrokeEvent(StrokeEvent.AllCleared())
    }

    fun getStrokesForRendering(): Pair<List<Stroke>, Stroke?> {
        val state = _uiState.value
        return state.strokes to state.currentStroke
    }

    /**
     * AR SurfaceView가 생성된 후 호출.
     * 서비스에 뷰를 전달하면, 서비스가 Room 연결과 뷰 모두 준비될 때 캡처를 시작합니다.
     */
    fun setSurfaceView(surfaceView: SurfaceView) {
        streamingSession.setARSurfaceView(surfaceView)
    }

    // ========== Streaming ==========

    private fun startStreaming() {
        viewModelScope.launch {
            try {
                streamingSession.connect(url = serverUrl, token = token)
                Timber.d("Streaming started")
            } catch (e: Exception) {
                Timber.e(e, "Streaming failed")
                _events.send(HostEvent.Error("스트리밍 연결 실패: ${e.message}"))
            }
        }
    }

    private fun observeRemoteTouchEvents() {
        viewModelScope.launch {
            streamingSession.remoteTouchEvents.collect { event ->
                handleRemoteTouchEvent(event)
            }
        }
    }

    private fun observeStreamingState() {
        viewModelScope.launch {
            streamingSession.connectionState.collect { state ->
                val uiStreamingState = when (state) {
                    is ConnectionState.Idle -> StreamingUiState.Idle
                    is ConnectionState.Connecting -> StreamingUiState.Connecting
                    is ConnectionState.Connected -> StreamingUiState.Streaming(roomName = state.roomName)
                    is ConnectionState.Error -> StreamingUiState.Error(state.message)
                }
                _uiState.update { it.copy(streamingState = uiStreamingState) }
            }
        }
        viewModelScope.launch {
            streamingSession.participantCount.collect { count ->
                _uiState.update { it.copy(participantCount = count) }
            }
        }
    }

    private fun publishStrokeEvent(event: StrokeEvent) {
        if (_uiState.value.streamingState is StreamingUiState.Streaming) {
            streamingSession.publishStrokeEvent(event)
        }
    }

    private fun disconnect() {
        streamingSession.disconnect()
        _uiState.update { it.copy(streamingState = StreamingUiState.Idle) }
        viewModelScope.launch { _events.send(HostEvent.Disconnected) }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
