package com.zambudio.registropesos.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.TestEnv.Companion.PECHO
import com.zambudio.registropesos.data.TestEnv.Companion.TRICEPS
import com.zambudio.registropesos.domain.backup.BACKUP_SCHEMA_VERSION
import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ExerciseDto
import com.zambudio.registropesos.domain.backup.MuscleGroupDto
import com.zambudio.registropesos.domain.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class BackupRepositoryTest {
    private lateinit var source: TestEnv
    private lateinit var target: TestEnv

    @Before
    fun setUp() {
        source = TestEnv("src")
        target = TestEnv("dst")
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    /**
     * Datos de ejemplo en [source]: 2 ejercicios (uno con imagen), rutina de 2 bloques,
     * 1 sesión terminada y 1 en curso.
     */
    private suspend fun seedSource(): String {
        val press = source.newExercise("Press banca", imagePath = "exercise_press.jpg")
        val pushdown = source.newExercise("Polea", TRICEPS)
        val routine = source.newRoutine("Día 1", PECHO to listOf(press to 2), TRICEPS to listOf(pushdown to 1))
        val sessionId = source.workouts.start(routine, 10_000L)
        source.addToSession(sessionId, press, 2)
        source.addToSession(sessionId, pushdown, 1)
        val sets = source.workouts.observeSessionDetail(sessionId).first()!!.exercises.flatMap { it.sets }
        val filled = sets.map { it.copy(weightKg = 50.0, reps = 10) }
        source.workouts.upsertSets(filled)
        source.clock.advance()
        source.workouts.finish(sessionId, 20_000L, filled.map { it.id })
        source.clock.advance()
        source.workouts.start(routine, 30_000L) // en curso: no se exporta
        return sessionId
    }

    private fun BackupSnapshot.normalized() = copy(
        exportedAt = 0L,
        muscleGroups = muscleGroups.sortedBy { it.id },
        exercises = exercises.sortedBy { it.id },
        routines = routines.sortedBy { it.id },
        routineBlocks = routineBlocks.sortedBy { it.id },
        routineExercises = routineExercises.sortedBy { it.id },
        sessions = sessions.sortedBy { it.id },
        sets = sets.sortedBy { it.id },
        tombstones = tombstones.sortedBy { it.id },
        sessionGroups = sessionGroups.sortedBy { it.id },
    )

    @Test
    fun exportExcludesInProgressSessionAndPrefixesImages() = runTest {
        val finishedId = seedSource()

        val snapshot = source.backup.exportSnapshot("0.1.0", 99L)

        assertEquals(BACKUP_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals(AppDatabase.VERSION, snapshot.dbVersion)
        assertEquals(listOf(finishedId), snapshot.sessions.map { it.id })
        assertTrue(snapshot.sets.all { it.sessionId == finishedId })
        assertEquals(3, snapshot.sets.size)
        assertEquals(listOf("Pecho", "Tríceps"), snapshot.sessionGroups.map { it.muscleGroupNameSnapshot })
        assertTrue(snapshot.sessionGroups.all { it.sessionId == finishedId })
        assertEquals("subtítulo", snapshot.sessions.single().routineCommentSnapshot)
        assertEquals("images/exercise_press.jpg", snapshot.exercises.first { it.name == "Press banca" }.imageFile)
        assertEquals(11, snapshot.muscleGroups.size)
    }

    @Test
    fun exportThenImportIntoEmptyDatabaseReproducesData() = runTest {
        seedSource()
        val exported = source.backup.exportSnapshot("0.1.0", 99L)

        val report = target.backup.importSnapshot(exported)

        assertEquals(0, report.muscleGroups.inserted) // mismos UUID del seed
        assertEquals(11, report.muscleGroups.skipped)
        assertEquals(2, report.exercises.inserted)
        assertEquals(1, report.routines.inserted)
        assertEquals(1, report.sessions.inserted)
        assertTrue(report.warnings.isEmpty())
        val pressId = exported.exercises.first { it.name == "Press banca" }.id
        assertEquals(mapOf(pressId to "images/exercise_press.jpg"), report.imagesToCopy)
        assertEquals("exercise_press.jpg", target.exercises.getById(pressId)!!.imagePath)
        assertEquals(exported.normalized(), target.backup.exportSnapshot("0.1.0", 0L).normalized())
        assertNull(target.workouts.getInProgress())
    }

    @Test
    fun importingTwiceDoesNotDuplicate() = runTest {
        seedSource()
        val exported = source.backup.exportSnapshot("0.1.0", 99L)
        target.backup.importSnapshot(exported)

        val second = target.backup.importSnapshot(exported)

        assertEquals(0, second.exercises.inserted + second.exercises.updated)
        assertEquals(1, second.routines.skipped)
        assertEquals(1, second.sessions.skipped)
        assertTrue(second.imagesToCopy.isEmpty())
        assertEquals(exported.normalized(), target.backup.exportSnapshot("0.1.0", 0L).normalized())
    }

    @Test
    fun olderIncomingDataDoesNotOverwriteNewerLocalData() = runTest {
        seedSource()
        val exported = source.backup.exportSnapshot("0.1.0", 99L)
        target.backup.importSnapshot(exported)
        val pressId = exported.exercises.first { it.name == "Press banca" }.id
        target.clock.now = exported.exercises.maxOf { it.updatedAt } + 100_000
        target.exercises.upsert(target.exercises.getById(pressId)!!.copy(name = "Press local"))

        val report = target.backup.importSnapshot(exported)

        assertEquals("Press local", target.exercises.getById(pressId)!!.name)
        assertEquals(0, report.exercises.updated)
        assertEquals(2, report.exercises.skipped)
    }

    @Test
    fun newerIncomingDataUpdatesAndTombstonesDelete() = runTest {
        seedSource()
        target.backup.importSnapshot(source.backup.exportSnapshot("0.1.0", 99L))
        val routineId = source.routines.observeSummaries().first().single().id
        val press = source.exercises.observeAll().first().first { it.name == "Press banca" }
        source.clock.advance(100_000)
        source.exercises.upsert(press.copy(name = "Press renombrado"))
        source.workouts.getInProgress()?.let { source.workouts.discard(it.id) }
        source.routines.delete(routineId)

        val report = target.backup.importSnapshot(source.backup.exportSnapshot("0.1.0", 199L))

        assertEquals(1, report.exercises.updated)
        assertEquals("Press renombrado", target.exercises.getById(press.id)!!.name)
        assertEquals(1, report.deleted)
        assertNull(target.routines.getDetail(routineId))
        val session = target.workouts.observeFinishedSessions().first().single()
        assertNull(target.workouts.getSession(session.id)!!.routineId)
    }

    @Test
    fun incomingGroupWithSameNameIsRemappedToLocalId() = runTest {
        val foreignGroup = MuscleGroupDto("foreign-pecho", "PECHO", 0, false, 5L, 5L)
        val snapshot = BackupSnapshot(
            dbVersion = 1,
            appVersion = "x",
            exportedAt = 1L,
            muscleGroups = listOf(foreignGroup),
            exercises = listOf(ExerciseDto("ex-1", "foreign-pecho", "Press", null, null, false, 5L, 5L)),
        )

        val report = target.backup.importSnapshot(snapshot)

        assertEquals(1, report.muscleGroups.skipped)
        assertEquals(PECHO, target.exercises.getById("ex-1")!!.muscleGroupId)
        assertEquals(11, target.groups.observeAll().first().size)
    }

    @Test
    fun brokenForeignKeysAreSkippedWithWarning() = runTest {
        val snapshot = BackupSnapshot(
            dbVersion = 1,
            appVersion = "x",
            exportedAt = 1L,
            exercises = listOf(ExerciseDto("ex-1", "missing-group", "Press", null, null, false, 5L, 5L)),
        )

        val report = target.backup.importSnapshot(snapshot)

        assertEquals(1, report.exercises.skipped)
        assertEquals(1, report.warnings.size)
        assertNull(target.exercises.getById("ex-1"))
    }

    @Test
    fun inProgressSessionsAreNeverImported() = runTest {
        seedSource()
        val exported = source.backup.exportSnapshot("0.1.0", 99L)
        val forged = exported.copy(
            sessions = exported.sessions.map { it.copy(status = SessionStatus.IN_PROGRESS.name) },
        )

        val report = target.backup.importSnapshot(forged)

        assertEquals(1, report.sessions.skipped)
        assertNull(target.workouts.getInProgress())
        assertTrue(target.workouts.observeFinishedSessions().first().isEmpty())
    }

    @Test
    fun v1BackupWithoutSessionGroupsImportsAndKeepsLegacyDoneFlag() = runTest {
        val finishedId = seedSource()
        val exported = source.backup.exportSnapshot("0.1.0", 99L)
        // Lo que escribía la v0.1.0: schemaVersion 1, sin grupos de sesión ni comentario, isDone en series.
        val v1 = exported.copy(
            schemaVersion = 1,
            dbVersion = 1,
            sessionGroups = emptyList(),
            sessions = exported.sessions.map { it.copy(routineCommentSnapshot = null) },
            sets = exported.sets.map { it.copy(isDone = true, completedAt = 15_000L) },
        )

        val report = target.backup.importSnapshot(v1)

        assertEquals(1, report.sessions.inserted)
        assertTrue(report.warnings.isEmpty())
        val detail = target.workouts.observeSessionDetail(finishedId).first()!!
        assertEquals(3, detail.exercises.sumOf { it.sets.size })
        assertTrue(detail.groups.isEmpty())
        assertTrue(target.db.workoutDao().getSets(finishedId).all { it.isDone && it.completedAt == 15_000L })
        assertEquals(1, target.progress.observeExercisePoints(detail.exercises.first().exerciseId, 0L).first().size)
    }

    @Test
    fun newerV1SessionKeepsLocalCommentAndGroups() = runTest {
        val finishedId = seedSource()
        val exported = source.backup.exportSnapshot("0.1.0", 99L)
        target.backup.importSnapshot(exported)
        val v1 = exported.copy(
            schemaVersion = 1,
            sessionGroups = emptyList(),
            sessions = exported.sessions.map { it.copy(routineCommentSnapshot = null, updatedAt = it.updatedAt + 1) },
        )

        val report = target.backup.importSnapshot(v1)

        assertEquals(1, report.sessions.updated)
        assertEquals("subtítulo", target.workouts.getSession(finishedId)!!.routineCommentSnapshot)
        assertEquals(2, target.db.workoutDao().getSessionGroups(finishedId).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun newerSchemaVersionIsRejected() = runTest {
        target.backup.importSnapshot(
            BackupSnapshot(schemaVersion = BACKUP_SCHEMA_VERSION + 1, dbVersion = 1, appVersion = "x", exportedAt = 1L),
        )
    }
}
