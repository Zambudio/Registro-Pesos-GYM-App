package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.repository.WorkoutRepository
import javax.inject.Inject

/** Lo que se hizo en una serie la última vez; se pinta en gris como placeholder. */
data class GhostSet(val setIndex: Int, val weightKg: Double?, val reps: Int?)

/** Series de la última sesión FINISHED con el ejercicio, ordenadas por setIndex; vacía si no hay. */
class PreviousSessionForExercise @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) {
    suspend operator fun invoke(exerciseId: String, excludeSessionId: String?): List<GhostSet> =
        workoutRepository.lastFinishedSetsForExercise(exerciseId, excludeSessionId)
            .filterNot { it.isEmpty }
            .sortedBy { it.setIndex }
            .map { GhostSet(it.setIndex, it.weightKg, it.reps) }
}
