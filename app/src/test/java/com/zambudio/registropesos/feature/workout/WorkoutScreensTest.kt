package com.zambudio.registropesos.feature.workout

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.domain.usecase.GhostSet
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailScreen
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailUiState
import com.zambudio.registropesos.feature.workout.session.ExercisePickerUi
import com.zambudio.registropesos.feature.workout.session.PickerExerciseUi
import com.zambudio.registropesos.feature.workout.session.WorkoutActions
import com.zambudio.registropesos.feature.workout.session.WorkoutBlockUi
import com.zambudio.registropesos.feature.workout.session.WorkoutExerciseUi
import com.zambudio.registropesos.feature.workout.session.WorkoutScreen
import com.zambudio.registropesos.feature.workout.session.WorkoutSetUi
import com.zambudio.registropesos.feature.workout.session.WorkoutUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class WorkoutScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val opened = mutableListOf<String>()
    private val picked = mutableListOf<String>()
    private val removed = mutableListOf<String>()
    private val actions = WorkoutActions(
        onOpenPicker = { opened += it },
        onPickExercise = { picked += it },
        onRemoveExercise = { removed += it },
    )

    private fun content(
        blocks: List<WorkoutBlockUi>,
        reminder: String? = "Pecho: 4 ejercicios · Tríceps: 2",
        expanded: String? = null,
        picker: ExercisePickerUi? = null,
    ) = WorkoutUiState.Content(
        sessionId = "s1",
        routineName = "Día 1",
        startedAt = 0L,
        reminder = reminder,
        blocks = blocks,
        expandedExerciseId = expanded,
        weightStepKg = 2.5,
        hasAnyData = false,
        isClosing = false,
        picker = picker,
    )

    private fun setWorkout(state: WorkoutUiState) {
        compose.setContent {
            GymTheme {
                WorkoutScreen(
                    state = state,
                    actions = actions,
                    onBack = {},
                    onFinish = {},
                    onDiscard = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun routineDetailShowsCommentAndGroupsBeforeStarting() {
        val detail = routineDetail("r3", "Día 3", "Pierna" to emptyList(), "Abdomen" to emptyList()).let {
            it.copy(routine = it.routine.copy(subtitle = "Pierna: 4 · Abdomen: 2"))
        }
        var started = 0
        compose.setContent {
            GymTheme {
                RoutineDetailScreen(
                    state = RoutineDetailUiState.Content(detail),
                    onBack = {},
                    onEditRoutine = {},
                    onStart = { started++ },
                    onGoToInProgress = {},
                    onDismissConflict = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("Pierna: 4 · Abdomen: 2").assertExists()
        compose.onNodeWithText("Pierna").assertExists()
        compose.onNodeWithText("Abdomen").assertExists()
        compose.onNodeWithText(context.getString(R.string.workout_start)).performClick()
        assertEquals(1, started)
    }

    @Test
    fun emptySessionShowsReminderAndEveryGroupWithItsAddButton() {
        setWorkout(
            content(
                listOf(
                    WorkoutBlockUi("g1", "Pecho", "g-pecho", emptyList()),
                    WorkoutBlockUi("g2", "Tríceps", "g-triceps", emptyList()),
                ),
            ),
        )

        compose.onNodeWithText(context.getString(R.string.workout_reminder_label)).assertExists()
        compose.onNodeWithText("Pecho: 4 ejercicios · Tríceps: 2").assertExists()
        compose.onNodeWithText(context.getString(R.string.workout_group_empty, "Pecho")).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.workout_group_add, "Tríceps")).performClick()
        assertEquals(listOf("g-triceps"), opened)
    }

    @Test
    fun noReminderWhenRoutineHasNoComment() {
        setWorkout(content(listOf(WorkoutBlockUi("g1", "Pecho", "g-pecho", emptyList())), reminder = null))

        compose.onAllNodesWithText(context.getString(R.string.workout_reminder_label)).assertCountEquals(0)
    }

    @Test
    fun setRowsHaveNoDoneColumnAndReferenceIsLabelled() {
        val press = WorkoutExerciseUi(
            exerciseId = "press",
            name = "Press banca",
            sets = listOf(
                WorkoutSetUi("p0", 1, 85.0, 8, 80.0, 8),
                WorkoutSetUi("p1", 2, null, null, 77.5, 6),
            ),
            previousSets = listOf(GhostSet(0, 80.0, 8), GhostSet(1, 77.5, 6)),
        )
        setWorkout(content(listOf(WorkoutBlockUi("g1", "Pecho", "g-pecho", listOf(press))), expanded = "press"))

        compose.onAllNodesWithText("Hecha").assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.workout_reference_legend)).assertExists()
        compose.onNodeWithText(context.getString(R.string.workout_previous_label, "80×8 · 77,5×6")).assertExists()
        // Campo vacío: lee la referencia; campo con dato de hoy: solo su descripción.
        val reference = context.getString(R.string.workout_weight_reference, "77,5")
        compose.onNodeWithContentDescription(
            context.getString(R.string.workout_weight_field, 2) + ". " + reference,
        ).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.workout_weight_field, 1)).assertExists()
        compose.onNodeWithText(context.getString(R.string.workout_copy_previous)).assertExists()
    }

    @Test
    fun removingExerciseWithDataAsksForConfirmation() {
        val sets = listOf(WorkoutSetUi("p0", 1, 80.0, 8, null, null))
        val press = WorkoutExerciseUi("press", "Press banca", sets, emptyList())
        setWorkout(content(listOf(WorkoutBlockUi("g1", "Pecho", "g-pecho", listOf(press))), expanded = "press"))

        // La barra inferior tapa el final de la tarjeta en la pantalla de test: se invoca la acción directamente.
        compose.onNodeWithText(context.getString(R.string.workout_remove_exercise))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(context.getString(R.string.workout_remove_exercise_title, "Press banca")).assertExists()
        assertEquals(emptyList<String>(), removed)
        compose.onNodeWithText(context.getString(R.string.workout_remove_exercise_confirm)).performClick()
        assertEquals(listOf("press"), removed)
    }

    @Test
    fun removingExerciseWithoutDataIsImmediate() {
        val sets = listOf(WorkoutSetUi("p0", 1, null, null, 80.0, 8))
        val press = WorkoutExerciseUi("press", "Press banca", sets, emptyList())
        setWorkout(content(listOf(WorkoutBlockUi("g1", "Pecho", "g-pecho", listOf(press))), expanded = "press"))

        // La barra inferior tapa el final de la tarjeta en la pantalla de test: se invoca la acción directamente.
        compose.onNodeWithText(context.getString(R.string.workout_remove_exercise))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf("press"), removed)
    }

    @Test
    fun pickerShowsNamesAndMarksExercisesAlreadyAdded() {
        val picker = ExercisePickerUi(
            muscleGroupId = "g-pecho",
            muscleGroupName = "Pecho",
            isLoading = false,
            exercises = listOf(
                PickerExerciseUi("press", "Press de banca plano con barra", null, alreadyAdded = true),
                PickerExerciseUi("fly", "Cruces de poleas", null, alreadyAdded = false),
            ),
        )
        setWorkout(content(listOf(WorkoutBlockUi("g1", "Pecho", "g-pecho", emptyList())), picker = picker))

        compose.onNodeWithText(context.getString(R.string.workout_picker_title, "Pecho")).assertExists()
        compose.onNodeWithText(context.getString(R.string.workout_picker_added)).assertExists()
        compose.onNodeWithText("Cruces de poleas").performClick()
        assertEquals(listOf("fly"), picked)
    }
}
