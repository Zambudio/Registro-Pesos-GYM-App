package com.zambudio.registropesos.feature.workout.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.core.di.ApplicationScope
import com.zambudio.registropesos.core.navigation.WorkoutRoute
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionGroup
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.DiscardSession
import com.zambudio.registropesos.domain.usecase.FinishSession
import com.zambudio.registropesos.domain.usecase.GhostSet
import com.zambudio.registropesos.domain.usecase.PreviousSessionForExercise
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import com.zambudio.registropesos.feature.exercises.fileOrNull
import com.zambudio.registropesos.feature.workout.common.flatten
import com.zambudio.registropesos.feature.workout.common.parseRepsText
import com.zambudio.registropesos.feature.workout.common.parseWeightText
import com.zambudio.registropesos.feature.workout.common.roundKg
import com.zambudio.registropesos.feature.workout.common.suspendRunCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
import java.io.File
import javax.inject.Inject

/**
 * Pantalla de sesión en curso.
 *
 * Las series se cargan una vez de Room y a partir de ahí la copia en memoria es la verdad de la
 * pantalla: cada cambio se aplica al instante en memoria y se encola su escritura en Room. Las
 * escrituras (y terminar/descartar) pasan por una única cola FIFO, así que se aplican en orden y
 * terminar nunca adelanta a la última serie tecleada. Así no hay saltos de cursor ni ediciones
 * rápidas que se pisen al releer la BD.
 *
 * La cola se consume en [applicationScope], no en viewModelScope: al cerrar la pantalla
 * ([onCleared]) se cierra la cola, el consumidor aplica lo pendiente y termina; nada se pierde.
 *
 * La sesión muestra desde el principio los grupos de la rutina; los ejercicios se eligen con el
 * selector de cada grupo. Añadir un ejercicio = crear sus filas vacías (ADR-018), así que la
 * elección queda guardada igual que las series y la sesión se reanuda tal cual.
 */
