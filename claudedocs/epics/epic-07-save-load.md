# Epic 7: 저장/불러오기

## 개요
- **목표**: 드로잉 세션 저장 및 불러오기 기능 구현
- **예상 작업량**: 중간
- **의존성**: Epic 3 (Data Layer), Epic 6 (AR Drawing) 완료

---

## 기능 명세

### 저장 기능
- 현재 드로잉을 Room 데이터베이스에 저장
- 세션 이름 지정 (다이얼로그)
- 모든 스트로크 데이터 직렬화

### 불러오기 기능
- 저장된 세션 목록 표시
- 선택한 세션의 스트로크 복원
- AR 공간에 스트로크 다시 렌더링

### 세션 관리
- 세션 목록 조회
- 세션 삭제
- 최근 순 정렬

---

## 작업 목록

### Task 7.1: Session List ViewModel

#### SessionListViewModel.kt
**파일**: `presentation/screen/sessions/SessionListViewModel.kt`

```kotlin
package com.sb.arsketch.presentation.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.usecase.session.DeleteSessionUseCase
import com.sb.arsketch.domain.usecase.session.GetAllSessionsUseCase
import com.sb.arsketch.domain.usecase.session.LoadSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 세션 목록 UI 상태
 */
data class SessionListUiState(
    val sessions: List<DrawingSession> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionToDelete: DrawingSession? = null  // 삭제 확인 다이얼로그용
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val getAllSessionsUseCase: GetAllSessionsUseCase,
    private val loadSessionUseCase: LoadSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    /**
     * 세션 목록 로드
     */
    private fun loadSessions() {
        viewModelScope.launch {
            getAllSessionsUseCase()
                .catch { e ->
                    Timber.e(e, "세션 목록 로드 실패")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "세션 목록을 불러오는데 실패했습니다"
                        )
                    }
                }
                .collect { sessions ->
                    _uiState.update {
                        it.copy(
                            sessions = sessions,
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * 삭제 확인 다이얼로그 표시
     */
    fun showDeleteConfirmation(session: DrawingSession) {
        _uiState.update { it.copy(sessionToDelete = session) }
    }

    /**
     * 삭제 확인 다이얼로그 닫기
     */
    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(sessionToDelete = null) }
    }

    /**
     * 세션 삭제
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                deleteSessionUseCase(sessionId)
                _uiState.update { it.copy(sessionToDelete = null) }
                Timber.d("세션 삭제 완료: $sessionId")
            } catch (e: Exception) {
                Timber.e(e, "세션 삭제 실패")
                _uiState.update {
                    it.copy(
                        errorMessage = "세션 삭제에 실패했습니다",
                        sessionToDelete = null
                    )
                }
            }
        }
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
```

---

### Task 7.2: Session List Screen

#### SessionListScreen.kt
**파일**: `presentation/screen/sessions/SessionListScreen.kt`

```kotlin
package com.sb.arsketch.presentation.screen.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sb.arsketch.domain.model.DrawingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 저장된 세션 목록 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onSessionSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("저장된 드로잉") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.sessions.isEmpty() -> {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    SessionList(
                        sessions = uiState.sessions,
                        onSessionClick = onSessionSelected,
                        onDeleteClick = viewModel::showDeleteConfirmation
                    )
                }
            }

            // 에러 메시지
            uiState.errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }

        // 삭제 확인 다이얼로그
        uiState.sessionToDelete?.let { session ->
            DeleteConfirmationDialog(
                sessionName = session.name,
                onConfirm = { viewModel.deleteSession(session.id) },
                onDismiss = viewModel::dismissDeleteConfirmation
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "저장된 드로잉이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AR 드로잉을 저장하면 여기에 표시됩니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionList(
    sessions: List<DrawingSession>,
    onSessionClick: (String) -> Unit,
    onDeleteClick: (DrawingSession) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = sessions,
            key = { it.id }
        ) { session ->
            SessionItem(
                session = session,
                onClick = { onSessionClick(session.id) },
                onDeleteClick = { onDeleteClick(session) }
            )
        }
    }
}

@Composable
private fun SessionItem(
    session: DrawingSession,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${session.strokes.size}개의 스트로크 • ${formatDate(session.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    sessionName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("드로잉 삭제") },
        text = { Text("'$sessionName'을(를) 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
```

---

### Task 7.3: 세션 불러오기 기능 추가

