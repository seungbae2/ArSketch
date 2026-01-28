# Epic 5: Presentation Layer

## 개요
- **목표**: Jetpack Compose UI 구현 및 ViewModel 연동
- **예상 작업량**: 중간
- **의존성**: Epic 2 (Domain), Epic 4 (AR Foundation) 완료

---

## UI 설계

### 화면 구성

```
┌─────────────────────────────────────────┐
│  [상태 표시바]    Tracking / Searching  │
├─────────────────────────────────────────┤
│                                         │
│                                         │
│           [AR 카메라 뷰]                │
│                                         │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│ [브러시 툴바]                           │
│ 🔴 🟠 🟡 🟢 🔵 🟣   ● ◉ ⬤             │
├─────────────────────────────────────────┤
│ [액션 툴바]                             │
│ ↶ Undo │ ↷ Redo │ 🗑️ Clear │ 💾 Save   │
└─────────────────────────────────────────┘
```

---

## 작업 목록

### Task 5.1: UI State 정의

#### DrawingUiState.kt
**파일**: `presentation/state/DrawingUiState.kt`

```kotlin
package com.sb.arsketch.presentation.state

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Stroke

/**
 * 드로잉 화면 UI 상태
 */
data class DrawingUiState(
    // AR 상태
    val arState: ARState = ARState.Initializing,

    // 드로잉 상태
    val strokes: List<Stroke> = emptyList(),
    val currentStroke: Stroke? = null,
    val undoneStrokes: List<Stroke> = emptyList(),

    // 브러시 설정
    val brushSettings: BrushSettings = BrushSettings.DEFAULT,
    val drawingMode: DrawingMode = DrawingMode.SURFACE,

    // UI 상태
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showSaveDialog: Boolean = false,
    val sessionName: String = "",

    // 에러 상태
    val errorMessage: String? = null
)

/**
 * AR 추적 상태
 */
sealed class ARState {
    object Initializing : ARState()
    object Searching : ARState()  // 평면 검색 중
    object Tracking : ARState()   // 정상 추적 중
    object Paused : ARState()     // 일시 중지
    data class Error(val message: String) : ARState()
}
```

---

### Task 5.2: Drawing ViewModel

#### DrawingViewModel.kt
**파일**: `presentation/screen/drawing/DrawingViewModel.kt`

```kotlin
package com.sb.arsketch.presentation.screen.drawing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import com.sb.arsketch.domain.usecase.session.CreateSessionUseCase
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
    private val createSessionUseCase: CreateSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawingUiState())
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()

    // 현재 세션 ID
    private var currentSessionId: String = UUID.randomUUID().toString()

    // ==================== AR 상태 ====================

    fun updateARState(state: ARState) {
        _uiState.update { it.copy(arState = state) }
    }

    // ==================== 드로잉 액션 ====================

    /**
     * 터치 시작 - 새 스트로크 생성
     */
    fun onTouchStart(point: Point3D) {
        val state = _uiState.value
        val stroke = createStrokeUseCase(
            startPoint = point,
            brush = state.brushSettings,
            mode = state.drawingMode
        )

        _uiState.update {
            it.copy(
                currentStroke = stroke,
                // 새 스트로크 시작 시 Redo 스택 클리어
                undoneStrokes = emptyList(),
                canRedo = false
            )
        }

        Timber.d("스트로크 시작: ${stroke.id}")
    }

    /**
     * 터치 이동 - 스트로크에 점 추가
     */
    fun onTouchMove(point: Point3D) {
        val currentStroke = _uiState.value.currentStroke ?: return

        val updatedStroke = addPointToStrokeUseCase(currentStroke, point)

        if (updatedStroke !== currentStroke) {
            _uiState.update { it.copy(currentStroke = updatedStroke) }
        }
    }

    /**
     * 터치 종료 - 스트로크 완료
     */
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
            // 유효하지 않은 스트로크는 버림
            _uiState.update { it.copy(currentStroke = null) }
            Timber.d("스트로크 취소 (유효하지 않음)")
        }
    }

    /**
     * Undo
     */
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

    /**
     * Redo
     */
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

    /**
     * 모두 지우기
     */
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

    // ==================== 브러시 설정 ====================

    /**
     * 색상 변경
     */
    fun setColor(color: Int) {
        _uiState.update {
            it.copy(brushSettings = it.brushSettings.copy(color = color))
        }
    }

    /**
     * 두께 변경
     */
    fun setThickness(thickness: BrushSettings.Thickness) {
        _uiState.update {
            it.copy(brushSettings = it.brushSettings.copy(thickness = thickness))
        }
    }

    /**
     * 드로잉 모드 변경
     */
    fun setDrawingMode(mode: DrawingMode) {
        _uiState.update { it.copy(drawingMode = mode) }
    }

    // ==================== 저장 ====================

    /**
     * 저장 다이얼로그 표시
     */
    fun showSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = true) }
    }

    /**
     * 저장 다이얼로그 닫기
     */
    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false) }
    }

    /**
     * 세션 이름 업데이트
     */
    fun updateSessionName(name: String) {
        _uiState.update { it.copy(sessionName = name) }
    }

    /**
     * 현재 세션 저장
     */
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

    /**
     * 새 세션 시작
     */
    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        clearAll()

        _uiState.update {
            it.copy(sessionName = "")
        }
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ==================== 외부 데이터 접근 ====================

    /**
     * 현재 스트로크 데이터 (렌더러용)
     */
    fun getStrokesForRendering(): Pair<List<Stroke>, Stroke?> {
        val state = _uiState.value
        return state.strokes to state.currentStroke
    }
}
```