// 11 dependencias inyectadas por Hilt: agruparlas en un contenedor solo añadiría indirección.
@Suppress("LongParameterList", "TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
    private val imageStore: ImageStore,
    private val previousSessionForExercise: PreviousSessionForExercise,
    private val finishSession: FinishSession,
    private val discardSession: DiscardSession,
    private val timeProvider: TimeProvider,
    private val idGenerator: IdGenerator,
    @ApplicationScope applicationScope: CoroutineScope,
) : ViewModel() {

    val sessionId: String = savedStateHandle.toRoute<WorkoutRoute>().sessionId

    private val editor = MutableStateFlow(EditorState())
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val eventChannel = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = eventChannel.receiveAsFlow()

    /** Última serie con datos quitada, pendiente de un posible "Deshacer". */
    private var lastRemoved: WorkoutSet? = null

    /** Último ejercicio quitado (todas sus series), pendiente de un posible "Deshacer". */
    private var lastRemovedExercise: RemovedExercise? = null

    private val weightStepKg: StateFlow<Double> = settingsRepository.settings
        .map { it.weightStepKg }
        .catch { emit(AppSettings().weightStepKg) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().weightStepKg)

    /** Filas vacías con las que entra un ejercicio elegido (ajuste "Series por defecto"). */
    private val defaultSets: StateFlow<Int> = settingsRepository.settings
        .map { it.defaultSets }
        .catch { emit(AppSettings().defaultSets) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings().defaultSets)

    /**
     * Ejercicios del grupo del selector abierto (se actualiza si se crea uno nuevo mientras tanto).
     * Es lo que se pinta y lo que se añade: elegir es síncrono, sin lecturas que puedan desordenar
     * dos elecciones seguidas.
     */
    private val pickerExercises: StateFlow<GroupExercises?> = editor
        .map { it.pickerGroup?.muscleGroupId }
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<WorkoutUiState> = combine(editor, weightStepKg, pickerExercises) { state, step, picker ->
        state.toUi(step, picker)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState.Loading)

    init {
        // Un único consumidor: FIFO. Termina cuando onCleared cierra la cola y se ha vaciado.
        applicationScope.launch {
            for (write in writes) write()
        }
        load()
    }

    override fun onCleared() {
        writes.close()
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

    /**
     * Quita [setId] o, si es null, la última serie. Siempre queda al menos una (para quitar el
     * ejercicio entero está [onRemoveExercise]). Si la serie tenía datos se emite
     * [WorkoutEvent.SetRemoved] para ofrecer "Deshacer".
     */
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
        if (!target.isEmpty) {
            lastRemoved = target
            eventChannel.trySend(WorkoutEvent.SetRemoved(target.id))
        }
    }

    /** "Deshacer" de [onRemoveSet]: la serie vuelve con el mismo id, datos y posición. */
    fun onUndoRemoveSet(setId: String) {
        val removed = lastRemoved?.takeIf { it.id == setId } ?: return
        if (!canEdit()) return
        lastRemoved = null
        val exerciseSets = setsOf(removed.exerciseId)
        val index = minOf(removed.setIndex, (exerciseSets.maxOfOrNull { it.setIndex } ?: -1) + 1)
        val restored = removed.copy(setIndex = index)
        // De mayor a menor: cada fila sube a un hueco ya libre y no choca con el índice único
        // (sesión, ejercicio, posición). La restaurada va al final, cuando su posición ya está libre.
        val shifted = exerciseSets
            .filter { it.setIndex >= index }
            .sortedByDescending { it.setIndex }
            .map { it.copy(setIndex = it.setIndex + 1) }
        val changed = shifted + restored
        applyChanges(changed = changed)
        enqueue { workoutRepository.upsertSets(changed) }
    }

    /**
     * Rellena con la sesión anterior los campos vacíos de cada fila (no pisa lo ya tecleado) y
     * crea las filas que falten. Lo copiado pasa a ser dato de hoy: se guarda y se ve como tal,
     * aunque luego no se cambie.
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

    // --- Elegir y quitar ejercicios ---

    /** Abre el selector de ejercicios de un grupo de la sesión. */
    fun onOpenPicker(muscleGroupId: String) {
        if (!canEdit()) return
        val group = editor.value.groups.firstOrNull { it.muscleGroupId == muscleGroupId } ?: return
        editor.update { it.copy(pickerGroup = group) }
    }

    fun onDismissPicker() {
        editor.update { it.copy(pickerGroup = null) }
    }

    /**
     * Añade el ejercicio elegido con las series por defecto vacías y lo abre. Si ya estaba en la
     * sesión, solo lo abre (nunca se duplica).
     */
    fun onPickExercise(exerciseId: String) {
        if (!canEdit()) return
        val group = editor.value.pickerGroup ?: return
        // Se lee antes de cerrar el selector: al cerrarlo deja de observarse su lista.
        val exercise = pickerExercises.value
            ?.takeIf { it.groupId == group.muscleGroupId }
            ?.exercises
            ?.firstOrNull { it.id == exerciseId }
        // El selector se cierra al tocar: un segundo toque ya no llega aquí.
        editor.update { it.copy(pickerGroup = null) }
        if (setsOf(exerciseId).isNotEmpty()) {
            expand(exerciseId)
            return
        }
        if (exercise == null) return
        val now = timeProvider.now()
        val order = (editor.value.sets.maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        val setCount = defaultSets.value.coerceIn(1, MAX_INITIAL_SETS)
        val added = List(setCount) { index ->
            WorkoutSet(
                id = idGenerator.newId(),
                sessionId = sessionId,
                exerciseId = exercise.id,
                exerciseNameSnapshot = exercise.name,
                muscleGroupNameSnapshot = group.muscleGroupName,
                exerciseOrder = order,
                setIndex = index,
                weightKg = null,
                reps = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        val info = ExerciseInfo(group.muscleGroupId, exercise.imagePath)
        editor.update { state -> state.copy(exercises = state.exercises + (exercise.id to info)) }
        applyChanges(changed = added)
        enqueue { workoutRepository.upsertSets(added) }
        expand(exercise.id)
    }

    /**
     * Quita el ejercicio y todas sus series de la sesión. La pantalla pide confirmación si tiene
     * datos; en cualquier caso se ofrece "Deshacer" ([WorkoutEvent.ExerciseRemoved]).
     */
    fun onRemoveExercise(exerciseId: String) {
        if (!canEdit()) return
        val removed = setsOf(exerciseId)
        if (removed.isEmpty()) return
        lastRemovedExercise = RemovedExercise(exerciseId, removed, editor.value.exercises[exerciseId])
        editor.update { state ->
            state.copy(
                sets = state.sets.filterNot { it.exerciseId == exerciseId },
                expandedExerciseId = state.expandedExerciseId.takeIf { it != exerciseId },
            )
        }
        enqueue { workoutRepository.removeExercise(sessionId, exerciseId) }
        eventChannel.trySend(WorkoutEvent.ExerciseRemoved(exerciseId))
    }

    /**
     * "Deshacer" de [onRemoveExercise]: vuelve con las mismas series y datos, en su posición o, si
     * entretanto otro ejercicio la ocupó, al final.
     */
    fun onUndoRemoveExercise(exerciseId: String) {
        val stored = lastRemovedExercise?.takeIf { it.exerciseId == exerciseId } ?: return
        if (!canEdit() || setsOf(exerciseId).isNotEmpty()) return
        lastRemovedExercise = null
        val usedOrders = editor.value.sets.mapTo(HashSet()) { it.exerciseOrder }
        val removed = if (stored.sets.first().exerciseOrder in usedOrders) {
            val order = usedOrders.max() + 1
            RemovedExercise(exerciseId, stored.sets.map { it.copy(exerciseOrder = order) }, stored.info)
        } else {
            stored
        }
        editor.update { state ->
            val info = removed.info ?: return@update state
            state.copy(exercises = state.exercises + (exerciseId to info))
        }
        applyChanges(changed = removed.sets)
        enqueue { workoutRepository.upsertSets(removed.sets) }
        expand(exerciseId)
    }

    // --- Navegación dentro de la sesión ---

    /** Abre [exerciseId] (cerrando el que estuviera abierto) o lo cierra si ya lo estaba. */
    fun onToggleExercise(exerciseId: String) {
        if (editor.value.expandedExerciseId != exerciseId) {
            expand(exerciseId)
        } else {
            editor.update { it.copy(expandedExerciseId = null) }
        }
    }

    // --- Cierre de la sesión ---

    fun onFinish() {
        if (!canEdit()) return
        editor.update { it.copy(isClosing = true, pickerGroup = null) }
        enqueue(reportFailure = false) {
            suspendRunCatching { finishSession(sessionId) }.flatten()
                .onSuccess { eventChannel.trySend(WorkoutEvent.OpenSummary(sessionId)) }
                .onFailure {
                    editor.update { it.copy(isClosing = false) }
                    eventChannel.trySend(WorkoutEvent.FinishFailed)
                }
        }
    }

    fun onDiscard() {
        if (!canEdit()) return
        editor.update { it.copy(isClosing = true, pickerGroup = null) }
        enqueue(reportFailure = false) {
            suspendRunCatching { discardSession(sessionId) }.flatten()
                .onSuccess { eventChannel.trySend(WorkoutEvent.Discarded) }
                .onFailure {
                    editor.update { it.copy(isClosing = false) }
                    eventChannel.trySend(WorkoutEvent.DiscardFailed)
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
                        groups = detail.groups.sortedBy { it.sortOrder },
                        exercises = detail.exercises.associate {
                            it.exerciseId to ExerciseInfo(it.muscleGroupId, it.imagePath)
                        },
                        expandedExerciseId = state.expandedExerciseId ?: lastEditedExercise(detail),
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

    private fun expand(exerciseId: String) {
        editor.update { it.copy(expandedExerciseId = exerciseId) }
        loadPrevious(exerciseId)
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

    // Los eventos de la cola usan trySend: tras cerrar la pantalla nadie los recoge y un send
    // suspendido con el búfer lleno bloquearía las escrituras siguientes.
    private fun enqueue(reportFailure: Boolean = true, write: suspend () -> Unit) {
        writes.trySend {
            val result = suspendRunCatching { write() }
            if (reportFailure && result.isFailure) eventChannel.trySend(WorkoutEvent.SaveFailed)
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
        createdAt = now,
        updatedAt = now,
    )

    /** Al reanudar se abre el ejercicio tocado más recientemente. */
    private fun lastEditedExercise(detail: SessionDetail): String? =
        detail.exercises.maxByOrNull { exercise -> exercise.sets.maxOf { it.updatedAt } }?.exerciseId

    private fun EditorState.toUi(stepKg: Double, groupExercises: GroupExercises?): WorkoutUiState = when (load) {
        Load.Loading -> WorkoutUiState.Loading
        Load.Failed -> WorkoutUiState.Error
        Load.Gone -> WorkoutUiState.NotInProgress
        is Load.Ready -> WorkoutUiState.Content(
            sessionId = sessionId,
            routineName = load.session.routineNameSnapshot,
            startedAt = load.session.startedAt,
            reminder = load.session.routineCommentSnapshot?.takeIf { it.isNotBlank() },
            blocks = buildBlocks(groups, sets, exercises, ghosts) { imageStore.fileOrNull(it) },
            expandedExerciseId = expandedExerciseId,
            weightStepKg = stepKg,
            hasAnyData = sets.any { !it.isEmpty },
            isClosing = isClosing,
            picker = pickerGroup?.let { group -> buildPicker(group, groupExercises) },
        )
    }

    private fun EditorState.buildPicker(group: SessionGroup, loaded: GroupExercises?): ExercisePickerUi? {
        val groupId = group.muscleGroupId ?: return null
        val list = loaded?.takeIf { it.groupId == groupId }
        val inSession = sets.mapTo(HashSet()) { it.exerciseId }
        return ExercisePickerUi(
            muscleGroupId = groupId,
            muscleGroupName = group.muscleGroupName,
            isLoading = list == null,
            exercises = list?.exercises.orEmpty().map { exercise ->
                PickerExerciseUi(
                    exerciseId = exercise.id,
                    name = exercise.name,
                    imageFile = imageStore.fileOrNull(exercise.imagePath),
                    alreadyAdded = exercise.id in inSession,
                )
            },
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
        val groups: List<SessionGroup> = emptyList(),
        /** Grupo actual e imagen de cada ejercicio de la sesión. */
        val exercises: Map<String, ExerciseInfo> = emptyMap(),
        val expandedExerciseId: String? = null,
        val ghosts: Map<String, List<GhostSet>> = emptyMap(),
        val isClosing: Boolean = false,
        val pickerGroup: SessionGroup? = null,
    )

    private data class GroupExercises(val groupId: String, val exercises: List<Exercise>)

    private class RemovedExercise(val exerciseId: String, val sets: List<WorkoutSet>, val info: ExerciseInfo?)

    companion object {
        private val SetOrder = compareBy<WorkoutSet>({ it.exerciseOrder }, { it.setIndex })
        private const val MAX_INITIAL_SETS = 20

        /**
         * Un bloque por grupo previsto de la sesión (aunque aún no tenga ejercicios), con sus
         * ejercicios en orden de sesión. Un ejercicio cuyo grupo no está previsto (sesión antigua o
         * ejercicio movido de grupo) va al final, en bloques por su grupo, sin botón de añadir.
         */
        internal fun buildBlocks(
            groups: List<SessionGroup>,
            sets: List<WorkoutSet>,
            exercises: Map<String, ExerciseInfo>,
            ghosts: Map<String, List<GhostSet>>,
            resolveImage: (String?) -> File? = { null },
        ): List<WorkoutBlockUi> {
            val items = sets.sortedWith(SetOrder).groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
                val previous = ghosts[exerciseId]
                val info = exercises[exerciseId]
                SessionItem(
                    groupId = info?.muscleGroupId,
                    groupName = exerciseSets.first().muscleGroupNameSnapshot,
                    ui = WorkoutExerciseUi(
                        exerciseId = exerciseId,
                        name = exerciseSets.first().exerciseNameSnapshot,
                        sets = exerciseSets.mapIndexed { index, set ->
                            val ghost = previous?.getOrNull(index)
                            WorkoutSetUi(
                                setId = set.id,
                                number = index + 1,
                                weightKg = set.weightKg,
                                reps = set.reps,
                                ghostWeightKg = ghost?.weightKg,
                                ghostReps = ghost?.reps,
                            )
                        },
                        previousSets = previous,
                        imageFile = resolveImage(info?.imagePath),
                    ),
                )
            }
            val planned = groups.distinctBy { it.muscleGroupId ?: it.id }
            val plannedIds = planned.mapNotNull { it.muscleGroupId }.toSet()
            val blocks = planned.map { group ->
                WorkoutBlockUi(
                    key = "group-${group.id}",
                    muscleGroupName = group.muscleGroupName,
                    muscleGroupId = group.muscleGroupId,
                    exercises = items.filter { it.groupId != null && it.groupId == group.muscleGroupId }.map { it.ui },
                )
            }.toMutableList()
            items.filter { it.groupId == null || it.groupId !in plannedIds }.forEach { item ->
                val last = blocks.lastOrNull()
                if (last != null && last.key.startsWith(OTHER_KEY) && last.muscleGroupName == item.groupName) {
                    blocks[blocks.lastIndex] = last.copy(exercises = last.exercises + item.ui)
                } else {
                    blocks += WorkoutBlockUi("$OTHER_KEY${blocks.size}", item.groupName, null, listOf(item.ui))
                }
            }
            return blocks
        }

        private const val OTHER_KEY = "other-"
    }

    private class SessionItem(val groupId: String?, val groupName: String, val ui: WorkoutExerciseUi)
}

/** Grupo actual y foto de un ejercicio de la sesión (de la biblioteca, no del snapshot). */
internal data class ExerciseInfo(val muscleGroupId: String?, val imagePath: String?)
