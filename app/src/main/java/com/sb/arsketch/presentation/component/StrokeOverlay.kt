package com.sb.arsketch.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sb.arsketch.domain.model.ViewerStroke

/**
 * 수신된 스트로크를 2D Canvas 위에 오버레이로 렌더링.
 *
 * AR 좌표계의 x, y를 화면 좌표로 변환하여 표시.
 */
@Composable
fun StrokeOverlay(
    strokes: List<ViewerStroke>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val scale = minOf(size.width, size.height)

        for (stroke in strokes) {
            if (stroke.points.size < 2) continue

            val path = Path().apply {
                val first = stroke.points.first()
                moveTo(
                    centerX + first.x * scale,
                    centerY - first.y * scale
                )
                for (i in 1 until stroke.points.size) {
                    val point = stroke.points[i]
                    lineTo(
                        centerX + point.x * scale,
                        centerY - point.y * scale
                    )
                }
            }

            drawPath(
                path = path,
                color = Color(stroke.color),
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
