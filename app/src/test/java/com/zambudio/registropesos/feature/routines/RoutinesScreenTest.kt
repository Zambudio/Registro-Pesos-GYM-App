package com.zambudio.registropesos.feature.routines

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.feature.routines.list.RoutinesScreen
import com.zambudio.registropesos.feature.routines.list.RoutinesUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RoutinesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val opened = mutableListOf<String>()
    private val movedUp = mutableListOf<String>()
    private val deleteClicks = mutableListOf<String>()
    private var createClicks = 0

    private fun setScreen(state: RoutinesUiState) {
        compose.setContent {
            GymTheme {
                RoutinesScreen(
                    state = state,
                    onOpenRoutine = { opened += it },
                    onCreateRoutine = { createClicks++ },
                    onMoveUp = { movedUp += it },
                    onMoveDown = {},
                    onDeleteClick = { deleteClicks += it },
                    onDeleteConfirm = {},
                    onDeleteDismiss = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun estadoVacioOfreceCrearRutina() {
        setScreen(RoutinesUiState(isLoading = false))

        compose.onNodeWithText(context.getString(R.string.routines_empty_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.routines_empty_action)).performClick()

        assertEquals(1, createClicks)
    }

    @Test
    fun listaMuestraRutinasConRecuentosYAcciones() {
        setScreen(
            RoutinesUiState(
                isLoading = false,
                routines = listOf(
                    RoutineSummary("r1", "Día 1", "4 ejer pecho, 2 tríceps", blockCount = 2, exerciseCount = 6),
                    RoutineSummary("r2", "Día 2", null, blockCount = 1, exerciseCount = 1),
                ),
            ),
        )

        compose.onNodeWithText("Día 1").assertExists()
        compose.onNodeWithText("4 ejer pecho, 2 tríceps").assertExists()
        compose.onNodeWithText("2 bloques · 6 ejercicios").assertExists()
        compose.onNodeWithText("1 bloque · 1 ejercicio").assertExists()
        compose.onNodeWithText(context.getString(R.string.routines_new), useUnmergedTree = true).assertExists()

        // La primera no puede subir; la segunda sí.
        compose.onNodeWithContentDescription(context.getString(R.string.routines_move_up, "Día 1")).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.routines_move_up, "Día 2")).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.routines_delete_named, "Día 1")).performClick()
        // Pulsar sobre el nombre (el centro de la tarjeta puede caer en los iconos en pantallas estrechas).
        compose.onNodeWithText("Día 2", useUnmergedTree = true).performClick()

        assertEquals(listOf("r2"), movedUp)
        assertEquals(listOf("r1"), deleteClicks)
        assertEquals(listOf("r2"), opened)
    }

    @Test
    fun dialogoDeBorradoExplicaQueElHistorialSeConserva() {
        val routine = RoutineSummary("r1", "Día 1", null, blockCount = 1, exerciseCount = 2)
        setScreen(RoutinesUiState(isLoading = false, routines = listOf(routine), pendingDelete = routine))

        compose.onNodeWithText(context.getString(R.string.routines_delete_message, "Día 1")).assertExists()
    }
}