---

### Task 5.3: 색상 선택 컴포넌트

#### ColorPicker.kt
**파일**: `presentation/component/ColorPicker.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sb.arsketch.domain.model.BrushSettings

/**
 * 색상 선택 컴포넌트
 */
@Composable
fun ColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BrushSettings.COLORS.forEach { color ->
            ColorItem(
                color = color,
                isSelected = color == selectedColor,
                onClick = { onColorSelected(color) }
            )
        }
    }
}

@Composable
private fun ColorItem(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, Color.White, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                }
            )
            .clickable(onClick = onClick)
    )
}
```

---

### Task 5.4: 두께 선택 컴포넌트

#### ThicknessSelector.kt
**파일**: `presentation/component/ThicknessSelector.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sb.arsketch.domain.model.BrushSettings

/**
 * 두께 선택 컴포넌트
 */
@Composable
fun ThicknessSelector(
    selectedThickness: BrushSettings.Thickness,
    currentColor: Int,
    onThicknessSelected: (BrushSettings.Thickness) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThicknessItem(
            thickness = BrushSettings.Thickness.THIN,
            displaySize = 12.dp,
            isSelected = selectedThickness == BrushSettings.Thickness.THIN,
            color = currentColor,
            onClick = { onThicknessSelected(BrushSettings.Thickness.THIN) }
        )

        ThicknessItem(
            thickness = BrushSettings.Thickness.MEDIUM,
            displaySize = 20.dp,
            isSelected = selectedThickness == BrushSettings.Thickness.MEDIUM,
            color = currentColor,
            onClick = { onThicknessSelected(BrushSettings.Thickness.MEDIUM) }
        )

        ThicknessItem(
            thickness = BrushSettings.Thickness.THICK,
            displaySize = 28.dp,
            isSelected = selectedThickness == BrushSettings.Thickness.THICK,
            color = currentColor,
            onClick = { onThicknessSelected(BrushSettings.Thickness.THICK) }
        )
    }
}

@Composable
private fun ThicknessItem(
    thickness: BrushSettings.Thickness,
    displaySize: Dp,
    isSelected: Boolean,
    color: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.White, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(displaySize)
                .clip(CircleShape)
                .background(Color(color))
        )
    }
}
```

---

### Task 5.5: 브러시 툴바

#### BrushToolbar.kt
**파일**: `presentation/component/BrushToolbar.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sb.arsketch.domain.model.BrushSettings

/**
 * 브러시 설정 툴바
 */
@Composable
fun BrushToolbar(
    brushSettings: BrushSettings,
    onColorSelected: (Int) -> Unit,
    onThicknessSelected: (BrushSettings.Thickness) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorPicker(
            selectedColor = brushSettings.color,
            onColorSelected = onColorSelected
        )

        ThicknessSelector(
            selectedThickness = brushSettings.thickness,
            currentColor = brushSettings.color,
            onThicknessSelected = onThicknessSelected
        )
    }
}
```

