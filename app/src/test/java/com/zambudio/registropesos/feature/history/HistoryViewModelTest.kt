package com.zambudio.registropesos.feature.history

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.feature.history.detail.SessionDetailEvent
import com.zambudio.registropesos.feature.history.detail.SessionDetailUiState
import com.zambudio.registropesos.feature.history.detail.SessionDetailViewModel
import com.zambudio.registropesos.feature.history.list.HistoryUiState
import com.zambudio.registropesos.feature.history.list.HistoryViewModel
import com.zambudio.registropesos.feature.workout.InMemoryWorkoutRepository
import com.zambudio.registropesos.feature.workout.InMemoryWorkoutRepository.Companion.emptySet
import com.zambudio.registropesos.feature.workout.MainDispatcherRule
import com.zambudio.registropesos.feature.workout.collectInBackground
import com.zambudio.registropesos.feature.workout.routeHandle
import kotlinx.coroutines.test.TestScope
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
class HistoryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repo = InMemoryWorkoutRepository()

    @Before
    fun setUp() {
        repo.addSession("old", "Pierna", 1_000L, SessionStatus.FINISHED, finishedAt = 1_000L + 2_700_000L)
        repo.addSession("new", "Pecho - Tríceps", 9_000L, SessionStatus.FINISHED, finishedAt = 9_000L + 3_600_000L)
        repo.addSession("running", "Espalda", startedAt = 20_000L)
        repo.addSets(
            emptySet("o0", "old", "squat", "Sentadilla", "Cuádriceps", 0, 0, weightKg = 100.0, reps = 5),
            emptySet("n0", "new", "press", "Press banca", "Pecho", 0, 0, weightKg = 80.0, reps = 8),
            emptySet("n1", "new", "press", "Press banca", "Pecho", 0, 1, weightKg = 80.0, reps = 6),
            emptySet("n2", "new", "pushdown", "Polea", "Tríceps", 1, 0, weightKg = null, reps = 10),
        )
    }

    private fun TestScope.historyViewModel() = HistoryViewModel(repo).also { vm ->
        collectInBackground(vm.uiState)
    }

    private fun TestScope.detailViewModel(sessionId: String, events: MutableList<SessionDetailEvent>) =
        SessionDetailViewModel(routeHandle("sessionId" to sessionId), repo).also { vm ->
            collectInBackground(vm.uiState)
            collectInBackground(vm.events) { events += it }
        }

    @Test
    fun listsFinishedSessionsMostRecentFirst() = runTest {
        val vm = historyViewModel()

        val sessions = (vm.uiState.value as HistoryUiState.Content).sessions
        assertEquals(listOf("new", "old"), sessions.map { it.sessionId })
        val latest = sessions[0]
        assertEquals("Pecho - Tríceps", latest.routineName)
        assertEquals(9_000L, latest.startedAt)
        assertEquals(3_600_000L, latest.durationMs)
        assertEquals(2, latest.exerciseCount)
        assertEquals(80.0 * 8 + 80.0 * 6, latest.volumeKg, 0.001)
    }

    @Test
    fun emptyHistoryIsEmptyContent() = runTest {
        repo.sessions.value = emptyMap()
        val vm = historyViewModel()

        assertTrue((vm.uiState.value as HistoryUiState.Content).sessions.isEmpty())
    }

    @Test
    fun detailShowsEverySetWithE1rm() = runTest {
        val vm = detailViewModel("new", mutableListOf())

        val content = vm.uiState.value as SessionDetailUiState.Content
        assertEquals("Pecho - Tríceps", content.routineName)
        assertEquals(3_600_000L, content.durationMs)
        assertEquals(listOf("Press banca", "Polea"), content.exercises.map { it.name })
        val press = content.exercises[0]
        assertEquals(listOf(1, 2), press.sets.map { it.number })
        assertEquals(listOf(80.0, 80.0), press.sets.map { it.weightKg })
        assertEquals(listOf(8, 6), press.sets.map { it.reps })
        // Epley redondeado a 0,5: 80 × (1 + 8/30) = 101,33 -> 101,5; 80 × 1,2 = 96.
        assertEquals(listOf(101.5, 96.0), press.sets.map { it.e1rmKg })
        assertEquals(0.0, content.exercises[1].sets[0].e1rmKg, 0.0)
    }

    @Test
    fun deletingSessionRemovesItFromHistoryAndGoesBack() = runTest {
        val history = historyViewModel()
        val events = mutableListOf<SessionDetailEvent>()
        val detail = detailViewModel("new", events)

        detail.delete()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.Deleted), events)
        assertNull(repo.sessions.value["new"])
        assertTrue(repo.setsOf("new").isEmpty())
        assertEquals(SessionDetailUiState.Loading, detail.uiState.value)
        assertEquals(listOf("old"), (history.uiState.value as HistoryUiState.Content).sessions.map { it.sessionId })
    }

    @Test
    fun failedDeleteKeepsSessionAndReportsIt() = runTest {
        repo.failDelete = true
        val events = mutableListOf<SessionDetailEvent>()
        val detail = detailViewModel("new", events)

        detail.delete()

        assertEquals(listOf<SessionDetailEvent>(SessionDetailEvent.DeleteFailed), events)
        assertFalse((detail.uiState.value as SessionDetailUiState.Content).isDeleting)
        assertTrue("new" in repo.sessions.value)
    }

    @Test
    fun missingSessionIsNotFound() = runTest {
        val detail = detailViewModel("nope", mutableListOf())

        assertEquals(SessionDetailUiState.NotFound, detail.uiState.value)
    }
}
