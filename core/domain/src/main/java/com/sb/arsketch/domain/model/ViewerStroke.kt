package com.sb.arsketch.domain.model

data class ViewerStroke(
    val id: String,
    val points: List<ViewerPoint>,
    val color: Int,
    val strokeWidth: Float,
    val isComplete: Boolean
)

data class ViewerPoint(
    val x: Float,
    val y: Float
)
