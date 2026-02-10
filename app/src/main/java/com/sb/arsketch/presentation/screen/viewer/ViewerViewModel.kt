package com.sb.arsketch.presentation.screen.viewer

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.domain.model.ViewerStroke
import com.sb.arsketch.streaming.StrokeEventReceiver
import com.sb.arsketch.streaming.ViewerConnectionManager
import com.sb.arsketch.streaming.ViewerConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class ViewerUiState(
    val connectionState: ViewerConnectionState = ViewerConnectionState.Disconnected,
    val strokes: List<ViewerStroke> = emptyList(),
    val participantCount: Int = 0
)

sealed interface ViewerAction {
    data object Disconnect : ViewerAction
}

sealed interface ViewerEvent {
    data class Error(val message: String) : ViewerEvent
    data object Disconnected : ViewerEvent
}

@Suppress("StaticFieldLeak") // Application context injected by Hilt, no leak
@HiltViewModel
class ViewerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverUrl: String = URLDecoder.decode(
        savedStateHandle["serverUrl"] ?: "", "UTF-8"
    )
    private val token: String = URLDecoder.decode(
        savedStateHandle["token"] ?: "", "UTF-8"
    )

    private val strokeEventReceiver = StrokeEventReceiver()
    private val connectionManager = ViewerConnectionManager(context, strokeEventReceiver)

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val _events = Channel<ViewerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            connectionManager.connect(serverUrl, token)
        }
        observeConnectionState()
        observeStrokes()
        observeParticipantCount()
    }

    fun onAction(action: ViewerAction) {
        when (action) {
            is ViewerAction.Disconnect -> disconnect()
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is ViewerConnectionState.Error) {
                    _events.send(ViewerEvent.Error(state.message))
                }
            }
        }
    }

    private fun observeStrokes() {
        viewModelScope.launch {
            strokeEventReceiver.strokes.collect { strokes ->
                _uiState.update { it.copy(strokes = strokes) }
            }
        }
    }

    private fun observeParticipantCount() {
        viewModelScope.launch {
            connectionManager.participantCount.collect { count ->
                _uiState.update { it.copy(participantCount = count) }
            }
        }
    }

    private fun disconnect() {
        connectionManager.disconnect()
        viewModelScope.launch { _events.send(ViewerEvent.Disconnected) }
    }

    override fun onCleared() {
        super.onCleared()
        connectionManager.destroy()
    }
}
