package com.zambudio.registropesos.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.data.TestEnv.Companion.PECHO
import com.zambudio.registropesos.data.TestEnv.Companion.TRICEPS
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.repository.SessionAlreadyInProgressException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WorkoutRepositoryTest : DataTestBase() {

    private lateinit var press: Exercise
    private lateinit var pushdown: Exercise
    private lateinit var routine: RoutineDetail

    private suspend fun givenRoutine() {
        press = env.newExercise("Press banca")
        pushdown = env.newExercise("Polea", TRICEPS)
        routine = env.newRoutine("Pecho - Tríceps", PECHO to listOf(press to 3), TRICEPS to listOf(pushdown to 2))
    }

    /** Sesión terminada con series (peso, reps) para [press], elegido durante la sesión. */
    private suspend fun finishedSession(startedAt: Long, vararg sets: Pair<Double?, Int?>): String {
        val sessionId = env.workouts.start(routine, startedAt)
        val pressSets = env.addToSession(sessionId, press, 3)
        val filled = sets.mapIndexed { index, (weight, reps) ->
            pressSets[index].copy(weightKg = weight, reps = reps)
        }
        env.workouts.upsertSets(filled)
        env.clock.advance()
        env.workouts.finish(sessionId, startedAt + 3_600_000, filled.filterNot { it.isEmpty }.map { it.id })
        return sessionId
    }

    @Test
    fun startCopiesRoutineGroupsAndCommentWithoutExercises() = runTest {
        givenRoutine()

        val sessionId = env.workouts.start(routine, 5_000L)

        val detail = env.workouts.observeSessionDetail(sessionId).first()!!
        assertEquals(SessionStatus.IN_PROGRESS, detail.session.status)
        assertEquals(routine.routine.id, detail.session.routineId)
        assertEquals("Pecho - Tríceps", detail.session.routineNameSnapshot)
        assertEquals("subtítulo", detail.session.routineCommentSnapshot)
        assertEquals(5_000L, detail.session.startedAt)
        // Los ejercicios preseleccionados de la rutina (v1) ya no se cargan: se eligen en la sesión.
        assertTrue(detail.exercises.isEmpty())
        assertEquals(listOf(PECHO, TRICEPS), detail.groups.map { it.muscleGroupId })
        assertEquals(listOf("Pecho", "Tríceps"), detail.groups.map { it.muscleGroupName })
        assertEquals(sessionId, env.workouts.observeInProgress().first()?.id)
    }

    @Test
    fun startWorksForRoutineWithGroupsOnlyAndDeduplicatesGroups() = runTest {
        val onlyGroups =
            env.newRoutine("Solo grupos", PECHO to emptyList(), TRICEPS to emptyList(), PECHO to emptyList())

        val sessionId = env.workouts.start(onlyGroups, 1L)

        val detail = env.workouts.observeSessionDetail(sessionId).first()!!
        assertEquals(listOf("Pecho", "Tríceps"), detail.groups.map { it.muscleGroupName })
        assertEquals(listOf(0, 1), detail.groups.map { it.sortOrder })
    }

    @Test
    fun exercisesChosenDuringSessionAreStoredWithTheirGroupImageAndOrder() = runTest {
        givenRoutine()
        val withImage = env.newExercise("Aperturas", imagePath = "exercise_fly.jpg")
        val sessionId = env.workouts.start(routine, 1L)

        env.addToSession(sessionId, pushdown, 2)
        env.addToSession(sessionId, withImage, 3)

        val detail = env.workouts.observeSessionDetail(sessionId).first()!!
        assertEquals(listOf(pushdown.id, withImage.id), detail.exercises.map { it.exerciseId })
        assertEquals(listOf(0, 1), detail.exercises.map { it.order })
        assertEquals(listOf(TRICEPS, PECHO), detail.exercises.map { it.muscleGroupId })
        assertEquals("exercise_fly.jpg", detail.exercises[1].imagePath)
        assertEquals(listOf(2, 3), detail.exercises.map { it.sets.size })
        assertTrue(detail.exercises.flatMap { it.sets }.all { it.weightKg == null && it.reps == null })
    }

    @Test
    fun commentSnapshotSurvivesRoutineEdits() = runTest {
        givenRoutine()
        val sessionId = env.workouts.start(routine, 1L)

        env.routines.save(routine.copy(routine = routine.routine.copy(subtitle = "otro comentario")))

        assertEquals("subtítulo", env.workouts.getSession(sessionId)!!.routineCommentSnapshot)
    }

    @Test
    fun removeExerciseDeletesOnlyItsSets() = runTest {
        givenRoutine()
        val sessionId = env.workouts.start(routine, 1L)
        env.addToSession(sessionId, press, 3)
        env.addToSession(sessionId, pushdown, 2)
        env.clock.advance(60_000)

        env.workouts.removeExercise(sessionId, press.id)

        val detail = env.workouts.observeSessionDetail(sessionId).first()!!
        assertEquals(listOf(pushdown.id), detail.exercises.map { it.exerciseId })
        assertEquals(2, detail.groups.size)
        assertEquals(env.clock.now, detail.session.updatedAt)
    }

    @Test
    fun upsertKeepsLegacyDoneColumnsOfExistingRows() = runTest {
        givenRoutine()
        val sessionId = env.workouts.start(routine, 1L)
        val row = env.addToSession(sessionId, press, 1).single()
        val dao = env.db.workoutDao()
        dao.updateSet(dao.getSet(row.id)!!.copy(isDone = true, completedAt = 42L))

        env.workouts.upsertSet(row.copy(weightKg = 80.0, reps = 5))

        val stored = dao.getSet(row.id)!!
        assertEquals(80.0, stored.weightKg!!, 0.0)
        assertTrue(stored.isDone)
        assertEquals(42L, stored.completedAt)
    }

    @Test
    fun secondStartFailsWhileOneIsInProgress() = runTest {
        givenRoutine()
        val first = env.workouts.start(routine, 1L)

        try {
            env.workouts.start(routine, 2L)
            fail("Debía lanzar SessionAlreadyInProgressException")
        } catch (expected: SessionAlreadyInProgressException) {
            assertEquals(first, expected.sessionId)
        }
        assertEquals(2, env.db.workoutDao().getSessionGroups(first).size)
    }

    @Test
    fun upsertSetTouchesSessionUpdatedAt() = runTest {
        givenRoutine()
        val sessionId = env.workouts.start(routine, 1L)
        val set = env.addToSession(sessionId, press, 1).single()
        env.clock.advance(60_000)

        env.workouts.upsertSet(set.copy(weightKg = 80.0, reps = 8))

        assertEquals(env.clock.now, env.workouts.getSession(sessionId)!!.updatedAt)
        assertEquals(80.0, env.db.workoutDao().getSet(set.id)!!.weightKg!!, 0.0)
    }

    @Test
    fun lastFinishedSetsExcludeCurrentAndUnfinishedSessions() = runTest {
        givenRoutine()
        val older = finishedSession(1_000L, 60.0 to 10)
        val newer = finishedSession(2_000L, 70.0 to 8, 72.5 to 6)
        val current = env.workouts.start(routine, 3_000L)
        env.workouts.upsertSet(env.addToSession(current, press, 1).single().copy(weightKg = 99.0, reps = 1))

        val last = env.workouts.lastFinishedSetsForExercise(press.id, current)
        assertEquals(listOf(newer), last.map { it.sessionId }.distinct())
        assertEquals(listOf(70.0, 72.5), last.map { it.weightKg })

        val beforeNewer = env.workouts.lastFinishedSetsForExercise(press.id, newer)
        assertEquals(listOf(older), beforeNewer.map { it.sessionId }.distinct())

        // Sin excluir nada, la sesión en curso tampoco cuenta (no está FINISHED).
        assertEquals(newer, env.workouts.lastFinishedSetsForExercise(press.id, null).first().sessionId)
        assertTrue(env.workouts.lastFinishedSetsForExercise(pushdown.id, current).isEmpty())
    }

    @Test
    fun finishDeletesSetsNotKept() = runTest {
        givenRoutine()
        val sessionId = finishedSession(1_000L, 60.0 to 10, null to null)

        val session = env.workouts.getSession(sessionId)!!
        assertEquals(SessionStatus.FINISHED, session.status)
        assertEquals(1_000L + 3_600_000, session.finishedAt)
        val remaining = env.db.workoutDao().getSets(sessionId)
        assertEquals(1, remaining.size)
        assertEquals(60.0, remaining.single().weightKg!!, 0.0)
        assertNull(env.workouts.getInProgress())
    }

    @Test
    fun deletingRoutineKeepsHistoryWithSnapshot() = runTest {
        givenRoutine()
        val sessionId = finishedSession(1_000L, 60.0 to 10)

        env.routines.delete(routine.routine.id)

        assertNull(env.routines.getDetail(routine.routine.id))
        assertNotNull(env.db.tombstoneDao().get(TombstoneType.ROUTINE, routine.routine.id))
        val session = env.workouts.getSession(sessionId)!!
        assertNull(session.routineId)
        assertEquals("Pecho - Tríceps", session.routineNameSnapshot)
        assertEquals(1, env.db.workoutDao().getSets(sessionId).size)
        assertTrue(env.db.routineDao().getAllBlocks().isEmpty())
        assertTrue(env.exercises.hasHistory(press.id))
    }

    @Test
    fun discardDeletesWithoutTombstoneAndDeleteSessionLeavesOne() = runTest {
        givenRoutine()
        val inProgress = env.workouts.start(routine, 1L)
        env.workouts.discard(inProgress)
        assertNull(env.workouts.getSession(inProgress))
        assertNull(env.db.tombstoneDao().get(TombstoneType.SESSION, inProgress))

        val finished = finishedSession(2_000L, 60.0 to 10)
        env.workouts.deleteSession(finished)
        assertNull(env.workouts.getSession(finished))
        assertTrue(env.db.workoutDao().getSets(finished).isEmpty())
        assertNotNull(env.db.tombstoneDao().get(TombstoneType.SESSION, finished))
    }

    @Test
    fun finishedSummariesAndProgressAggregates() = runTest {
        givenRoutine()
        val first = finishedSession(1_000L, 60.0 to 10, 80.0 to 3)
        val second = finishedSession(2_000L, 70.0 to 0, 50.0 to 10)
        env.workouts.start(routine, 3_000L) // en curso: no cuenta

        val summaries = env.workouts.observeFinishedSessions().first()
        assertEquals(listOf(second, first), summaries.map { it.id })
        assertEquals(60.0 * 10 + 80.0 * 3, summaries[1].volumeKg, 1e-9)
        assertEquals(1, summaries[1].exerciseCount)

        val points = env.progress.observeExercisePoints(press.id, 0L).first()
        assertEquals(listOf(first, second), points.map { it.sessionId })
        assertEquals(80.0, points[0].maxWeightKg, 1e-9)
        assertEquals(840.0, points[0].volumeKg, 1e-9)
        assertEquals(80.0 * (1 + 3 / 30.0), points[0].bestE1rmKg, 1e-9)
        // La serie con 0 reps no cuenta para el máximo.
        assertEquals(50.0, points[1].maxWeightKg, 1e-9)
        assertEquals(listOf(second), env.progress.observeExercisePoints(press.id, 1_500L).first().map { it.sessionId })

        val volumes = env.progress.observeGroupSessionVolumes(PECHO, 0L).first()
        assertEquals(listOf(840.0, 500.0), volumes.map { it.volumeKg })
        assertTrue(env.progress.observeGroupSessionVolumes(TRICEPS, 0L).first().isEmpty())

        val flat = env.progress.finishedSetsFlat(null)
        assertEquals(3, flat.size)
        assertFalse(flat.any { it.reps == 0 })
        assertEquals(flat, env.progress.observeFinishedSetsFlat(press.id).first())
    }
}
