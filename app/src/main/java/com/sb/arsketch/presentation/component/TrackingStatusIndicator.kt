package com.sb.arsketch.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sb.arsketch.presentation.state.ARState

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
        Canvas(
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
