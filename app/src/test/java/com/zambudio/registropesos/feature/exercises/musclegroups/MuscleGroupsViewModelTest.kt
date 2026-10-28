package com.zambudio.registropesos.feature.exercises.musclegroups

import com.zambudio.registropesos.R
import com.zambudio.registropesos.feature.exercises.CountingIds
import com.zambudio.registropesos.feature.exercises.FakeExerciseRepository
import com.zambudio.registropesos.feature.exercises.FakeMuscleGroupRepository
import com.zambudio.registropesos.feature.exercises.FixedClock
import com.zambudio.registropesos.feature.exercises.UiMessage
import com.zambudio.registropesos.feature.exercises.exercise
import com.zambudio.registropesos.feature.exercises.group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MuscleGroupsViewModelTest {
    private val exercises = FakeExerciseRepository(
        listOf(
            exercise("e1", "Press banca", "g1"),
            exercise("e2", "Aperturas", "g1"),
            exercise("e3", "Remo", "g2", archived = true),
        ),
    )
    private val repository = FakeMuscleGroupRepository(
        listOf(group("g1", "Pecho", 0), group("g2", "Espalda", 1), group("g3", "Cardio", 2)),
        exercises,
    )
    private val messages = mutableListOf<UiMessage>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(): MuscleGroupsViewModel {
        val viewModel = MuscleGroupsViewModel(repository, CountingIds(), FixedClock(now = 7_000L))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { event -> messages += (event as MuscleGroupsEvent.ShowMessage).message }
        }
        return viewModel
    }

    @Test
    fun listsGroupsInOrderWithActiveExerciseCounts() = runTest {
        val viewModel = newViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Pecho", "Espalda", "Cardio"), state.groups.map { it.name })
        // El ejercicio archivado de Espalda no cuenta.
        assertEquals(listOf(2, 0, 0), state.groups.map { it.exerciseCount })
    }

    @Test
    fun createAddsGroupAtTheEnd() = runTest {
        val viewModel = newViewModel()
        viewModel.onCreateClick()

        viewModel.onEditorSubmit("  Antebrazos ")

        assertNull(viewModel.uiState.value.editor)
        val created = repository.groups.value.single { it.id == "new-1" }
        assertEquals("Antebrazos", created.name)
        assertEquals(3, created.sortOrder)
        assertFalse(created.isDefault)
        assertEquals(7_000L, created.createdAt)
    }

    @Test
    fun emptyNameIsRejected() = runTest {
        val viewModel = newViewModel()
        viewModel.onCreateClick()

        viewModel.onEditorSubmit("   ")

        assertEquals(GroupNameError.EMPTY, viewModel.uiState.value.editor?.error)
        assertEquals(3, repository.groups.value.size)

        viewModel.onEditorInputChange()
        assertNull(viewModel.uiState.value.editor?.error)
    }

    @Test
    fun duplicateNameIgnoringCaseIsRejectedBeforeCallingRepository() = runTest {
        val viewModel = newViewModel()
        viewModel.onCreateClick()

        viewModel.onEditorSubmit("PECHO")

        assertEquals(GroupNameError.DUPLICATE, viewModel.uiState.value.editor?.error)
        assertEquals(3, repository.groups.value.size)
    }

    @Test
    fun renameToAnotherGroupsNameIsDuplicateButKeepingOwnNameIsAllowed() = runTest {
        val viewModel = newViewModel()

        viewModel.onRenameClick("g2", "Espalda")
        viewModel.onEditorSubmit("cardio")
        assertEquals(GroupNameError.DUPLICATE, viewModel.uiState.value.editor?.error)

        viewModel.onEditorSubmit("ESPALDA")
        assertNull(viewModel.uiState.value.editor)
        assertEquals("ESPALDA", repository.groups.value.single { it.id == "g2" }.name)
    }

    @Test
    fun moveUpAndDownReorder() = runTest {
        val viewModel = newViewModel()

        viewModel.onMoveUp("g3")
        assertEquals(listOf("g1", "g3", "g2"), repository.reorderCalls.last())
        assertEquals(listOf("Pecho", "Cardio", "Espalda"), viewModel.uiState.value.groups.map { it.name })

        viewModel.onMoveDown("g1")
        assertEquals(listOf("g3", "g1", "g2"), repository.reorderCalls.last())

        // Mover el primero hacia arriba no hace nada.
        viewModel.onMoveUp("g3")
        assertEquals(2, repository.reorderCalls.size)
    }

    @Test
    fun deletingGroupWithExercisesFailsWithReason() = runTest {
        val viewModel = newViewModel()

        viewModel.onDelete("g2")

        assertEquals(3, repository.groups.value.size)
        assertEquals(listOf(UiMessage(R.string.exercises_groups_delete_in_use, "Espalda")), messages)
    }

    @Test
    fun deletingEmptyGroupSucceeds() = runTest {
        val viewModel = newViewModel()

        viewModel.onDelete("g3")

        assertTrue(repository.groups.value.none { it.id == "g3" })
        assertEquals(listOf(UiMessage(R.string.exercises_groups_deleted)), messages)
        assertEquals(listOf("Pecho", "Espalda"), viewModel.uiState.value.groups.map { it.name })
    }
}