---

### Task 5.6: 액션 툴바

#### ActionToolbar.kt
**파일**: `presentation/component/ActionToolbar.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 액션 버튼 툴바
 */
@Composable
fun ActionToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = Icons.AutoMirrored.Filled.Undo,
            contentDescription = "실행 취소",
            enabled = canUndo,
            onClick = onUndo
        )

        ActionButton(
            icon = Icons.Default.Redo,
            contentDescription = "다시 실행",
            enabled = canRedo,
            onClick = onRedo
        )

        ActionButton(
            icon = Icons.Default.Delete,
            contentDescription = "모두 지우기",
            enabled = true,
            onClick = onClear
        )

        ActionButton(
            icon = Icons.Default.Save,
            contentDescription = "저장",
            enabled = true,
            onClick = onSave
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.Gray.copy(alpha = 0.5f)
        )
    }
}
```

---

### Task 5.7: AR 추적 상태 인디케이터

#### TrackingStatusIndicator.kt
**파일**: `presentation/component/TrackingStatusIndicator.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sb.arsketch.presentation.state.ARState

/**
 * AR 추적 상태 표시 인디케이터
 */
@Composable
fun TrackingStatusIndicator(
    arState: ARState,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when (arState) {
        is ARState.Initializing -> "초기화 중..." to Color.Yellow
        is ARState.Searching -> "평면 검색 중..." to Color.Yellow
        is ARState.Tracking -> "추적 중" to Color.Green
        is ARState.Paused -> "일시 정지" to Color.Gray
        is ARState.Error -> arState.message to Color.Red
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        label = "status_color"
    )

    Row(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 상태 표시 점
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
        ) {
            drawCircle(color = animatedColor)
        }

        Text(
            text = statusText,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
```

---

### Task 5.8: 저장 다이얼로그

#### SaveSessionDialog.kt
**파일**: `presentation/component/SaveSessionDialog.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 세션 저장 다이얼로그
 */
@Composable
fun SaveSessionDialog(
    sessionName: String,
    onSessionNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("드로잉 저장") },
        text = {
            Column {
                Text("저장할 드로잉의 이름을 입력하세요.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = onSessionNameChange,
                    label = { Text("이름") },
                    placeholder = { Text("내 드로잉") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
```

---

### Task 5.9: Navigation 설정

#### ArSketchNavGraph.kt
**파일**: `presentation/navigation/ArSketchNavGraph.kt`

```kotlin
package com.sb.arsketch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sb.arsketch.presentation.screen.drawing.DrawingScreen
import com.sb.arsketch.presentation.screen.sessions.SessionListScreen

/**
 * 네비게이션 라우트 정의
 */
object Routes {
    const val DRAWING = "drawing"
    const val SESSION_LIST = "sessions"
}

/**
 * 앱 네비게이션 그래프
 */
@Composable
fun ArSketchNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DRAWING
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.DRAWING) {
            DrawingScreen(
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                }
            )
        }

        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSessionSelected = { sessionId ->
                    // TODO: Load session and navigate back
                    navController.popBackStack()
                }
            )
        }
    }
}
```

---

## 완료 조건

- [ ] UI State 클래스 정의 완료
- [ ] DrawingViewModel 구현 완료
- [ ] ColorPicker 컴포넌트 구현
- [ ] ThicknessSelector 컴포넌트 구현
- [ ] BrushToolbar 컴포넌트 구현
- [ ] ActionToolbar 컴포넌트 구현
- [ ] TrackingStatusIndicator 컴포넌트 구현
- [ ] SaveSessionDialog 컴포넌트 구현
- [ ] Navigation 설정 완료
- [ ] `./gradlew assembleDebug` 빌드 성공

---

## 다음 단계

→ [Epic 6: AR Drawing 구현](epic-06-ar-drawing.md)
