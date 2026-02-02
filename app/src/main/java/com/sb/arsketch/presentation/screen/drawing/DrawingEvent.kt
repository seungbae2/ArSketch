package com.sb.arsketch.presentation.screen.drawing

/**
 * 일회성 UI 이벤트를 정의하는 sealed interface
 * Channel을 통해 전달되어 한 번만 소비됨
 */
sealed interface DrawingEvent {
    // Snackbar 메시지
    data class ShowSnackbar(val message: String) : DrawingEvent

    // 세션 저장 성공
    data class SessionSaved(val sessionId: String, val sessionName: String) : DrawingEvent

    // 세션 로드 성공
    data class SessionLoaded(val sessionName: String, val strokeCount: Int) : DrawingEvent

    // 에러 발생
    data class Error(val message: String) : DrawingEvent

    // 스트리밍 시작
    data object StreamingStarted : DrawingEvent

    // 스트리밍 중지
    data object StreamingStopped : DrawingEvent
}
