package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.PersonalRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordsTest {
    @Test
    fun emptyInput_noRecords() {
        assertTrue(PersonalRecords(emptyList()).isEmpty())
    }

    @Test
    fun bestWeightAndE1rm_withTheirDates() {
        val records = PersonalRecords(
            listOf(
                flatSet("ex1", 100.0, 5, startedAt = 1_000),
                flatSet("ex1", 90.0, 12, startedAt = 2_000),
                flatSet("ex1", 105.0, 1, startedAt = 3_000),
                flatSet("ex2", 20.0, 10, startedAt = 1_500),
            ),
        )
        // e1RM: 100×5 → 116.5; 90×12 → 126.0; 105×1 → 105.0
        assertEquals(PersonalRecord("ex1", 105.0, 3_000, 126.0, 2_000), records["ex1"])
        assertEquals(PersonalRecord("ex2", 20.0, 1_500, 26.5, 1_500), records["ex2"])
    }

    @Test
    fun ties_keepFirstDateReached() {
        val records = PersonalRecords(
            // Desordenadas a propósito: la fecha válida es la más antigua.
            listOf(
                flatSet("ex1", 100.0, 10, startedAt = 5_000),
                flatSet("ex1", 100.0, 10, startedAt = 1_000),
                flatSet("ex1", 100.0, 10, startedAt = 3_000),
            ),
        )
        val record = records.getValue("ex1")
        assertEquals(1_000L, record.bestWeightAt)
        assertEquals(1_000L, record.bestE1rmAt)
    }

    @Test
    fun zeroRepSets_areIgnored() {
        val records = PersonalRecords(
            listOf(flatSet("ex1", 200.0, 0, startedAt = 1_000), flatSet("ex1", 50.0, 5, startedAt = 2_000)),
        )
        assertEquals(50.0, records.getValue("ex1").bestWeightKg, 0.0)
    }
}

class DetectNewRecordsTest {
    private val previous = mapOf("ex1" to PersonalRecord("ex1", 100.0, 1_000, 120.0, 1_000))

    @Test
    fun firstTimeExercise_isNotARecord() {
        val result = DetectNewRecords(emptyMap(), listOf(workoutSet("a", weightKg = 100.0, reps = 5)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun heavierWeight_andBetterE1rm_areBothReported() {
        val result = DetectNewRecords(
            previous,
            listOf(
                workoutSet("a", weightKg = 102.5, reps = 1, setIndex = 0),
                workoutSet("b", weightKg = 95.0, reps = 10, setIndex = 1),
            ),
        )
        assertEquals(
            listOf(
                NewRecord("ex1", "Press banca", RecordType.WEIGHT, 100.0, 102.5),
                // 95 × (1 + 10/30) = 126.67 → 126.5
                NewRecord("ex1", "Press banca", RecordType.E1RM, 120.0, 126.5),
            ),
            result,
        )
    }

    @Test
    fun equalValues_areNotRecords() {
        val result = DetectNewRecords(previous, listOf(workoutSet("a", weightKg = 100.0, reps = 1)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun emptySets_areIgnored() {
        val result = DetectNewRecords(
            previous,
            listOf(workoutSet("a"), workoutSet("b", weightKg = 300.0, reps = 0)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun onlyE1rmRecord_whenWeightIsNotBeaten() {
        val result = DetectNewRecords(previous, listOf(workoutSet("a", weightKg = 90.0, reps = 15)))
        // 90 × 1.5 = 135
        assertEquals(listOf(NewRecord("ex1", "Press banca", RecordType.E1RM, 120.0, 135.0)), result)
    }
}
