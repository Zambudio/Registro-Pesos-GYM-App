package com.zambudio.registropesos.data.repository

import androidx.room.withTransaction
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.data.local.entity.WorkoutSessionEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSessionGroupEntity
import com.zambudio.registropesos.data.mapper.toDomain
import com.zambudio.registropesos.data.mapper.toEntity
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionExercise
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkoutRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) : WorkoutRepository {
    private val dao = db.workoutDao()

    override fun observeInProgress(): Flow<WorkoutSession?> = dao.observeInProgress().map { it?.toDomain() }

    override suspend fun getInProgress(): WorkoutSession? = dao.getInProgress()?.toDomain()

    override fun observeSessionDetail(id: String): Flow<SessionDetail?> =
        db.invalidationTracker
            .createFlow(
                "workout_sessions",
                "workout_sets",
                "workout_session_groups",
                "exercises",
                emitInitialState = true,
            )
            .map { loadSessionDetail(id) }
            .distinctUntilChanged()

    override suspend fun getSession(id: String): WorkoutSession? = dao.getSession(id)?.toDomain()

    /**
     * Comprobación y alta en la misma transacción: garantiza una sola sesión IN_PROGRESS (ADR-005).
     * La sesión empieza sin ejercicios: se copian los grupos de la rutina (un grupo repetido solo
     * cuenta una vez) y su comentario. Los ejercicios se eligen durante la sesión (ADR-018).
     */
    override suspend fun start(routine: RoutineDetail, startedAt: Long): String = db.withTransaction {
        dao.getInProgress()?.let { throw SessionAlreadyInProgressException(it.id) }
        val now = time.now()
        val sessionId = ids.newId()
        val routineExists = db.routineDao().getById(routine.routine.id) != null
        dao.insertSession(
            WorkoutSessionEntity(
                id = sessionId,
                routineId = routine.routine.id.takeIf { routineExists },
                routineNameSnapshot = routine.routine.name,
                startedAt = startedAt,
                finishedAt = null,
                status = SessionStatus.IN_PROGRESS,
                notes = null,
                createdAt = now,
                updatedAt = now,
                routineCommentSnapshot = routine.routine.subtitle?.trim()?.ifEmpty { null },
            ),
        )
        val groups = routine.blocks.map { it.muscleGroup }.distinctBy { it.id }
        dao.insertSessionGroups(
            groups.mapIndexed { index, group ->
                WorkoutSessionGroupEntity(
                    id = ids.newId(),
                    sessionId = sessionId,
                    muscleGroupId = group.id,
                    muscleGroupNameSnapshot = group.name,
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        sessionId
    }

    override suspend fun upsertSet(set: WorkoutSet) = upsertSets(listOf(set))

    /**
     * Cada cambio de serie toca el updatedAt de su sesión (agregado, ADR-004). Las columnas
     * heredadas is_done/completed_at de una fila existente se conservan tal cual (ADR-018).
     */
    override suspend fun upsertSets(sets: List<WorkoutSet>) {
        if (sets.isEmpty()) return
        val now = time.now()
        db.withTransaction {
            sets.forEach { set ->
                val existing = dao.getSet(set.id)
                val createdAt = existing?.createdAt ?: set.createdAt.orNow(now)
                val entity = set.copy(createdAt = createdAt, updatedAt = now).toEntity()
                    .copy(isDone = existing?.isDone ?: false, completedAt = existing?.completedAt)
                dao.saveSet(entity)
            }
            sets.map { it.sessionId }.distinct().forEach { dao.touchSession(it, now) }
        }
    }

    override suspend fun deleteSet(setId: String) {
        db.withTransaction {
            val set = dao.getSet(setId) ?: return@withTransaction
            dao.deleteSet(setId)
            dao.touchSession(set.sessionId, time.now())
        }
    }

    override suspend fun removeExercise(sessionId: String, exerciseId: String) {
        db.withTransaction {
            if (dao.deleteSetsOfExercise(sessionId, exerciseId) > 0) dao.touchSession(sessionId, time.now())
        }
    }

    override suspend fun finish(sessionId: String, finishedAt: Long, keepSetIds: List<String>) {
        db.withTransaction {
            dao.markFinished(sessionId, finishedAt, time.now())
            dao.deleteSetsNotIn(sessionId, keepSetIds)
        }
    }

    override suspend fun discard(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    override suspend fun lastFinishedSetsForExercise(exerciseId: String, excludeSessionId: String?): List<WorkoutSet> =
        dao.lastFinishedSetsForExercise(exerciseId, excludeSessionId).map { it.toDomain() }

    override fun observeFinishedSessions(): Flow<List<SessionSummary>> =
        dao.observeFinishedSummaries().map { rows -> rows.map { it.toDomain() } }

    override suspend fun deleteSession(id: String) {
        db.withTransaction {
            if (dao.deleteSession(id) > 0) {
                db.tombstoneDao().upsert(DeletedEntityEntity(TombstoneType.SESSION, id, time.now()))
            }
        }
    }

    private suspend fun loadSessionDetail(id: String): SessionDetail? = db.withTransaction {
        val session = dao.getSession(id) ?: return@withTransaction null
        // getSets ya viene ordenado por exercise_order y set_index; groupBy conserva ese orden.
        val setsByExercise = dao.getSets(id).map { it.toDomain() }.groupBy { it.exerciseId }
        val library = db.exerciseDao().getByIds(setsByExercise.keys.toList()).associateBy { it.id }
        val exercises = setsByExercise
            .map { (exerciseId, sets) ->
                val first = sets.first()
                SessionExercise(
                    exerciseId = exerciseId,
                    exerciseName = first.exerciseNameSnapshot,
                    muscleGroupName = first.muscleGroupNameSnapshot,
                    muscleGroupId = library[exerciseId]?.muscleGroupId,
                    imagePath = library[exerciseId]?.imagePath,
                    order = sets.minOf { it.exerciseOrder },
                    sets = sets.sortedBy { it.setIndex },
                )
            }
            .sortedBy { it.order }
        SessionDetail(session.toDomain(), exercises, dao.getSessionGroups(id).map { it.toDomain() })
    }
}
