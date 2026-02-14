package com.sb.arsketch.presentation.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ConnectRoute(
    onNavigateToHost: (serverUrl: String, token: String) -> Unit,
    onNavigateToViewer: (serverUrl: String, token: String) -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectEvent.NavigateToHost -> onNavigateToHost(event.serverUrl, event.token)
                is ConnectEvent.NavigateToViewer -> onNavigateToViewer(event.serverUrl, event.token)
            }
        }
    }

    ConnectScreen(
        uiState = uiState,
        onAction = viewModel::onAction
    )
}
