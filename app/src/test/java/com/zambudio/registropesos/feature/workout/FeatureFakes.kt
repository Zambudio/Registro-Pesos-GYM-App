package com.zambudio.registropesos.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.RestTimerState
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionExercise
import com.zambudio.registropesos.domain.model.SessionGroup
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import com.zambudio.registropesos.domain.repository.RestTimer
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.SessionVolume
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// Fakes en memoria y utilidades para los tests de ViewModel de feature/workout y feature/history.

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class TestClock(var now: Long = 1_000_000L) : TimeProvider {
    override fun now(): Long = now
}

class TestIds(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}

/** Argumentos de ruta. toRoute() usa android.os.Bundle: los tests que lo usan corren con Robolectric. */
fun routeHandle(vararg args: Pair<String, Any?>) = SavedStateHandle(mapOf(*args))

/** Colecciona [flow] durante el test, de forma inmediata (el StateFlow WhileSubscribed se activa ya). */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.collectInBackground(flow: Flow<T>, sink: (T) -> Unit = {}) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { sink(it) } }
}

/**
 * Sustituto del @ApplicationScope: inmediato (las escrituras encoladas se aplican en la misma
 * llamada) y cancelado al acabar el test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.immediateApplicationScope(): CoroutineScope =
    CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

/** Simula cerrar la pantalla: ViewModelStore.clear() cancela viewModelScope y llama a onCleared(). */
fun ViewModel.clearAsIfScreenClosed() {
    val vm = this
    val store = ViewModelStore()
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
    }
    ViewModelProvider(store, factory)[vm.javaClass]
    store.clear()
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

/** Descanso en memoria: guarda el estado y registra las llamadas. */
class FakeRestTimer(private val clock: TimeProvider) : RestTimer {
    override val state = MutableStateFlow<RestTimerState?>(null)
    val starts = mutableListOf<Long>()
    var stops = 0

    override suspend fun start(durationMillis: Long) {
        starts += durationMillis
        state.value = RestTimerState(clock.now() + durationMillis, durationMillis)
    }

    override suspend fun adjust(deltaMillis: Long) {
        state.update { current -> current?.copy(endsAt = current.endsAt + deltaMillis) }
    }

    override suspend fun stop() {
        stops++
        state.value = null
    }
}

/** Repositorio de sesiones en memoria con Flows reactivos, como Room. */
class InMemoryWorkoutRepository : WorkoutRepository {
    val sessions = MutableStateFlow<Map<String, WorkoutSession>>(emptyMap())
    val sets = MutableStateFlow<List<WorkoutSet>>(emptyList())
    val groups = MutableStateFlow<List<SessionGroup>>(emptyList())

    /** Grupo actual de cada ejercicio; por defecto "g-<nombre del grupo del snapshot>", como routineDetail(). */
    val exerciseGroupIds = mutableMapOf<String, String>()

    /** Series de la "sesión anterior" por ejercicio que devuelve lastFinishedSetsForExercise. */
    val lastFinished = mutableMapOf<String, List<WorkoutSet>>()

    /** Cada lote escrito con upsertSet/upsertSets, en orden. */
    val writes = mutableListOf<List<WorkoutSet>>()
    var failWrites = false
    var failDelete = false

