package com.zambudio.registropesos.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.data.TestEnv.Companion.PECHO
import com.zambudio.registropesos.data.TestEnv.Companion.TRICEPS
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RoutineRepositoryTest : DataTestBase() {

    @Test
    fun routineWithTwoBlocksIsStoredInOrder() = runTest {
        val press = env.newExercise("Press banca")
        val incline = env.newExercise("Press inclinado")
        val pushdown = env.newExercise("Polea", TRICEPS)

        val detail = env.newRoutine(
            "Pecho - Tríceps",
            PECHO to listOf(press to 4, incline to 3),
            TRICEPS to listOf(pushdown to 4),
        )

        assertEquals(2, detail.blocks.size)
        assertEquals(listOf(PECHO, TRICEPS), detail.blocks.map { it.muscleGroup.id })
        assertEquals(listOf("Press banca", "Press inclinado"), detail.blocks[0].exercises.map { it.exercise.name })
        assertEquals(listOf(4, 3), detail.blocks[0].exercises.map { it.routineExercise.defaultSets })
        val summary = env.routines.observeSummaries().first().single()
        assertEquals(listOf("Pecho", "Tríceps"), summary.groupNames)
    }

    @Test
    fun saveReplacesChildrenAndTouchesUpdatedAt() = runTest {
        val press = env.newExercise("Press banca")
        val incline = env.newExercise("Press inclinado")
        val pushdown = env.newExercise("Polea", TRICEPS)
        val dips = env.newExercise("Fondos", TRICEPS)
        val original = env.newRoutine(
            "Día 1",
            PECHO to listOf(press to 4, incline to 3),
            TRICEPS to listOf(pushdown to 4),
        )
        env.clock.advance(10_000)

        // Se quita el bloque de pecho; en tríceps se añade "Fondos" delante de "Polea".
        val tricepsBlock = original.blocks[1]
        val edited = original.copy(
            routine = original.routine.copy(name = "Día 1 (solo tríceps)"),
            blocks = listOf(
                tricepsBlock.copy(
                    exercises = listOf(
                        RoutineExerciseDetail(
                            RoutineExercise("re-new", tricepsBlock.block.id, dips.id, 0, 5, 0L, 0L),
                            dips,
                        ),
                    ) + tricepsBlock.exercises,
                ),
            ),
        )
        env.routines.save(edited)

        val reloaded = env.routines.getDetail(original.routine.id)!!
        assertEquals("Día 1 (solo tríceps)", reloaded.routine.name)
        assertEquals(env.clock.now, reloaded.routine.updatedAt)
        assertEquals(original.routine.createdAt, reloaded.routine.createdAt)
        assertEquals(listOf(TRICEPS), reloaded.blocks.map { it.muscleGroup.id })
        assertEquals(0, reloaded.blocks[0].block.sortOrder)
        assertEquals(listOf("Fondos", "Polea"), reloaded.blocks[0].exercises.map { it.exercise.name })
        assertEquals(listOf(0, 1), reloaded.blocks[0].exercises.map { it.routineExercise.sortOrder })
        assertEquals(2, env.db.routineDao().getAllRoutineExercises().size)
        assertEquals(1, env.db.routineDao().getAllBlocks().size)
    }

    @Test
    fun observeDetailEmitsAfterChanges() = runTest {
        val press = env.newExercise("Press banca")
        val routine = env.newRoutine("Día 1", PECHO to listOf(press to 4))

        env.routines.save(routine.copy(routine = routine.routine.copy(subtitle = "nuevo")))

        assertEquals("nuevo", env.routines.observeDetail(routine.routine.id).first()?.routine?.subtitle)
        env.routines.delete(routine.routine.id)
        assertNull(env.routines.observeDetail(routine.routine.id).first())
    }
}
