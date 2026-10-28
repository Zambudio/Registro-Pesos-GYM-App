package com.zambudio.registropesos.feature.exercises

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.core.theme.GymTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ExercisesScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val groups = listOf(MuscleGroupOption("g1", "Pecho"), MuscleGroupOption("g2", "Espalda"))

    private fun setExercisesScreen(
        state: ExercisesUiState,
        onExerciseClick: (String) -> Unit = {},
        onAddExercise: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            GymTheme {
                ExercisesScreen(
                    state = state,
                    query = "",
                    onQueryChange = {},
                    onShowArchivedChange = {},
                    onExerciseClick = onExerciseClick,
                    onAddExercise = onAddExercise,
                    onManageGroups = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun emptyLibraryShowsEmptyStateAndAddGoesThroughGroupPicker() {
        var addedTo: String? = null
        setExercisesScreen(
            state = ExercisesUiState(isLoading = false, isLibraryEmpty = true, groups = groups),
            onAddExercise = { addedTo = it },
        )

        composeRule.onNodeWithText("Aún no tienes ejercicios").assertIsDisplayed()
        composeRule.onAllNodesWithText("Añadir ejercicio").onFirst().performClick()
        composeRule.onNodeWithText("Elige grupo muscular").assertIsDisplayed()
        composeRule.onNodeWithText("Espalda").performClick()

        assertEquals("g2", addedTo)
    }

    @Test
    fun listShowsGroupHeadersAndExercises() {
        var opened: String? = null
        setExercisesScreen(
            state = ExercisesUiState(
                isLoading = false,
                groups = groups,
                sections = listOf(
                    ExerciseSection(
                        "g1",
                        "Pecho",
                        listOf(
                            ExerciseListItem("e1", "Press banca", null, isArchived = false),
                            ExerciseListItem("e2", "Pullover", null, isArchived = true),
                        ),
                    ),
                    ExerciseSection("g2", "Espalda", listOf(ExerciseListItem("e3", "Remo", null, isArchived = false))),
                ),
            ),
            onExerciseClick = { opened = it },
        )

        composeRule.onNodeWithText("Pecho").assertIsDisplayed()
        composeRule.onNodeWithText("Espalda").assertIsDisplayed()
        composeRule.onNodeWithText("Archivado").assertIsDisplayed()
        composeRule.onNodeWithText("Remo").performClick()

        assertEquals("e3", opened)
    }

    @Test
    fun noSearchResultsShowsMessage() {
        setExercisesScreen(state = ExercisesUiState(isLoading = false, groups = groups))

        composeRule.onNodeWithText("Sin resultados").assertIsDisplayed()
    }

    @Test
    fun newExerciseFlowSavesWithoutCrashing() {
        val exercises = FakeExerciseRepository()
        val viewModel = ExerciseEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("muscleGroupId" to "g1")),
            exerciseRepository = exercises,
            muscleGroupRepository = FakeMuscleGroupRepository(
                listOf(group("g1", "Pecho", 0), group("g2", "Espalda", 1)),
            ),
            imageStore = FakeImageStore(),
            ids = CountingIds(),
            time = FixedClock(),
            io = Dispatchers.Unconfined,
        )
        var closed = false
        composeRule.setContent {
            GymTheme { ExerciseEditorRoute(onClose = { closed = true }, viewModel = viewModel) }
        }

        // Guardar sin nombre: error inline.
        composeRule.onNodeWithText("Guardar").performScrollTo().performClick()
        composeRule.onNodeWithText("El nombre es obligatorio").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Nombre").performScrollTo().performTextInput("Fondos")
        composeRule.onNodeWithText("Pecho").performScrollTo().performClick()
        composeRule.onNodeWithText("Espalda").performClick()
        // Lanzar galería y cámara no debe romper aunque no haya apps que respondan.
        composeRule.onNodeWithText("Galería").performScrollTo().performClick()
        composeRule.onNodeWithText("Cámara").performScrollTo().performClick()
        assertEquals("Fondos", viewModel.uiState.value.name)
        assertEquals("g2", viewModel.uiState.value.muscleGroupId)
        // Puede haber un Snackbar encima del botón: se invoca la acción de clic directamente.
        composeRule.onNodeWithText("Guardar").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        val saved = exercises.upserts.single()
        assertEquals("Fondos", saved.name)
        assertEquals("g2", saved.muscleGroupId)
        assertTrue(closed)
    }

    @Test
    fun archivingExistingExerciseAsksForConfirmation() {
        val exercises = FakeExerciseRepository(listOf(exercise("e1", "Press banca", "g1")))
        val viewModel = ExerciseEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("exerciseId" to "e1")),
            exerciseRepository = exercises,
            muscleGroupRepository = FakeMuscleGroupRepository(listOf(group("g1", "Pecho", 0))),
            imageStore = FakeImageStore(),
            ids = CountingIds(),
            time = FixedClock(),
            io = Dispatchers.Unconfined,
        )
        composeRule.setContent {
            GymTheme { ExerciseEditorRoute(onClose = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithContentDescription("Archivar").performClick()
        composeRule.onNodeWithText("¿Archivar ejercicio?").assertIsDisplayed()
        composeRule.onAllNodesWithText("Archivar").onFirst().performClick()
        composeRule.waitForIdle()

        assertTrue(exercises.exercises.value.single().isArchived)
        composeRule.onNodeWithText("Este ejercicio está archivado.").assertIsDisplayed()
    }
}
