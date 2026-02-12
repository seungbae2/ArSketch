package com.sb.arsketch.presentation.host

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sb.arsketch.ar.core.ARGLSurfaceView
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.presentation.host.component.ActionToolbar
import com.sb.arsketch.presentation.host.component.BrushToolbar
import com.sb.arsketch.presentation.host.component.DepthSlider
import com.sb.arsketch.presentation.host.component.DrawingModeToggle
import com.sb.arsketch.presentation.host.component.PlaneVisibilityToggle
import com.sb.arsketch.presentation.host.component.TrackingStatusIndicator

@Composable
fun HostScreen(
    uiState: HostUiState,
    snackbarHostState: SnackbarHostState,
    hasCameraPermission: Boolean,
    isSessionReady: Boolean,
    onAction: (HostAction) -> Unit,
    arViewFactory: (Context, (ARGLSurfaceView) -> Unit) -> ARGLSurfaceView
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        // AR GLSurfaceView
        if (hasCameraPermission && isSessionReady) {
            AndroidView(
                factory = { ctx -> arViewFactory(ctx) { _ -> } },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLandscape) {
            LandscapeOverlay(uiState = uiState, onAction = onAction)
        } else {
            PortraitOverlay(uiState = uiState, onAction = onAction)
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 16.dp else 200.dp)
        )
    }
}

// ─── Portrait: 상단 + 하단 ───

@Composable
private fun PortraitOverlay(
    uiState: HostUiState,
    onAction: (HostAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 상단
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

        // 하단
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StreamingStatusBar(
                streamingState = uiState.streamingState,
                participantCount = uiState.participantCount
            )

            Spacer(modifier = Modifier.height(8.dp))

            BrushToolbar(
                brushSettings = uiState.brushSettings,
                onColorSelected = { color -> onAction(HostAction.SetColor(color)) },
                onThicknessSelected = { thickness -> onAction(HostAction.SetThickness(thickness)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionToolbar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = { onAction(HostAction.Undo) },
                onRedo = { onAction(HostAction.Redo) },
                onClear = { onAction(HostAction.ClearAll) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DisconnectButton(
                onClick = { onAction(HostAction.Disconnect) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Landscape: 오른쪽 패널 ───

@Composable
private fun LandscapeOverlay(
    uiState: HostUiState,
    onAction: (HostAction) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 좌상단: 트래킹 상태
        TrackingStatusIndicator(
            arState = uiState.arState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 12.dp)
        )

        // 우측 패널
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(220.dp)
                .navigationBarsPadding()
                .padding(vertical = 8.dp, horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 스트리밍 상태
            StreamingStatusBar(
                streamingState = uiState.streamingState,
                participantCount = uiState.participantCount,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 드로잉 모드
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
                    onDepthChange = { depth -> onAction(HostAction.SetAirDrawingDepth(depth)) }
                )
            }

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

            // 연결 해제
            DisconnectButton(
                onClick = { onAction(HostAction.Disconnect) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── 공통 컴포넌트 ───

@Composable
private fun DisconnectButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        ),
        modifier = modifier
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

@Composable
private fun StreamingStatusBar(
    streamingState: StreamingUiState,
    participantCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
