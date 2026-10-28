package com.zambudio.registropesos.feature.workout

import androidx.lifecycle.SavedStateHandle
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionExercise
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.usecase.SessionVolume
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
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

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

/** Repositorio de sesiones en memoria con Flows reactivos, como Room. */
class InMemoryWorkoutRepository(
    private val ids: IdGenerator = TestIds("set"),
) : WorkoutRepository {
    val sessions = MutableStateFlow<Map<String, WorkoutSession>>(emptyMap())
    val sets = MutableStateFlow<List<WorkoutSet>>(emptyList())

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
        combine(sessions, sets) { all, _ -> all[id]?.let { SessionDetail(it, exercisesOf(id)) } }

    override suspend fun getSession(id: String): WorkoutSession? = sessions.value[id]

    override suspend fun start(routine: RoutineDetail, startedAt: Long): String {
        getInProgress()?.let { throw SessionAlreadyInProgressException(it.id) }
        val sessionId = "session-${sessions.value.size + 1}"
        addSession(sessionId, routine.routine.name, startedAt)
        var order = 0
        val created = routine.blocks.flatMap { block ->
            block.exercises.flatMap { item ->
                val exerciseOrder = order++
                val exercise = item.exercise
                val group = block.muscleGroup.name
                (0 until item.routineExercise.defaultSets).map { index ->
                    emptySet(ids.newId(), sessionId, exercise.id, exercise.name, group, exerciseOrder, index)
                }
            }
        }
        sets.update { it + created }
        return sessionId
    }

    fun addSession(
        id: String,
        routineName: String = "Día 1",
        startedAt: Long = 0L,
        status: SessionStatus = SessionStatus.IN_PROGRESS,
        finishedAt: Long? = null,
    ) {
        val session = WorkoutSession(id, "r1", routineName, startedAt, finishedAt, status, null, startedAt, startedAt)
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
    }

    private fun exercisesOf(sessionId: String): List<SessionExercise> =
        setsOf(sessionId).groupBy { it.exerciseId }.map { (exerciseId, exerciseSets) ->
            val first = exerciseSets.first()
            SessionExercise(
                exerciseId,
                first.exerciseNameSnapshot,
                first.muscleGroupNameSnapshot,
                first.exerciseOrder,
                exerciseSets,
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
            isDone = false,
            completedAt = null,
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
                blockCount = detail.blocks.size,
                exerciseCount = detail.blocks.sumOf { it.exercises.size },
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
