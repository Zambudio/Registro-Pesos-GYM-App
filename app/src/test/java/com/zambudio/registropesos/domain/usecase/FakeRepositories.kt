package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionExercise
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.model.SessionVolumePoint
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import com.zambudio.registropesos.domain.repository.ProgressRepository
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Repositorios en memoria mínimos: solo implementan lo que usan los casos de uso.

private fun unused(): Nothing = error("No usado en estos tests")

class FakeWorkoutRepository : WorkoutRepository {
    val sessions = mutableMapOf<String, WorkoutSession>()
    val sets = mutableListOf<WorkoutSet>()
    val lastFinishedSets = mutableMapOf<String, List<WorkoutSet>>()
    var finishCall: Triple<String, Long, List<String>>? = null
    val discarded = mutableListOf<String>()
    var startedRoutine: RoutineDetail? = null

    /** Simula la carrera: la sesión aparece en curso justo al llamar a start(). */
    var throwOnStart: String? = null

    override fun observeInProgress(): Flow<WorkoutSession?> = flowOf(inProgress())
    override suspend fun getInProgress(): WorkoutSession? = inProgress()
    private fun inProgress() = sessions.values.firstOrNull { it.status == SessionStatus.IN_PROGRESS }

    override fun observeSessionDetail(id: String): Flow<SessionDetail?> {
        val session = sessions[id] ?: return flowOf(null)
        val exercises = sets.filter { it.sessionId == id }
            .groupBy { it.exerciseId }
            .map { (exerciseId, exerciseSets) ->
                val first = exerciseSets.first()
                SessionExercise(
                    exerciseId = exerciseId,
                    exerciseName = first.exerciseNameSnapshot,
                    muscleGroupName = first.muscleGroupNameSnapshot,
                    order = first.exerciseOrder,
                    sets = exerciseSets.sortedBy { it.setIndex },
                )
            }
            .sortedBy { it.order }
        return flowOf(SessionDetail(session, exercises))
    }

    override suspend fun getSession(id: String): WorkoutSession? = sessions[id]

    override suspend fun start(routine: RoutineDetail, startedAt: Long): String {
        throwOnStart?.let { throw SessionAlreadyInProgressException(it) }
        startedRoutine = routine
        val id = "new-session"
        sessions[id] = session(id, startedAt = startedAt, routineName = routine.routine.name)
        return id
    }

    override suspend fun finish(sessionId: String, finishedAt: Long, keepSetIds: List<String>) {
        finishCall = Triple(sessionId, finishedAt, keepSetIds)
        sets.removeAll { it.sessionId == sessionId && it.id !in keepSetIds }
        sessions[sessionId] = sessions.getValue(sessionId)
            .copy(status = SessionStatus.FINISHED, finishedAt = finishedAt)
    }

    override suspend fun discard(sessionId: String) {
        discarded += sessionId
        sessions.remove(sessionId)
    }

    override suspend fun lastFinishedSetsForExercise(exerciseId: String, excludeSessionId: String?): List<WorkoutSet> =
        lastFinishedSets[exerciseId].orEmpty()

    override suspend fun upsertSet(set: WorkoutSet) { unused() }
    override suspend fun upsertSets(sets: List<WorkoutSet>) { unused() }
    override suspend fun deleteSet(setId: String) { unused() }
    override fun observeFinishedSessions(): Flow<List<SessionSummary>> = unused()
    override suspend fun deleteSession(id: String) { unused() }
}

class FakeProgressRepository(private val flat: List<FlatSet>) : ProgressRepository {
    override suspend fun finishedSetsFlat(exerciseId: String?): List<FlatSet> =
        flat.filter { exerciseId == null || it.exerciseId == exerciseId }

    override fun observeFinishedSetsFlat(exerciseId: String?): Flow<List<FlatSet>> = unused()
    override fun observeExercisePoints(exerciseId: String, fromMillis: Long): Flow<List<ExerciseSessionPoint>> =
        unused()
    override fun observeGroupSessionVolumes(groupId: String, fromMillis: Long): Flow<List<SessionVolumePoint>> =
        unused()
}

class FakeRoutineRepository(private val details: Map<String, RoutineDetail>) : RoutineRepository {
    override suspend fun getDetail(id: String): RoutineDetail? = details[id]

    override fun observeSummaries(): Flow<List<RoutineSummary>> = unused()
    override fun observeDetail(id: String): Flow<RoutineDetail?> = unused()
    override suspend fun save(detail: RoutineDetail) { unused() }
    override suspend fun reorder(orderedIds: List<String>) { unused() }
    override suspend fun delete(id: String) { unused() }
}
