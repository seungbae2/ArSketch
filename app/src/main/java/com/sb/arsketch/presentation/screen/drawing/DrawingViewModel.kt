package com.sb.arsketch.presentation.screen.drawing

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DrawingViewModel @Inject constructor(
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

    private var currentSessionId: String = UUID.randomUUID().toString()

    fun updateARState(state: ARState) {
        _uiState.update { it.copy(arState = state) }
    }

    fun onTouchStart(point: Point3D) {
        onTouchStartWithAnchor(point, null)
    }

    fun onTouchStartWithAnchor(point: Point3D, anchorId: String?) {
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

    fun onTouchMove(point: Point3D) {
        val currentStroke = _uiState.value.currentStroke ?: return

        val updatedStroke = addPointToStrokeUseCase(currentStroke, point)

        if (updatedStroke !== currentStroke) {
            _uiState.update { it.copy(currentStroke = updatedStroke) }
        }
    }

    fun onTouchEnd() {
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

    fun undo() {
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

    fun redo() {
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

    fun clearAll() {
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

    fun setColor(color: Int) {
        _uiState.update {
            it.copy(brushSettings = it.brushSettings.copy(color = color))
        }
    }

    fun setThickness(thickness: BrushSettings.Thickness) {
        _uiState.update {
            it.copy(brushSettings = it.brushSettings.copy(thickness = thickness))
        }
    }

    fun setDrawingMode(mode: DrawingMode) {
        _uiState.update { it.copy(drawingMode = mode) }
    }

    fun setAirDrawingDepth(depth: Float) {
        _uiState.update { it.copy(airDrawingDepth = depth) }
    }

    fun showSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    fun updateSessionName(name: String) {
        _uiState.update { it.copy(sessionName = name) }
    }

    fun saveSession() {
        val state = _uiState.value

        viewModelScope.launch {
            try {
                saveSessionUseCase(
                    sessionId = currentSessionId,
                    name = state.sessionName.ifBlank { "Drawing ${System.currentTimeMillis()}" },
                    strokes = state.strokes
                )

                _uiState.update {
                    it.copy(
                        showSaveDialog = false,
                        errorMessage = null
                    )
                }

                Timber.d("세션 저장 완료: $currentSessionId")
            } catch (e: Exception) {
                Timber.e(e, "세션 저장 실패")
                _uiState.update {
                    it.copy(errorMessage = "저장 실패: ${e.message}")
                }
            }
        }
    }

    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        clearAll()

        _uiState.update {
            it.copy(sessionName = "")
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 저장된 세션 불러오기
     */
    fun loadSession(sessionId: String) {
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

                    Timber.d("세션 불러오기 완료: ${session.id}, 스트로크 수: ${strokes.size}")
                }
            } catch (e: Exception) {
                Timber.e(e, "세션 불러오기 실패")
                _uiState.update {
                    it.copy(errorMessage = "세션을 불러오는데 실패했습니다")
                }
            }
        }
    }

    fun getStrokesForRendering(): Pair<List<Stroke>, Stroke?> {
        val state = _uiState.value
        return state.strokes to state.currentStroke
    }
}
