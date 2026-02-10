package com.sb.arsketch.presentation.screen.host

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sb.arsketch.ar.core.ARGLSurfaceView
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.presentation.component.ActionToolbar
import com.sb.arsketch.presentation.component.BrushToolbar
import com.sb.arsketch.presentation.component.DepthSlider
import com.sb.arsketch.presentation.component.DrawingModeToggle
import com.sb.arsketch.presentation.component.PlaneVisibilityToggle
import com.sb.arsketch.presentation.component.TrackingStatusIndicator
import com.sb.arsketch.presentation.state.DrawingUiState
import com.sb.arsketch.presentation.state.StreamingUiState

@Composable
fun HostScreen(
    uiState: DrawingUiState,
    snackbarHostState: SnackbarHostState,
    hasCameraPermission: Boolean,
    isSessionReady: Boolean,
    onAction: (HostAction) -> Unit,
    arViewFactory: (Context, (ARGLSurfaceView) -> Unit) -> ARGLSurfaceView
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // AR GLSurfaceView
        if (hasCameraPermission && isSessionReady) {
            AndroidView(
                factory = { ctx -> arViewFactory(ctx) { _ -> } },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 상단: Tracking status
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TrackingStatusIndicator(arState = uiState.arState)

            Spacer(modifier = Modifier.height(8.dp))

            DrawingModeToggle(
                currentMode = uiState.drawingMode,
                onModeChange = { mode -> onAction(HostAction.SetDrawingMode(mode)) }
            )

            if (uiState.drawingMode == DrawingMode.SURFACE) {
                Spacer(modifier = Modifier.height(8.dp))
                PlaneVisibilityToggle(
                    showPlanes = uiState.showPlanes,
                    onToggle = { onAction(HostAction.ToggleShowPlanes) }
                )
            }

            if (uiState.drawingMode == DrawingMode.AIR) {
                Spacer(modifier = Modifier.height(8.dp))
                DepthSlider(
                    depth = uiState.airDrawingDepth,
                    onDepthChange = { depth -> onAction(HostAction.SetAirDrawingDepth(depth)) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // 하단: 스트리밍 상태 + 브러시 + 액션 + 연결 해제
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 스트리밍 상태 바
            StreamingStatusBar(
                streamingState = uiState.streamingState,
                participantCount = uiState.participantCount
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 브러시 도구
            BrushToolbar(
                brushSettings = uiState.brushSettings,
                onColorSelected = { color -> onAction(HostAction.SetColor(color)) },
                onThicknessSelected = { thickness -> onAction(HostAction.SetThickness(thickness)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 액션 도구
            ActionToolbar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = { onAction(HostAction.Undo) },
                onRedo = { onAction(HostAction.Redo) },
                onClear = { onAction(HostAction.ClearAll) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 연결 해제 버튼
            Button(
                onClick = { onAction(HostAction.Disconnect) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect")
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 200.dp)
        )
    }
}

@Composable
private fun StreamingStatusBar(
    streamingState: StreamingUiState,
    participantCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (streamingState) {
            is StreamingUiState.Idle -> {
                Text("대기 중", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            is StreamingUiState.Connecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("연결 중...", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            is StreamingUiState.Streaming -> {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(
                        "LIVE",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "$participantCount",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is StreamingUiState.Error -> {
                Text(
                    "오류: ${streamingState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
