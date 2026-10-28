package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun routineDetail(
    name: String = "Día 1",
    defaultSets: List<Int> = listOf(4),
    blockCount: Int = 1,
): RoutineDetail {
    val group = MuscleGroup("g1", "Pecho", 0, true, 0, 0)
    val blocks = (0 until blockCount).map { b ->
        RoutineBlockDetail(
            block = RoutineBlock("b$b", "r1", "g1", b, 0, 0),
            muscleGroup = group,
            exercises = defaultSets.mapIndexed { i, sets ->
                RoutineExerciseDetail(
                    RoutineExercise("re$b-$i", "b$b", "ex$i", i, sets, 0, 0),
                    Exercise("ex$i", "g1", "Ejercicio $i", null, null, false, 0, 0),
                )
            },
        )
    }
    return RoutineDetail(Routine("r1", name, null, 0, 0, 0), blocks)
}

class FinishSessionTest {
    private val now = 10_000_000L
    private val clock = TimeProvider { now }

    private fun repoWithSession(): FakeWorkoutRepository = FakeWorkoutRepository().apply {
        sessions["s1"] = session("s1", startedAt = now - 3_600_000)
        sets += listOf(
            workoutSet("a", "ex1", 100.0, 5, setIndex = 0, exerciseOrder = 0),
            workoutSet("b", "ex1", 105.0, 3, setIndex = 1, exerciseOrder = 0),
            workoutSet("c", "ex1", setIndex = 2, exerciseOrder = 0),
            workoutSet("d", "ex2", 20.0, 12, setIndex = 0, exerciseOrder = 1, exerciseName = "Fondos"),
            workoutSet("e", "ex3", setIndex = 0, exerciseOrder = 2),
            workoutSet("f", "ex3", 0.0, 0, setIndex = 1, exerciseOrder = 2),
        )
    }

    @Test
    fun finish_keepsNonEmptySets_andBuildsSummary() = runTest {
        val workout = repoWithSession()
        val progress = FakeProgressRepository(
            listOf(
                flatSet("ex1", 100.0, 5, startedAt = 1_000),
                flatSet("ex2", 25.0, 10, startedAt = 1_000),
            ),
        )

        val summary = FinishSession(workout, progress, clock)("s1").getOrThrow()

        assertEquals(Triple("s1", now, listOf("a", "b", "d")), workout.finishCall)
        assertEquals(SessionStatus.FINISHED, workout.sessions.getValue("s1").status)
        assertEquals(3_600_000L, summary.durationMs)
        assertEquals(2, summary.exerciseCount)
        assertEquals(3, summary.setCount)
        assertEquals(500.0 + 315.0 + 240.0, summary.volumeKg, 1e-9)
        // ex1: 105 > 100 (peso); e1RM 105×3 = 115.5 < 116.5 previo. ex2: 20 < 25, e1RM 28.0 < 33.5.
        assertEquals(listOf(NewRecord("ex1", "Press banca", RecordType.WEIGHT, 100.0, 105.0)), summary.newRecords)
    }

    @Test
    fun finish_excludesCurrentSessionFromPreviousRecords() = runTest {
        val workout = repoWithSession()
        // Si el repositorio devolviera ya las series de s1, no deben anular el récord.
        val progress = FakeProgressRepository(
            listOf(
                flatSet("ex1", 90.0, 5, startedAt = 1_000),
                flatSet("ex1", 105.0, 3, startedAt = now, sessionId = "s1"),
            ),
        )

        val summary = FinishSession(workout, progress, clock)("s1").getOrThrow()

        assertEquals(
            listOf(RecordType.WEIGHT, RecordType.E1RM),
            summary.newRecords.map { it.type },
        )
    }

    @Test
    fun finish_withoutHistory_reportsNoRecords() = runTest {
        val summary = FinishSession(repoWithSession(), FakeProgressRepository(emptyList()), clock)("s1").getOrThrow()
        assertTrue(summary.newRecords.isEmpty())
    }

