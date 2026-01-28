package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

class UndoStrokeUseCase @Inject constructor() {
    operator fun invoke(
        strokes: List<Stroke>,
        undoneStrokes: List<Stroke>
    ): Pair<List<Stroke>, List<Stroke>> {
        if (strokes.isEmpty()) {
            return strokes to undoneStrokes
        }

        val removedStroke = strokes.last()
        val newStrokes = strokes.dropLast(1)
        val newUndoneStrokes = undoneStrokes + removedStroke

        return newStrokes to newUndoneStrokes
    }
}
