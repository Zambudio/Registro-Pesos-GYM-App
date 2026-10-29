package com.zambudio.registropesos.data.mapper

import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.ExerciseEntity
import com.zambudio.registropesos.data.local.entity.MuscleGroupEntity
import com.zambudio.registropesos.data.local.entity.RoutineBlockEntity
import com.zambudio.registropesos.data.local.entity.RoutineEntity
import com.zambudio.registropesos.data.local.entity.RoutineExerciseEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSessionEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSessionGroupEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSetEntity
import com.zambudio.registropesos.domain.backup.ExerciseDto
import com.zambudio.registropesos.domain.backup.MuscleGroupDto
import com.zambudio.registropesos.domain.backup.RoutineBlockDto
import com.zambudio.registropesos.domain.backup.RoutineDto
import com.zambudio.registropesos.domain.backup.RoutineExerciseDto
import com.zambudio.registropesos.domain.backup.SessionDto
import com.zambudio.registropesos.domain.backup.SessionGroupDto
import com.zambudio.registropesos.domain.backup.SetDto
import com.zambudio.registropesos.domain.backup.TombstoneDto
import com.zambudio.registropesos.domain.model.SessionStatus

/** Prefijo de las imágenes dentro del ZIP de backup. */
const val BACKUP_IMAGES_DIR = "images/"

// --- Entidad <-> DTO de backup ---

fun MuscleGroupEntity.toDto() = MuscleGroupDto(id, name, sortOrder, isDefault, createdAt, updatedAt)
fun MuscleGroupDto.toEntity() = MuscleGroupEntity(id, name, sortOrder, isDefault, createdAt, updatedAt)

fun ExerciseEntity.toDto() = ExerciseDto(
    id = id,
    muscleGroupId = muscleGroupId,
    name = name,
    notes = notes,
    imageFile = imagePath?.let { BACKUP_IMAGES_DIR + it },
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** [imageFile] "images/x.jpg" -> imagePath "x.jpg" (solo el nombre: nunca rutas del ZIP). */
fun ExerciseDto.toEntity(muscleGroupId: String) = ExerciseEntity(
    id = id,
    muscleGroupId = muscleGroupId,
    name = name,
    notes = notes,
    imagePath = imageFile?.substringAfterLast('/')?.takeIf { it.isNotBlank() },
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RoutineEntity.toDto() = RoutineDto(id, name, subtitle, sortOrder, createdAt, updatedAt)
fun RoutineDto.toEntity() = RoutineEntity(id, name, subtitle, sortOrder, createdAt, updatedAt)

fun RoutineBlockEntity.toDto() = RoutineBlockDto(id, routineId, muscleGroupId, sortOrder, createdAt, updatedAt)

fun RoutineExerciseEntity.toDto() =
    RoutineExerciseDto(id, blockId, exerciseId, sortOrder, defaultSets, createdAt, updatedAt)

fun RoutineExerciseDto.toEntity() =
    RoutineExerciseEntity(id, blockId, exerciseId, sortOrder, defaultSets, createdAt, updatedAt)

fun WorkoutSessionEntity.toDto() = SessionDto(
    id = id,
    routineId = routineId,
    routineNameSnapshot = routineNameSnapshot,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status.name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    routineCommentSnapshot = routineCommentSnapshot,
)

fun SessionDto.toEntity(status: SessionStatus, routineId: String?) = WorkoutSessionEntity(
    id = id,
    routineId = routineId,
    routineNameSnapshot = routineNameSnapshot,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    routineCommentSnapshot = routineCommentSnapshot,
)

fun WorkoutSessionGroupEntity.toDto() =
    SessionGroupDto(id, sessionId, muscleGroupId, muscleGroupNameSnapshot, sortOrder, createdAt, updatedAt)

/** [muscleGroupId] ya remapeado al id local (o null si el grupo no existe aquí). */
fun SessionGroupDto.toEntity(muscleGroupId: String?) =
    WorkoutSessionGroupEntity(id, sessionId, muscleGroupId, muscleGroupNameSnapshot, sortOrder, createdAt, updatedAt)

fun WorkoutSetEntity.toDto() = SetDto(
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

fun SetDto.toEntity() = WorkoutSetEntity(
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

fun DeletedEntityEntity.toDto() = TombstoneDto(entityType.name, id, deletedAt)
