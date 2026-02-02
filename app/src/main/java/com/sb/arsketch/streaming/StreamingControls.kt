package com.sb.arsketch.streaming

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sb.arsketch.presentation.state.StreamingUiState

/**
 * AR 스트리밍 컨트롤 UI 컴포넌트 (FBO 기반)
 *
 * @param streamingState 현재 스트리밍 상태
 * @param onStartStreaming 스트리밍 시작 콜백
 * @param onStopStreaming 스트리밍 중지 콜백
 */
@Composable
fun ARStreamingControls(
    streamingState: StreamingUiState,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (streamingState) {
            is StreamingUiState.Idle -> {
                Button(
                    onClick = onStartStreaming,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "스트리밍",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            is StreamingUiState.Connecting -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "연결 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            is StreamingUiState.Streaming -> {
                // LIVE 배지
                Badge(
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // 해상도/FPS 정보
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = streamingState.resolution,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (streamingState.fps > 0) {
                        Text(
                            text = "${streamingState.fps.toInt()} FPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 중지 버튼
                Button(
                    onClick = onStopStreaming,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "중지",
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            is StreamingUiState.Error -> {
                Text(
                    text = "오류: ${streamingState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onStartStreaming) {
                    Text("재시도")
                }
            }
        }
    }
}

// ========== Legacy Screen Capture 방식 (유지) ==========

/**
 * 스트리밍 컨트롤 UI 컴포넌트 (Screen Capture 방식 - Legacy)
 */
@Composable
fun StreamingControls(
    modifier: Modifier = Modifier,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Screen Capture 권한 요청 런처
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onScreenCapturePermissionGranted(
            resultCode = result.resultCode,
            data = result.data
        )
    }

    // 에러 다이얼로그
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("오류") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("확인")
                }
            }
        )
    }

    Row(
        modifier = modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            uiState.isConnecting -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = "연결 중...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            uiState.isStreaming -> {
                // 스트리밍 중 표시
                Badge(
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Button(
                    onClick = { viewModel.stopStreaming() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("공유 중지")
                }
            }

            else -> {
                Button(
                    onClick = {
                        val intent = viewModel.createScreenCaptureIntent()
                        screenCaptureLauncher.launch(intent)
                    }
                ) {
                    Text("화면 공유 시작")
                }
            }
        }
    }
}
