package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UndoRedoUseCaseTest {

    private lateinit var undoUseCase: UndoStrokeUseCase
    private lateinit var redoUseCase: RedoStrokeUseCase

    @Before
    fun setup() {
        undoUseCase = UndoStrokeUseCase()
        redoUseCase = RedoStrokeUseCase()
    }

    private fun createStroke(id: String): Stroke {
        return Stroke(
            id = id,
            points = listOf(Point3D.ZERO, Point3D(0.1f, 0f, 0f)),
            color = BrushSettings.DEFAULT.color,
            thickness = BrushSettings.DEFAULT.thickness.value,
            mode = DrawingMode.SURFACE
        )
    }

    @Test
    fun `Undo 시 마지막 스트로크가 undoneStrokes로 이동`() {
        // Given
        val stroke1 = createStroke("stroke-1")
        val stroke2 = createStroke("stroke-2")
        val strokes = listOf(stroke1, stroke2)
        val undoneStrokes = emptyList<Stroke>()

        // When
        val (newStrokes, newUndoneStrokes) = undoUseCase(strokes, undoneStrokes)

        // Then
        assertEquals(1, newStrokes.size)
        assertEquals(stroke1, newStrokes[0])
        assertEquals(1, newUndoneStrokes.size)
        assertEquals(stroke2, newUndoneStrokes[0])
    }

    @Test
    fun `빈 strokes에서 Undo 시 변화 없음`() {
        // Given
        val strokes = emptyList<Stroke>()
        val undoneStrokes = emptyList<Stroke>()

        // When
        val (newStrokes, newUndoneStrokes) = undoUseCase(strokes, undoneStrokes)

        // Then
        assertTrue(newStrokes.isEmpty())
        assertTrue(newUndoneStrokes.isEmpty())
    }

    @Test
    fun `Redo 시 undoneStrokes에서 strokes로 복원`() {
        // Given
        val stroke1 = createStroke("stroke-1")
        val stroke2 = createStroke("stroke-2")
        val strokes = listOf(stroke1)
        val undoneStrokes = listOf(stroke2)

        // When
        val (newStrokes, newUndoneStrokes) = redoUseCase(strokes, undoneStrokes)

        // Then
        assertEquals(2, newStrokes.size)
        assertEquals(stroke2, newStrokes[1])
        assertTrue(newUndoneStrokes.isEmpty())
    }

    @Test
    fun `Undo 후 Redo 시 원래 상태로 복원`() {
        // Given
        val stroke = createStroke("stroke-1")
        val strokes = listOf(stroke)
        val undoneStrokes = emptyList<Stroke>()

        // When
        val (afterUndo, afterUndoUndone) = undoUseCase(strokes, undoneStrokes)
        val (afterRedo, afterRedoUndone) = redoUseCase(afterUndo, afterUndoUndone)

        // Then
        assertEquals(1, afterRedo.size)
        assertEquals(stroke, afterRedo[0])
        assertTrue(afterRedoUndone.isEmpty())
    }
}
