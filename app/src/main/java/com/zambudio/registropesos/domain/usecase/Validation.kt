package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.RoutineDetail

enum class ExerciseNameError { EMPTY, TOO_LONG }

/** Nombre de ejercicio: no vacío tras trim y como mucho [MAX_LENGTH] caracteres. Null = válido. */
object ValidateExerciseName {
    const val MAX_LENGTH = 60

    operator fun invoke(name: String): ExerciseNameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ExerciseNameError.EMPTY
            trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH -> ExerciseNameError.TOO_LONG
            else -> null
        }
    }
}

enum class RoutineError { NAME_EMPTY, NO_GROUPS }

/**
 * Rutina: nombre no vacío y al menos un grupo muscular. Los ejercicios se eligen al entrenar,
 * así que una rutina sin ejercicios es válida (ADR-018). Devuelve todos los errores; vacío = válida.
 */
object ValidateRoutine {
    operator fun invoke(detail: RoutineDetail): Set<RoutineError> = buildSet {
        if (detail.routine.name.isBlank()) add(RoutineError.NAME_EMPTY)
        if (detail.blocks.isEmpty()) add(RoutineError.NO_GROUPS)
    }
}
