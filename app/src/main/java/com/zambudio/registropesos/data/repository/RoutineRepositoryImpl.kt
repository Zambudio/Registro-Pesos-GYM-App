package com.zambudio.registropesos.data.repository

import androidx.room.withTransaction
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.data.mapper.toDomain
import com.zambudio.registropesos.data.mapper.toEntity
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoutineRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
) : RoutineRepository {
    private val dao = db.routineDao()

    override fun observeSummaries(): Flow<List<RoutineSummary>> =
        db.invalidationTracker
            .createFlow("routines", "routine_blocks", "muscle_groups", emitInitialState = true)
            .map { loadSummaries() }
            .distinctUntilChanged()

    override fun observeDetail(id: String): Flow<RoutineDetail?> =
        db.invalidationTracker
            .createFlow(
                "routines",
                "routine_blocks",
                "routine_exercises",
                "exercises",
                "muscle_groups",
                emitInitialState = true,
            )
            .map { loadDetail(id) }
            .distinctUntilChanged()

    override suspend fun getDetail(id: String): RoutineDetail? = loadDetail(id)

    /**
     * Upsert de la rutina, borra los bloques/ejercicios que ya no están y upsert del resto.
     * El orden de las listas manda: sortOrder se normaliza a la posición.
     */
    override suspend fun save(detail: RoutineDetail) {
        val now = time.now()
        val routine = detail.routine
        val blocks = detail.blocks.mapIndexed { index, blockDetail ->
            val block = blockDetail.block
            block.copy(
                routineId = routine.id,
                sortOrder = index,
                createdAt = block.createdAt.orNow(now),
                updatedAt = now,
            )
        }
        val exercises = detail.blocks.flatMap { blockDetail ->
            blockDetail.exercises.mapIndexed { index, exerciseDetail ->
                val item = exerciseDetail.routineExercise
                item.copy(
                    blockId = blockDetail.block.id,
                    sortOrder = index,
                    createdAt = item.createdAt.orNow(now),
                    updatedAt = now,
                )
            }
        }
        db.withTransaction {
            val existing = dao.getById(routine.id)
            val createdAt = existing?.createdAt ?: routine.createdAt.orNow(now)
            dao.save(routine.copy(createdAt = createdAt, updatedAt = now).toEntity())
            dao.deleteRoutineExercisesNotIn(routine.id, exercises.map { it.id })
            dao.deleteBlocksNotIn(routine.id, blocks.map { it.id })
            blocks.forEach { dao.saveBlock(it.toEntity()) }
            exercises.forEach { dao.saveRoutineExercise(it.toEntity()) }
            db.tombstoneDao().delete(TombstoneType.ROUTINE, routine.id)
        }
    }

    override suspend fun reorder(orderedIds: List<String>) {
        val now = time.now()
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.updateSortOrder(id, index, now) }
        }
    }

    override suspend fun delete(id: String) {
        db.withTransaction {
            if (dao.deleteById(id) > 0) {
                db.tombstoneDao().upsert(DeletedEntityEntity(TombstoneType.ROUTINE, id, time.now()))
            }
        }
    }

    /** Resumen de cada rutina con los nombres de sus grupos en orden (group_concat no garantiza orden). */
    private suspend fun loadSummaries(): List<RoutineSummary> = db.withTransaction {
        val groupNames = db.muscleGroupDao().getAll().associate { it.id to it.name }
        val blocksByRoutine = dao.getAllBlocks().groupBy { it.routineId }
        dao.getAll().map { routine ->
            RoutineSummary(
                id = routine.id,
                name = routine.name,
                subtitle = routine.subtitle,
                groupNames = blocksByRoutine[routine.id].orEmpty()
                    .sortedBy { it.sortOrder }
                    .mapNotNull { groupNames[it.muscleGroupId] },
            )
        }
    }

    private suspend fun loadDetail(id: String): RoutineDetail? = db.withTransaction {
        val routine = dao.getById(id) ?: return@withTransaction null
        val blocks = dao.getBlocks(id)
        val items = dao.getRoutineExercises(id)
        val groups = db.muscleGroupDao().getByIds(blocks.map { it.muscleGroupId }.distinct()).associateBy { it.id }
        val exercises = db.exerciseDao().getByIds(items.map { it.exerciseId }.distinct()).associateBy { it.id }
        val itemsByBlock = items.groupBy { it.blockId }
        // Las FKs garantizan que existen; mapNotNull solo es defensivo.
        RoutineDetail(
            routine = routine.toDomain(),
            blocks = blocks.mapNotNull { block ->
                val group = groups[block.muscleGroupId] ?: return@mapNotNull null
                RoutineBlockDetail(
                    block = block.toDomain(),
                    muscleGroup = group.toDomain(),
                    exercises = itemsByBlock[block.id].orEmpty()
                        .sortedBy { it.sortOrder }
                        .mapNotNull { item ->
                            exercises[item.exerciseId]?.let { RoutineExerciseDetail(item.toDomain(), it.toDomain()) }
                        },
                )
            },
        )
    }
}

internal fun Long.orNow(now: Long): Long = if (this > 0) this else now
