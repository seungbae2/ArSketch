package com.sb.arsketch.streaming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sb.arsketch.presentation.state.StreamingUiState

/**
 * Hybrid 스트리밍 컨트롤 UI 컴포넌트
 * Camera Track + AR DataChannel 방식
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

                // Room 정보
                if (streamingState.roomName.isNotBlank()) {
                    Text(
                        text = streamingState.roomName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
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