    @Test
    fun finish_unknownSession_fails() = runTest {
        val result = FinishSession(FakeWorkoutRepository(), FakeProgressRepository(emptyList()), clock)("nope")
        assertTrue(result.exceptionOrNull() is SessionNotFoundException)
    }

    @Test
    fun finish_alreadyFinished_failsWithoutTouchingRepo() = runTest {
        val workout = FakeWorkoutRepository().apply {
            sessions["s1"] = session("s1", status = SessionStatus.FINISHED)
        }
        val result = FinishSession(workout, FakeProgressRepository(emptyList()), clock)("s1")
        assertTrue(result.exceptionOrNull() is SessionNotInProgressException)
        assertNull(workout.finishCall)
    }
}

class StartAndDiscardSessionTest {
    private val clock = TimeProvider { 42L }
    private val routines = FakeRoutineRepository(mapOf("r1" to routineDetail()))

    @Test
    fun start_createsSessionWithCurrentTime() = runTest {
        val workout = FakeWorkoutRepository()
        val id = StartSession(routines, workout, clock)("r1").getOrThrow()
        assertEquals("new-session", id)
        assertEquals(42L, workout.sessions.getValue(id).startedAt)
        assertEquals("r1", workout.startedRoutine?.routine?.id)
    }

    @Test
    fun start_withSessionInProgress_failsWithItsId() = runTest {
        val workout = FakeWorkoutRepository().apply { sessions["s0"] = session("s0") }
        val error = StartSession(routines, workout, clock)("r1").exceptionOrNull()
        assertEquals("s0", (error as SessionAlreadyInProgressException).sessionId)
        assertNull(workout.startedRoutine)
    }

    @Test
    fun start_whenRepositoryDetectsRace_returnsFailure() = runTest {
        val workout = FakeWorkoutRepository().apply { throwOnStart = "s9" }
        val error = StartSession(routines, workout, clock)("r1").exceptionOrNull()
        assertEquals("s9", (error as SessionAlreadyInProgressException).sessionId)
    }

    @Test
    fun start_unknownRoutine_fails() = runTest {
        val result = StartSession(routines, FakeWorkoutRepository(), clock)("missing")
        assertTrue(result.exceptionOrNull() is RoutineNotFoundException)
    }

    @Test
    fun discard_inProgressSession_deletesIt() = runTest {
        val workout = FakeWorkoutRepository().apply { sessions["s1"] = session("s1") }
        assertTrue(DiscardSession(workout)("s1").isSuccess)
        assertEquals(listOf("s1"), workout.discarded)
    }

    @Test
    fun discard_finishedSession_isRefused() = runTest {
        val workout = FakeWorkoutRepository().apply {
            sessions["s1"] = session("s1", status = SessionStatus.FINISHED)
        }
        assertTrue(DiscardSession(workout)("s1").exceptionOrNull() is SessionNotInProgressException)
        assertTrue(workout.discarded.isEmpty())
    }

    @Test
    fun discard_unknownSession_fails() = runTest {
        assertTrue(DiscardSession(FakeWorkoutRepository())("x").exceptionOrNull() is SessionNotFoundException)
    }
}

class PreviousSessionForExerciseTest {
    @Test
    fun returnsGhostsSortedBySetIndex_skippingEmptyOnes() = runTest {
        val workout = FakeWorkoutRepository().apply {
            lastFinishedSets["ex1"] = listOf(
                workoutSet("b", weightKg = 82.5, reps = 8, setIndex = 1),
                workoutSet("x", setIndex = 2),
                workoutSet("a", weightKg = 80.0, reps = 10, setIndex = 0),
                workoutSet("c", weightKg = null, reps = 12, setIndex = 3),
            )
        }
        assertEquals(
            listOf(GhostSet(0, 80.0, 10), GhostSet(1, 82.5, 8), GhostSet(3, null, 12)),
            PreviousSessionForExercise(workout)("ex1", excludeSessionId = "s1"),
        )
    }

    @Test
    fun noHistory_returnsEmpty() = runTest {
        assertTrue(PreviousSessionForExercise(FakeWorkoutRepository())("ex1", null).isEmpty())
    }
}
