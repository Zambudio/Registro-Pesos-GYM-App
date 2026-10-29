package com.zambudio.registropesos.feature.workout.session

import com.zambudio.registropesos.domain.usecase.GhostSet
import java.io.File

sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object Error : WorkoutUiState

    /** La sesión ya no está en curso (terminada o descartada) o no existe. */
    data object NotInProgress : WorkoutUiState

    data class Content(
        val sessionId: String,
        val routineName: String,
        val startedAt: Long,
        /** Comentario de la rutina copiado al empezar; null si no tenía. */
        val reminder: String?,
        val blocks: List<WorkoutBlockUi>,
        val expandedExerciseId: String?,
        val weightStepKg: Double,
        /** Alguna serie tiene peso o reps: si no, terminar no tiene sentido y se ofrece descartar. */
        val hasAnyData: Boolean,
        /** Terminando o descartando: se bloquean las ediciones. */
        val isClosing: Boolean,
        /** Selector de ejercicios abierto para un grupo; null si está cerrado. */
        val picker: ExercisePickerUi? = null,
    ) : WorkoutUiState
}

/**
 * Un grupo muscular de la sesión con los ejercicios elegidos (puede no tener ninguno todavía).
 * [muscleGroupId] null = no se pueden añadir ejercicios (el grupo ya no existe o son ejercicios
 * que no encajan en ningún grupo previsto).
 */
data class WorkoutBlockUi(
    val key: String,
    val muscleGroupName: String,
    val muscleGroupId: String?,
    val exercises: List<WorkoutExerciseUi>,
) {
    val canAddExercise: Boolean get() = muscleGroupId != null
}

data class WorkoutExerciseUi(
    val exerciseId: String,
    val name: String,
    val sets: List<WorkoutSetUi>,
    /** Series de la sesión anterior; null mientras no se ha consultado (se pide al abrir el ejercicio). */
    val previousSets: List<GhostSet>?,
    val imageFile: File? = null,
) {
    val hasPrevious: Boolean? get() = previousSets?.isNotEmpty()
    val canRemoveSet: Boolean get() = sets.size > 1

    /** Hay alguna serie con peso o reps: quitar el ejercicio pide confirmación. */
    val hasData: Boolean get() = sets.any { it.hasData }

    /** Alguna fila vacía muestra la referencia de la última vez (se explica con una leyenda). */
    val showsReference: Boolean
        get() = sets.any { it.showsReference }
}

/** Una fila de serie. [ghostWeightKg]/[ghostReps]: lo hecho en la misma serie la última vez (referencia). */
data class WorkoutSetUi(
    val setId: String,
    val number: Int,
    val weightKg: Double?,
    val reps: Int?,
    val ghostWeightKg: Double?,
    val ghostReps: Int?,
) {
    val hasData: Boolean get() = (weightKg != null && weightKg != 0.0) || (reps != null && reps != 0)

    /** Algún campo vacío enseña la referencia de la última vez. */
    val showsReference: Boolean get() = showsWeightReference || showsRepsReference
    private val showsWeightReference: Boolean get() = weightKg == null && ghostWeightKg != null
    private val showsRepsReference: Boolean get() = reps == null && ghostReps != null
}

/** Ejercicios disponibles de un grupo para añadir a la sesión. */
data class ExercisePickerUi(
    val muscleGroupId: String,
    val muscleGroupName: String,
    val isLoading: Boolean,
    val exercises: List<PickerExerciseUi> = emptyList(),
)

data class PickerExerciseUi(
    val exerciseId: String,
    val name: String,
    val imageFile: File?,
    /** Ya está en la sesión: elegirlo solo lo abre, no lo duplica. */
    val alreadyAdded: Boolean,
)

sealed interface WorkoutEvent {
    data class OpenSummary(val sessionId: String) : WorkoutEvent
    data object Discarded : WorkoutEvent
    data object SaveFailed : WorkoutEvent

    /** Se quitó una serie con datos: la pantalla ofrece "Deshacer" ([WorkoutViewModel.onUndoRemoveSet]). */
    data class SetRemoved(val setId: String) : WorkoutEvent

    /** Se quitó un ejercicio: la pantalla ofrece "Deshacer" ([WorkoutViewModel.onUndoRemoveExercise]). */
    data class ExerciseRemoved(val exerciseId: String) : WorkoutEvent
    data object FinishFailed : WorkoutEvent
    data object DiscardFailed : WorkoutEvent
}
