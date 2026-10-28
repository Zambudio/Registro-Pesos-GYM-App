package com.zambudio.registropesos.feature.workout

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.usecase.FakeProgressRepository
import com.zambudio.registropesos.domain.usecase.FinishSession
import com.zambudio.registropesos.domain.usecase.RecordType
import com.zambudio.registropesos.feature.workout.InMemoryWorkoutRepository.Companion.emptySet
import com.zambudio.registropesos.feature.workout.summary.WorkoutSummaryUiState
import com.zambudio.registropesos.feature.workout.summary.WorkoutSummaryViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric: SavedStateHandle.toRoute() necesita android.os.Bundle.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WorkoutSummaryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun summaryMatchesFinishSessionResult() = runTest {
        val clock = TestClock(3_600_000L + 1_000L)
        val repo = InMemoryWorkoutRepository()
        repo.addSession("s1", routineName = "Pecho - Tríceps", startedAt = 1_000L)
        repo.addSets(
            emptySet("p0", "s1", "press", "Press banca", "Pecho", 0, 0, weightKg = 85.0, reps = 5),
            emptySet("p1", "s1", "press", "Press banca", "Pecho", 0, 1, weightKg = 80.0, reps = 8),
            emptySet("p2", "s1", "press", "Press banca", "Pecho", 0, 2),
            emptySet("t0", "s1", "pushdown", "Polea", "Tríceps", 1, 0, weightKg = 20.0, reps = 12),
        )
        val previous = listOf(FlatSet("press", "old", 80.0, 8, 0L), FlatSet("pushdown", "old", 30.0, 12, 0L))
        val progress = FakeProgressRepository(previous)
        val finished = FinishSession(repo, progress, clock)("s1").getOrThrow()

        val vm = WorkoutSummaryViewModel(routeHandle("sessionId" to "s1"), repo, progress)
        collectInBackground(vm.uiState)

        val content = vm.uiState.value as WorkoutSummaryUiState.Content
        assertEquals(SessionStatus.FINISHED, repo.sessions.value.getValue("s1").status)
        assertEquals("Pecho - Tríceps", content.routineName)
        assertEquals(finished.durationMs, content.durationMs)
        assertEquals(3_600_000L, content.durationMs)
        assertEquals(finished.exerciseCount, content.exerciseCount)
        assertEquals(finished.setCount, content.setCount)
        assertEquals(3, content.setCount)
        assertEquals(finished.volumeKg, content.volumeKg, 0.001)
        assertEquals(85.0 * 5 + 80.0 * 8 + 20.0 * 12, content.volumeKg, 0.001)
        assertEquals(finished.newRecords, content.newRecords)
        assertEquals(listOf(RecordType.WEIGHT), content.newRecords.map { it.type })
        assertEquals(80.0, content.newRecords[0].previousValueKg, 0.0)
        assertEquals(85.0, content.newRecords[0].newValueKg, 0.0)
    }

    @Test
    fun sessionNotFinishedIsAnError() = runTest {
        val repo = InMemoryWorkoutRepository()
        repo.addSession("s1")

        val vm = WorkoutSummaryViewModel(routeHandle("sessionId" to "s1"), repo, FakeProgressRepository(emptyList()))
        collectInBackground(vm.uiState)

        assertEquals(WorkoutSummaryUiState.Error, vm.uiState.value)
    }
}
