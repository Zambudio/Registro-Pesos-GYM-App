package com.zambudio.registropesos.feature.progress

import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.PersonalRecord
import com.zambudio.registropesos.domain.usecase.ProgressRange
import com.zambudio.registropesos.domain.usecase.WeeklyVolumePoint

enum class ProgressTab { EXERCISE, GROUP }

data class ExerciseOption(
    val id: String,
    val name: String,
    val muscleGroupId: String,
    val isArchived: Boolean,
)

data class RecordCard(
    val exerciseId: String,
    val muscleGroupId: String,
    val exerciseName: String,
    val groupName: String,
    val record: PersonalRecord,
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val tab: ProgressTab = ProgressTab.EXERCISE,
    val range: ProgressRange = DEFAULT_RANGE,
    val groups: List<MuscleGroup> = emptyList(),
    // Pestaña "Por ejercicio"
    val exerciseGroupId: String? = null,
    /** Ejercicios del grupo elegido: activos y archivados con historial. */
    val exercises: List<ExerciseOption> = emptyList(),
    val selectedExercise: ExerciseOption? = null,
    /** Puntos por sesión del ejercicio elegido dentro del rango. */
    val exercisePoints: List<ExerciseSessionPoint> = emptyList(),
    /** Récord de todo el historial (no depende del rango). */
    val selectedRecord: PersonalRecord? = null,
    /** Récords de todos los ejercicios con historial. */
    val records: List<RecordCard> = emptyList(),
    // Pestaña "Por grupo"
    val chartGroupId: String? = null,
    val weekly: List<WeeklyVolumePoint> = emptyList(),
) {
    companion object {
        val DEFAULT_RANGE = ProgressRange.M3
    }
}
