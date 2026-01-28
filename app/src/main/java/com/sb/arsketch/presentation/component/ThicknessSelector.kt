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
            displaySize = 12.dp,
            isSelected = selectedThickness == BrushSettings.Thickness.THIN,
            color = currentColor,
            onClick = { onThicknessSelected(BrushSettings.Thickness.THIN) }
        )

        ThicknessItem(
            displaySize = 20.dp,
            isSelected = selectedThickness == BrushSettings.Thickness.MEDIUM,
            color = currentColor,
            onClick = { onThicknessSelected(BrushSettings.Thickness.MEDIUM) }
        )

        ThicknessItem(
            displaySize = 28.dp,
            isSelected = selectedThickness == BrushSettings.Thickness.THICK,
            color = currentColor,
            onClick = { onThicknessSelected(BrushSettings.Thickness.THICK) }
        )
    }
}

@Composable
private fun ThicknessItem(
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
