package com.sb.arsketch.presentation.screen.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.domain.model.RoomRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUiState(
    val serverUrl: String = ConnectViewModel.DEFAULT_SERVER_URL,
    val token: String = ConnectViewModel.DEFAULT_TOKEN,
    val role: RoomRole = RoomRole.HOST,
    val error: String? = null
)

sealed interface ConnectAction {
    data class UpdateUrl(val url: String) : ConnectAction
    data class UpdateToken(val token: String) : ConnectAction
    data class SetRole(val role: RoomRole) : ConnectAction
    data object Connect : ConnectAction
    data object ClearError : ConnectAction
}

sealed interface ConnectEvent {
    data class NavigateToHost(val serverUrl: String, val token: String) : ConnectEvent
    data class NavigateToViewer(val serverUrl: String, val token: String) : ConnectEvent
}

@HiltViewModel
class ConnectViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private val _events = Channel<ConnectEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: ConnectAction) {
        when (action) {
            is ConnectAction.UpdateUrl -> _uiState.update { it.copy(serverUrl = action.url) }
            is ConnectAction.UpdateToken -> _uiState.update { it.copy(token = action.token) }
            is ConnectAction.SetRole -> _uiState.update { it.copy(role = action.role) }
            is ConnectAction.ClearError -> _uiState.update { it.copy(error = null) }
            is ConnectAction.Connect -> connect()
        }
    }

    private fun connect() {
        val state = _uiState.value

        if (state.serverUrl.isBlank()) {
            _uiState.update { it.copy(error = "서버 URL을 입력해주세요") }
            return
        }
        if (state.token.isBlank()) {
            _uiState.update { it.copy(error = "토큰을 입력해주세요") }
            return
        }

        val event = when (state.role) {
            RoomRole.HOST -> ConnectEvent.NavigateToHost(state.serverUrl, state.token)
            RoomRole.VIEWER -> ConnectEvent.NavigateToViewer(state.serverUrl, state.token)
        }
        viewModelScope.launch { _events.send(event) }
    }

    companion object {
        const val DEFAULT_SERVER_URL = "wss://ardrawing-xabqpgun.livekit.cloud"
        const val DEFAULT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzE0MDAxMzYsImlkZW50aXR5IjoiYW5kcm9pZC1ob3N0IiwiaXNzIjoiQVBJb3dMNkNRdjM4M21BIiwibmFtZSI6IkFuZHJvaWQgSG9zdCIsIm5iZiI6MTc3MDc5NTMzNiwic3ViIjoiYW5kcm9pZC1ob3N0IiwidmlkZW8iOnsiY2FuUHVibGlzaCI6dHJ1ZSwiY2FuUHVibGlzaERhdGEiOnRydWUsImNhblN1YnNjcmliZSI6dHJ1ZSwicm9vbSI6ImFyc2tldGNoIiwicm9vbUpvaW4iOnRydWV9fQ.4bHkPDiq6eajimMcdgr1eo9awL7m88-IUhvKXvTF1tw"
    }
}
