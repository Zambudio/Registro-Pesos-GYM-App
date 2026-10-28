package com.zambudio.registropesos.domain.usecase

import kotlin.math.roundToLong

private const val EPLEY_REPS_DIVISOR = 30.0

/**
 * 1RM estimado con Epley: `peso × (1 + reps/30)`, redondeado al 0,5 kg más cercano.
 * Sin peso útil (null o ≤ 0) o sin reps (null o ≤ 0) devuelve 0; con 1 rep devuelve el propio peso.
 */
object CalculateE1rm {
    operator fun invoke(weightKg: Double?, reps: Int?): Double {
        if (weightKg == null || weightKg <= 0.0) return 0.0
        if (reps == null || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        return roundToHalfKg(weightKg * (1 + reps / EPLEY_REPS_DIVISOR))
    }
}

/** Redondea al múltiplo de 0,5 kg más cercano (los empates van hacia arriba). */
fun roundToHalfKg(kg: Double): Double = (kg * 2).roundToLong() / 2.0
