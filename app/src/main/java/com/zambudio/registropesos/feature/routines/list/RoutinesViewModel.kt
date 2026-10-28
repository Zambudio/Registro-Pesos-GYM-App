package com.zambudio.registropesos.feature.routines.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.feature.routines.moved
import com.zambudio.registropesos.feature.routines.runCatchingNonCancellation
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutinesUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val routines: List<RoutineSummary> = emptyList(),
    /** Rutina pendiente de confirmar borrado (diálogo abierto). */
    val pendingDelete: RoutineSummary? = null,
)

sealed interface RoutinesEvent {
    data object ReorderFailed : RoutinesEvent
    data object DeleteFailed : RoutinesEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
) : ViewModel() {

    private val retry = MutableStateFlow(0)
    private val pendingDeleteId = MutableStateFlow<String?>(null)

    private val events = Channel<RoutinesEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RoutinesEvent> = events.receiveAsFlow()

    private val routines: Flow<RoutinesUiState> = retry.flatMapLatest {
        routineRepository.observeSummaries()
            .map { RoutinesUiState(isLoading = false, routines = it) }
            .catch { emit(RoutinesUiState(isLoading = false, hasError = true)) }
    }

    val uiState: StateFlow<RoutinesUiState> = combine(routines, pendingDeleteId) { state, deleteId ->
        state.copy(pendingDelete = state.routines.firstOrNull { it.id == deleteId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutinesUiState())

    fun onRetry() = retry.update { it + 1 }

    fun onMoveUp(routineId: String) = move(routineId, -1)

    fun onMoveDown(routineId: String) = move(routineId, 1)

    fun onDeleteClick(routineId: String) {
        pendingDeleteId.value = routineId
    }

    fun onDeleteDismiss() {
        pendingDeleteId.value = null
    }

    fun onDeleteConfirm() {
        val id = pendingDeleteId.value ?: return
        pendingDeleteId.value = null
        viewModelScope.launch {
            runCatchingNonCancellation { routineRepository.delete(id) }
                .onFailure { events.send(RoutinesEvent.DeleteFailed) }
        }
    }

    /** Persiste el nuevo orden completo; la lista se refresca sola desde el repositorio. */
    private fun move(routineId: String, offset: Int) {
        val current = uiState.value.routines
        val index = current.indexOfFirst { it.id == routineId }
        val reordered = current.moved(index, offset)
        if (reordered === current) return
        viewModelScope.launch {
            runCatchingNonCancellation { routineRepository.reorder(reordered.map { it.id }) }
                .onFailure { events.send(RoutinesEvent.ReorderFailed) }
        }
    }
}
