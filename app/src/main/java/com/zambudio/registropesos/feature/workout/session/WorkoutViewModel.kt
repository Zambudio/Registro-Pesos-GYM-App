package com.zambudio.registropesos.feature.workout.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.navigation.WorkoutRoute
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.DiscardSession
import com.zambudio.registropesos.domain.usecase.FinishSession
import com.zambudio.registropesos.domain.usecase.GhostSet
import com.zambudio.registropesos.domain.usecase.PreviousSessionForExercise
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import com.zambudio.registropesos.feature.workout.common.flatten
import com.zambudio.registropesos.feature.workout.common.parseRepsText
import com.zambudio.registropesos.feature.workout.common.parseWeightText
import com.zambudio.registropesos.feature.workout.common.roundKg
import com.zambudio.registropesos.feature.workout.common.suspendRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Pantalla de sesión en curso.
 *
 * Las series se cargan una vez de Room y a partir de ahí la copia en memoria es la verdad de la
 * pantalla: cada cambio se aplica al instante en memoria y se encola su escritura en Room. Las
 * escrituras (y terminar/descartar) pasan por una única cola FIFO, así que se aplican en orden y
 * terminar nunca adelanta a la última serie tecleada. Así no hay saltos de cursor ni ediciones
 * rápidas que se pisen al releer la BD.
 */
