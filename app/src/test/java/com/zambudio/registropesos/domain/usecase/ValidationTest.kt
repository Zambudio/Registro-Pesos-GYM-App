package com.zambudio.registropesos.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateExerciseNameTest {
    @Test
    fun blankNames_areEmpty() {
        assertEquals(ExerciseNameError.EMPTY, ValidateExerciseName(""))
        assertEquals(ExerciseNameError.EMPTY, ValidateExerciseName("   \t "))
    }

    @Test
    fun lengthLimit_isAppliedAfterTrim() {
        assertNull(ValidateExerciseName("a".repeat(60)))
        assertNull(ValidateExerciseName("  " + "a".repeat(60) + "  "))
        assertEquals(ExerciseNameError.TOO_LONG, ValidateExerciseName("a".repeat(61)))
    }

    @Test
    fun normalName_isValid() {
        assertNull(ValidateExerciseName("Press banca inclinado"))
    }
}

class ValidateRoutineTest {
    @Test
    fun validRoutine_hasNoErrors() {
        assertTrue(ValidateRoutine(routineDetail(defaultSets = listOf(1, 4, 20))).isEmpty())
    }

    @Test
    fun blankName_isReported() {
        assertEquals(setOf(RoutineError.NAME_EMPTY), ValidateRoutine(routineDetail(name = "  ")))
    }

    @Test
    fun noBlocks_orOnlyEmptyBlocks_isReported() {
        assertEquals(setOf(RoutineError.NO_EXERCISES), ValidateRoutine(routineDetail(blockCount = 0)))
        assertEquals(setOf(RoutineError.NO_EXERCISES), ValidateRoutine(routineDetail(defaultSets = emptyList())))
    }

    @Test
    fun defaultSetsOutOfRange_isReported() {
        assertEquals(setOf(RoutineError.INVALID_DEFAULT_SETS), ValidateRoutine(routineDetail(defaultSets = listOf(0))))
        assertEquals(setOf(RoutineError.INVALID_DEFAULT_SETS), ValidateRoutine(routineDetail(defaultSets = listOf(21))))
    }

    @Test
    fun allErrors_areReportedTogether() {
        assertEquals(
            setOf(RoutineError.NAME_EMPTY, RoutineError.INVALID_DEFAULT_SETS),
            ValidateRoutine(routineDetail(name = "", defaultSets = listOf(4, 25))),
        )
    }
}