#### DrawingViewModel.kt 업데이트

```kotlin
// DrawingViewModel.kt에 추가할 메서드

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
```

---

### Task 7.4: Navigation 업데이트

#### ArSketchNavGraph.kt 업데이트

```kotlin
package com.sb.arsketch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.screen.drawing.DrawingScreen
import com.sb.arsketch.presentation.screen.drawing.DrawingViewModel
import com.sb.arsketch.presentation.screen.sessions.SessionListScreen

/**
 * 네비게이션 라우트 정의
 */
object Routes {
    const val DRAWING = "drawing"
    const val DRAWING_WITH_SESSION = "drawing/{sessionId}"
    const val SESSION_LIST = "sessions"

    fun drawingWithSession(sessionId: String) = "drawing/$sessionId"
}

/**
 * 앱 네비게이션 그래프
 */
@Composable
fun ArSketchNavGraph(
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DRAWING
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 새 드로잉 화면
        composable(Routes.DRAWING) {
            DrawingScreen(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                }
            )
        }

        // 저장된 세션 불러오기
        composable(
            route = Routes.DRAWING_WITH_SESSION,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            val viewModel: DrawingViewModel = hiltViewModel()

            // 세션 ID로 불러오기
            LaunchedEffect(sessionId) {
                sessionId?.let { viewModel.loadSession(it) }
            }

            DrawingScreen(
                viewModel = viewModel,
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                }
            )
        }

        // 세션 목록 화면
        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSessionSelected = { sessionId ->
                    navController.navigate(Routes.drawingWithSession(sessionId)) {
                        // 기존 드로잉 화면 제거
                        popUpTo(Routes.DRAWING) { inclusive = true }
                    }
                }
            )
        }
    }
}
```

---

### Task 7.5: DrawingScreen에 세션 목록 버튼 추가

#### DrawingScreen 업데이트

```kotlin
// ActionToolbar에 목록 보기 버튼 추가

@Composable
fun ActionToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onShowSessions: () -> Unit,  // 추가
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

        ActionButton(
            icon = Icons.Default.FolderOpen,  // 추가
            contentDescription = "불러오기",
            enabled = true,
            onClick = onShowSessions
        )
    }
}
```

---

## 데이터 흐름

```
┌─────────────────────────────────────────────────────────────┐
│                        저장 흐름                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Save 버튼 클릭                                              │
│       │                                                      │
│       ▼                                                      │
│  SaveSessionDialog                                           │
│       │                                                      │
│       ▼                                                      │
│  DrawingViewModel.saveSession()                              │
│       │                                                      │
│       ▼                                                      │
│  SaveSessionUseCase                                          │
│       │                                                      │
│       ├──▶ SessionRepository.updateSession()                 │
│       │                                                      │
│       └──▶ StrokeRepository.saveStrokes()                   │
│               │                                              │
│               ▼                                              │
│         Room Database                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      불러오기 흐름                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  SessionListScreen                                           │
│       │                                                      │
│       ▼                                                      │
│  세션 선택                                                   │
│       │                                                      │
│       ▼                                                      │
│  Navigation: drawingWithSession(sessionId)                   │
│       │                                                      │
│       ▼                                                      │
│  DrawingViewModel.loadSession(sessionId)                     │
│       │                                                      │
│       ▼                                                      │
│  LoadSessionUseCase                                          │
│       │                                                      │
│       ├──▶ SessionRepository.getSession()                    │
│       │                                                      │
│       └──▶ StrokeRepository.getStrokesForSession()          │
│               │                                              │
│               ▼                                              │
│         UI State 업데이트                                    │
│               │                                              │
│               ▼                                              │
│         ARRenderer.updateStrokes()                           │
│               │                                              │
│               ▼                                              │
│         화면에 스트로크 렌더링                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 완료 조건

- [ ] SessionListViewModel 구현 완료
- [ ] SessionListScreen UI 구현 완료
- [ ] 세션 불러오기 기능 구현
- [ ] Navigation 업데이트 완료
- [ ] 저장 버튼 동작 확인
- [ ] 세션 목록 표시 확인
- [ ] 세션 선택 시 불러오기 확인
- [ ] 세션 삭제 동작 확인
- [ ] 불러온 세션 AR 렌더링 확인

---

## 다음 단계

→ [Epic 8: Plus 기능](epic-08-plus-features.md)
