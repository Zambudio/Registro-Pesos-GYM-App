package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet

// Constructores compactos para los tests del dominio.

fun workoutSet(
    id: String,
    exerciseId: String = "ex1",
    weightKg: Double? = null,
    reps: Int? = null,
    setIndex: Int = 0,
    exerciseOrder: Int = 0,
    sessionId: String = "s1",
    exerciseName: String = "Press banca",
    groupName: String = "Pecho",
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
    isDone = weightKg != null,
    completedAt = null,
    createdAt = 0L,
    updatedAt = 0L,
)

fun flatSet(exerciseId: String, weightKg: Double, reps: Int, startedAt: Long, sessionId: String = "old$startedAt") =
    FlatSet(exerciseId, sessionId, weightKg, reps, startedAt)

fun session(
    id: String,
    status: SessionStatus = SessionStatus.IN_PROGRESS,
    startedAt: Long = 0L,
    routineName: String = "Día 1",
) = WorkoutSession(
    id = id,
    routineId = "r1",
    routineNameSnapshot = routineName,
    startedAt = startedAt,
    finishedAt = null,
    status = status,
    notes = null,
    createdAt = startedAt,
    updatedAt = startedAt,
)
