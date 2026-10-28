package com.zambudio.registropesos.feature.progress

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.PersonalRecord
import com.zambudio.registropesos.domain.usecase.ProgressRange
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ProgressScreenTest {
    @get:Rule val compose = createComposeRule()

    private val chest = MuscleGroup("g1", "Pecho", 0, true, 0L, 0L)
    private val bench = ExerciseOption("e1", "Press banca", "g1", isArchived = false)
    private val record = PersonalRecord("e1", 80.0, 1_760_000_000_000L, 101.5, 1_760_000_000_000L)

    @Test
    fun recordsListOpensTheExerciseAndRangeChipsNotify() {
        var opened: Pair<String, String>? = null
        var range: ProgressRange? = null
        compose.setContent {
            GymTheme {
                ProgressScreen(
                    state = ProgressUiState(
                        isLoading = false,
                        groups = listOf(chest),
                        exerciseGroupId = "g1",
                        exercises = listOf(bench),
                        records = listOf(RecordCard("e1", "g1", "Press banca", "Pecho", record)),
                    ),
                    callbacks = ProgressCallbacks(
                        onRecordClick = { exerciseId, groupId -> opened = exerciseId to groupId },
                        onRangeSelected = { range = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("Récords personales").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ver progreso de Press banca").performScrollTo().performClick()
        compose.onNodeWithText("Todo").performScrollTo().performClick()

        assertEquals("e1" to "g1", opened)
        assertEquals(ProgressRange.ALL, range)
    }

    @Test
    fun exerciseWithoutSessionsShowsEmptyState() {
        compose.setContent {
            GymTheme {
                ProgressScreen(
                    state = ProgressUiState(
                        isLoading = false,
                        groups = listOf(chest),
                        exerciseGroupId = "g1",
                        exercises = listOf(bench),
                        selectedExercise = bench,
                    ),
                    callbacks = ProgressCallbacks(),
                )
            }
        }

        compose.onNodeWithText("Aún no hay sesiones terminadas con este ejercicio")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun noHistoryShowsEmptyRecords() {
        compose.setContent {
            GymTheme {
                ProgressScreen(
                    state = ProgressUiState(isLoading = false, groups = listOf(chest), exerciseGroupId = "g1"),
                    callbacks = ProgressCallbacks(),
                )
            }
        }

        compose.onNodeWithText("Aún no hay récords").performScrollTo().assertIsDisplayed()
    }
}
