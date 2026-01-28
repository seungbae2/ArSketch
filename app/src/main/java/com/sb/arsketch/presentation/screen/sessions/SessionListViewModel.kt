package com.sb.arsketch.presentation.screen.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sb.arsketch.domain.model.DrawingSession
import com.sb.arsketch.domain.usecase.session.DeleteSessionUseCase
import com.sb.arsketch.domain.usecase.session.GetAllSessionsUseCase
import com.sb.arsketch.domain.usecase.session.LoadSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 세션 목록 UI 상태
 */
data class SessionListUiState(
    val sessions: List<DrawingSession> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val sessionToDelete: DrawingSession? = null  // 삭제 확인 다이얼로그용
)

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val getAllSessionsUseCase: GetAllSessionsUseCase,
    private val loadSessionUseCase: LoadSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
    }

    /**
     * 세션 목록 로드
     */
    private fun loadSessions() {
        viewModelScope.launch {
            getAllSessionsUseCase()
                .catch { e ->
                    Timber.e(e, "세션 목록 로드 실패")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "세션 목록을 불러오는데 실패했습니다"
                        )
                    }
                }
                .collect { sessions ->
                    _uiState.update {
                        it.copy(
                            sessions = sessions,
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * 삭제 확인 다이얼로그 표시
     */
    fun showDeleteConfirmation(session: DrawingSession) {
        _uiState.update { it.copy(sessionToDelete = session) }
    }

    /**
     * 삭제 확인 다이얼로그 닫기
     */
    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(sessionToDelete = null) }
    }

    /**
     * 세션 삭제
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                deleteSessionUseCase(sessionId)
                _uiState.update { it.copy(sessionToDelete = null) }
                Timber.d("세션 삭제 완료: $sessionId")
            } catch (e: Exception) {
                Timber.e(e, "세션 삭제 실패")
                _uiState.update {
                    it.copy(
                        errorMessage = "세션 삭제에 실패했습니다",
                        sessionToDelete = null
                    )
                }
            }
        }
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
