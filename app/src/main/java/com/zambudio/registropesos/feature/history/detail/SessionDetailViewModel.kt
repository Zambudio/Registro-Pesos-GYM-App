package com.zambudio.registropesos.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.navigation.SessionDetailRoute
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.CalculateE1rm
import com.zambudio.registropesos.domain.usecase.SessionVolume
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

data class HistorySetUi(val number: Int, val weightKg: Double?, val reps: Int?, val e1rmKg: Double)

data class HistoryExerciseUi(
    val exerciseId: String,
    val name: String,
    val muscleGroupName: String,
    val sets: List<HistorySetUi>,
    val volumeKg: Double,
)

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data object Error : SessionDetailUiState
    data object NotFound : SessionDetailUiState
    data class Content(
        val routineName: String,
        val startedAt: Long,
        val durationMs: Long?,
        val volumeKg: Double,
        val exercises: List<HistoryExerciseUi>,
        val isDeleting: Boolean = false,
    ) : SessionDetailUiState
}

sealed interface SessionDetailEvent {
    data object Deleted : SessionDetailEvent
    data object DeleteFailed : SessionDetailEvent
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.toRoute<SessionDetailRoute>().sessionId
    private val reload = MutableStateFlow(0)
    private val deleting = MutableStateFlow(false)
    private val eventChannel = Channel<SessionDetailEvent>(Channel.BUFFERED)
    val events: Flow<SessionDetailEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionDetailUiState> = reload
        .flatMapLatest {
            combine(workoutRepository.observeSessionDetail(sessionId), deleting) { detail, isDeleting ->
                when {
                    detail != null -> detail.toUi(isDeleting)
                    // Tras borrar, la sesión desaparece antes de salir de la pantalla: sin parpadeo de "no encontrada".
                    isDeleting -> SessionDetailUiState.Loading
                    else -> SessionDetailUiState.NotFound
                }
            }.catch { emit(SessionDetailUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    fun delete() {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            suspendRunCatching { workoutRepository.deleteSession(sessionId) }
                .onSuccess { eventChannel.send(SessionDetailEvent.Deleted) }
                .onFailure {
                    deleting.value = false
                    eventChannel.send(SessionDetailEvent.DeleteFailed)
                }
        }
    }

    private fun SessionDetail.toUi(isDeleting: Boolean) = SessionDetailUiState.Content(
        routineName = session.routineNameSnapshot,
        startedAt = session.startedAt,
        durationMs = session.finishedAt?.let { (it - session.startedAt).coerceAtLeast(0L) },
        volumeKg = SessionVolume.of(this),
        exercises = exercises.map { exercise ->
            HistoryExerciseUi(
                exerciseId = exercise.exerciseId,
                name = exercise.exerciseName,
                muscleGroupName = exercise.muscleGroupName,
                sets = exercise.sets.mapIndexed { index, set ->
                    HistorySetUi(index + 1, set.weightKg, set.reps, CalculateE1rm(set.weightKg, set.reps))
                },
                volumeKg = SessionVolume(exercise.sets),
            )
        },
        isDeleting = isDeleting,
    )
}
