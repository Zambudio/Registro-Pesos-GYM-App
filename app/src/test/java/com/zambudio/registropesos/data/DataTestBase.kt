package com.zambudio.registropesos.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.core.db.Migrations
import com.zambudio.registropesos.core.db.SeedCallback
import com.zambudio.registropesos.data.repository.BackupRepositoryImpl
import com.zambudio.registropesos.data.repository.ExerciseRepositoryImpl
import com.zambudio.registropesos.data.repository.MuscleGroupRepositoryImpl
import com.zambudio.registropesos.data.repository.ProgressRepositoryImpl
import com.zambudio.registropesos.data.repository.RoutineRepositoryImpl
import com.zambudio.registropesos.data.repository.WorkoutRepositoryImpl
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import org.junit.After
import org.junit.Before

/** Reloj de test controlable. */
class FakeClock(var now: Long = 1_000_000L) : TimeProvider {
    override fun now(): Long = now
    fun advance(millis: Long = 1_000L) {
        now += millis
    }
}

/** Ids legibles y deterministas. */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}

/** Repositorios sobre una BD Room en memoria con el seed real. */
class TestEnv(idPrefix: String = "id") {
    val clock = FakeClock()
    val ids = SequentialIds(idPrefix)
    val db: AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .addCallback(SeedCallback)
        .addMigrations(*Migrations.ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()
    val groups = MuscleGroupRepositoryImpl(db, clock)
    val exercises = ExerciseRepositoryImpl(db, clock)
    val routines = RoutineRepositoryImpl(db, clock)
    val workouts = WorkoutRepositoryImpl(db, clock, ids)
    val progress = ProgressRepositoryImpl(db)
    val backup = BackupRepositoryImpl(db)

    suspend fun newExercise(name: String, groupId: String = PECHO, imagePath: String? = null): Exercise {
        clock.advance()
        val exercise = Exercise(ids.newId(), groupId, name, null, imagePath, false, 0L, 0L)
        exercises.upsert(exercise)
        return checkNotNull(exercises.getById(exercise.id))
    }

    /** Rutina con bloques (grupo -> [(ejercicio, series)]), guardada y releída. */
    suspend fun newRoutine(name: String, vararg blocks: Pair<String, List<Pair<Exercise, Int>>>): RoutineDetail {
        clock.advance()
        val routineId = ids.newId()
        val detail = RoutineDetail(
            routine = Routine(routineId, name, "subtítulo", 0, 0L, 0L),
            blocks = blocks.mapIndexed { blockIndex, (groupId, items) ->
                val blockId = ids.newId()
                RoutineBlockDetail(
                    block = RoutineBlock(blockId, routineId, groupId, blockIndex, 0L, 0L),
                    muscleGroup = checkNotNull(groups.getById(groupId)),
                    exercises = items.mapIndexed { index, (exercise, sets) ->
                        RoutineExerciseDetail(
                            RoutineExercise(ids.newId(), blockId, exercise.id, index, sets, 0L, 0L),
                            exercise,
                        )
                    },
                )
            },
        )
        routines.save(detail)
        return checkNotNull(routines.getDetail(routineId))
    }

    fun close() = db.close()

    companion object {
        val PECHO = DefaultMuscleGroups.all[0].id
        val ESPALDA = DefaultMuscleGroups.all[1].id
        val TRICEPS = DefaultMuscleGroups.all[4].id
    }
}

abstract class DataTestBase {
    protected lateinit var env: TestEnv

    @Before
    fun setUpEnv() {
        env = TestEnv()
    }

    @After
    fun tearDownEnv() {
        env.close()
    }
}
