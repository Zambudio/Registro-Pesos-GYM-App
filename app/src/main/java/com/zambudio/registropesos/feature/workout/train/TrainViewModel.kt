package com.zambudio.registropesos.feature.workout.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.DiscardSession
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

/** Sesión que se puede reanudar desde la pestaña Entrenar. */
data class InProgressSessionUi(val sessionId: String, val routineName: String, val startedAt: Long)

sealed interface TrainUiState {
    data object Loading : TrainUiState
    data object Error : TrainUiState
    data class Content(
        val inProgress: InProgressSessionUi?,
        val routines: List<RoutineSummary>,
    ) : TrainUiState
}

sealed interface TrainEvent {
    data object DiscardFailed : TrainEvent
}

@HiltViewModel
class TrainViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    routineRepository: RoutineRepository,
    private val discardSession: DiscardSession,
) : ViewModel() {

    private val reload = MutableStateFlow(0)
    private val eventChannel = Channel<TrainEvent>(Channel.BUFFERED)
    val events: Flow<TrainEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrainUiState> = reload
        .flatMapLatest {
            combine(
                workoutRepository.observeInProgress(),
                routineRepository.observeSummaries(),
            ) { session, routines ->
                TrainUiState.Content(session?.toUi(), routines) as TrainUiState
            }.catch { emit(TrainUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    fun discardInProgress(sessionId: String) {
        viewModelScope.launch {
            val result = suspendRunCatching { discardSession(sessionId) }.flatten()
            if (result.isFailure) eventChannel.send(TrainEvent.DiscardFailed)
        }
    }

    private fun WorkoutSession.toUi() = InProgressSessionUi(id, routineNameSnapshot, startedAt)
}
