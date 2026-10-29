package com.zambudio.registropesos.feature.routines.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.navigation.RoutineEditRoute
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.usecase.RoutineError
import com.zambudio.registropesos.domain.usecase.ValidateRoutine
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import com.zambudio.registropesos.feature.routines.moved
import com.zambudio.registropesos.feature.routines.runCatchingNonCancellation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val notFound: Boolean = false,
    val draft: RoutineDraft = RoutineDraft(),
    /** Errores de validación; solo se rellenan tras el primer intento de guardar. */
    val errors: Set<RoutineError> = emptySet(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    /** Grupos que aún no están en la rutina (los que ofrece el selector). */
    val availableGroups: List<MuscleGroup> = emptyList(),
    val showGroupPicker: Boolean = false,
    val dialog: RoutineEditDialog? = null,
)

enum class RoutineEditDialog { DISCARD_CHANGES, DELETE }

sealed interface RoutineEditEvent {
    /** Salir del editor (guardado, descartado o sin cambios). */
    data object Close : RoutineEditEvent
    data object Deleted : RoutineEditEvent
    data object SaveFailed : RoutineEditEvent
    data object DeleteFailed : RoutineEditEvent
}

/**
 * Editor de rutina: nombre, comentario opcional y grupos musculares ordenados. Mantiene un
 * [RoutineDraft] en memoria y solo persiste al guardar. Los ejercicios se eligen al entrenar.
 */
