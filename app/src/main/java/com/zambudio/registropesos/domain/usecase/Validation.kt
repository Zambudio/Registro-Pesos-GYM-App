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

enum class RoutineError { NAME_EMPTY, NO_EXERCISES, INVALID_DEFAULT_SETS }

/**
 * Rutina: nombre no vacío, al menos un bloque con algún ejercicio y series por defecto en
 * [MIN_DEFAULT_SETS]..[MAX_DEFAULT_SETS]. Devuelve todos los errores; vacío = válida.
 */
object ValidateRoutine {
    const val MIN_DEFAULT_SETS = 1
    const val MAX_DEFAULT_SETS = 20

    operator fun invoke(detail: RoutineDetail): Set<RoutineError> = buildSet {
        if (detail.routine.name.isBlank()) add(RoutineError.NAME_EMPTY)
        if (detail.blocks.none { it.exercises.isNotEmpty() }) add(RoutineError.NO_EXERCISES)
        val sets = detail.blocks.flatMap { block -> block.exercises.map { it.routineExercise.defaultSets } }
        if (sets.any { it !in MIN_DEFAULT_SETS..MAX_DEFAULT_SETS }) add(RoutineError.INVALID_DEFAULT_SETS)
    }
}
