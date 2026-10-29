package com.zambudio.registropesos.feature.exercises.musclegroups

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.R
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.repository.MuscleGroupInUseException
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import com.zambudio.registropesos.feature.exercises.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class MuscleGroupItem(
    val id: String,
    val name: String,
    /** Ejercicios no archivados del grupo. */
    val exerciseCount: Int,
)

enum class GroupNameError { EMPTY, DUPLICATE }

/** Diálogo de crear/renombrar abierto. [groupId] null = crear. */
data class GroupEditorState(
    val groupId: String? = null,
    val initialName: String = "",
    val error: GroupNameError? = null,
)

data class MuscleGroupsUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val groups: List<MuscleGroupItem> = emptyList(),
    val editor: GroupEditorState? = null,
)

sealed interface MuscleGroupsEvent {
    data class ShowMessage(val message: UiMessage) : MuscleGroupsEvent
}

@HiltViewModel
class MuscleGroupsViewModel @Inject constructor(
    private val repository: MuscleGroupRepository,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) : ViewModel() {
    private val editor = MutableStateFlow<GroupEditorState?>(null)
    private val retryTrigger = MutableStateFlow(0)

    private val eventChannel = Channel<MuscleGroupsEvent>(Channel.BUFFERED)
    val events: Flow<MuscleGroupsEvent> = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<MuscleGroupsUiState> = retryTrigger.flatMapLatest {
        combine(repository.observeAll(), repository.observeExerciseCounts()) { groups, counts ->
            MuscleGroupsUiState(
                isLoading = false,
                groups = groups.sortedBy { it.sortOrder }.map { MuscleGroupItem(it.id, it.name, counts[it.id] ?: 0) },
            )
        }.catch { emit(MuscleGroupsUiState(isLoading = false, isError = true)) }
    }

    val uiState: StateFlow<MuscleGroupsUiState> = combine(content, editor) { state, editorState ->
        state.copy(editor = editorState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MuscleGroupsUiState())

    fun onRetry() {
        retryTrigger.update { it + 1 }
    }

    fun onCreateClick() {
        editor.value = GroupEditorState()
    }

    fun onRenameClick(groupId: String, currentName: String) {
        editor.value = GroupEditorState(groupId = groupId, initialName = currentName)
    }

    fun onEditorDismiss() {
        editor.value = null
    }

    fun onEditorInputChange() {
        editor.update { it?.copy(error = null) }
    }

    /** Valida (vacío y duplicado sin distinguir mayúsculas) y crea o renombra el grupo. */
    fun onEditorSubmit(name: String) {
        val current = editor.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            editor.update { it?.copy(error = GroupNameError.EMPTY) }
            return
        }
        viewModelScope.launch {
            val groups = repository.observeAll().first()
            val key = trimmed.lowercase(Locale.ROOT)
            if (groups.any { it.id != current.groupId && it.name.trim().lowercase(Locale.ROOT) == key }) {
                editor.update { it?.copy(error = GroupNameError.DUPLICATE) }
                return@launch
            }
            val group = buildGroup(current.groupId, trimmed, groups)
            if (group == null) {
                // El grupo a renombrar ya no existe.
                editor.value = null
                return@launch
            }
            try {
                repository.upsert(group)
                editor.value = null
            } catch (ignored: SQLiteConstraintException) {
                // Carrera con otro cambio o colación distinta en SQLite: el índice único manda.
                editor.update { it?.copy(error = GroupNameError.DUPLICATE) }
            } catch (ignored: SQLiteException) {
                // Otro fallo de BD (disco lleno, BD bloqueada…): se avisa y el diálogo sigue abierto.
                showGenericError()
            }
        }
    }

    fun onMoveUp(groupId: String) = move(groupId, -1)

    fun onMoveDown(groupId: String) = move(groupId, +1)

    /** Solo borra grupos sin ejercicios (ni archivados) ni bloques de rutina; si no, explica el motivo. */
    fun onDelete(groupId: String) {
        viewModelScope.launch {
            val name = repository.getById(groupId)?.name ?: return@launch
            val result = repository.delete(groupId)
            val message = when {
                result.isSuccess -> UiMessage(R.string.exercises_groups_deleted)
                result.exceptionOrNull() is MuscleGroupInUseException ->
                    UiMessage(R.string.exercises_groups_delete_in_use, name)
                else -> UiMessage(R.string.common_error_generic)
            }
            eventChannel.send(MuscleGroupsEvent.ShowMessage(message))
        }
    }

    private fun move(groupId: String, offset: Int) {
        viewModelScope.launch {
            val ordered = repository.observeAll().first().sortedBy { it.sortOrder }.map { it.id }.toMutableList()
            val from = ordered.indexOf(groupId)
            val to = from + offset
            if (from < 0 || to !in ordered.indices) return@launch
            ordered.add(to, ordered.removeAt(from))
            try {
                repository.reorder(ordered)
            } catch (ignored: SQLiteException) {
                showGenericError()
            }
        }
    }

    private suspend fun showGenericError() {
        eventChannel.send(MuscleGroupsEvent.ShowMessage(UiMessage(R.string.common_error_generic)))
    }

    private fun buildGroup(groupId: String?, name: String, groups: List<MuscleGroup>): MuscleGroup? {
        val now = time.now()
        return if (groupId == null) {
            MuscleGroup(
                id = ids.newId(),
                name = name,
                sortOrder = (groups.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                isDefault = false,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            groups.firstOrNull { it.id == groupId }?.copy(name = name, updatedAt = now)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
