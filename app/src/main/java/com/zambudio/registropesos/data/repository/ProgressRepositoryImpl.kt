package com.zambudio.registropesos.data.repository

import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.mapper.toDomain
import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.SessionVolumePoint
import com.zambudio.registropesos.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProgressRepositoryImpl @Inject constructor(db: AppDatabase) : ProgressRepository {
    private val dao = db.progressDao()

    override fun observeExercisePoints(exerciseId: String, fromMillis: Long): Flow<List<ExerciseSessionPoint>> =
        dao.observeExercisePoints(exerciseId, fromMillis).map { rows -> rows.map { it.toDomain() } }

    override fun observeGroupSessionVolumes(groupId: String, fromMillis: Long): Flow<List<SessionVolumePoint>> =
        dao.observeGroupSessionVolumes(groupId, fromMillis).map { rows -> rows.map { it.toDomain() } }

    override fun observeFinishedSetsFlat(exerciseId: String?): Flow<List<FlatSet>> =
        dao.observeFinishedSetsFlat(exerciseId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun finishedSetsFlat(exerciseId: String?): List<FlatSet> =
        dao.finishedSetsFlat(exerciseId).map { it.toDomain() }
}
