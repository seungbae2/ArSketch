package com.sb.arsketch.presentation.connect

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
import javax.inject.Named

@HiltViewModel
class ConnectViewModel @Inject constructor(
    @Named("defaultServerUrl") defaultServerUrl: String,
    @Named("defaultToken") defaultToken: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConnectUiState(serverUrl = defaultServerUrl, token = defaultToken)
    )
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
}
