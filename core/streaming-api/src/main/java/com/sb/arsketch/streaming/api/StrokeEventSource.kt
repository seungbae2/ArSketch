package com.sb.arsketch.streaming.api

import com.sb.arsketch.domain.model.ViewerStroke
import kotlinx.coroutines.flow.StateFlow

interface StrokeEventSource {
    val strokes: StateFlow<List<ViewerStroke>>
    fun clear()
}