@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    muscleGroupRepository: MuscleGroupRepository,
    private val idGenerator: IdGenerator,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val editingId: String? = savedStateHandle.toRoute<RoutineEditRoute>().routineId
    private val routineId: String = editingId ?: idGenerator.newId()

    /** Rutina cargada (edición); null mientras carga o si es nueva. */
    private var loadedRoutine: Routine? = null

    private val editor = MutableStateFlow(EditorState(isLoading = editingId != null))

    private val events = Channel<RoutineEditEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RoutineEditEvent> = events.receiveAsFlow()

    val uiState: StateFlow<RoutineEditUiState> = combine(
        editor,
        muscleGroupRepository.observeAll().catch { emit(emptyList()) },
    ) { state, groups -> state.toUiState(groups) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = editor.value.toUiState(emptyList()),
        )

    init {
        if (editingId != null) load(editingId)
    }

    fun onRetryLoad() {
        editingId?.let(::load)
    }

    fun onNameChange(name: String) = updateDraft { it.copy(name = name) }

    fun onSubtitleChange(subtitle: String) = updateDraft { it.copy(subtitle = subtitle) }

    // --- Grupos ---

    fun onAddBlockClick() = editor.update { it.copy(showGroupPicker = true) }

    fun onGroupPickerDismiss() = editor.update { it.copy(showGroupPicker = false) }

    /** Añade el grupo elegido al final. Un grupo ya presente no se repite. */
    fun onGroupSelected(group: MuscleGroup) {
        editor.update { state ->
            val present = state.draft.blocks.any { it.muscleGroup.id == group.id }
            val blocks = if (present) {
                state.draft.blocks
            } else {
                state.draft.blocks + DraftBlock(id = idGenerator.newId(), muscleGroup = group)
            }
            state.copy(draft = state.draft.copy(blocks = blocks), showGroupPicker = false)
        }
    }

    fun onMoveBlockUp(blockId: String) = moveBlock(blockId, -1)

    fun onMoveBlockDown(blockId: String) = moveBlock(blockId, 1)

    fun onRemoveBlock(blockId: String) = updateDraft { draft ->
        draft.copy(blocks = draft.blocks.filterNot { it.id == blockId })
    }

    // --- Guardar, salir y borrar ---

    fun onSave() {
        val state = editor.value
        if (state.isSaving || !state.isEditable) return
        if (validate(state.draft).isNotEmpty()) {
            editor.update { it.copy(showErrors = true) }
            return
        }
        editor.update { it.copy(isSaving = true, showErrors = true) }
        viewModelScope.launch {
            val now = timeProvider.now()
            runCatchingNonCancellation { routineRepository.save(state.draft.toDetail(baseRoutine(now), now)) }
                .onSuccess {
                    editor.update { it.copy(isSaving = false, original = state.draft) }
                    events.send(RoutineEditEvent.Close)
                }
                .onFailure {
                    editor.update { it.copy(isSaving = false) }
                    events.send(RoutineEditEvent.SaveFailed)
                }
        }
    }

    /** Back del sistema o de la barra superior: pide confirmación si hay cambios sin guardar. */
    fun onBack() {
        if (editor.value.isDirty) {
            editor.update { it.copy(dialog = RoutineEditDialog.DISCARD_CHANGES) }
        } else {
            events.trySend(RoutineEditEvent.Close)
        }
    }

    fun onDiscardConfirm() {
        editor.update { it.copy(dialog = null) }
        events.trySend(RoutineEditEvent.Close)
    }

    fun onDeleteClick() {
        if (editingId != null) editor.update { it.copy(dialog = RoutineEditDialog.DELETE) }
    }

    fun onDeleteConfirm() {
        val id = editingId ?: return
        editor.update { it.copy(dialog = null) }
        viewModelScope.launch {
            runCatchingNonCancellation { routineRepository.delete(id) }
                .onSuccess {
                    editor.update { it.copy(original = it.draft) }
                    events.send(RoutineEditEvent.Deleted)
                }
                .onFailure { events.send(RoutineEditEvent.DeleteFailed) }
        }
    }

    fun onDialogDismiss() = editor.update { it.copy(dialog = null) }

    // --- Interno ---

    private fun load(id: String) {
        editor.update { it.copy(isLoading = true, loadFailed = false, notFound = false) }
        viewModelScope.launch {
            runCatchingNonCancellation { routineRepository.getDetail(id) }
                .onSuccess { detail ->
                    if (detail == null) {
                        editor.update { it.copy(isLoading = false, notFound = true) }
                    } else {
                        loadedRoutine = detail.routine
                        val draft = detail.toDraft()
                        editor.update { it.copy(isLoading = false, original = draft, draft = draft) }
                    }
                }
                .onFailure { editor.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    /** Rutina base: la cargada al editar (conserva id, sortOrder, createdAt) o una nueva al final de la lista. */
    private suspend fun baseRoutine(now: Long): Routine = loadedRoutine ?: Routine(
        id = routineId,
        name = "",
        subtitle = null,
        sortOrder = nextSortOrder(),
        createdAt = now,
        updatedAt = now,
    )

    /** Las rutinas vienen ordenadas por sortOrder: la última tiene el máximo. */
    private suspend fun nextSortOrder(): Int {
        val last = routineRepository.observeSummaries().first().lastOrNull() ?: return 0
        val lastOrder = routineRepository.getDetail(last.id)?.routine?.sortOrder ?: return 0
        return lastOrder + 1
    }

    private fun updateDraft(transform: (RoutineDraft) -> RoutineDraft) =
        editor.update { it.copy(draft = transform(it.draft)) }

    private fun moveBlock(blockId: String, offset: Int) = updateDraft { draft ->
        draft.copy(blocks = draft.blocks.moved(draft.blocks.indexOfFirst { it.id == blockId }, offset))
    }

    private fun EditorState.toUiState(groups: List<MuscleGroup>): RoutineEditUiState {
        val errors = if (showErrors) validate(draft) else emptySet()
        val used = draft.blocks.mapTo(HashSet()) { it.muscleGroup.id }
        return RoutineEditUiState(
            isNew = editingId == null,
            isLoading = isLoading,
            loadFailed = loadFailed,
            notFound = notFound,
            draft = draft,
            errors = errors,
            isDirty = isDirty,
            isSaving = isSaving,
            availableGroups = groups.filterNot { it.id in used },
            showGroupPicker = showGroupPicker,
            dialog = dialog,
        )
    }

    /** La validación no depende de ids ni fechas: basta una rutina base cualquiera. */
    private fun validate(draft: RoutineDraft): Set<RoutineError> =
        ValidateRoutine(draft.toDetail(Routine(routineId, "", null, 0, 0L, 0L), 0L))

    private data class EditorState(
        val isLoading: Boolean = false,
        val loadFailed: Boolean = false,
        val notFound: Boolean = false,
        /** Estado de referencia para detectar cambios: el cargado, o vacío si es nueva. */
        val original: RoutineDraft = RoutineDraft(),
        val draft: RoutineDraft = RoutineDraft(),
        val showErrors: Boolean = false,
        val isSaving: Boolean = false,
        val showGroupPicker: Boolean = false,
        val dialog: RoutineEditDialog? = null,
    ) {
        val isEditable: Boolean get() = !isLoading && !notFound && !loadFailed
        val isDirty: Boolean get() = isEditable && draft != original
    }
}
