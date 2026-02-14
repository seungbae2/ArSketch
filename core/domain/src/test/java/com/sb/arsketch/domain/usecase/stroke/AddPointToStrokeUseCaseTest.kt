package com.sb.arsketch.domain.usecase.stroke

import com.sb.arsketch.domain.model.BrushSettings
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class AddPointToStrokeUseCaseTest {

    private lateinit var useCase: AddPointToStrokeUseCase

    @Before
    fun setup() {
        useCase = AddPointToStrokeUseCase()
    }

    private fun createStroke(points: List<Point3D> = listOf(Point3D.ZERO)): Stroke {
        return Stroke(
            id = "test-stroke",
            points = points,
            color = BrushSettings.DEFAULT.color,
            thickness = BrushSettings.DEFAULT.thickness.value,
            mode = DrawingMode.SURFACE
        )
    }

    @Test
    fun `새 점이 최소 거리 이상이면 추가됨`() {
        // Given
        val stroke = createStroke(listOf(Point3D(0f, 0f, 0f)))
        val newPoint = Point3D(0.01f, 0f, 0f)  // 10mm > 5mm

        // When
        val result = useCase(stroke, newPoint)

        // Then
        assertEquals(2, result.points.size)
        assertEquals(newPoint, result.points.last())
    }

    @Test
    fun `새 점이 최소 거리 미만이면 무시됨`() {
        // Given
        val stroke = createStroke(listOf(Point3D(0f, 0f, 0f)))
        val newPoint = Point3D(0.003f, 0f, 0f)  // 3mm < 5mm

        // When
        val result = useCase(stroke, newPoint)

        // Then
        assertSame(stroke, result)  // 같은 객체 반환
        assertEquals(1, result.points.size)
    }

    @Test
    fun `최대 점 개수 초과 시 추가 무시`() {
        // Given
        val maxPoints = AddPointToStrokeUseCase.MAX_POINTS
        val points = (0 until maxPoints).map { Point3D(it.toFloat() * 0.01f, 0f, 0f) }
        val stroke = createStroke(points)
        val newPoint = Point3D(maxPoints.toFloat() * 0.01f, 0f, 0f)

        // When
        val result = useCase(stroke, newPoint)

        // Then
        assertSame(stroke, result)
        assertEquals(maxPoints, result.points.size)
    }
}
