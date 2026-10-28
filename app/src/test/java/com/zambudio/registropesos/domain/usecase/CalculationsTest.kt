package com.zambudio.registropesos.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateE1rmTest {
    @Test
    fun zeroOrNegativeReps_returnsZero() {
        assertEquals(0.0, CalculateE1rm(100.0, 0), 0.0)
        assertEquals(0.0, CalculateE1rm(100.0, -3), 0.0)
        assertEquals(0.0, CalculateE1rm(100.0, null), 0.0)
    }

    @Test
    fun nullWeight_returnsZero() {
        assertEquals(0.0, CalculateE1rm(null, 10), 0.0)
    }

    @Test
    fun oneRep_returnsWeightItself() {
        assertEquals(100.0, CalculateE1rm(100.0, 1), 0.0)
        assertEquals(82.5, CalculateE1rm(82.5, 1), 0.0)
    }

    @Test
    fun tenReps_appliesEpleyRoundedToHalfKg() {
        // 100 × (1 + 10/30) = 133.33 → 133.5
        assertEquals(133.5, CalculateE1rm(100.0, 10), 0.0)
        // 80 × (1 + 5/30) = 93.33 → 93.5
        assertEquals(93.5, CalculateE1rm(80.0, 5), 0.0)
        // 60 × (1 + 3/30) = 66.0
        assertEquals(66.0, CalculateE1rm(60.0, 3), 0.0)
    }

    @Test
    fun roundToHalfKg_roundsToNearestHalf() {
        assertEquals(101.0, roundToHalfKg(101.24), 0.0)
        assertEquals(101.5, roundToHalfKg(101.25), 0.0)
        assertEquals(101.5, roundToHalfKg(101.74), 0.0)
        assertEquals(102.0, roundToHalfKg(101.76), 0.0)
        assertEquals(0.0, roundToHalfKg(0.0), 0.0)
    }
}

class SessionVolumeTest {
    private val sets = listOf(
        workoutSet("a", exerciseId = "ex1", weightKg = 100.0, reps = 10),
        workoutSet("b", exerciseId = "ex1", weightKg = 82.5, reps = 8),
        workoutSet("c", exerciseId = "ex1"),
        workoutSet("d", exerciseId = "ex2", weightKg = 50.0, reps = null),
        workoutSet("e", exerciseId = "ex2", weightKg = null, reps = 12),
        workoutSet("f", exerciseId = "ex2", weightKg = 40.0, reps = 0),
        workoutSet("g", exerciseId = "ex3", weightKg = 20.0, reps = 15),
    )

    @Test
    fun totalVolume_ignoresEmptyAndIncompleteSets() {
        assertEquals(1000.0 + 660.0 + 300.0, SessionVolume(sets), 1e-9)
    }

    @Test
    fun emptyList_isZero() {
        assertEquals(0.0, SessionVolume(emptyList()), 0.0)
    }

    @Test
    fun byExercise_onlyExercisesWithCountedSets() {
        assertEquals(mapOf("ex1" to 1660.0, "ex3" to 300.0), SessionVolume.byExercise(sets))
    }

    @Test
    fun counts_requiresWeightAndPositiveReps() {
        assertTrue(SessionVolume.counts(workoutSet("x", weightKg = 0.0, reps = 10)))
        assertFalse(SessionVolume.counts(workoutSet("x", weightKg = 10.0, reps = 0)))
        assertFalse(SessionVolume.counts(workoutSet("x", weightKg = null, reps = 10)))
    }
}
