package com.zambudio.registropesos.data.mapper

import com.zambudio.registropesos.data.local.dao.ExerciseSessionPointRow
import com.zambudio.registropesos.data.local.dao.FlatSetRow
import com.zambudio.registropesos.data.local.dao.RoutineSummaryRow
import com.zambudio.registropesos.data.local.dao.SessionSummaryRow
import com.zambudio.registropesos.data.local.dao.SessionVolumeRow
import com.zambudio.registropesos.data.local.entity.ExerciseEntity
import com.zambudio.registropesos.data.local.entity.MuscleGroupEntity
import com.zambudio.registropesos.data.local.entity.RoutineBlockEntity
import com.zambudio.registropesos.data.local.entity.RoutineEntity
import com.zambudio.registropesos.data.local.entity.RoutineExerciseEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSessionEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSetEntity
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.model.SessionVolumePoint
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet

// --- Entidad <-> dominio ---

fun MuscleGroupEntity.toDomain() = MuscleGroup(id, name, sortOrder, isDefault, createdAt, updatedAt)
fun MuscleGroup.toEntity() = MuscleGroupEntity(id, name, sortOrder, isDefault, createdAt, updatedAt)

fun ExerciseEntity.toDomain() =
    Exercise(id, muscleGroupId, name, notes, imagePath, isArchived, createdAt, updatedAt)

fun Exercise.toEntity() =
    ExerciseEntity(id, muscleGroupId, name, notes, imagePath, isArchived, createdAt, updatedAt)

fun RoutineEntity.toDomain() = Routine(id, name, subtitle, sortOrder, createdAt, updatedAt)
fun Routine.toEntity() = RoutineEntity(id, name, subtitle, sortOrder, createdAt, updatedAt)

fun RoutineBlockEntity.toDomain() = RoutineBlock(id, routineId, muscleGroupId, sortOrder, createdAt, updatedAt)
fun RoutineBlock.toEntity() = RoutineBlockEntity(id, routineId, muscleGroupId, sortOrder, createdAt, updatedAt)

fun RoutineExerciseEntity.toDomain() =
    RoutineExercise(id, blockId, exerciseId, sortOrder, defaultSets, createdAt, updatedAt)

fun RoutineExercise.toEntity() =
    RoutineExerciseEntity(id, blockId, exerciseId, sortOrder, defaultSets, createdAt, updatedAt)

fun WorkoutSessionEntity.toDomain() =
    WorkoutSession(id, routineId, routineNameSnapshot, startedAt, finishedAt, status, notes, createdAt, updatedAt)

fun WorkoutSession.toEntity() =
    WorkoutSessionEntity(id, routineId, routineNameSnapshot, startedAt, finishedAt, status, notes, createdAt, updatedAt)

fun WorkoutSetEntity.toDomain() = WorkoutSet(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseNameSnapshot = exerciseNameSnapshot,
    muscleGroupNameSnapshot = muscleGroupNameSnapshot,
    exerciseOrder = exerciseOrder,
    setIndex = setIndex,
    weightKg = weightKg,
    reps = reps,
    isDone = isDone,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WorkoutSet.toEntity() = WorkoutSetEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseNameSnapshot = exerciseNameSnapshot,
    muscleGroupNameSnapshot = muscleGroupNameSnapshot,
    exerciseOrder = exerciseOrder,
    setIndex = setIndex,
    weightKg = weightKg,
    reps = reps,
    isDone = isDone,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// --- Filas de consulta -> dominio ---

fun RoutineSummaryRow.toDomain() = RoutineSummary(id, name, subtitle, blockCount, exerciseCount)

fun SessionSummaryRow.toDomain() =
    SessionSummary(id, routineName, startedAt, finishedAt, exerciseCount, volumeKg)

fun ExerciseSessionPointRow.toDomain() =
    ExerciseSessionPoint(sessionId, startedAt, maxWeightKg, volumeKg, bestE1rmKg)

fun SessionVolumeRow.toDomain() = SessionVolumePoint(sessionId, startedAt, volumeKg)

fun FlatSetRow.toDomain() = FlatSet(exerciseId, sessionId, weightKg, reps, startedAt)
