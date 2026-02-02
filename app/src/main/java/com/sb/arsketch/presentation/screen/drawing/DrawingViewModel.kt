package com.sb.arsketch.presentation.screen.drawing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.usecase.session.CreateSessionUseCase
import com.sb.arsketch.domain.usecase.session.LoadSessionUseCase
import com.sb.arsketch.domain.usecase.session.SaveSessionUseCase
import com.sb.arsketch.domain.usecase.stroke.AddPointToStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.ClearAllStrokesUseCase
import com.sb.arsketch.domain.usecase.stroke.CreateStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.RedoStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.UndoStrokeUseCase
import com.sb.arsketch.presentation.state.ARState
import com.sb.arsketch.presentation.state.DrawingUiState
import com.sb.arsketch.presentation.state.StreamingUiState
import com.sb.arsketch.streaming.ARStreamingService
import com.sb.arsketch.streaming.StreamingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DrawingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val createStrokeUseCase: CreateStrokeUseCase,
    private val addPointToStrokeUseCase: AddPointToStrokeUseCase,
    private val undoStrokeUseCase: UndoStrokeUseCase,
    private val redoStrokeUseCase: RedoStrokeUseCase,
    private val clearAllStrokesUseCase: ClearAllStrokesUseCase,
    private val saveSessionUseCase: SaveSessionUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val loadSessionUseCase: LoadSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()

    private val _events = Channel<DrawingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentSessionId: String = UUID.randomUUID().toString()

    // AR 스트리밍
    private var streamingService: ARStreamingService? = null
    private var isServiceBound = false
    private var pendingStreamingConfig: StreamingConfig? = null

    private data class StreamingConfig(
        val url: String,
        val token: String,
        val width: Int,
        val height: Int
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ARStreamingService.LocalBinder
            streamingService = binder.getService()
            isServiceBound = true
            Timber.d("ARStreamingService connected")

            // 대기 중인 스트리밍 시작
            pendingStreamingConfig?.let { config ->
                startStreamingWithService(config)
                pendingStreamingConfig = null
            }

            // 서비스 상태 관찰
            observeStreamingState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
            isServiceBound = false
            Timber.d("ARStreamingService disconnected")

            _uiState.update { it.copy(streamingState = StreamingUiState.Idle) }
        }
    }

    private fun observeStreamingState() {
        viewModelScope.launch {
            streamingService?.streamingState?.collect { state ->
                val uiState = when (state) {
                    is StreamingState.Idle -> StreamingUiState.Idle
                    is StreamingState.Connecting -> StreamingUiState.Connecting
                    is StreamingState.Streaming -> StreamingUiState.Streaming(
                        roomName = state.roomName,
                        resolution = state.resolution,
                        fps = streamingService?.getCurrentFps() ?: 0f
                    )
                    is StreamingState.Error -> StreamingUiState.Error(state.message)
                }
                _uiState.update { it.copy(streamingState = uiState) }
            }
        }
    }

    /**
     * 모든 사용자 액션의 단일 진입점
     */
    fun onAction(action: DrawingAction) {
        when (action) {
            // AR 상태
            is DrawingAction.UpdateARState -> updateARState(action.state)

            // 터치 이벤트
            is DrawingAction.TouchStart -> onTouchStart(action.point, action.anchorId)
            is DrawingAction.TouchMove -> onTouchMove(action.point)
            is DrawingAction.TouchEnd -> onTouchEnd()

            // Undo/Redo/Clear
            is DrawingAction.Undo -> undo()
            is DrawingAction.Redo -> redo()
            is DrawingAction.ClearAll -> clearAll()

            // 브러시 설정
            is DrawingAction.SetColor -> setColor(action.color)
            is DrawingAction.SetThickness -> setThickness(action.thickness)

            // 드로잉 모드
            is DrawingAction.SetDrawingMode -> setDrawingMode(action.mode)
            is DrawingAction.SetAirDrawingDepth -> setAirDrawingDepth(action.depth)
            is DrawingAction.ToggleShowPlanes -> toggleShowPlanes()

            // 세션 관리
            is DrawingAction.ShowSaveDialog -> showSaveDialog()
            is DrawingAction.DismissSaveDialog -> dismissSaveDialog()
            is DrawingAction.UpdateSessionName -> updateSessionName(action.name)
            is DrawingAction.SaveSession -> saveSession()
            is DrawingAction.StartNewSession -> startNewSession()
            is DrawingAction.LoadSession -> loadSession(action.sessionId)

            // 에러 처리
            is DrawingAction.ClearError -> clearError()

            // AR 스트리밍
            is DrawingAction.StartStreaming -> startStreaming(
                action.url,
                action.token,
                action.width,
                action.height
            )
            is DrawingAction.StopStreaming -> stopStreaming()
        }
    }

    private fun updateARState(state: ARState) {
        _uiState.update { it.copy(arState = state) }
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
            it.copy(
                currentStroke = stroke,
                undoneStrokes = emptyList(),
                canRedo = false
            )
        }

        Timber.d("스트로크 시작: ${stroke.id}, anchorId: $anchorId")
    }

    private fun onTouchMove(point: Point3D) {
        val currentStroke = _uiState.value.currentStroke ?: return

        val updatedStroke = addPointToStrokeUseCase(currentStroke, point)

        if (updatedStroke !== currentStroke) {
            _uiState.update { it.copy(currentStroke = updatedStroke) }
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
            Timber.d("스트로크 완료: ${currentStroke.id}, 점 개수: ${currentStroke.points.size}")
        } else {
            _uiState.update { it.copy(currentStroke = null) }
            Timber.d("스트로크 취소 (유효하지 않음)")
        }
    }

    private fun undo() {
        val state = _uiState.value
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

        Timber.d("Undo 실행, 남은 스트로크: ${newStrokes.size}")
    }

    private fun redo() {
        val state = _uiState.value
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

        Timber.d("Redo 실행, 총 스트로크: ${newStrokes.size}")
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

        Timber.d("모두 지우기 실행")
    }

    private fun setColor(color: Int) {
        _uiState.update {
            it.copy(brushSettings = it.brushSettings.copy(color = color))
        }
    }

    private fun setThickness(thickness: BrushSettings.Thickness) {
        _uiState.update {
            it.copy(brushSettings = it.brushSettings.copy(thickness = thickness))
        }
    }

    private fun setDrawingMode(mode: DrawingMode) {
        _uiState.update { it.copy(drawingMode = mode) }
    }

    private fun setAirDrawingDepth(depth: Float) {
        _uiState.update { it.copy(airDrawingDepth = depth) }
    }

    private fun toggleShowPlanes() {
        _uiState.update { it.copy(showPlanes = !it.showPlanes) }
    }

    private fun showSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    private fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    private fun updateSessionName(name: String) {
        _uiState.update { it.copy(sessionName = name) }
    }

    private fun saveSession() {
        val state = _uiState.value
        val sessionName = state.sessionName.ifBlank { "Drawing ${System.currentTimeMillis()}" }

        viewModelScope.launch {
            try {
                saveSessionUseCase(
                    sessionId = currentSessionId,
                    name = sessionName,
                    strokes = state.strokes
                )

                _uiState.update {
                    it.copy(showSaveDialog = false)
                }

                _events.send(DrawingEvent.SessionSaved(currentSessionId, sessionName))
                Timber.d("세션 저장 완료: $currentSessionId")
            } catch (e: Exception) {
                Timber.e(e, "세션 저장 실패")
                _events.send(DrawingEvent.Error("저장 실패: ${e.message}"))
            }
        }
    }

    private fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        clearAll()

        _uiState.update {
            it.copy(sessionName = "")
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val result = loadSessionUseCase(sessionId)
                if (result != null) {
                    val (session, strokes) = result
                    currentSessionId = session.id

                    _uiState.update {
                        it.copy(
                            strokes = strokes,
                            currentStroke = null,
                            undoneStrokes = emptyList(),
                            sessionName = session.name,
                            canUndo = strokes.isNotEmpty(),
                            canRedo = false
                        )
                    }

                    _events.send(DrawingEvent.SessionLoaded(session.name, strokes.size))
                    Timber.d("세션 불러오기 완료: ${session.id}, 스트로크 수: ${strokes.size}")
                }
            } catch (e: Exception) {
                Timber.e(e, "세션 불러오기 실패")
                _events.send(DrawingEvent.Error("세션을 불러오는데 실패했습니다"))
            }
        }
    }

    fun getStrokesForRendering(): Pair<List<Stroke>, Stroke?> {
        val state = _uiState.value
        return state.strokes to state.currentStroke
    }

    // ========== AR 스트리밍 ==========

    private fun startStreaming(url: String, token: String, width: Int, height: Int) {
        if (_uiState.value.streamingState !is StreamingUiState.Idle) {
            Timber.w("Already streaming or connecting")
            return
        }

        _uiState.update { it.copy(streamingState = StreamingUiState.Connecting) }

        val config = StreamingConfig(url, token, width, height)

        if (isServiceBound && streamingService != null) {
            startStreamingWithService(config)
        } else {
            // 서비스 시작 및 바인딩
            pendingStreamingConfig = config
            val serviceIntent = Intent(context, ARStreamingService::class.java)
            context.startForegroundService(serviceIntent)
            context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startStreamingWithService(config: StreamingConfig) {
        streamingService?.connect(
            url = config.url,
            token = config.token,
            width = config.width,
            height = config.height,
            fps = 30,
            onSuccess = {
                Timber.d("Streaming started successfully")
                _events.trySend(DrawingEvent.StreamingStarted)
            },
            onError = { e ->
                Timber.e(e, "Streaming failed")
                _uiState.update {
                    it.copy(streamingState = StreamingUiState.Error(e.message ?: "Connection failed"))
                }
                _events.trySend(DrawingEvent.Error("스트리밍 시작 실패: ${e.message}"))
            }
        )
    }

    private fun stopStreaming() {
        streamingService?.disconnect()

        if (isServiceBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Timber.e(e, "Error unbinding service")
            }
            isServiceBound = false
        }

        streamingService = null
        _uiState.update { it.copy(streamingState = StreamingUiState.Idle) }
        _events.trySend(DrawingEvent.StreamingStopped)
    }

    /**
     * ARRenderer에서 호출되는 프레임 콜백.
     * GLThread에서 호출됩니다.
     *
     * @param bitmap 합성된 AR 프레임
     */
    fun onFrameComposited(bitmap: Bitmap) {
        if (_uiState.value.streamingState is StreamingUiState.Streaming) {
            streamingService?.pushFrame(bitmap)
        }
    }

    /**
     * 스트리밍 활성화 여부
     */
    fun isStreaming(): Boolean = _uiState.value.streamingState is StreamingUiState.Streaming

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }
}
