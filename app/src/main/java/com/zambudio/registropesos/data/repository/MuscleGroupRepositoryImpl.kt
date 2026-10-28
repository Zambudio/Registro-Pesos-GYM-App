package com.zambudio.registropesos.data.repository

import androidx.room.withTransaction
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.data.mapper.toDomain
import com.zambudio.registropesos.data.mapper.toEntity
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.repository.MuscleGroupInUseException
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MuscleGroupRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
) : MuscleGroupRepository {
    private val dao = db.muscleGroupDao()

    override fun observeAll(): Flow<List<MuscleGroup>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeExerciseCounts(): Flow<Map<String, Int>> =
        dao.observeExerciseCounts().map { rows -> rows.associate { it.groupId to it.count } }

    override suspend fun getById(id: String): MuscleGroup? = dao.getById(id)?.toDomain()

    /** Un nombre repetido (sin distinguir mayúsculas) lanza SQLiteConstraintException. */
    override suspend fun upsert(group: MuscleGroup) {
        val now = time.now()
        db.withTransaction {
            val existing = dao.getById(group.id)
            val createdAt = existing?.createdAt ?: group.createdAt.takeIf { it > 0 } ?: now
            dao.save(group.copy(createdAt = createdAt, updatedAt = now).toEntity())
            db.tombstoneDao().delete(TombstoneType.MUSCLE_GROUP, group.id)
        }
    }

    override suspend fun reorder(orderedIds: List<String>) {
        val now = time.now()
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> dao.updateSortOrder(id, index, now) }
        }
    }

    override suspend fun delete(id: String): Result<Unit> = db.withTransaction {
        val exercises = dao.countExercises(id)
        val blocks = dao.countBlocks(id)
        if (exercises > 0 || blocks > 0) {
            Result.failure(
                MuscleGroupInUseException(
                    "El grupo $id está en uso: $exercises ejercicios y $blocks bloques de rutina",
                ),
            )
        } else {
            if (dao.deleteById(id) > 0) {
                db.tombstoneDao().upsert(DeletedEntityEntity(TombstoneType.MUSCLE_GROUP, id, time.now()))
            }
            Result.success(Unit)
        }
    }
}
