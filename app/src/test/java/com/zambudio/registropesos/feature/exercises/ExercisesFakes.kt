package com.zambudio.registropesos.feature.exercises

import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.repository.MuscleGroupInUseException
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

// Fakes en memoria (reactivos) para los ViewModels de ejercicios y grupos musculares.

fun group(id: String, name: String, sortOrder: Int) = MuscleGroup(id, name, sortOrder, isDefault = false, 0L, 0L)

fun exercise(
    id: String,
    name: String,
    groupId: String,
    archived: Boolean = false,
    imagePath: String? = null,
    createdAt: Long = 100L,
) = Exercise(id, groupId, name, null, imagePath, archived, createdAt, createdAt)

class FakeExerciseRepository(initial: List<Exercise> = emptyList()) : ExerciseRepository {
    val exercises = MutableStateFlow(initial)
    val upserts = mutableListOf<Exercise>()

    override fun observeAll(includeArchived: Boolean): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { includeArchived || !it.isArchived } }

    override fun observeByGroup(groupId: String, includeArchived: Boolean): Flow<List<Exercise>> =
        observeAll(includeArchived).map { list -> list.filter { it.muscleGroupId == groupId } }

    override fun observeById(id: String): Flow<Exercise?> = exercises.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): Exercise? = exercises.value.firstOrNull { it.id == id }

    override suspend fun upsert(exercise: Exercise) {
        upserts += exercise
        exercises.value = exercises.value.filterNot { it.id == exercise.id } + exercise
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        exercises.value = exercises.value.map { if (it.id == id) it.copy(isArchived = archived) else it }
    }

    override suspend fun hasHistory(id: String): Boolean = false
}

class FakeMuscleGroupRepository(
    initial: List<MuscleGroup> = emptyList(),
    private val exercises: FakeExerciseRepository? = null,
) : MuscleGroupRepository {
    val groups = MutableStateFlow(initial)
    val reorderCalls = mutableListOf<List<String>>()

    /** Simula el índice único de SQLite lanzando esta excepción en el próximo upsert. */
    var upsertError: (() -> Throwable)? = null

    override fun observeAll(): Flow<List<MuscleGroup>> = groups.map { list -> list.sortedBy { it.sortOrder } }

    override fun observeExerciseCounts(): Flow<Map<String, Int>> =
        (exercises?.exercises ?: MutableStateFlow(emptyList())).map { list ->
            list.filterNot { it.isArchived }.groupingBy { it.muscleGroupId }.eachCount()
        }

    override suspend fun getById(id: String): MuscleGroup? = groups.value.firstOrNull { it.id == id }

    override suspend fun upsert(group: MuscleGroup) {
        upsertError?.let { factory ->
            upsertError = null
            throw factory()
        }
        groups.value = groups.value.filterNot { it.id == group.id } + group
    }

    override suspend fun reorder(orderedIds: List<String>) {
        reorderCalls += orderedIds
        groups.value = groups.value.map { it.copy(sortOrder = orderedIds.indexOf(it.id)) }
    }

    override suspend fun delete(id: String): Result<Unit> {
        val inUse = exercises?.exercises?.value.orEmpty().any { it.muscleGroupId == id }
        if (inUse) return Result.failure(MuscleGroupInUseException("en uso: $id"))
        groups.value = groups.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}

/** Guarda "ficheros" como nombres; [deleted] registra los borrados. */
class FakeImageStore(private val root: File = File("fake-images")) : ImageStore {
    val saved = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failNextSave = false
    private var counter = 0

    override suspend fun saveResized(sourceUri: String): String {
        if (failNextSave) {
            failNextSave = false
            throw IOException("imagen inválida: $sourceUri")
        }
        val name = "exercise_new${++counter}.jpg"
        saved += name
        return name
    }

    override fun resolve(relativePath: String): File = File(root, relativePath)

    override suspend fun delete(relativePath: String) {
        deleted += relativePath
    }

    override fun listAll(): List<File> = emptyList()

    override suspend fun importRaw(relativePath: String, bytes: ByteArray) = Unit
}

class FixedClock(var now: Long = 5_000L) : TimeProvider {
    override fun now(): Long = now
}

class CountingIds : IdGenerator {
    private var counter = 0
    override fun newId(): String = "new-${++counter}"
}
