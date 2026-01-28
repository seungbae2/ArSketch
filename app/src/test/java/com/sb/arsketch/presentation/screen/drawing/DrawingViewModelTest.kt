package com.sb.arsketch.presentation.screen.drawing

import com.sb.arsketch.domain.model.Point3D
import com.sb.arsketch.domain.usecase.session.CreateSessionUseCase
import com.sb.arsketch.domain.usecase.session.LoadSessionUseCase
import com.sb.arsketch.domain.usecase.session.SaveSessionUseCase
import com.sb.arsketch.domain.usecase.stroke.AddPointToStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.ClearAllStrokesUseCase
import com.sb.arsketch.domain.usecase.stroke.CreateStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.RedoStrokeUseCase
import com.sb.arsketch.domain.usecase.stroke.UndoStrokeUseCase
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingViewModelTest {

    private lateinit var viewModel: DrawingViewModel
    private val testDispatcher = StandardTestDispatcher()

    // Use Cases
    private val createStrokeUseCase = CreateStrokeUseCase()
    private val addPointToStrokeUseCase = AddPointToStrokeUseCase()
    private val undoStrokeUseCase = UndoStrokeUseCase()
    private val redoStrokeUseCase = RedoStrokeUseCase()
    private val clearAllStrokesUseCase = ClearAllStrokesUseCase()
    private val saveSessionUseCase: SaveSessionUseCase = mockk()
    private val createSessionUseCase: CreateSessionUseCase = mockk()
    private val loadSessionUseCase: LoadSessionUseCase = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DrawingViewModel(
            createStrokeUseCase = createStrokeUseCase,
            addPointToStrokeUseCase = addPointToStrokeUseCase,
            undoStrokeUseCase = undoStrokeUseCase,
            redoStrokeUseCase = redoStrokeUseCase,
            clearAllStrokesUseCase = clearAllStrokesUseCase,
            saveSessionUseCase = saveSessionUseCase,
            createSessionUseCase = createSessionUseCase,
            loadSessionUseCase = loadSessionUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `터치 시작 시 currentStroke 생성`() = runTest {
        // Given
        val startPoint = Point3D(1f, 0f, 0f)

        // When
        viewModel.onTouchStart(startPoint)

        // Then
        val state = viewModel.uiState.value
        assertNotNull(state.currentStroke)
        assertEquals(1, state.currentStroke?.points?.size)
        assertEquals(startPoint, state.currentStroke?.points?.first())
    }

    @Test
    fun `터치 이동 시 currentStroke에 점 추가`() = runTest {
        // Given
        val startPoint = Point3D(0f, 0f, 0f)
        val movePoint = Point3D(0.1f, 0f, 0f)  // 10cm

        viewModel.onTouchStart(startPoint)

        // When
        viewModel.onTouchMove(movePoint)

        // Then
        val state = viewModel.uiState.value
        assertEquals(2, state.currentStroke?.points?.size)
    }

    @Test
    fun `터치 종료 시 currentStroke가 strokes로 이동`() = runTest {
        // Given
        val startPoint = Point3D(0f, 0f, 0f)
        val endPoint = Point3D(0.1f, 0f, 0f)

        viewModel.onTouchStart(startPoint)
        viewModel.onTouchMove(endPoint)

        // When
        viewModel.onTouchEnd()

        // Then
        val state = viewModel.uiState.value
        assertNull(state.currentStroke)
        assertEquals(1, state.strokes.size)
        assertTrue(state.canUndo)
    }

    @Test
    fun `Undo 시 canUndo와 canRedo 상태 업데이트`() = runTest {
        // Given - 스트로크 하나 생성
        viewModel.onTouchStart(Point3D(0f, 0f, 0f))
        viewModel.onTouchMove(Point3D(0.1f, 0f, 0f))
        viewModel.onTouchEnd()

        // When
        viewModel.undo()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.strokes.isEmpty())
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test
    fun `색상 변경 시 brushSettings 업데이트`() = runTest {
        // Given
        val newColor = 0xFFFF0000.toInt()

        // When
        viewModel.setColor(newColor)

        // Then
        assertEquals(newColor, viewModel.uiState.value.brushSettings.color)
    }

    @Test
    fun `clearAll 시 모든 스트로크 삭제`() = runTest {
        // Given
        repeat(3) { i ->
            viewModel.onTouchStart(Point3D(i.toFloat(), 0f, 0f))
            viewModel.onTouchMove(Point3D(i.toFloat() + 0.1f, 0f, 0f))
            viewModel.onTouchEnd()
        }

        // When
        viewModel.clearAll()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.strokes.isEmpty())
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
    }
}
