package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.Stroke
import javax.inject.Inject

class ClearAllStrokesUseCase @Inject constructor() {
    operator fun invoke(): Pair<List<Stroke>, List<Stroke>> {
        return emptyList<Stroke>() to emptyList()
    }
}
