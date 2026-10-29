package com.zambudio.registropesos.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.zambudio.registropesos.core.navigation.GymBottomBar
import com.zambudio.registropesos.core.navigation.TopLevelDestination
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.usecase.RoutineError
import com.zambudio.registropesos.feature.routines.edit.DraftBlock
import com.zambudio.registropesos.feature.routines.edit.RoutineDraft
import com.zambudio.registropesos.feature.routines.edit.RoutineEditActions
import com.zambudio.registropesos.feature.routines.edit.RoutineEditScreen
import com.zambudio.registropesos.feature.routines.edit.RoutineEditUiState
import com.zambudio.registropesos.feature.routines.list.RoutinesScreen
import com.zambudio.registropesos.feature.routines.list.RoutinesUiState
import org.junit.Test

/** Galería de rutinas y de la barra inferior. */
class ScreenshotGalleryRoutinesTest : ScreenshotGallery() {

    private val routines = listOf(
        RoutineSummary("r1", "Día 1 · Empuje", "Pecho: 4 ejercicios · Tríceps: 2", listOf("Pecho", "Tríceps")),
        RoutineSummary("r2", "Día 2 · Tirón", "Dominadas primero, remo pesado al final", listOf("Espalda", "Bíceps")),
        RoutineSummary("r3", "Día 3 · Pierna", null, listOf("Pierna", "Glúteo", "Abdomen")),
        RoutineSummary("r4", "Hombro y core", "Ligera, para la semana de descarga", emptyList()),
    )

    @Test
    fun routinesList() = snap("routines-list") {
        Scaffold(bottomBar = { GymBottomBar(selected = TopLevelDestination.ROUTINES, onSelect = {}) }) { padding ->
            RoutinesScreen(
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                state = RoutinesUiState(isLoading = false, routines = routines),
                onOpenRoutine = {},
                onCreateRoutine = {},
                onMoveUp = {},
                onMoveDown = {},
                onDeleteClick = {},
                onDeleteConfirm = {},
                onDeleteDismiss = {},
                onRetry = {},
            )
        }
    }

    @Test
    fun routinesEmpty() = snap("routines-empty") {
        RoutinesScreen(
            state = RoutinesUiState(isLoading = false),
            onOpenRoutine = {},
            onCreateRoutine = {},
            onMoveUp = {},
            onMoveDown = {},
            onDeleteClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onRetry = {},
        )
    }

    @Test
    fun routineEdit() = snap("routine-edit") {
        RoutineEditScreen(
            state = RoutineEditUiState(
                isNew = false,
                draft = RoutineDraft(
                    name = "Día 1 · Empuje",
                    subtitle = "Pecho: 4 ejercicios · Tríceps: 2",
                    blocks = listOf(
                        DraftBlock("b1", group("g1", "Pecho")),
                        DraftBlock("b2", group("g2", "Hombro")),
                        DraftBlock("b3", group("g3", "Tríceps")),
                    ),
                ),
            ),
            actions = RoutineEditActions(),
        )
    }

    @Test
    fun routineEditNew() = snap("routine-edit-new") {
        RoutineEditScreen(
            state = RoutineEditUiState(isNew = true, errors = setOf(RoutineError.NAME_EMPTY, RoutineError.NO_GROUPS)),
            actions = RoutineEditActions(),
        )
    }

    private fun group(id: String, name: String) = MuscleGroup(id, name, 0, true, 0L, 0L)
}
