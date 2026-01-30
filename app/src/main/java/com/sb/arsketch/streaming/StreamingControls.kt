package com.sb.arsketch.streaming

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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

/**
 * 스트리밍 컨트롤 UI 컴포넌트
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
