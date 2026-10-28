package com.zambudio.registropesos.feature.exercises

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class ExercisesViewModelTest {
    private val groups = listOf(group("g2", "Espalda", 1), group("g1", "Pecho", 0), group("g3", "Cardio", 2))
    private val exercises = FakeExerciseRepository(
        listOf(
            exercise("e1", "Press banca", "g1"),
            exercise("e2", "Aperturas", "g1"),
            exercise("e3", "Remo con barra", "g2", imagePath = "exercise_e3.jpg"),
            exercise("e4", "Jalón al pecho", "g2"),
            exercise("e5", "Pullover", "g1", archived = true),
        ),
    )
    private val imageStore = FakeImageStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ExercisesViewModel(exercises, FakeMuscleGroupRepository(groups), imageStore)

    @Test
    fun groupsExercisesBySortOrderAndHidesArchived() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        // Cardio no tiene ejercicios: no aparece. Pecho (0) antes que Espalda (1).
        assertEquals(listOf("Pecho", "Espalda"), state.sections.map { it.groupName })
        assertEquals(listOf("Aperturas", "Press banca"), state.sections[0].exercises.map { it.name })
        assertEquals(listOf("Jalón al pecho", "Remo con barra"), state.sections[1].exercises.map { it.name })
        assertEquals(listOf("Pecho", "Espalda", "Cardio"), state.groups.map { it.name })
        assertEquals("exercise_e3.jpg", state.sections[1].exercises[1].imageFile?.name)
        assertNull(state.sections[1].exercises[0].imageFile)
        assertFalse(state.isLibraryEmpty)
    }

    @Test
    fun searchIgnoresCaseAndAccents() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onQueryChange("  JALON ")
        assertEquals(listOf("Jalón al pecho"), names(viewModel))

        viewModel.onQueryChange("PRÉS")
        assertEquals(listOf("Press banca"), names(viewModel))

        viewModel.onQueryChange("sentadilla")
        assertTrue(viewModel.uiState.value.sections.isEmpty())
        assertFalse(viewModel.uiState.value.isLibraryEmpty)
    }

    @Test
    fun showArchivedIncludesArchivedExercises() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onShowArchivedChange(true)

        val state = viewModel.uiState.value
        assertTrue(state.showArchived)
        val pecho = state.sections.first { it.groupId == "g1" }.exercises
        assertEquals(listOf("Aperturas", "Press banca", "Pullover"), pecho.map { it.name })
        assertTrue(pecho.last().isArchived)
    }

    @Test
    fun emptyLibraryIsFlagged() = runTest {
        exercises.exercises.value = emptyList()
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.isLibraryEmpty)
        assertTrue(viewModel.uiState.value.sections.isEmpty())
    }

    @Test
    fun newExercisesAppearReactively() = runTest {
        val viewModel = newViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        exercises.upsert(exercise("e6", "Elíptica", "g3"))

        assertEquals(listOf("Pecho", "Espalda", "Cardio"), viewModel.uiState.value.sections.map { it.groupName })
    }

    private fun names(viewModel: ExercisesViewModel) =
        viewModel.uiState.value.sections.flatMap { section -> section.exercises.map { it.name } }

    @Test
    fun normalizeForSearchStripsAccents() {
        assertEquals("jalon al pecho", normalizeForSearch("  Jalón al PECHO "))
        assertEquals("pinon", normalizeForSearch("Piñón"))
    }
}
