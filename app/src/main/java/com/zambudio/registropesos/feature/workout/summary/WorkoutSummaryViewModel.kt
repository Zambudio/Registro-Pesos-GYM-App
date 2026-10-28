package com.zambudio.registropesos.feature.workout.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.navigation.WorkoutSummaryRoute
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.repository.ProgressRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.DetectNewRecords
import com.zambudio.registropesos.domain.usecase.NewRecord
import com.zambudio.registropesos.domain.usecase.PersonalRecords
import com.zambudio.registropesos.domain.usecase.SessionVolume
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

sealed interface WorkoutSummaryUiState {
    data object Loading : WorkoutSummaryUiState
    data object Error : WorkoutSummaryUiState
    data class Content(
        val routineName: String,
        val durationMs: Long,
        val exerciseCount: Int,
        val setCount: Int,
        val volumeKg: Double,
        val newRecords: List<NewRecord>,
    ) : WorkoutSummaryUiState
}

/**
 * Resumen tras terminar. Se recalcula desde Room (la sesión ya terminada y sin series vacías) con
 * el mismo criterio que [com.zambudio.registropesos.domain.usecase.FinishSession], así sobrevive a
 * rotaciones y a la muerte del proceso sin pasar el resultado por la navegación.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    workoutRepository: WorkoutRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val sessionId: String = savedStateHandle.toRoute<WorkoutSummaryRoute>().sessionId
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WorkoutSummaryUiState> = reload
        .flatMapLatest {
            workoutRepository.observeSessionDetail(sessionId)
                .map { detail -> detail?.let { summarize(it) } ?: WorkoutSummaryUiState.Error }
                .catch { emit(WorkoutSummaryUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState.Loading)

    fun retry() {
        reload.update { it + 1 }
    }

    private suspend fun summarize(detail: SessionDetail): WorkoutSummaryUiState {
        val session = detail.session
        if (session.status != SessionStatus.FINISHED) return WorkoutSummaryUiState.Error
        val sets = detail.exercises.flatMap { it.sets }.filterNot { it.isEmpty }
        val exerciseIds = sets.mapTo(HashSet()) { it.exerciseId }
        val previous = progressRepository.finishedSetsFlat(null)
            .filter { it.sessionId != session.id && it.exerciseId in exerciseIds }
        return WorkoutSummaryUiState.Content(
            routineName = session.routineNameSnapshot,
            durationMs = ((session.finishedAt ?: session.startedAt) - session.startedAt).coerceAtLeast(0L),
            exerciseCount = exerciseIds.size,
            setCount = sets.size,
            volumeKg = SessionVolume(sets),
            newRecords = DetectNewRecords(PersonalRecords(previous), sets),
        )
    }
}
