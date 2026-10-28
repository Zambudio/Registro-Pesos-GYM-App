package com.zambudio.registropesos.feature.workout

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.core.db.Migrations
import com.zambudio.registropesos.core.db.SeedCallback
import com.zambudio.registropesos.data.DefaultMuscleGroups
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
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.usecase.DiscardSession
import com.zambudio.registropesos.domain.usecase.FinishSession
import com.zambudio.registropesos.domain.usecase.PreviousSessionForExercise
import com.zambudio.registropesos.domain.usecase.StartSession
import com.zambudio.registropesos.feature.workout.session.WorkoutUiState
import com.zambudio.registropesos.feature.workout.session.WorkoutViewModel
import com.zambudio.registropesos.feature.workout.train.TrainUiState
import com.zambudio.registropesos.feature.workout.train.TrainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Aceptación del kickoff (§2.C): matar el proceso a mitad de sesión y reabrir reanuda con los datos.
 * BD Room en fichero construida como DatabaseModule/AppDatabase.build (seed + migraciones) y repos
 * reales; "matar el proceso" = cerrar la BD y abandonar los ViewModels, "reabrir" = instancia nueva.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WorkoutResumeAfterProcessDeathTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "resume-${UUID.randomUUID()}.db"
    private val clock = TestClock(10_000_000L)
    private val ids = TestIds("id")

    // Igual que AppDatabase.build (seed + migraciones), con un fichero temporal propio del test. Única
    // diferencia: diario TRUNCATE en vez de WAL, porque el SQLite nativo de Robolectric en Windows no
    // puede abrir el fichero -shm de WAL (SQLITE_CANTOPEN). Lo confirmado en una transacción persiste igual.
    @Suppress("SpreadOperator")
    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(SeedCallback)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private inner class App(val db: AppDatabase) {
        val groups = MuscleGroupRepositoryImpl(db, clock)
        val routines = RoutineRepositoryImpl(db, clock)
        val exercises = ExerciseRepositoryImpl(db, clock)
        val workouts = WorkoutRepositoryImpl(db, clock, ids)
        val progress = ProgressRepositoryImpl(db)

        fun TestScope.workoutViewModel(sessionId: String): WorkoutViewModel {
            val vm = WorkoutViewModel(
                savedStateHandle = routeHandle("sessionId" to sessionId),
                workoutRepository = workouts,
                settingsRepository = FakeSettingsRepository(),
                previousSessionForExercise = PreviousSessionForExercise(workouts),
                finishSession = FinishSession(workouts, progress, clock),
                discardSession = DiscardSession(workouts),
                timeProvider = clock,
                idGenerator = ids,
                applicationScope = immediateApplicationScope(),
            )
            collectInBackground(vm.uiState)
            return vm
        }

        fun TestScope.trainViewModel(): TrainViewModel {
            val vm = TrainViewModel(workouts, routines, DiscardSession(workouts))
            collectInBackground(vm.uiState)
            return vm
        }
    }

    private suspend fun App.createRoutine(): RoutineDetail {
        val chest = DefaultMuscleGroups.all[0].id
        val press = Exercise("ex-press", chest, "Press banca", null, null, false, 0L, 0L)
        val fly = Exercise("ex-fly", chest, "Aperturas", null, null, false, 0L, 0L)
        exercises.upsert(press)
        exercises.upsert(fly)
        val group = checkNotNull(groups.getById(chest)) { "El seed debe crear Pecho" }
        val detail = RoutineDetail(
            routine = Routine("routine-1", "Pecho", null, 0, 0L, 0L),
            blocks = listOf(
                RoutineBlockDetail(
                    block = RoutineBlock("block-1", "routine-1", chest, 0, 0L, 0L),
                    muscleGroup = group,
                    exercises = listOf(
                        RoutineExerciseDetail(RoutineExercise("re-1", "block-1", press.id, 0, 3, 0L, 0L), press),
                        RoutineExerciseDetail(RoutineExercise("re-2", "block-1", fly.id, 1, 2, 0L, 0L), fly),
                    ),
                ),
            ),
        )
        routines.save(detail)
        return checkNotNull(routines.getDetail("routine-1"))
    }

    private fun SessionDetail.set(exerciseId: String, position: Int) =
        exercises.single { it.exerciseId == exerciseId }.sets[position]

    @Test
    fun sessionInProgressSurvivesProcessDeathAndIsOfferedForResume() = runTest {
        // --- Primera "vida" del proceso: empezar y rellenar series desde la pantalla de sesión ---
        val first = App(openDatabase())
        first.createRoutine()
        val sessionId = StartSession(first.routines, first.workouts, clock)("routine-1").getOrThrow()

        val vm = with(first) { workoutViewModel(sessionId) }
        val loaded = vm.uiState.first { it is WorkoutUiState.Content } as WorkoutUiState.Content
        val pressRows = loaded.blocks.single().exercises.first { it.exerciseId == "ex-press" }.sets
        val flyRows = loaded.blocks.single().exercises.first { it.exerciseId == "ex-fly" }.sets

        vm.onWeightChange(pressRows[0].setId, "82,5")
        vm.onRepsChange(pressRows[0].setId, "8")
        vm.onToggleDone(pressRows[0].setId)
        vm.onWeightChange(pressRows[1].setId, "80")
        vm.onRepsChange(pressRows[1].setId, "10")
        vm.onAddSet("ex-fly")
        vm.onWeightChange(flyRows[0].setId, "14")

        // Las escrituras van a Room en segundo plano: esperar a que estén todas confirmadas.
        first.workouts.observeSessionDetail(sessionId).first { detail ->
            detail != null &&
                detail.set("ex-press", 0).isDone &&
                detail.set("ex-press", 1).reps == 10 &&
                detail.exercises.single { it.exerciseId == "ex-fly" }.sets.size == 3 &&
                detail.set("ex-fly", 0).weightKg == 14.0
        }

        // --- El sistema mata el proceso ---
        first.db.close()

        // --- Segunda "vida": BD nueva sobre el mismo fichero ---
        val second = App(openDatabase())
        try {
            val inProgress = checkNotNull(second.workouts.observeInProgress().first())
            assertEquals(sessionId, inProgress.id)
            assertEquals(SessionStatus.IN_PROGRESS, inProgress.status)
            assertEquals("Pecho", inProgress.routineNameSnapshot)

            val detail = checkNotNull(second.workouts.observeSessionDetail(sessionId).first())
            val press0 = detail.set("ex-press", 0)
            assertEquals(82.5, press0.weightKg)
            assertEquals(8, press0.reps)
            assertTrue(press0.isDone)
            assertEquals(10_000_000L, press0.completedAt)
            assertEquals(80.0, detail.set("ex-press", 1).weightKg)
            assertEquals(10, detail.set("ex-press", 1).reps)
            assertEquals(14.0, detail.set("ex-fly", 0).weightKg)
            val flySets = detail.exercises.single { it.exerciseId == "ex-fly" }.sets
            assertEquals(listOf(0, 1, 2), flySets.map { it.setIndex })

            // Entrenar ofrece reanudar...
            val train = with(second) { trainViewModel() }
            val home = train.uiState.first { it is TrainUiState.Content } as TrainUiState.Content
            assertEquals(sessionId, home.inProgress?.sessionId)
            assertEquals("Pecho", home.inProgress?.routineName)

            // ...y la pantalla de sesión reanuda con lo tecleado.
            val resumed = with(second) { workoutViewModel(sessionId) }
            val content = resumed.uiState.first { it is WorkoutUiState.Content } as WorkoutUiState.Content
            val press = content.blocks.single().exercises.first { it.exerciseId == "ex-press" }
            assertEquals(listOf(82.5, 80.0, null), press.sets.map { it.weightKg })
            assertEquals(listOf(8, 10, null), press.sets.map { it.reps })
            assertEquals(listOf(true, false, false), press.sets.map { it.isDone })
        } finally {
            second.db.close()
        }
    }
}
