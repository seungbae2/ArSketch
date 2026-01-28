package com.sb.arsketch.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sb.arsketch.domain.model.BrushSettings

@Composable
fun BrushToolbar(
    brushSettings: BrushSettings,
    onColorSelected: (Int) -> Unit,
    onThicknessSelected: (BrushSettings.Thickness) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorPicker(
            selectedColor = brushSettings.color,
            onColorSelected = onColorSelected
        )

        ThicknessSelector(
            selectedThickness = brushSettings.thickness,
            currentColor = brushSettings.color,
            onThicknessSelected = onThicknessSelected
        )
    }
}
