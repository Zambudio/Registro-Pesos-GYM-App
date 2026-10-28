package com.zambudio.registropesos.feature.routines.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.navigation.RoutineEditRoute
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.usecase.RoutineError
import com.zambudio.registropesos.domain.usecase.ValidateRoutine
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val muscleGroups: List<MuscleGroup> = emptyList(),
    val showGroupPicker: Boolean = false,
    val exercisePicker: ExercisePickerUiState? = null,
    val dialog: RoutineEditDialog? = null,
)

/** Selector de selección múltiple de ejercicios para un bloque. */
data class ExercisePickerUiState(
    val blockId: String,
    val muscleGroup: MuscleGroup,
    val isLoading: Boolean,
    /** Ejercicios no archivados del grupo que aún no están en el bloque. */
    val available: List<Exercise> = emptyList(),
    /** false si el grupo no tiene ningún ejercicio (no confundir con "todos ya añadidos"). */
    val groupHasExercises: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
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
 * Editor de rutina: mantiene un [RoutineDraft] en memoria y solo persiste al guardar.
 * LongParameterList suprimido: son siete dependencias inyectadas por Hilt y agruparlas
 * en un objeto artificial no aporta claridad.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("LongParameterList")
class RoutineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routineRepository: RoutineRepository,
    muscleGroupRepository: MuscleGroupRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
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

    private val pickerExercises: Flow<GroupExercises?> = editor
        .map { it.picker?.muscleGroup?.id }
        .distinctUntilChanged()
        .flatMapLatest { groupId ->
            if (groupId == null) {
                flowOf(null)
            } else {
                exerciseRepository.observeByGroup(groupId)
                    .map { GroupExercises(groupId, it) }
                    .catch { emit(GroupExercises(groupId, emptyList())) }
            }
        }

    val uiState: StateFlow<RoutineEditUiState> = combine(
        editor,
        muscleGroupRepository.observeAll().catch { emit(emptyList()) },
        pickerExercises,
    ) { state, groups, groupExercises -> state.toUiState(groups, groupExercises) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = editor.value.toUiState(emptyList(), null),
        )

    init {
        if (editingId != null) load(editingId)
    }

    fun onRetryLoad() {
        editingId?.let(::load)
    }

    fun onNameChange(name: String) = updateDraft { it.copy(name = name) }

    fun onSubtitleChange(subtitle: String) = updateDraft { it.copy(subtitle = subtitle) }

    // --- Bloques ---

    fun onAddBlockClick() = editor.update { it.copy(showGroupPicker = true) }

    fun onGroupPickerDismiss() = editor.update { it.copy(showGroupPicker = false) }

    /** Añade un bloque del grupo elegido al final y abre directamente su selector de ejercicios. */
    fun onGroupSelected(group: MuscleGroup) {
        val block = DraftBlock(id = idGenerator.newId(), muscleGroup = group)
        editor.update { state ->
            state.copy(
                draft = state.draft.copy(blocks = state.draft.blocks + block),
                showGroupPicker = false,
                picker = PickerTarget(block.id, group),
            )
        }
    }

    fun onMoveBlockUp(blockId: String) = moveBlock(blockId, -1)

    fun onMoveBlockDown(blockId: String) = moveBlock(blockId, 1)

    fun onRemoveBlock(blockId: String) = updateDraft { draft ->
        draft.copy(blocks = draft.blocks.filterNot { it.id == blockId })
    }

    // --- Ejercicios de un bloque ---

    fun onAddExercisesClick(blockId: String) {
        val block = editor.value.draft.blocks.firstOrNull { it.id == blockId } ?: return
        editor.update { it.copy(picker = PickerTarget(blockId, block.muscleGroup)) }
    }

    fun onPickerToggle(exerciseId: String) = editor.update { state ->
        val picker = state.picker ?: return@update state
        val selected = if (exerciseId in picker.selectedIds) {
            picker.selectedIds - exerciseId
        } else {
            picker.selectedIds + exerciseId
        }
        state.copy(picker = picker.copy(selectedIds = selected))
    }

    fun onPickerDismiss() = editor.update { it.copy(picker = null) }

    /** Añade los seleccionados (en el orden del selector) con las series por defecto de ajustes. */
    fun onPickerConfirm() {
        val picker = editor.value.picker ?: return
        if (picker.selectedIds.isEmpty()) {
            onPickerDismiss()
            return
        }
        viewModelScope.launch {
            val defaultSets = runCatchingNonCancellation { settingsRepository.settings.first().defaultSets }
                .getOrDefault(DEFAULT_SETS_FALLBACK)
                .coerceIn(ValidateRoutine.MIN_DEFAULT_SETS, ValidateRoutine.MAX_DEFAULT_SETS)
            val groupExercises = exerciseRepository.observeByGroup(picker.muscleGroup.id).first()
            editor.update { state ->
                val draft = state.draft.updateBlock(picker.blockId) { block ->
                    val present = block.exercises.map { it.exercise.id }.toSet()
                    val added = groupExercises
                        .filter { it.id in picker.selectedIds && it.id !in present }
                        .map { DraftExercise(id = idGenerator.newId(), exercise = it, defaultSets = defaultSets) }
                    block.copy(exercises = block.exercises + added)
                }
                state.copy(draft = draft, picker = null)
            }
        }
    }

    fun onMoveExerciseUp(blockId: String, itemId: String) = moveExercise(blockId, itemId, -1)

    fun onMoveExerciseDown(blockId: String, itemId: String) = moveExercise(blockId, itemId, 1)

    fun onRemoveExercise(blockId: String, itemId: String) = updateDraft { draft ->
        draft.updateBlock(blockId) { block -> block.copy(exercises = block.exercises.filterNot { it.id == itemId }) }
    }

    fun onIncrementSets(blockId: String, itemId: String) = changeSets(blockId, itemId, 1)

    fun onDecrementSets(blockId: String, itemId: String) = changeSets(blockId, itemId, -1)

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

    private fun moveExercise(blockId: String, itemId: String, offset: Int) = updateDraft { draft ->
        draft.updateBlock(blockId) { block ->
            block.copy(exercises = block.exercises.moved(block.exercises.indexOfFirst { it.id == itemId }, offset))
        }
    }

    private fun changeSets(blockId: String, itemId: String, delta: Int) = updateDraft { draft ->
        draft.updateBlock(blockId) { block ->
            block.copy(
                exercises = block.exercises.map { item ->
                    if (item.id != itemId) {
                        item
                    } else {
                        val sets = (item.defaultSets + delta)
                            .coerceIn(ValidateRoutine.MIN_DEFAULT_SETS, ValidateRoutine.MAX_DEFAULT_SETS)
                        item.copy(defaultSets = sets)
                    }
                },
            )
        }
    }

    private fun RoutineDraft.updateBlock(blockId: String, transform: (DraftBlock) -> DraftBlock): RoutineDraft =
        copy(blocks = blocks.map { if (it.id == blockId) transform(it) else it })

    private fun EditorState.toUiState(groups: List<MuscleGroup>, groupExercises: GroupExercises?): RoutineEditUiState {
        val errors = if (showErrors) validate(draft) else emptySet()
        return RoutineEditUiState(
            isNew = editingId == null,
            isLoading = isLoading,
            loadFailed = loadFailed,
            notFound = notFound,
            draft = draft,
            errors = errors,
            isDirty = isDirty,
            isSaving = isSaving,
            muscleGroups = groups,
            showGroupPicker = showGroupPicker,
            exercisePicker = picker?.toUiState(draft, groupExercises),
            dialog = dialog,
        )
    }

    private fun PickerTarget.toUiState(draft: RoutineDraft, groupExercises: GroupExercises?): ExercisePickerUiState {
        val loaded = groupExercises?.takeIf { it.groupId == muscleGroup.id }
        val present = draft.blocks.firstOrNull { it.id == blockId }?.exercises?.map { it.exercise.id }.orEmpty().toSet()
        return ExercisePickerUiState(
            blockId = blockId,
            muscleGroup = muscleGroup,
            isLoading = loaded == null,
            available = loaded?.exercises.orEmpty().filter { it.id !in present },
            groupHasExercises = loaded?.exercises.orEmpty().isNotEmpty(),
            selectedIds = selectedIds,
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
        val picker: PickerTarget? = null,
        val dialog: RoutineEditDialog? = null,
    ) {
        val isEditable: Boolean get() = !isLoading && !notFound && !loadFailed
        val isDirty: Boolean get() = isEditable && draft != original
    }

    private data class PickerTarget(
        val blockId: String,
        val muscleGroup: MuscleGroup,
        val selectedIds: Set<String> = emptySet(),
    )

    private data class GroupExercises(val groupId: String, val exercises: List<Exercise>)

    private companion object {
        const val DEFAULT_SETS_FALLBACK = 4
    }
}
