package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.PersonalRecord
import com.zambudio.registropesos.domain.model.WorkoutSet

/**
 * Récords personales por ejercicio a partir de series terminadas. Ante empates gana la
 * primera vez que se alcanzó el valor (menor `startedAt`).
 */
object PersonalRecords {
    operator fun invoke(sets: List<FlatSet>): Map<String, PersonalRecord> =
        sets.asSequence()
            .filter { it.reps > 0 }
            .sortedBy { it.startedAt }
            .groupBy { it.exerciseId }
            .mapValues { (exerciseId, exerciseSets) -> recordOf(exerciseId, exerciseSets) }

    private fun recordOf(exerciseId: String, chronological: List<FlatSet>): PersonalRecord {
        var record: PersonalRecord? = null
        for (set in chronological) {
            val e1rm = CalculateE1rm(set.weightKg, set.reps)
            val current = record
            record = if (current == null) {
                PersonalRecord(exerciseId, set.weightKg, set.startedAt, e1rm, set.startedAt)
            } else {
                current.copy(
                    bestWeightKg = maxOf(current.bestWeightKg, set.weightKg),
                    bestWeightAt = if (set.weightKg > current.bestWeightKg) set.startedAt else current.bestWeightAt,
                    bestE1rmKg = maxOf(current.bestE1rmKg, e1rm),
                    bestE1rmAt = if (e1rm > current.bestE1rmKg) set.startedAt else current.bestE1rmAt,
                )
            }
        }
        return requireNotNull(record) { "Sin series para $exerciseId" }
    }
}

enum class RecordType { WEIGHT, E1RM }

data class NewRecord(
    val exerciseId: String,
    val exerciseName: String,
    val type: RecordType,
    val previousValueKg: Double,
    val newValueKg: Double,
)

/**
 * Récords batidos en una sesión. [previous] debe excluir la sesión actual. El primer registro
 * de un ejercicio (sin récord previo) no cuenta. Orden: el de aparición en la sesión, peso antes que e1RM.
 */
object DetectNewRecords {
    operator fun invoke(previous: Map<String, PersonalRecord>, sessionSets: List<WorkoutSet>): List<NewRecord> {
        val result = mutableListOf<NewRecord>()
        sessionSets.filter(SessionVolume::counts)
            .sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex }))
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, sets) ->
                val before = previous[exerciseId] ?: return@forEach
                val name = sets.first().exerciseNameSnapshot
                val bestWeight = sets.maxOf { it.weightKg ?: 0.0 }
                val bestE1rm = sets.maxOf { CalculateE1rm(it.weightKg, it.reps) }
                if (bestWeight > before.bestWeightKg) {
                    result += NewRecord(exerciseId, name, RecordType.WEIGHT, before.bestWeightKg, bestWeight)
                }
                if (bestE1rm > before.bestE1rmKg) {
                    result += NewRecord(exerciseId, name, RecordType.E1RM, before.bestE1rmKg, bestE1rm)
                }
            }
        return result
    }
}