// 8 dependencias inyectadas por Hilt: agruparlas en un contenedor solo añadiría indirección.
@Suppress("LongParameterList")
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    private val previousSessionForExercise: PreviousSessionForExercise,
    private val finishSession: FinishSession,
    private val discardSession: DiscardSession,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
) : ViewModel() {

    val sessionId: String = savedStateHandle.toRoute<WorkoutRoute>().sessionId

    private val editor = MutableStateFlow(EditorState())
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    private val weightStepKg: StateFlow<Double> = settingsRepository.settings
        .map { it.weightStepKg }
        .catch { emit(AppSettings().weightStepKg) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().weightStepKg)

    val uiState: StateFlow<WorkoutUiState> = combine(editor, weightStepKg) { state, step -> state.toUi(step) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState.Loading)

    init {
        viewModelScope.launch {
            for (write in writes) {
                // Una escritura empezada se completa aunque se cierre la pantalla.
                withContext(NonCancellable) { write() }
            }
        }
        load()
    }

    fun retry() = load()

    // --- Edición de series ---

    /** Texto vacío borra el peso; texto no válido (p. ej. "," a medio teclear) se ignora. */
    fun onWeightChange(setId: String, text: String) {
        val weight = if (text.isBlank()) null else parseWeightText(text) ?: return
        modifySet(setId) { it.copy(weightKg = weight) }
    }

    fun onRepsChange(setId: String, text: String) {
        val reps = if (text.isBlank()) null else parseRepsText(text) ?: return
        modifySet(setId) { it.copy(reps = reps) }
    }

    fun onToggleDone(setId: String) {
        modifySet(setId) { set ->
            val done = !set.isDone
            set.copy(isDone = done, completedAt = if (done) timeProvider.now() else null)
        }
    }

    /** ±paso de ajustes. Sin peso, parte del de la sesión anterior (el que se ve en gris) o de 0. Nunca baja de 0. */
    fun onStepWeight(setId: String, increase: Boolean) {
        val step = weightStepKg.value
        modifySet(setId) { set ->
            val base = set.weightKg ?: ghostFor(set)?.weightKg ?: 0.0
            val next = if (increase) base + step else base - step
            set.copy(weightKg = roundKg(next.coerceAtLeast(0.0)))
        }
    }

    fun onAddSet(exerciseId: String) {
        if (!canEdit()) return
        val last = setsOf(exerciseId).lastOrNull() ?: return
        val added = newSetAfter(last, offset = 1, timeProvider.now())
        applyChanges(changed = listOf(added))
        enqueue { workoutRepository.upsertSet(added) }
    }

    /** Quita [setId] o, si es null, la última serie. Siempre queda al menos una. */
    fun onRemoveSet(exerciseId: String, setId: String? = null) {
        if (!canEdit()) return
        val exerciseSets = setsOf(exerciseId)
        if (exerciseSets.size <= 1) return
        val target = exerciseSets.firstOrNull { it.id == setId } ?: exerciseSets.last()
        // Las siguientes bajan un puesto para que la numeración siga siendo contigua.
        val shifted = exerciseSets
            .filter { it.setIndex > target.setIndex }
            .map { it.copy(setIndex = it.setIndex - 1) }
        applyChanges(changed = shifted, removedId = target.id)
        enqueue {
            workoutRepository.deleteSet(target.id)
            workoutRepository.upsertSets(shifted)
        }
    }

    /**
     * Rellena con la sesión anterior los campos vacíos de cada fila (no pisa lo ya tecleado) y
     * crea las filas que falten.
     */
    fun onCopyPrevious(exerciseId: String) {
        if (!canEdit()) return
        val previous = editor.value.ghosts[exerciseId].orEmpty()
        val existing = setsOf(exerciseId)
        val last = existing.lastOrNull() ?: return
        if (previous.isEmpty()) return
        val now = timeProvider.now()
        val changed = previous.mapIndexedNotNull { index, ghost ->
            val row = existing.getOrNull(index)
            if (row == null) {
                newSetAfter(last, offset = index - existing.size + 1, now)
                    .copy(weightKg = ghost.weightKg, reps = ghost.reps)
            } else {
                row.copy(weightKg = row.weightKg ?: ghost.weightKg, reps = row.reps ?: ghost.reps)
                    .takeIf { it != row }
            }
        }
        if (changed.isEmpty()) return
        applyChanges(changed = changed)
        enqueue { workoutRepository.upsertSets(changed) }
    }

    // --- Navegación dentro de la sesión ---

    /** Abre [exerciseId] (cerrando el que estuviera abierto) o lo cierra si ya lo estaba. */
    fun onToggleExercise(exerciseId: String) {
        val expand = editor.value.expandedExerciseId != exerciseId
        editor.update { it.copy(expandedExerciseId = if (expand) exerciseId else null) }
        if (expand) loadPrevious(exerciseId)
    }

    // --- Cierre de la sesión ---

    fun onFinish() {
        if (!canEdit()) return
        editor.update { it.copy(isClosing = true) }
        enqueue(reportFailure = false) {
            suspendRunCatching { finishSession(sessionId) }.flatten()
                .onSuccess { eventChannel.send(WorkoutEvent.OpenSummary(sessionId)) }
                .onFailure {
                    editor.update { it.copy(isClosing = false) }
                    eventChannel.send(WorkoutEvent.FinishFailed)
                }
        }
    }

    fun onDiscard() {
        if (!canEdit()) return
        editor.update { it.copy(isClosing = true) }
        enqueue(reportFailure = false) {
            suspendRunCatching { discardSession(sessionId) }.flatten()
                .onSuccess { eventChannel.send(WorkoutEvent.Discarded) }
                .onFailure {
                    editor.update { it.copy(isClosing = false) }
                    eventChannel.send(WorkoutEvent.DiscardFailed)
                }
        }
    }

    // --- Interno ---

    private fun load() {
        editor.update { it.copy(load = Load.Loading) }
        viewModelScope.launch {
            val result = suspendRunCatching { workoutRepository.observeSessionDetail(sessionId).first() }
            val detail = result.getOrNull()
            val loaded = when {
                result.isFailure -> Load.Failed
                detail == null || detail.session.status != SessionStatus.IN_PROGRESS -> Load.Gone
                else -> Load.Ready(detail.session)
            }
            editor.update { state ->
                if (loaded is Load.Ready && detail != null) {
                    state.copy(
                        load = loaded,
                        sets = detail.exercises.flatMap { it.sets }.sortedWith(SetOrder),
                        expandedExerciseId = state.expandedExerciseId ?: firstPendingExercise(detail),
                    )
                } else {
                    state.copy(load = loaded)
                }
            }
            editor.value.expandedExerciseId?.let(::loadPrevious)
        }
    }

    private fun loadPrevious(exerciseId: String) {
        if (exerciseId in editor.value.ghosts) return
        viewModelScope.launch {
            // Sin sesión anterior legible la pantalla sigue funcionando: simplemente no hay gris.
            val previous = suspendRunCatching { previousSessionForExercise(exerciseId, sessionId) }
                .getOrDefault(emptyList())
            editor.update { it.copy(ghosts = it.ghosts + (exerciseId to previous)) }
        }
    }

    private fun modifySet(setId: String, transform: (WorkoutSet) -> WorkoutSet) {
        if (!canEdit()) return
        val current = editor.value.sets.firstOrNull { it.id == setId } ?: return
        val updated = transform(current)
        if (updated == current) return
        applyChanges(changed = listOf(updated))
        enqueue { workoutRepository.upsertSet(updated) }
    }

    private fun applyChanges(changed: List<WorkoutSet>, removedId: String? = null) {
        val replacedIds = changed.mapTo(HashSet()) { it.id }
        removedId?.let(replacedIds::add)
        editor.update { state ->
            state.copy(sets = (state.sets.filterNot { it.id in replacedIds } + changed).sortedWith(SetOrder))
        }
    }

    private fun enqueue(reportFailure: Boolean = true, write: suspend () -> Unit) {
        writes.trySend {
            val result = suspendRunCatching { write() }
            if (reportFailure && result.isFailure) eventChannel.send(WorkoutEvent.SaveFailed)
        }
    }

    private fun canEdit(): Boolean = editor.value.let { it.load is Load.Ready && !it.isClosing }

    private fun setsOf(exerciseId: String): List<WorkoutSet> = editor.value.sets.filter { it.exerciseId == exerciseId }

    /** Lo que se hizo la última vez en la misma posición de serie. */
    private fun ghostFor(set: WorkoutSet): GhostSet? {
        val position = setsOf(set.exerciseId).indexOfFirst { it.id == set.id }
        return editor.value.ghosts[set.exerciseId]?.getOrNull(position)
    }

    private fun newSetAfter(last: WorkoutSet, offset: Int, now: Long) = last.copy(
        id = idGenerator.newId(),
        setIndex = last.setIndex + offset,
        weightKg = null,
        reps = null,
        isDone = false,
        completedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun firstPendingExercise(detail: SessionDetail): String? {
        val pending = detail.exercises.firstOrNull { exercise -> exercise.sets.any { !it.isDone } }
        return (pending ?: detail.exercises.firstOrNull())?.exerciseId
    }

    private fun EditorState.toUi(stepKg: Double): WorkoutUiState = when (load) {
        Load.Loading -> WorkoutUiState.Loading
        Load.Failed -> WorkoutUiState.Error
        Load.Gone -> WorkoutUiState.NotInProgress
        is Load.Ready -> WorkoutUiState.Content(
            sessionId = sessionId,
            routineName = load.session.routineNameSnapshot,
            startedAt = load.session.startedAt,
            blocks = buildBlocks(sets, ghosts),
            expandedExerciseId = expandedExerciseId,
            weightStepKg = stepKg,
            hasAnyData = sets.any { !it.isEmpty },
            isClosing = isClosing,
        )
    }

    private sealed interface Load {
        data object Loading : Load
        data object Failed : Load
        data object Gone : Load
        data class Ready(val session: WorkoutSession) : Load
    }

    private data class EditorState(
        val load: Load = Load.Loading,
        val sets: List<WorkoutSet> = emptyList(),
        val expandedExerciseId: String? = null,
        val ghosts: Map<String, List<GhostSet>> = emptyMap(),
        val isClosing: Boolean = false,
    )

    companion object {
        private val SetOrder = compareBy<WorkoutSet>({ it.exerciseOrder }, { it.setIndex })

        /** Agrupa los ejercicios (en orden de sesión) en bloques de grupo muscular consecutivos. */
        internal fun buildBlocks(sets: List<WorkoutSet>, ghosts: Map<String, List<GhostSet>>): List<WorkoutBlockUi> {
            val exercises = sets.groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
                val previous = ghosts[exerciseId]
                exerciseSets.first().muscleGroupNameSnapshot to WorkoutExerciseUi(
                    exerciseId = exerciseId,
                    name = exerciseSets.first().exerciseNameSnapshot,
                    sets = exerciseSets.mapIndexed { index, set ->
                        val ghost = previous?.getOrNull(index)
                        WorkoutSetUi(
                            setId = set.id,
                            number = index + 1,
                            weightKg = set.weightKg,
                            reps = set.reps,
                            isDone = set.isDone,
                            ghostWeightKg = ghost?.weightKg,
                            ghostReps = ghost?.reps,
                        )
                    },
                    previousSets = previous,
                )
            }
            val blocks = mutableListOf<WorkoutBlockUi>()
            exercises.forEach { (group, exercise) ->
                val last = blocks.lastOrNull()
                if (last != null && last.muscleGroupName == group) {
                    blocks[blocks.lastIndex] = last.copy(exercises = last.exercises + exercise)
                } else {
                    blocks += WorkoutBlockUi(group, listOf(exercise))
                }
            }
            return blocks
        }
    }
}
