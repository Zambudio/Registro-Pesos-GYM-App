package com.zambudio.registropesos.domain.backup

import kotlinx.serialization.Serializable

// Formato de backup.json. Espejo plano de la BD. Cambios incompatibles => subir SCHEMA_VERSION
// y mantener la lectura de versiones anteriores.

const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val dbVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val muscleGroups: List<MuscleGroupDto> = emptyList(),
    val exercises: List<ExerciseDto> = emptyList(),
    val routines: List<RoutineDto> = emptyList(),
    val routineBlocks: List<RoutineBlockDto> = emptyList(),
    val routineExercises: List<RoutineExerciseDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val sets: List<SetDto> = emptyList(),
    val tombstones: List<TombstoneDto> = emptyList(),
)

@Serializable
data class MuscleGroupDto(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ExerciseDto(
    val id: String,
    val muscleGroupId: String,
    val name: String,
    val notes: String? = null,
    /** Ruta dentro del ZIP, p. ej. "images/exercise_<id>.jpg". */
    val imageFile: String? = null,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RoutineDto(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RoutineBlockDto(
    val id: String,
    val routineId: String,
    val muscleGroupId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RoutineExerciseDto(
    val id: String,
    val blockId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val defaultSets: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SessionDto(
    val id: String,
    val routineId: String? = null,
    val routineNameSnapshot: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class SetDto(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val muscleGroupNameSnapshot: String,
    val exerciseOrder: Int,
    val setIndex: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isDone: Boolean,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TombstoneDto(
    /** ROUTINE | SESSION | MUSCLE_GROUP */
    val entityType: String,
    val id: String,
    val deletedAt: Long,
)

data class TableReport(val inserted: Int = 0, val updated: Int = 0, val skipped: Int = 0)

data class ImportReport(
    val muscleGroups: TableReport = TableReport(),
    val exercises: TableReport = TableReport(),
    val routines: TableReport = TableReport(),
    val sessions: TableReport = TableReport(),
    val deleted: Int = 0,
    val warnings: List<String> = emptyList(),
    /** Ejercicios insertados/actualizados cuya imagen hay que copiar del ZIP: exerciseId -> imageFile. */
    val imagesToCopy: Map<String, String> = emptyMap(),
)
