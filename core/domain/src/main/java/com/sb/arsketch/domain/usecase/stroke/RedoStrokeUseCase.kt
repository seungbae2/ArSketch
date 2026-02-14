package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

class RedoStrokeUseCase @Inject constructor() {
    operator fun invoke(
        strokes: List<Stroke>,
        undoneStrokes: List<Stroke>
    ): Pair<List<Stroke>, List<Stroke>> {
        if (undoneStrokes.isEmpty()) {
            return strokes to undoneStrokes
        }

        val restoredStroke = undoneStrokes.last()
        val newStrokes = strokes + restoredStroke
        val newUndoneStrokes = undoneStrokes.dropLast(1)

        return newStrokes to newUndoneStrokes
    }
}
