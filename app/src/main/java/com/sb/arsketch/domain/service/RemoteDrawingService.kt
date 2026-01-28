package com.sb.arsketch.domain.service

import com.sb.arsketch.domain.model.StrokeEvent
import kotlinx.coroutines.flow.Flow

/**
 * 원격 드로잉 서비스 인터페이스
 * 향후 WebRTC 구현 시 사용
 */
interface RemoteDrawingService {

    /**
     * 연결 상태
     */
    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * 연결 상태 Flow
     */
    val connectionState: Flow<ConnectionState>

    /**
     * 수신된 원격 이벤트 Flow
     */
    val remoteEvents: Flow<StrokeEvent>

    /**
     * 세션 연결
     */
    suspend fun connect(sessionCode: String)

    /**
     * 세션 연결 해제
     */
    suspend fun disconnect()

    /**
     * 이벤트 전송
     */
    suspend fun sendEvent(event: StrokeEvent)

    /**
     * 새 세션 생성 및 코드 반환
     */
    suspend fun createSession(): String
}
