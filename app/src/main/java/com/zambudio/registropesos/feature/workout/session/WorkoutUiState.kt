package com.zambudio.registropesos.feature.workout.session

import com.zambudio.registropesos.domain.usecase.GhostSet

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object Error : WorkoutUiState

    /** La sesión ya no está en curso (terminada o descartada) o no existe. */
    data object NotInProgress : WorkoutUiState

    data class Content(
        val sessionId: String,
        val routineName: String,
        val startedAt: Long,
        val blocks: List<WorkoutBlockUi>,
        val expandedExerciseId: String?,
        val weightStepKg: Double,
        /** Alguna serie tiene peso o reps: si no, terminar no tiene sentido y se ofrece descartar. */
        val hasAnyData: Boolean,
        /** Terminando o descartando: se bloquean las ediciones. */
        val isClosing: Boolean,
    ) : WorkoutUiState
}

/** Ejercicios consecutivos del mismo grupo muscular (los bloques de la rutina). */
data class WorkoutBlockUi(
    val muscleGroupName: String,
    val exercises: List<WorkoutExerciseUi>,
)

data class WorkoutExerciseUi(
    val exerciseId: String,
    val name: String,
    val sets: List<WorkoutSetUi>,
    /** Series de la sesión anterior; null mientras no se ha consultado (se pide al abrir el ejercicio). */
    val previousSets: List<GhostSet>?,
) {
    val hasPrevious: Boolean? get() = previousSets?.isNotEmpty()
    val doneCount: Int get() = sets.count { it.isDone }
    val canRemoveSet: Boolean get() = sets.size > 1
}

/** Una fila de serie. [ghostWeightKg]/[ghostReps]: lo hecho en la misma serie la última vez. */
data class WorkoutSetUi(
    val setId: String,
    val number: Int,
    val weightKg: Double?,
    val reps: Int?,
    val isDone: Boolean,
    val ghostWeightKg: Double?,
    val ghostReps: Int?,
)

sealed interface WorkoutEvent {
    data class OpenSummary(val sessionId: String) : WorkoutEvent
    data object Discarded : WorkoutEvent
    data object SaveFailed : WorkoutEvent
    data object FinishFailed : WorkoutEvent
    data object DiscardFailed : WorkoutEvent
}
