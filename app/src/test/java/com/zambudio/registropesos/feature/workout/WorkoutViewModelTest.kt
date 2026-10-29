package com.zambudio.registropesos.feature.workout

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.usecase.DiscardSession
import com.zambudio.registropesos.domain.usecase.FakeProgressRepository
import com.zambudio.registropesos.domain.usecase.FinishSession
import com.zambudio.registropesos.domain.usecase.PreviousSessionForExercise
import com.zambudio.registropesos.feature.exercises.FakeExerciseRepository
import com.zambudio.registropesos.feature.exercises.FakeImageStore
import com.zambudio.registropesos.feature.exercises.exercise
import com.zambudio.registropesos.feature.workout.InMemoryWorkoutRepository.Companion.emptySet
import com.zambudio.registropesos.feature.workout.session.WorkoutEvent
import com.zambudio.registropesos.feature.workout.session.WorkoutExerciseUi
import com.zambudio.registropesos.feature.workout.session.WorkoutUiState
import com.zambudio.registropesos.feature.workout.session.WorkoutViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric: SavedStateHandle.toRoute() necesita android.os.Bundle.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WorkoutViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = TestClock(5_000_000L)
    private lateinit var repo: InMemoryWorkoutRepository
    private val library = FakeExerciseRepository(
        listOf(
            exercise("press", "Press banca", "g-Pecho"),
            exercise("fly", "Aperturas", "g-Pecho"),
            exercise("dips", "Fondos", "g-Pecho", imagePath = "exercise_dips.jpg"),
            exercise("pushdown", "Polea", "g-Tríceps"),
        ),
    )
    private lateinit var sessionId: String
    private val events = mutableListOf<WorkoutEvent>()

    @Before
    fun setUp() {
        repo = InMemoryWorkoutRepository()
        sessionId = "s1"
        repo.addSession(sessionId, routineName = "Pecho - Tríceps", startedAt = 4_000_000L)
        // Press banca (3 series) y Aperturas (2 series) en Pecho; Polea (1 serie) en Tríceps.
        repo.addSets(
            emptySet("p0", sessionId, "press", "Press banca", "Pecho", 0, 0),
            emptySet("p1", sessionId, "press", "Press banca", "Pecho", 0, 1),
            emptySet("p2", sessionId, "press", "Press banca", "Pecho", 0, 2),
            emptySet("a0", sessionId, "fly", "Aperturas", "Pecho", 1, 0),
            emptySet("a1", sessionId, "fly", "Aperturas", "Pecho", 1, 1),
            emptySet("t0", sessionId, "pushdown", "Polea", "Tríceps", 2, 0),
        )
    }

    private fun TestScope.viewModel(
        settings: AppSettings = AppSettings(),
        applicationScope: CoroutineScope = immediateApplicationScope(),
    ): WorkoutViewModel {
        val vm = WorkoutViewModel(
            savedStateHandle = routeHandle("sessionId" to sessionId),
            workoutRepository = repo,
            exerciseRepository = library,
            settingsRepository = FakeSettingsRepository(settings),
            imageStore = FakeImageStore(),
            previousSessionForExercise = PreviousSessionForExercise(repo),
            finishSession = FinishSession(repo, FakeProgressRepository(emptyList()), clock),
            discardSession = DiscardSession(repo),
            timeProvider = clock,
            idGenerator = TestIds("new"),
            applicationScope = applicationScope,
        )
        collectInBackground(vm.uiState)
        collectInBackground(vm.events) { events += it }
        return vm
    }

    private fun WorkoutViewModel.content(): WorkoutUiState.Content = uiState.value as WorkoutUiState.Content

    private fun WorkoutViewModel.exercise(id: String): WorkoutExerciseUi =
        content().blocks.flatMap { it.exercises }.single { it.exerciseId == id }

    @Test
    fun loadsSessionGroupedByMuscleGroupAndOpensFirstExercise() = runTest {
        val vm = viewModel()

        val content = vm.content()
        assertEquals("Pecho - Tríceps", content.routineName)
        assertEquals(4_000_000L, content.startedAt)
        assertEquals(listOf("Pecho", "Tríceps"), content.blocks.map { it.muscleGroupName })
        assertEquals(listOf("press", "fly"), content.blocks[0].exercises.map { it.exerciseId })
        assertEquals(listOf(1, 2, 3), vm.exercise("press").sets.map { it.number })
        assertEquals("press", content.expandedExerciseId)
        assertFalse(content.hasAnyData)
    }

    @Test
    fun editingWeightAndRepsPersistsEveryChange() = runTest {
        val vm = viewModel()

        vm.onWeightChange("p0", "8")
        vm.onWeightChange("p0", "82")
        vm.onWeightChange("p0", "82,")
        vm.onWeightChange("p0", "82,5")
        vm.onRepsChange("p0", "1")
        vm.onRepsChange("p0", "10")

        // "82," es 82 (no cambia nada); cada cambio real es una escritura, en orden.
        assertEquals(
            listOf(8.0, 82.0, 82.5, 82.5, 82.5),
            repo.writes.map { it.single().weightKg },
        )
        assertEquals(listOf(null, null, null, 1, 10), repo.writes.map { it.single().reps })
        assertEquals(82.5, repo.set("p0").weightKg)
        assertEquals(10, repo.set("p0").reps)
        assertEquals(82.5, vm.exercise("press").sets[0].weightKg)
        assertTrue(vm.content().hasAnyData)
    }

    @Test
    fun weightAcceptsDotAndInvalidOrBlankTextIsHandled() = runTest {
        val vm = viewModel()

        vm.onWeightChange("p0", "77.5")
        assertEquals(77.5, repo.set("p0").weightKg)

        val writesBefore = repo.writes.size
        vm.onWeightChange("p0", ",")
        vm.onWeightChange("p0", "-5")
        vm.onWeightChange("p0", "5000")
        vm.onRepsChange("p0", "1,5")
        assertEquals(writesBefore, repo.writes.size)
        assertEquals(77.5, repo.set("p0").weightKg)

        vm.onWeightChange("p0", "")
        assertNull(repo.set("p0").weightKg)
    }

    @Test
    fun stepButtonsUseSettingsStepAndNeverGoBelowZero() = runTest {
        val vm = viewModel()

        vm.onStepWeight("p0", increase = true)
        assertEquals(2.5, repo.set("p0").weightKg)
        vm.onStepWeight("p0", increase = false)
        assertEquals(0.0, repo.set("p0").weightKg)
        val writes = repo.writes.size
        vm.onStepWeight("p0", increase = false)
        assertEquals(0.0, repo.set("p0").weightKg)
        assertEquals("sin cambio no se escribe", writes, repo.writes.size)

        vm.onWeightChange("p1", "80")
        vm.onStepWeight("p1", increase = true)
        assertEquals(82.5, repo.set("p1").weightKg)
        vm.onWeightChange("p2", "1")
        vm.onStepWeight("p2", increase = false)
        assertEquals(0.0, repo.set("p2").weightKg)
    }

    @Test
    fun stepButtonsHonourCustomStep() = runTest {
        val vm = viewModel(AppSettings(weightStepKg = 1.25))

        vm.onWeightChange("p0", "20")
        vm.onStepWeight("p0", increase = true)
        vm.onStepWeight("p0", increase = true)

        assertEquals(22.5, repo.set("p0").weightKg)
    }

    @Test
    fun stepFromEmptyStartsAtPreviousSessionWeight() = runTest {
        repo.lastFinished["press"] = listOf(emptySet("old0", "old", "press", "Press banca", "Pecho", 0, 0, 80.0, 8))
        val vm = viewModel()

        vm.onStepWeight("p0", increase = true)

        assertEquals(82.5, repo.set("p0").weightKg)
    }

    @Test
    fun ghostShowsPreviousSessionPerSetPosition() = runTest {
        // La sesión anterior tuvo una serie vacía descartada: los huecos de setIndex no desplazan el gris.
        repo.lastFinished["press"] = listOf(
            emptySet("old0", "old", "press", "Press banca", "Pecho", 0, 0, 80.0, 8),
            emptySet("old2", "old", "press", "Press banca", "Pecho", 0, 2, 77.5, 6),
        )
        repo.lastFinished["fly"] = listOf(emptySet("oldf", "old", "fly", "Aperturas", "Pecho", 1, 0, 14.0, 12))
        val vm = viewModel()

        val press = vm.exercise("press")
        assertEquals(true, press.hasPrevious)
        assertEquals(listOf(80.0, 77.5, null), press.sets.map { it.ghostWeightKg })
        assertEquals(listOf(8, 6, null), press.sets.map { it.ghostReps })
        assertNull("aún no abierto: no se consulta", vm.exercise("fly").hasPrevious)

        vm.onToggleExercise("fly")

        assertEquals("fly", vm.content().expandedExerciseId)
        assertEquals(14.0, vm.exercise("fly").sets[0].ghostWeightKg)
        assertEquals(12, vm.exercise("fly").sets[0].ghostReps)

        vm.onToggleExercise("pushdown")
        assertEquals(false, vm.exercise("pushdown").hasPrevious)
        vm.onToggleExercise("pushdown")
        assertNull(vm.content().expandedExerciseId)
    }

    @Test
    fun copyPreviousFillsEmptyFieldsAndCreatesMissingRows() = runTest {
        repo.lastFinished["fly"] = listOf(
            emptySet("o0", "old", "fly", "Aperturas", "Pecho", 1, 0, 14.0, 12),
            emptySet("o1", "old", "fly", "Aperturas", "Pecho", 1, 1, 14.0, 10),
            emptySet("o2", "old", "fly", "Aperturas", "Pecho", 1, 2, 12.0, 10),
        )
        val vm = viewModel()
        vm.onToggleExercise("fly")
        vm.onRepsChange("a1", "11")

        vm.onCopyPrevious("fly")

        val fly = repo.setsOf(sessionId).filter { it.exerciseId == "fly" }
        assertEquals(listOf(0, 1, 2), fly.map { it.setIndex })
        assertEquals(listOf(14.0, 14.0, 12.0), fly.map { it.weightKg })
        assertEquals("no pisa lo ya tecleado", listOf(12, 11, 10), fly.map { it.reps })
        assertEquals("Aperturas", fly[2].exerciseNameSnapshot)
        assertEquals(1, fly[2].exerciseOrder)
        assertEquals(3, vm.exercise("fly").sets.size)
    }

    @Test
    fun addAndRemoveSets() = runTest {
        val vm = viewModel()

        vm.onAddSet("press")
        var press = repo.setsOf(sessionId).filter { it.exerciseId == "press" }
        assertEquals(listOf(0, 1, 2, 3), press.map { it.setIndex })
        assertEquals("new-1", press[3].id)
        assertEquals(4, vm.exercise("press").sets.size)

        vm.onRemoveSet("press")
        press = repo.setsOf(sessionId).filter { it.exerciseId == "press" }
        assertEquals(listOf("p0", "p1", "p2"), press.map { it.id })

        // Quitar una serie intermedia renumera las siguientes.
        vm.onWeightChange("p2", "60")
        vm.onRemoveSet("press", "p1")
        press = repo.setsOf(sessionId).filter { it.exerciseId == "press" }
        assertEquals(listOf("p0", "p2"), press.map { it.id })
        assertEquals(listOf(0, 1), press.map { it.setIndex })
        assertEquals(60.0, press[1].weightKg)

        // Siempre queda al menos una serie.
        vm.onRemoveSet("pushdown")
        assertEquals(1, repo.setsOf(sessionId).count { it.exerciseId == "pushdown" })
        assertFalse(vm.exercise("pushdown").canRemoveSet)
    }

    @Test
    fun removingSetWithDataOffersUndoThatRestoresIt() = runTest {
        val vm = viewModel()
        vm.onWeightChange("p1", "80")
        vm.onRepsChange("p1", "8")
        vm.onWeightChange("p2", "70")
        val original = repo.set("p1")

        vm.onRemoveSet("press", "p1")

        assertEquals(listOf<WorkoutEvent>(WorkoutEvent.SetRemoved("p1")), events)
        assertEquals(listOf("p0", "p2"), repo.setsOf(sessionId).filter { it.exerciseId == "press" }.map { it.id })

        vm.onUndoRemoveSet("p1")

        val press = repo.setsOf(sessionId).filter { it.exerciseId == "press" }
        assertEquals(listOf("p0", "p1", "p2"), press.map { it.id })
        assertEquals(listOf(0, 1, 2), press.map { it.setIndex })
        assertEquals("mismo id y datos", original, repo.set("p1"))
        assertEquals(70.0, repo.set("p2").weightKg)
        // Se escribe de mayor a menor posición para no chocar con el índice único de Room.
        assertEquals(listOf("p2" to 2, "p1" to 1), repo.writes.last().map { it.id to it.setIndex })
        assertEquals(listOf("p0", "p1", "p2"), vm.exercise("press").sets.map { it.setId })
        assertEquals(listOf(null, 80.0, 70.0), vm.exercise("press").sets.map { it.weightKg })

        // Deshacer solo funciona una vez.
        vm.onUndoRemoveSet("p1")
        assertEquals(3, repo.setsOf(sessionId).count { it.exerciseId == "press" })
    }

    @Test
    fun emptySetIsRemovedWithoutUndo() = runTest {
        val vm = viewModel()

        vm.onRemoveSet("press")

        assertTrue("vacía: sin deshacer", events.isEmpty())
        vm.onUndoRemoveSet("p2")
        assertEquals(listOf("p0", "p1"), repo.setsOf(sessionId).filter { it.exerciseId == "press" }.map { it.id })
    }

    // --- Grupos de la sesión y selector de ejercicios ---

    @Test
    fun plannedGroupsAreShownFromTheStartEvenWithoutExercises() = runTest {
        repo.addSession("s2", comment = "Pecho: 4 · Tríceps: 2")
        repo.addGroups("s2", "Pecho", "Tríceps")
        sessionId = "s2"

        val vm = viewModel()

        val content = vm.content()
        assertEquals("Pecho: 4 · Tríceps: 2", content.reminder)
        assertEquals(listOf("Pecho", "Tríceps"), content.blocks.map { it.muscleGroupName })
        assertTrue(content.blocks.all { it.exercises.isEmpty() && it.canAddExercise })
        assertNull(content.expandedExerciseId)
        assertFalse(content.hasAnyData)
    }

    @Test
    fun sessionWithoutCommentHasNoReminder() = runTest {
        assertNull(viewModel().content().reminder)
    }

    @Test
    fun exercisesGoUnderTheirPlannedGroupInSessionOrder() = runTest {
        repo.addGroups(sessionId, "Tríceps", "Pecho")

        val content = viewModel().content()

        assertEquals(listOf("Tríceps", "Pecho"), content.blocks.map { it.muscleGroupName })
        assertEquals(listOf("pushdown"), content.blocks[0].exercises.map { it.exerciseId })
        assertEquals(listOf("press", "fly"), content.blocks[1].exercises.map { it.exerciseId })
    }

    @Test
    fun pickerListsGroupExercisesWithPhotoAndMarksTheOnesAlreadyAdded() = runTest {
        repo.addGroups(sessionId, "Pecho", "Tríceps")
        val vm = viewModel()

        vm.onOpenPicker("g-Pecho")

        val picker = vm.content().picker!!
        assertEquals("Pecho", picker.muscleGroupName)
        assertFalse(picker.isLoading)
        // El orden alfabético lo pone el DAO; el fake devuelve el de inserción.
        assertEquals(listOf("Press banca", "Aperturas", "Fondos"), picker.exercises.map { it.name })
        assertEquals(listOf(true, true, false), picker.exercises.map { it.alreadyAdded })
        assertEquals("exercise_dips.jpg", picker.exercises[2].imageFile?.name)

        vm.onDismissPicker()
        assertNull(vm.content().picker)
    }

    @Test
    fun pickingAnExerciseAddsDefaultEmptySetsAndOpensIt() = runTest {
        repo.addGroups(sessionId, "Pecho", "Tríceps")
        val vm = viewModel(settings = AppSettings(defaultSets = 3))

        vm.onOpenPicker("g-Pecho")
        vm.onPickExercise("dips")

        assertNull(vm.content().picker)
        val dips = repo.setsOf(sessionId).filter { it.exerciseId == "dips" }
        assertEquals(listOf(0, 1, 2), dips.map { it.setIndex })
        assertTrue(dips.all { it.weightKg == null && it.reps == null })
        assertEquals(setOf(3), dips.map { it.exerciseOrder }.toSet()) // después de los 3 que ya había
        assertEquals("Fondos", dips[0].exerciseNameSnapshot)
        assertEquals("Pecho", dips[0].muscleGroupNameSnapshot)
        assertEquals("dips", vm.content().expandedExerciseId)
        assertEquals(listOf("press", "fly", "dips"), vm.content().blocks[0].exercises.map { it.exerciseId })
        assertEquals("exercise_dips.jpg", vm.exercise("dips").imageFile?.name)
    }

    @Test
    fun severalExercisesCanBeAddedToOneGroupButNeverTheSameTwice() = runTest {
        repo.addSession("s2")
        repo.addGroups("s2", "Pecho")
        sessionId = "s2"
        val vm = viewModel(settings = AppSettings(defaultSets = 2))

        vm.onOpenPicker("g-Pecho")
        vm.onPickExercise("press")
        vm.onOpenPicker("g-Pecho")
        vm.onPickExercise("dips")
        vm.onOpenPicker("g-Pecho")
        vm.onPickExercise("press") // ya estaba: solo lo abre

        assertEquals(listOf("press", "dips"), vm.content().blocks.single().exercises.map { it.exerciseId })
        assertEquals(2, repo.setsOf("s2").count { it.exerciseId == "press" })
        assertEquals(listOf(0, 1), repo.setsOf("s2").map { it.exerciseOrder }.distinct())
        assertEquals("press", vm.content().expandedExerciseId)
    }

    @Test
    fun removingAnExerciseDeletesItsSetsAndUndoRestoresThem() = runTest {
        repo.addGroups(sessionId, "Pecho", "Tríceps")
        val vm = viewModel()
        vm.onWeightChange("a0", "14")
        vm.onRepsChange("a0", "12")
        val before = repo.setsOf(sessionId).filter { it.exerciseId == "fly" }

        vm.onRemoveExercise("fly")

        assertEquals(listOf<WorkoutEvent>(WorkoutEvent.ExerciseRemoved("fly")), events)
        assertTrue(repo.setsOf(sessionId).none { it.exerciseId == "fly" })
        assertEquals(listOf("press"), vm.content().blocks[0].exercises.map { it.exerciseId })

        vm.onUndoRemoveExercise("fly")

        assertEquals(before, repo.setsOf(sessionId).filter { it.exerciseId == "fly" })
        assertEquals(listOf("press", "fly"), vm.content().blocks[0].exercises.map { it.exerciseId })
        assertEquals("fly", vm.content().expandedExerciseId)

        // Deshacer solo funciona una vez.
        vm.onUndoRemoveExercise("fly")
        assertEquals(2, repo.setsOf(sessionId).count { it.exerciseId == "fly" })
    }

    @Test
    fun undoAfterAnotherExerciseTookThePositionPutsTheRestoredOneAtTheEnd() = runTest {
        repo.addGroups(sessionId, "Pecho", "Tríceps")
        val vm = viewModel()
        vm.onRemoveExercise("pushdown") // el último (orden 2)
        vm.onOpenPicker("g-Pecho")
        vm.onPickExercise("dips") // ocupa el orden 2

        vm.onUndoRemoveExercise("pushdown")

        val orders = repo.setsOf(sessionId).groupBy { it.exerciseId }.mapValues { (_, s) -> s.first().exerciseOrder }
        assertEquals(mapOf("press" to 0, "fly" to 1, "dips" to 2, "pushdown" to 3), orders)
        assertEquals(listOf("pushdown"), vm.content().blocks[1].exercises.map { it.exerciseId })
    }

    @Test
    fun exerciseOutsidePlannedGroupsIsShownAtTheEndWithoutAddButton() = runTest {
        repo.addGroups(sessionId, "Pecho")

        val content = viewModel().content()

        assertEquals(listOf("Pecho", "Tríceps"), content.blocks.map { it.muscleGroupName })
        assertTrue(content.blocks[0].canAddExercise)
        assertFalse(content.blocks[1].canAddExercise)
        assertEquals(listOf("pushdown"), content.blocks[1].exercises.map { it.exerciseId })
    }

    @Test
    fun referenceValuesAreExposedSeparatelyFromTodaysData() = runTest {
        repo.addSession("old", status = SessionStatus.FINISHED, startedAt = 1L, finishedAt = 2L)
        repo.lastFinished["press"] = listOf(
            emptySet("o0", "old", "press", "Press banca", "Pecho", 0, 0, 80.0, 8),
            emptySet("o1", "old", "press", "Press banca", "Pecho", 0, 1, 77.5, 6),
        )
        val vm = viewModel()
        vm.onWeightChange("p0", "85")

        val rows = vm.exercise("press").sets
        assertEquals(85.0, rows[0].weightKg)
        assertEquals(80.0, rows[0].ghostWeightKg)
        assertNull(rows[1].weightKg)
        assertEquals(77.5, rows[1].ghostWeightKg)
        assertTrue(vm.exercise("press").showsReference)

        // Copiar convierte la referencia en datos de hoy (guardados), sin pisar lo tecleado.
        vm.onCopyPrevious("press")
        val copied = vm.exercise("press").sets
        assertEquals(listOf(85.0, 77.5, null), copied.map { it.weightKg })
        assertEquals(listOf(8, 6, null), copied.map { it.reps })
        assertEquals(77.5, repo.set("p1").weightKg)
        assertEquals(6, repo.set("p1").reps)
    }

    @Test
    fun finishDropsEmptySetsAndOpensSummary() = runTest {
        val vm = viewModel()
        vm.onWeightChange("p0", "80")
        vm.onRepsChange("p0", "8")
        vm.onWeightChange("t0", "25")
        vm.onRepsChange("t0", "12")

        vm.onFinish()

        assertEquals(listOf<WorkoutEvent>(WorkoutEvent.OpenSummary(sessionId)), events)
        val session = checkNotNull(repo.sessions.value[sessionId])
        assertEquals(SessionStatus.FINISHED, session.status)
        assertEquals(5_000_000L, session.finishedAt)
        assertEquals(listOf("p0", "t0"), repo.setsOf(sessionId).map { it.id })
        assertTrue(vm.content().isClosing)

        // Tras terminar no se aceptan más ediciones.
        val writes = repo.writes.size
        vm.onWeightChange("p0", "90")
        assertEquals(writes, repo.writes.size)
    }

    @Test
    fun discardDeletesSessionAndReturns() = runTest {
        val vm = viewModel()
        vm.onWeightChange("p0", "80")

        vm.onDiscard()

        assertEquals(listOf<WorkoutEvent>(WorkoutEvent.Discarded), events)
        assertNull(repo.sessions.value[sessionId])
        assertTrue(repo.setsOf(sessionId).isEmpty())
    }

    @Test
    fun failedWriteIsReportedAndScreenKeepsWorking() = runTest {
        val vm = viewModel()
        repo.failWrites = true

        vm.onWeightChange("p0", "80")

        assertEquals(listOf<WorkoutEvent>(WorkoutEvent.SaveFailed), events)
        assertEquals(80.0, vm.exercise("press").sets[0].weightKg)

        repo.failWrites = false
        vm.onRepsChange("p0", "8")
        assertEquals(80.0, repo.set("p0").weightKg)
        assertEquals(8, repo.set("p0").reps)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pendingWritesAreAppliedAfterScreenIsClosed() = runTest {
        // Scope de aplicación que aún no ha corrido: las escrituras se quedan en cola.
        val applicationScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val vm = viewModel(applicationScope = applicationScope)
        vm.onWeightChange("p0", "80")
        vm.onRepsChange("p0", "8")
        assertTrue("aún en cola", repo.writes.isEmpty())

        vm.clearAsIfScreenClosed()
        advanceUntilIdle()

        assertEquals(2, repo.writes.size)
        assertEquals(80.0, repo.set("p0").weightKg)
        assertEquals(8, repo.set("p0").reps)
        assertTrue("el consumidor termina al vaciar la cola", applicationScope.coroutineContext.job.children.none())
        applicationScope.cancel()
    }

    @Test
    fun sessionNoLongerInProgressShowsNotInProgress() = runTest {
        repo.addSession(sessionId, status = SessionStatus.FINISHED, finishedAt = 1L)

        val vm = viewModel()

        assertEquals(WorkoutUiState.NotInProgress, vm.uiState.value)
        vm.onWeightChange("p0", "80")
        assertTrue(repo.writes.isEmpty())
    }
}
