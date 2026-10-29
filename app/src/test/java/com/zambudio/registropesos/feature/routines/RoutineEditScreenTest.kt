package com.zambudio.registropesos.feature.routines

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.feature.routines.edit.DraftBlock
import com.zambudio.registropesos.feature.routines.edit.RoutineDraft
import com.zambudio.registropesos.feature.routines.edit.RoutineEditActions
import com.zambudio.registropesos.feature.routines.edit.RoutineEditScreen
import com.zambudio.registropesos.feature.routines.edit.RoutineEditUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class RoutineEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun editorAsksOnlyForNameCommentAndOrderedGroups() {
        val movedDown = mutableListOf<String>()
        var addClicks = 0
        val state = RoutineEditUiState(
            isNew = true,
            draft = RoutineDraft(
                name = "Día 1",
                subtitle = "Pecho: 4 · Tríceps: 2",
                blocks = listOf(DraftBlock("b1", pecho), DraftBlock("b2", triceps)),
            ),
        )
        compose.setContent {
            GymTheme {
                RoutineEditScreen(
                    state = state,
                    actions = RoutineEditActions(
                        onAddBlockClick = { addClicks++ },
                        onMoveBlockDown = { movedDown += it },
                    ),
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.routines_edit_subtitle)).assertExists()
        compose.onNodeWithText("Pecho").assertExists()
        compose.onNodeWithText("Tríceps").assertExists()
        // Sin selección de ejercicios ni series en el editor.
        compose.onAllNodesWithText("Añadir ejercicios").assertCountEquals(0)
        compose.onAllNodesWithText("Series").assertCountEquals(0)

        compose.onNodeWithContentDescription(context.getString(R.string.routines_edit_move_block_down, "Pecho"))
            .performClick()
        val addGroup = context.getString(R.string.routines_edit_add_block)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(addGroup))
        compose.onNodeWithText(context.getString(R.string.routines_edit_add_block)).performClick()
        assertEquals(listOf("b1"), movedDown)
        assertEquals(1, addClicks)
    }
}
