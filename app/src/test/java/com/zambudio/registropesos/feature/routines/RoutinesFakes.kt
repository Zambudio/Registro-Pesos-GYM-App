package com.zambudio.registropesos.feature.routines

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
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// Fakes en memoria para los ViewModels de rutinas.

private fun unused(): Nothing = error("No usado en estos tests")

class FakeRoutineRepository(initial: List<RoutineDetail> = emptyList()) : RoutineRepository {
    private val details = MutableStateFlow(initial)
    val saved = mutableListOf<RoutineDetail>()
    val reorderCalls = mutableListOf<List<String>>()
    val deleted = mutableListOf<String>()
    var failOnSave = false
    var failOnDelete = false

    override fun observeSummaries(): Flow<List<RoutineSummary>> = details.map { list ->
        list.sortedBy { it.routine.sortOrder }.map { detail ->
            RoutineSummary(
                id = detail.routine.id,
                name = detail.routine.name,
                subtitle = detail.routine.subtitle,
                blockCount = detail.blocks.size,
                exerciseCount = detail.blocks.sumOf { it.exercises.size },
            )
        }
    }

    override fun observeDetail(id: String): Flow<RoutineDetail?> =
        details.map { list -> list.firstOrNull { it.routine.id == id } }

    override suspend fun getDetail(id: String): RoutineDetail? = details.value.firstOrNull { it.routine.id == id }

    override suspend fun save(detail: RoutineDetail) {
        check(!failOnSave) { "fallo simulado" }
        saved += detail
        details.value = details.value.filterNot { it.routine.id == detail.routine.id } + detail
    }

    override suspend fun reorder(orderedIds: List<String>) {
        reorderCalls += orderedIds
        details.value = details.value.map { detail ->
            detail.copy(routine = detail.routine.copy(sortOrder = orderedIds.indexOf(detail.routine.id)))
        }
    }

    override suspend fun delete(id: String) {
        check(!failOnDelete) { "fallo simulado" }
        deleted += id
        details.value = details.value.filterNot { it.routine.id == id }
    }
}

class FakeMuscleGroupRepository(groups: List<MuscleGroup>) : MuscleGroupRepository {
    private val flow = MutableStateFlow(groups)
    override fun observeAll(): Flow<List<MuscleGroup>> = flow
    override fun observeExerciseCounts(): Flow<Map<String, Int>> = unused()
    override suspend fun getById(id: String): MuscleGroup? = flow.value.firstOrNull { it.id == id }
    override suspend fun upsert(group: MuscleGroup) { unused() }
    override suspend fun reorder(orderedIds: List<String>) { unused() }
    override suspend fun delete(id: String): Result<Unit> = unused()
}

class FakeExerciseRepository(exercises: List<Exercise>) : ExerciseRepository {
    val exercises = MutableStateFlow(exercises)

    override fun observeAll(includeArchived: Boolean): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { includeArchived || !it.isArchived }.sortedBy { it.name } }

    override fun observeByGroup(groupId: String, includeArchived: Boolean): Flow<List<Exercise>> =
        observeAll(includeArchived).map { list -> list.filter { it.muscleGroupId == groupId } }

    override fun observeById(id: String): Flow<Exercise?> = unused()
    override suspend fun getById(id: String): Exercise? = exercises.value.firstOrNull { it.id == id }
    override suspend fun upsert(exercise: Exercise) { unused() }
    override suspend fun setArchived(id: String, archived: Boolean) { unused() }
    override suspend fun hasHistory(id: String): Boolean = unused()
}

class FakeSettingsRepository(defaultSets: Int = 4) : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings(defaultSets = defaultSets))
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        settings.value = transform(settings.value)
    }
}

/** Ids deterministas: id-1, id-2… */
class SequentialIdGenerator : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${++next}"
}

// --- Datos de prueba ---

val pecho = MuscleGroup("g-pecho", "Pecho", 0, true, 1L, 1L)
val triceps = MuscleGroup("g-triceps", "Tríceps", 1, true, 1L, 1L)
val cardio = MuscleGroup("g-cardio", "Cardio", 2, true, 1L, 1L)

fun exercise(id: String, group: MuscleGroup, name: String, archived: Boolean = false) =
    Exercise(id, group.id, name, null, null, archived, 1L, 1L)

val pressBanca = exercise("e-press", pecho, "Press banca")
val aperturas = exercise("e-aperturas", pecho, "Aperturas")
val fondos = exercise("e-fondos", pecho, "Fondos")
val pressArchivado = exercise("e-press-viejo", pecho, "Press viejo", archived = true)
val extensiones = exercise("e-ext", triceps, "Extensiones")

/** Rutina guardada "r1": bloque Pecho (press 4 series, aperturas 3) + bloque Tríceps (extensiones 4). */
fun storedRoutine(
    id: String = "r1",
    name: String = "Día 1",
    sortOrder: Int = 0,
    createdAt: Long = 100L,
): RoutineDetail {
    fun item(itemId: String, block: RoutineBlock, exercise: Exercise, order: Int, sets: Int) =
        RoutineExerciseDetail(
            RoutineExercise(itemId, block.id, exercise.id, order, sets, createdAt, createdAt),
            exercise,
        )
    val routine = Routine(id, name, "Pecho y tríceps", sortOrder, createdAt, createdAt)
    val b1 = RoutineBlock("$id-b1", id, pecho.id, 0, createdAt, createdAt)
    val b2 = RoutineBlock("$id-b2", id, triceps.id, 1, createdAt, createdAt)
    return RoutineDetail(
        routine = routine,
        blocks = listOf(
            RoutineBlockDetail(
                block = b1,
                muscleGroup = pecho,
                exercises = listOf(
                    item("$id-i1", b1, pressBanca, 0, 4),
                    item("$id-i2", b1, aperturas, 1, 3),
                ),
            ),
            RoutineBlockDetail(
                block = b2,
                muscleGroup = triceps,
                exercises = listOf(
                    item("$id-i3", b2, extensiones, 0, 4),
                ),
            ),
        ),
    )
}
