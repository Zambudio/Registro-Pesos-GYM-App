package com.zambudio.registropesos.feature.workout

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.usecase.DiscardSession
import com.zambudio.registropesos.domain.usecase.StartSession
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailEvent
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailUiState
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailViewModel
import com.zambudio.registropesos.feature.workout.train.InProgressSessionUi
import com.zambudio.registropesos.feature.workout.train.TrainUiState
import com.zambudio.registropesos.feature.workout.train.TrainViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric: SavedStateHandle.toRoute() necesita android.os.Bundle.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class TrainAndRoutineDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = TestClock(9_000L)
    private val workouts = InMemoryWorkoutRepository()
    private val pushDay = routineDetail(
        "r1",
        "Pecho - Tríceps",
        "Pecho" to listOf(Triple("press", "Press banca", 3), Triple("fly", "Aperturas", 2)),
        "Tríceps" to listOf(Triple("pushdown", "Polea", 2)),
    )
    private val emptyRoutine = routineDetail("r2", "Vacía")

    /** Rutina de la v2: solo grupos y comentario, sin ejercicios preseleccionados. */
    private val legDay = routineDetail("r3", "Día 3", "Pierna" to emptyList(), "Abdomen" to emptyList()).let {
        it.copy(routine = it.routine.copy(subtitle = "Pierna: 4 ejercicios · Abdomen: 2"))
    }
    private val routines = InMemoryRoutineRepository(pushDay, emptyRoutine, legDay)

    private fun TestScope.trainViewModel(): TrainViewModel {
        val vm = TrainViewModel(workouts, routines, DiscardSession(workouts))
        collectInBackground(vm.uiState)
        return vm
    }

    private fun TestScope.detailViewModel(
        routineId: String,
        events: MutableList<RoutineDetailEvent>,
    ): RoutineDetailViewModel {
        val vm = RoutineDetailViewModel(
            routeHandle("routineId" to routineId),
            routines,
            StartSession(routines, workouts, clock),
        )
        collectInBackground(vm.uiState)
        collectInBackground(vm.events) { events += it }
        return vm
    }

    @Test
    fun trainListsRoutinesAndOffersToResumeSessionInProgress() = runTest {
        val vm = trainViewModel()
        var content = vm.uiState.value as TrainUiState.Content
        assertNull(content.inProgress)
        assertEquals(listOf("Pecho - Tríceps", "Vacía", "Día 3"), content.routines.map { it.name })
        assertEquals(listOf("Pecho", "Tríceps"), content.routines[0].groupNames)

        workouts.addSession("s1", routineName = "Pecho - Tríceps", startedAt = 1_000L)

        content = vm.uiState.value as TrainUiState.Content
        assertEquals(InProgressSessionUi("s1", "Pecho - Tríceps", 1_000L), content.inProgress)
    }

    @Test
    fun trainShowsEmptyListWhenThereAreNoRoutines() = runTest {
        routines.details.value = emptyMap()
        val vm = trainViewModel()

        val content = vm.uiState.value as TrainUiState.Content
        assertTrue(content.routines.isEmpty())
    }

    @Test
    fun trainDiscardRemovesSessionInProgress() = runTest {
        workouts.addSession("s1")
        val vm = trainViewModel()

        vm.discardInProgress("s1")

        assertNull((vm.uiState.value as TrainUiState.Content).inProgress)
        assertTrue(workouts.sessions.value.isEmpty())
    }

    @Test
    fun startWorkoutCreatesSessionAndOpensIt() = runTest {
        val events = mutableListOf<RoutineDetailEvent>()
        val vm = detailViewModel("r1", events)
        val content = vm.uiState.value as RoutineDetailUiState.Content
        assertEquals(listOf("Pecho", "Tríceps"), content.groups.map { it.name })
        assertTrue(content.canStart)

        vm.startWorkout()

        val sessionId = (events.single() as RoutineDetailEvent.OpenWorkout).sessionId
        val session = workouts.sessions.value.getValue(sessionId)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(9_000L, session.startedAt)
        // Empieza sin ejercicios: con los grupos de la rutina, que se rellenan durante la sesión.
        assertTrue(workouts.setsOf(sessionId).isEmpty())
        assertEquals(listOf("Pecho", "Tríceps"), workouts.groups.value.map { it.muscleGroupName })
        assertFalse((vm.uiState.value as RoutineDetailUiState.Content).isStarting)
    }

    @Test
    fun routineWithOnlyGroupsAndCommentShowsThemAndStarts() = runTest {
        val events = mutableListOf<RoutineDetailEvent>()
        val vm = detailViewModel("r3", events)
        val content = vm.uiState.value as RoutineDetailUiState.Content
        assertEquals(listOf("Pierna", "Abdomen"), content.groups.map { it.name })
        assertEquals("Pierna: 4 ejercicios · Abdomen: 2", content.detail.routine.subtitle)
        assertTrue(content.canStart)

        vm.startWorkout()

        val sessionId = (events.single() as RoutineDetailEvent.OpenWorkout).sessionId
        val session = workouts.sessions.value.getValue(sessionId)
        assertEquals("Pierna: 4 ejercicios · Abdomen: 2", session.routineCommentSnapshot)
    }

    @Test
    fun startWithSessionInProgressOffersToGoToIt() = runTest {
        workouts.addSession("running")
        val events = mutableListOf<RoutineDetailEvent>()
        val vm = detailViewModel("r1", events)

        vm.startWorkout()

        assertTrue(events.isEmpty())
        assertEquals("running", (vm.uiState.value as RoutineDetailUiState.Content).conflictingSessionId)
        assertEquals(1, workouts.sessions.value.size)

        vm.dismissConflict()
        assertNull((vm.uiState.value as RoutineDetailUiState.Content).conflictingSessionId)
    }

    @Test
    fun routineWithoutGroupsCannotStartAndDeletedRoutineIsNotFound() = runTest {
        val events = mutableListOf<RoutineDetailEvent>()
        val empty = detailViewModel("r2", events)
        assertFalse((empty.uiState.value as RoutineDetailUiState.Content).canStart)

        val missing = detailViewModel("nope", events)
        assertEquals(RoutineDetailUiState.NotFound, missing.uiState.value)
    }
}
