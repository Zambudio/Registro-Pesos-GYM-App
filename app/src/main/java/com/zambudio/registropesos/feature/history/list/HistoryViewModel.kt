package com.zambudio.registropesos.feature.history.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class HistoryItemUi(
    val sessionId: String,
    val routineName: String,
    val startedAt: Long,
    /** null si la sesión no tiene hora de fin (no debería pasar en una sesión terminada). */
    val durationMs: Long?,
    val exerciseCount: Int,
    val volumeKg: Double,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Error : HistoryUiState
    data class Content(val sessions: List<HistoryItemUi>) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val reload = MutableStateFlow(0)

    /** Sesiones terminadas, la más reciente primero (orden del repositorio). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = reload
        .flatMapLatest {
            workoutRepository.observeFinishedSessions()
                .map { sessions -> HistoryUiState.Content(sessions.map { it.toUi() }) as HistoryUiState }
                .catch { emit(HistoryUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    private fun SessionSummary.toUi() = HistoryItemUi(
        sessionId = id,
        routineName = routineName,
        startedAt = startedAt,
        durationMs = finishedAt?.let { (it - startedAt).coerceAtLeast(0L) },
        exerciseCount = exerciseCount,
        volumeKg = volumeKg,
    )
}
