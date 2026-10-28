package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.repository.ProgressRepository
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// Casos de uso del ciclo de vida de una sesión: empezar, terminar y descartar.
// Los fallos esperables vuelven como Result.failure; los errores de los repositorios se propagan.

class RoutineNotFoundException(val routineId: String) : NoSuchElementException("Rutina no encontrada: $routineId")

class SessionNotFoundException(val sessionId: String) : NoSuchElementException("Sesión no encontrada: $sessionId")

class SessionNotInProgressException(val sessionId: String, val status: SessionStatus) :
    IllegalStateException("La sesión $sessionId no está en curso ($status)")

/** Resumen que se muestra al terminar el entrenamiento. */
data class WorkoutSummaryResult(
    val durationMs: Long,
    val exerciseCount: Int,
    val setCount: Int,
    val volumeKg: Double,
    val newRecords: List<NewRecord>,
)

/** Crea la sesión IN_PROGRESS de la rutina y devuelve su id. */
class StartSession @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(routineId: String): Result<String> {
        workoutRepository.getInProgress()?.let { return Result.failure(SessionAlreadyInProgressException(it.id)) }
        val routine = routineRepository.getDetail(routineId)
            ?: return Result.failure(RoutineNotFoundException(routineId))
        return try {
            Result.success(workoutRepository.start(routine, timeProvider.now()))
        } catch (e: SessionAlreadyInProgressException) {
            Result.failure(e)
        }
    }
}

/**
 * Termina la sesión: conserva solo las series no vacías, la marca FINISHED con la hora actual y
 * calcula el resumen, incluidos los récords batidos frente al historial anterior.
 */
class FinishSession @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val progressRepository: ProgressRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(sessionId: String): Result<WorkoutSummaryResult> {
        val detail = workoutRepository.observeSessionDetail(sessionId).first()
            ?: return Result.failure(SessionNotFoundException(sessionId))
        val session = detail.session
        if (session.status != SessionStatus.IN_PROGRESS) {
            return Result.failure(SessionNotInProgressException(sessionId, session.status))
        }
        val kept = detail.exercises.flatMap { it.sets }.filterNot { it.isEmpty }
        val exerciseIds = kept.mapTo(HashSet()) { it.exerciseId }
        val previous = progressRepository.finishedSetsFlat(null)
            .filter { it.sessionId != sessionId && it.exerciseId in exerciseIds }

        val finishedAt = timeProvider.now()
        workoutRepository.finish(sessionId, finishedAt, kept.map { it.id })

        return Result.success(
            WorkoutSummaryResult(
                durationMs = (finishedAt - session.startedAt).coerceAtLeast(0L),
                exerciseCount = exerciseIds.size,
                setCount = kept.size,
                volumeKg = SessionVolume(kept),
                newRecords = DetectNewRecords(PersonalRecords(previous), kept),
            ),
        )
    }
}

/** Descarta (borra) la sesión en curso. Nunca toca sesiones terminadas. */
class DiscardSession @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> {
        val session = workoutRepository.getSession(sessionId)
            ?: return Result.failure(SessionNotFoundException(sessionId))
        if (session.status != SessionStatus.IN_PROGRESS) {
            return Result.failure(SessionNotInProgressException(sessionId, session.status))
        }
        workoutRepository.discard(sessionId)
        return Result.success(Unit)
    }
}
