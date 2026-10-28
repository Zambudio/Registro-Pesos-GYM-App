package com.zambudio.registropesos.domain.model

// Modelos de dominio puros (sin Android). Todos los ids son UUID en texto y las
// marcas de tiempo epoch millis, preparados para un sync futuro.

data class MuscleGroup(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Exercise(
    val id: String,
    val muscleGroupId: String,
    val name: String,
    val notes: String?,
    /** Nombre de fichero relativo a filesDir/images (p. ej. "exercise_<uuid>.jpg"), nunca ruta absoluta. */
    val imagePath: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Routine(
    val id: String,
    val name: String,
    val subtitle: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RoutineBlock(
    val id: String,
    val routineId: String,
    val muscleGroupId: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RoutineExercise(
    val id: String,
    val blockId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val defaultSets: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class SessionStatus { IN_PROGRESS, FINISHED, DISCARDED }

data class WorkoutSession(
    val id: String,
    val routineId: String?,
    val routineNameSnapshot: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val status: SessionStatus,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Una serie. [weightKg] y [reps] son null mientras la fila está vacía; una serie
 * "vacía" (sin peso ni reps útiles) se descarta al terminar la sesión.
 * [exerciseOrder] conserva el orden del ejercicio dentro de la sesión.
 */
data class WorkoutSet(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val muscleGroupNameSnapshot: String,
    val exerciseOrder: Int,
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int?,
    val isDone: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isEmpty: Boolean get() = (weightKg == null || weightKg == 0.0) && (reps == null || reps == 0)
}

// --- Agregados de lectura ---

data class RoutineSummary(
    val id: String,
    val name: String,
    val subtitle: String?,
    val blockCount: Int,
    val exerciseCount: Int,
)

data class RoutineDetail(
    val routine: Routine,
    val blocks: List<RoutineBlockDetail>,
)

data class RoutineBlockDetail(
    val block: RoutineBlock,
    val muscleGroup: MuscleGroup,
    val exercises: List<RoutineExerciseDetail>,
)

data class RoutineExerciseDetail(
    val routineExercise: RoutineExercise,
    val exercise: Exercise,
)

data class SessionDetail(
    val session: WorkoutSession,
    /** Ejercicios en orden de sesión, cada uno con sus series ordenadas por setIndex. */
    val exercises: List<SessionExercise>,
)

data class SessionExercise(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroupName: String,
    val order: Int,
    val sets: List<WorkoutSet>,
)

data class SessionSummary(
    val id: String,
    val routineName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val exerciseCount: Int,
    val volumeKg: Double,
)

// --- Progreso ---

data class ExerciseSessionPoint(
    val sessionId: String,
    val startedAt: Long,
    val maxWeightKg: Double,
    val volumeKg: Double,
    val bestE1rmKg: Double,
)

data class SessionVolumePoint(
    val sessionId: String,
    val startedAt: Long,
    val volumeKg: Double,
)

/** Serie terminada aplanada con su fecha, materia prima para récords. */
data class FlatSet(
    val exerciseId: String,
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    val startedAt: Long,
)

data class PersonalRecord(
    val exerciseId: String,
    val bestWeightKg: Double,
    val bestWeightAt: Long,
    val bestE1rmKg: Double,
    val bestE1rmAt: Long,
)

data class AppSettings(
    val weightStepKg: Double = 2.5,
    val defaultSets: Int = 4,
)
