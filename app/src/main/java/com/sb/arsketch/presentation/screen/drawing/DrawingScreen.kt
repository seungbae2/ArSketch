package com.sb.arsketch.presentation.screen.drawing

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sb.arsketch.ar.core.ARGLSurfaceView
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.presentation.component.ActionToolbar
import com.sb.arsketch.presentation.component.BrushToolbar
import com.sb.arsketch.presentation.component.DepthSlider
import com.sb.arsketch.presentation.component.DrawingModeToggle
import com.sb.arsketch.presentation.component.PlaneVisibilityToggle
import com.sb.arsketch.presentation.component.SaveSessionDialog
import com.sb.arsketch.presentation.component.TrackingStatusIndicator
import com.sb.arsketch.presentation.state.DrawingUiState
import com.sb.arsketch.streaming.StreamingControls

/**
 * Stateless 드로잉 화면
 * UI 렌더링만 담당하며, 모든 상태와 이벤트는 외부에서 주입받음
 */
@Composable
fun DrawingScreen(
    uiState: DrawingUiState,
    snackbarHostState: SnackbarHostState,
    hasCameraPermission: Boolean,
    isSessionReady: Boolean,
    isStreamingMode: Boolean,
    onAction: (DrawingAction) -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateBack: () -> Unit,
    arViewFactory: (Context, (ARGLSurfaceView) -> Unit) -> ARGLSurfaceView
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // AR GLSurfaceView - 권한과 세션 모두 준비되었을 때만 표시
        if (hasCameraPermission && isSessionReady) {
            AndroidView(
                factory = { ctx ->
                    arViewFactory(ctx) { _ -> }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 상단 뒤로가기 버튼
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기"
                )
            }
        }

        // 스트리밍 컨트롤 (스트리밍 모드일 때만)
        if (isStreamingMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 8.dp, top = 8.dp)
            ) {
                StreamingControls()
            }
        }

        // 상단 상태 표시 및 모드 토글
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TrackingStatusIndicator(arState = uiState.arState)

            Spacer(modifier = Modifier.height(8.dp))

            DrawingModeToggle(
                currentMode = uiState.drawingMode,
                onModeChange = { mode -> onAction(DrawingAction.SetDrawingMode(mode)) }
            )

            // Surface 모드일 때 평면 표시 토글
            if (uiState.drawingMode == DrawingMode.SURFACE) {
                Spacer(modifier = Modifier.height(8.dp))
                PlaneVisibilityToggle(
                    showPlanes = uiState.showPlanes,
                    onToggle = { onAction(DrawingAction.ToggleShowPlanes) }
                )
            }

            // Air 모드일 때만 깊이 슬라이더 표시
            if (uiState.drawingMode == DrawingMode.AIR) {
                Spacer(modifier = Modifier.height(8.dp))
                DepthSlider(
                    depth = uiState.airDrawingDepth,
                    onDepthChange = { depth -> onAction(DrawingAction.SetAirDrawingDepth(depth)) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // 하단 툴바
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            BrushToolbar(
                brushSettings = uiState.brushSettings,
                onColorSelected = { color -> onAction(DrawingAction.SetColor(color)) },
                onThicknessSelected = { thickness -> onAction(DrawingAction.SetThickness(thickness)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionToolbar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = { onAction(DrawingAction.Undo) },
                onRedo = { onAction(DrawingAction.Redo) },
                onClear = { onAction(DrawingAction.ClearAll) },
                onSave = { onAction(DrawingAction.ShowSaveDialog) },
                onShowSessions = onNavigateToSessions
            )
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 140.dp)
        )

        // 저장 다이얼로그
        if (uiState.showSaveDialog) {
            SaveSessionDialog(
                sessionName = uiState.sessionName,
                onSessionNameChange = { name -> onAction(DrawingAction.UpdateSessionName(name)) },
                onDismiss = { onAction(DrawingAction.DismissSaveDialog) },
                onConfirm = { onAction(DrawingAction.SaveSession) }
            )
        }
    }
}
