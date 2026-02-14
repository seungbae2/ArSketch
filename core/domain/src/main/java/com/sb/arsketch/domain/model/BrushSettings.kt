package com.sb.arsketch.domain.model

data class BrushSettings(
    val color: Int,
    val thickness: Thickness
) {
    enum class Thickness(val value: Float) {
        THIN(0.003f),
        MEDIUM(0.006f),
        THICK(0.012f)
    }

    companion object {
        val COLORS = listOf(
            0xFFFF0000.toInt(),
            0xFFFF8800.toInt(),
            0xFFFFFF00.toInt(),
            0xFF00FF00.toInt(),
            0xFF0088FF.toInt(),
            0xFF8800FF.toInt(),
            0xFFFFFFFF.toInt(),
            0xFF000000.toInt()
        )

        val DEFAULT = BrushSettings(
            color = COLORS[4],
            thickness = Thickness.MEDIUM
        )
    }
}
