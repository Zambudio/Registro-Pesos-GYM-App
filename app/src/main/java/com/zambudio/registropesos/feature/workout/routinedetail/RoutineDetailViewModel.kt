package com.zambudio.registropesos.feature.workout.routinedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.navigation.RoutineDetailRoute
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.usecase.StartSession
import com.zambudio.registropesos.feature.workout.common.flatten
import com.zambudio.registropesos.feature.workout.common.suspendRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RoutineDetailUiState {
    data object Loading : RoutineDetailUiState
    data object Error : RoutineDetailUiState

    /** La rutina se borró mientras estaba abierta (o el id no existe). */
    data object NotFound : RoutineDetailUiState

    data class Content(
        val detail: RoutineDetail,
        val isStarting: Boolean = false,
        /** Id de la sesión que ya está en curso cuando se intentó empezar otra (muestra el diálogo). */
        val conflictingSessionId: String? = null,
    ) : RoutineDetailUiState {
        /** Grupos a trabajar, en orden y sin repetir (los ejercicios se eligen durante la sesión). */
        val groups: List<MuscleGroup> get() = detail.blocks.map { it.muscleGroup }.distinctBy { it.id }
        val canStart: Boolean get() = groups.isNotEmpty() && !isStarting
    }
}

sealed interface RoutineDetailEvent {
    data class OpenWorkout(val sessionId: String) : RoutineDetailEvent
    data object StartFailed : RoutineDetailEvent
}

private data class StartState(val isStarting: Boolean = false, val conflictingSessionId: String? = null)

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    routineRepository: RoutineRepository,
    private val startSession: StartSession,
) : ViewModel() {

    val routineId: String = savedStateHandle.toRoute<RoutineDetailRoute>().routineId

    private val reload = MutableStateFlow(0)
    private val start = MutableStateFlow(StartState())
    private val eventChannel = Channel<RoutineDetailEvent>(Channel.BUFFERED)
    val events: Flow<RoutineDetailEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RoutineDetailUiState> = reload
        .flatMapLatest {
            combine(routineRepository.observeDetail(routineId), start) { detail, startState ->
                if (detail == null) {
                    RoutineDetailUiState.NotFound
                } else {
                    RoutineDetailUiState.Content(detail, startState.isStarting, startState.conflictingSessionId)
                }
            }.catch { emit(RoutineDetailUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    fun startWorkout() {
        if (start.value.isStarting) return
        start.update { it.copy(isStarting = true) }
        viewModelScope.launch {
            val result = suspendRunCatching { startSession(routineId) }.flatten()
            start.update { it.copy(isStarting = false) }
            result
                .onSuccess { eventChannel.send(RoutineDetailEvent.OpenWorkout(it)) }
                .onFailure { error ->
                    if (error is SessionAlreadyInProgressException) {
                        start.update { it.copy(conflictingSessionId = error.sessionId) }
                    } else {
                        eventChannel.send(RoutineDetailEvent.StartFailed)
                    }
                }
        }
    }

    fun dismissConflict() {
        start.update { it.copy(conflictingSessionId = null) }
    }
}
