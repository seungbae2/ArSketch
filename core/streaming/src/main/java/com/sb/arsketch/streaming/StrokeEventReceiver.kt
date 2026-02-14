package com.sb.arsketch.streaming

import com.sb.arsketch.domain.model.StrokeEvent
import com.sb.arsketch.domain.model.ViewerPoint
import com.sb.arsketch.domain.model.ViewerStroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import com.sb.arsketch.streaming.api.StrokeEventSource
import timber.log.Timber

/**
 * DataChannel에서 수신한 StrokeEvent를 ViewerStroke로 재구성.
 *
 * 3D Point3D → 2D ViewerPoint 변환은 단순 x,y 투영 사용 (추후 개선 가능).
 * 모든 mutable state 접근은 lock으로 동기화됩니다.
 */
class StrokeEventReceiver : StrokeEventSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    // 완료된 스트로크
    private val completedStrokes = mutableListOf<ViewerStroke>()
    // 진행 중인 스트로크 (strokeId → mutable points)
    private val activeStrokes = mutableMapOf<String, MutableViewerStroke>()
    // Undo된 스트로크 (Redo 복원용)
    private val deletedStrokes = mutableMapOf<String, ViewerStroke>()

    private val _strokes = MutableStateFlow<List<ViewerStroke>>(emptyList())
    override val strokes: StateFlow<List<ViewerStroke>> = _strokes.asStateFlow()

    private data class MutableViewerStroke(
        val id: String,
        val points: MutableList<ViewerPoint>,
        val color: Int,
        val strokeWidth: Float
    )

    fun onDataReceived(data: ByteArray) {
        try {
            val jsonString = data.toString(Charsets.UTF_8)
            val event = json.decodeFromString<StrokeEvent>(jsonString)
            processEvent(event)
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize StrokeEvent")
        }
    }

    private fun processEvent(event: StrokeEvent) {
        val snapshot = synchronized(lock) {
            when (event) {
                is StrokeEvent.Started -> {
                    val point = ViewerPoint(event.startPoint.x, event.startPoint.y)
                    activeStrokes[event.strokeId] = MutableViewerStroke(
                        id = event.strokeId,
                        points = mutableListOf(point),
                        color = event.color,
                        strokeWidth = event.thickness * THICKNESS_SCALE
                    )
                }
                is StrokeEvent.PointAdded -> {
                    activeStrokes[event.strokeId]?.points?.add(
                        ViewerPoint(event.point.x, event.point.y)
                    )
                }
                is StrokeEvent.Ended -> {
                    activeStrokes.remove(event.strokeId)?.let { active ->
                        completedStrokes.add(
                            ViewerStroke(
                                id = active.id,
                                points = active.points.toList(),
                                color = active.color,
                                strokeWidth = active.strokeWidth,
                                isComplete = true
                            )
                        )
                    }
                }
                is StrokeEvent.Deleted -> {
                    activeStrokes.remove(event.strokeId)
                    val removed = completedStrokes.firstOrNull { it.id == event.strokeId }
                    if (removed != null) {
                        deletedStrokes[event.strokeId] = removed
                        completedStrokes.removeAll { it.id == event.strokeId }
                    }
                }
                is StrokeEvent.Restored -> {
                    deletedStrokes.remove(event.strokeId)?.let { stroke ->
                        completedStrokes.add(stroke)
                    }
                }
                is StrokeEvent.AllCleared -> {
                    activeStrokes.clear()
                    completedStrokes.clear()
                    deletedStrokes.clear()
                }
            }
            buildSnapshot()
        }
        _strokes.value = snapshot
    }

    private fun buildSnapshot(): List<ViewerStroke> {
        val activeList = activeStrokes.values.map { active ->
            ViewerStroke(
                id = active.id,
                points = active.points.toList(),
                color = active.color,
                strokeWidth = active.strokeWidth,
                isComplete = false
            )
        }
        return completedStrokes.toList() + activeList
    }

    override fun clear() {
        synchronized(lock) {
            activeStrokes.clear()
            completedStrokes.clear()
            deletedStrokes.clear()
        }
        _strokes.value = emptyList()
    }

    companion object {
        // AR 좌표 (미터) → 화면 스트로크 두께 스케일링
        private const val THICKNESS_SCALE = 500f
    }
}
