package com.zambudio.registropesos.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.data.TestEnv.Companion.ESPALDA
import com.zambudio.registropesos.data.TestEnv.Companion.PECHO
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.repository.MuscleGroupInUseException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class CatalogRepositoryTest : DataTestBase() {

    @Test
    fun seedCreatesElevenDefaultGroupsWithFixedIds() = runTest {
        val groups = env.groups.observeAll().first()

        assertEquals(11, groups.size)
        assertEquals(DefaultMuscleGroups.all.map { it.id }, groups.map { it.id })
        assertEquals(DefaultMuscleGroups.all.map { it.name }, groups.map { it.name })
        assertEquals((0..10).toList(), groups.map { it.sortOrder })
        assertTrue(groups.all { it.isDefault })
    }

    @Test
    fun createExerciseIsObservableByGroupAndCounted() = runTest {
        val press = env.newExercise("Press banca")
        assertEquals(env.clock.now, press.updatedAt)
        assertEquals(env.clock.now, press.createdAt)
        env.newExercise("Remo", ESPALDA)
        val archived = env.newExercise("Aperturas")
        env.exercises.setArchived(archived.id, true)

        assertEquals(listOf("Press banca"), env.exercises.observeByGroup(PECHO).first().map { it.name })
        assertEquals(2, env.exercises.observeByGroup(PECHO, includeArchived = true).first().size)
        assertEquals(2, env.exercises.observeAll().first().size)
        val counts = env.groups.observeExerciseCounts().first()
        assertEquals(1, counts[PECHO])
        assertEquals(1, counts[ESPALDA])
        assertEquals(0, counts[TestEnv.TRICEPS])
        assertFalse(env.exercises.hasHistory(press.id))
    }

    @Test
    fun upsertKeepsCreatedAtAndTouchesUpdatedAt() = runTest {
        val press = env.newExercise("Press banca")
        env.clock.advance(5_000)
        env.exercises.upsert(press.copy(name = "Press plano"))

        val reloaded = env.exercises.getById(press.id)!!
        assertEquals("Press plano", reloaded.name)
        assertEquals(press.createdAt, reloaded.createdAt)
        assertEquals(env.clock.now, reloaded.updatedAt)
    }

    @Test
    fun deletingGroupWithExercisesFailsEvenIfArchived() = runTest {
        val exercise = env.newExercise("Press banca")
        env.exercises.setArchived(exercise.id, true)

        val result = env.groups.delete(PECHO)

        assertTrue(result.exceptionOrNull() is MuscleGroupInUseException)
        assertNotNull(env.groups.getById(PECHO))
    }

    @Test
    fun deletingGroupUsedByRoutineBlockFails() = runTest {
        val custom = MuscleGroup("g-custom", "Antebrazo", 11, false, 0L, 0L)
        env.groups.upsert(custom)
        env.newRoutine("Día 1", custom.id to emptyList())

        assertTrue(env.groups.delete(custom.id).exceptionOrNull() is MuscleGroupInUseException)
    }

    @Test
    fun deletingUnusedGroupLeavesTombstone() = runTest {
        val result = env.groups.delete(ESPALDA)

        assertTrue(result.isSuccess)
        assertNull(env.groups.getById(ESPALDA))
        assertNotNull(env.db.tombstoneDao().get(TombstoneType.MUSCLE_GROUP, ESPALDA))
    }

    @Test
    fun reorderGroupsRewritesSortOrder() = runTest {
        val ids = env.groups.observeAll().first().map { it.id }
        env.groups.reorder(ids.reversed())

        assertEquals(ids.reversed(), env.groups.observeAll().first().map { it.id })
    }
}