    fun setsOf(sessionId: String): List<WorkoutSet> =
        sets.value.filter { it.sessionId == sessionId }.sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex }))

    fun set(id: String): WorkoutSet = sets.value.single { it.id == id }

    override fun observeInProgress(): Flow<WorkoutSession?> =
        sessions.map { all -> all.values.firstOrNull { it.status == SessionStatus.IN_PROGRESS } }

    override suspend fun getInProgress(): WorkoutSession? =
        sessions.value.values.firstOrNull { it.status == SessionStatus.IN_PROGRESS }

    override fun observeSessionDetail(id: String): Flow<SessionDetail?> =
        combine(sessions, sets, groups) { all, _, sessionGroups ->
            all[id]?.let { session ->
                val planned = sessionGroups.filter { it.sessionId == id }.sortedBy { it.sortOrder }
                SessionDetail(session, exercisesOf(id), planned)
            }
        }

    override suspend fun getSession(id: String): WorkoutSession? = sessions.value[id]

    override suspend fun start(routine: RoutineDetail, startedAt: Long): String {
        getInProgress()?.let { throw SessionAlreadyInProgressException(it.id) }
        val sessionId = "session-${sessions.value.size + 1}"
        addSession(sessionId, routine.routine.name, startedAt, comment = routine.routine.subtitle)
        addGroups(sessionId, *routine.blocks.map { it.muscleGroup.name }.distinct().toTypedArray())
        return sessionId
    }

    /** Grupos previstos de la sesión, con id de grupo "g-<nombre>". */
    fun addGroups(sessionId: String, vararg names: String) {
        groups.update { current ->
            current + names.mapIndexed { index, name ->
                SessionGroup("$sessionId-sg$index", sessionId, "g-$name", name, index, 0L, 0L)
            }
        }
    }

    fun addSession(
        id: String,
        routineName: String = "Día 1",
        startedAt: Long = 0L,
        status: SessionStatus = SessionStatus.IN_PROGRESS,
        finishedAt: Long? = null,
        comment: String? = null,
    ) {
        val session = WorkoutSession(
            id,
            "r1",
            routineName,
            startedAt,
            finishedAt,
            status,
            null,
            startedAt,
            startedAt,
            routineCommentSnapshot = comment,
        )
        sessions.update { it + (id to session) }
    }

    fun addSets(vararg newSets: WorkoutSet) = sets.update { it + newSets }

    override suspend fun upsertSet(set: WorkoutSet) = upsertSets(listOf(set))

    override suspend fun upsertSets(sets: List<WorkoutSet>) {
        check(!failWrites) { "Fallo de escritura simulado" }
        if (sets.isEmpty()) return
        writes += sets
        val byId = sets.associateBy { it.id }
        this.sets.update { current -> current.filterNot { it.id in byId } + sets }
    }

    override suspend fun deleteSet(setId: String) {
        check(!failWrites) { "Fallo de escritura simulado" }
        sets.update { current -> current.filterNot { it.id == setId } }
    }

    override suspend fun removeExercise(sessionId: String, exerciseId: String) {
        check(!failWrites) { "Fallo de escritura simulado" }
        sets.update { current -> current.filterNot { it.sessionId == sessionId && it.exerciseId == exerciseId } }
    }

    override suspend fun finish(sessionId: String, finishedAt: Long, keepSetIds: List<String>) {
        sets.update { current -> current.filterNot { it.sessionId == sessionId && it.id !in keepSetIds } }
        sessions.update { all ->
            all + (sessionId to all.getValue(sessionId).copy(status = SessionStatus.FINISHED, finishedAt = finishedAt))
        }
    }

    override suspend fun discard(sessionId: String) = deleteSession(sessionId)

    override suspend fun lastFinishedSetsForExercise(exerciseId: String, excludeSessionId: String?): List<WorkoutSet> =
        lastFinished[exerciseId].orEmpty()

    override fun observeFinishedSessions(): Flow<List<SessionSummary>> = combine(sessions, sets) { all, _ ->
        all.values.filter { it.status == SessionStatus.FINISHED }
            .sortedByDescending { it.startedAt }
            .map { session ->
                val sessionSets = setsOf(session.id)
                SessionSummary(
                    id = session.id,
                    routineName = session.routineNameSnapshot,
                    startedAt = session.startedAt,
                    finishedAt = session.finishedAt,
                    exerciseCount = sessionSets.map { it.exerciseId }.distinct().size,
                    volumeKg = SessionVolume(sessionSets),
                )
            }
    }

    override suspend fun deleteSession(id: String) {
        check(!failDelete) { "Fallo de borrado simulado" }
        sessions.update { it - id }
        sets.update { current -> current.filterNot { it.sessionId == id } }
        groups.update { current -> current.filterNot { it.sessionId == id } }
    }

    private fun exercisesOf(sessionId: String): List<SessionExercise> =
        setsOf(sessionId).groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
            val first = exerciseSets.first()
            SessionExercise(
                exerciseId = exerciseId,
                exerciseName = first.exerciseNameSnapshot,
                muscleGroupName = first.muscleGroupNameSnapshot,
                muscleGroupId = exerciseGroupIds[exerciseId] ?: "g-${first.muscleGroupNameSnapshot}",
                order = first.exerciseOrder,
                sets = exerciseSets,
            )
        }

    companion object {
        fun emptySet(
            id: String,
            sessionId: String,
            exerciseId: String,
            exerciseName: String,
            groupName: String,
            exerciseOrder: Int,
            setIndex: Int,
            weightKg: Double? = null,
            reps: Int? = null,
        ) = WorkoutSet(
            id = id,
            sessionId = sessionId,
            exerciseId = exerciseId,
            exerciseNameSnapshot = exerciseName,
            muscleGroupNameSnapshot = groupName,
            exerciseOrder = exerciseOrder,
            setIndex = setIndex,
            weightKg = weightKg,
            reps = reps,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}

class InMemoryRoutineRepository(vararg initial: RoutineDetail) : RoutineRepository {
    val details = MutableStateFlow(initial.associateBy { it.routine.id })

    override fun observeSummaries(): Flow<List<RoutineSummary>> = details.map { all ->
        all.values.map { detail ->
            RoutineSummary(
                id = detail.routine.id,
                name = detail.routine.name,
                subtitle = detail.routine.subtitle,
                groupNames = detail.blocks.map { it.muscleGroup.name },
            )
        }
    }

    override fun observeDetail(id: String): Flow<RoutineDetail?> = details.map { it[id] }
    override suspend fun getDetail(id: String): RoutineDetail? = details.value[id]
    override suspend fun save(detail: RoutineDetail) = details.update { it + (detail.routine.id to detail) }
    override suspend fun reorder(orderedIds: List<String>) = Unit
    override suspend fun delete(id: String) = details.update { it - id }
}

/** Rutina de prueba: bloques (grupo → [(id de ejercicio, nombre, series)]). */
fun routineDetail(
    id: String,
    name: String,
    vararg blocks: Pair<String, List<Triple<String, String, Int>>>,
): RoutineDetail = RoutineDetail(
    routine = Routine(id, name, null, 0, 0L, 0L),
    blocks = blocks.mapIndexed { blockIndex, (groupName, items) ->
        val blockId = "$id-b$blockIndex"
        RoutineBlockDetail(
            block = RoutineBlock(blockId, id, "g-$groupName", blockIndex, 0L, 0L),
            muscleGroup = MuscleGroup("g-$groupName", groupName, blockIndex, true, 0L, 0L),
            exercises = items.mapIndexed { index, (exerciseId, exerciseName, sets) ->
                RoutineExerciseDetail(
                    RoutineExercise("$blockId-e$index", blockId, exerciseId, index, sets, 0L, 0L),
                    Exercise(exerciseId, "g-$groupName", exerciseName, null, null, false, 0L, 0L),
                )
            },
        )
    },
)
