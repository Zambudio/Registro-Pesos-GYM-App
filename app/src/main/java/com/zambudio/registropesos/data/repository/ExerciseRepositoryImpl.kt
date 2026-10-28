package com.zambudio.registropesos.data.repository

import androidx.room.withTransaction
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.mapper.toDomain
import com.zambudio.registropesos.data.mapper.toEntity
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ExerciseRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
) : ExerciseRepository {
    private val dao = db.exerciseDao()

    override fun observeAll(includeArchived: Boolean): Flow<List<Exercise>> =
        dao.observeAll(includeArchived).map { list -> list.map { it.toDomain() } }

    override fun observeByGroup(groupId: String, includeArchived: Boolean): Flow<List<Exercise>> =
        dao.observeByGroup(groupId, includeArchived).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Exercise?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Exercise? = dao.getById(id)?.toDomain()

    override suspend fun upsert(exercise: Exercise) {
        val now = time.now()
        db.withTransaction {
            val existing = dao.getById(exercise.id)
            val createdAt = existing?.createdAt ?: exercise.createdAt.takeIf { it > 0 } ?: now
            dao.save(exercise.copy(createdAt = createdAt, updatedAt = now).toEntity())
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        dao.setArchived(id, archived, time.now())
    }

    override suspend fun hasHistory(id: String): Boolean = dao.hasHistory(id)
}
