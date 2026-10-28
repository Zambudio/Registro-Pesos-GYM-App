package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.WorkoutSet

/** Volumen (Σ peso×reps) de las series que cuentan: peso y reps no nulos y reps > 0. */
object SessionVolume {
    operator fun invoke(sets: List<WorkoutSet>): Double = sets.sumOf(::volumeOf)

    fun of(detail: SessionDetail): Double = invoke(detail.exercises.flatMap { it.sets })

    /** exerciseId → volumen, en el orden de aparición; solo ejercicios con alguna serie que cuente. */
    fun byExercise(sets: List<WorkoutSet>): Map<String, Double> =
        sets.filter(::counts)
            .groupBy { it.exerciseId }
            .mapValues { (_, exerciseSets) -> exerciseSets.sumOf(::volumeOf) }

    fun counts(set: WorkoutSet): Boolean = set.weightKg != null && set.reps != null && set.reps > 0

    private fun volumeOf(set: WorkoutSet): Double =
        if (counts(set)) (set.weightKg ?: 0.0) * (set.reps ?: 0) else 0.0
}
