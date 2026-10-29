package com.zambudio.registropesos.screenshots

import com.zambudio.registropesos.domain.usecase.ExerciseNameError
import com.zambudio.registropesos.feature.exercises.ExerciseEditScreen
import com.zambudio.registropesos.feature.exercises.ExerciseEditUiState
import com.zambudio.registropesos.feature.exercises.ExerciseListItem
import com.zambudio.registropesos.feature.exercises.ExerciseSection
import com.zambudio.registropesos.feature.exercises.ExercisesScreen
import com.zambudio.registropesos.feature.exercises.ExercisesUiState
import com.zambudio.registropesos.feature.exercises.MuscleGroupOption
import com.zambudio.registropesos.feature.exercises.musclegroups.MuscleGroupItem
import com.zambudio.registropesos.feature.exercises.musclegroups.MuscleGroupsScreen
import com.zambudio.registropesos.feature.exercises.musclegroups.MuscleGroupsUiState
import org.junit.Test

/** Galería de ejercicios y grupos musculares (las fotos no cargan aquí: sale el hueco con icono). */
class ScreenshotGalleryExercisesTest : ScreenshotGallery() {

    private val groups = listOf(
        MuscleGroupOption("g1", "Pecho"),
        MuscleGroupOption("g2", "Espalda"),
        MuscleGroupOption("g3", "Pierna"),
    )

    private val sections = listOf(
        ExerciseSection(
            "g1",
            "Pecho",
            listOf(
                ExerciseListItem("e1", "Press banca con barra", null, isArchived = false),
                ExerciseListItem("e2", "Press inclinado con mancuernas", null, isArchived = false),
                ExerciseListItem("e3", "Aperturas en máquina contractora", null, isArchived = false),
                ExerciseListItem("e4", "Fondos en paralelas", null, isArchived = true),
            ),
        ),
        ExerciseSection(
            "g2",
            "Espalda",
            listOf(
                ExerciseListItem("e5", "Jalón al pecho agarre abierto", null, isArchived = false),
                ExerciseListItem("e6", "Remo en polea baja", null, isArchived = false),
            ),
        ),
    )

    @Test
    fun exercisesList() = snap("exercises-list") {
        ExercisesScreen(
            state = ExercisesUiState(isLoading = false, showArchived = true, sections = sections, groups = groups),
            query = "",
            onQueryChange = {},
            onShowArchivedChange = {},
            onExerciseClick = {},
            onAddExercise = {},
            onManageGroups = {},
            onRetry = {},
        )
    }

    @Test
    fun exercisesNoResults() = snap("exercises-no-results") {
        ExercisesScreen(
            state = ExercisesUiState(isLoading = false, groups = groups),
            query = "sentadilla búlgara",
            onQueryChange = {},
            onShowArchivedChange = {},
            onExerciseClick = {},
            onAddExercise = {},
            onManageGroups = {},
            onRetry = {},
        )
    }

    @Test
    fun exerciseEdit() = snap("exercise-edit") {
        ExerciseEditScreen(
            state = ExerciseEditUiState(
                isLoading = false,
                isNew = false,
                name = "Press inclinado con mancuernas",
                notes = "Banco a 30°. Bajar controlado hasta la altura del pecho.",
                muscleGroupId = "g1",
                groups = groups,
                isArchived = true,
            ),
            onBack = {},
            onNameChange = {},
            onNotesChange = {},
            onGroupSelected = {},
            onPickFromGallery = {},
            onTakePhoto = {},
            onRemoveImage = {},
            onToggleArchived = {},
            onSave = {},
            onDiscardConfirm = {},
            onDiscardDismiss = {},
        )
    }

    @Test
    fun exerciseNewWithErrors() = snap("exercise-new-errors") {
        ExerciseEditScreen(
            state = ExerciseEditUiState(
                isLoading = false,
                isNew = true,
                nameError = ExerciseNameError.EMPTY,
                isGroupMissing = true,
                groups = groups,
            ),
            onBack = {},
            onNameChange = {},
            onNotesChange = {},
            onGroupSelected = {},
            onPickFromGallery = {},
            onTakePhoto = {},
            onRemoveImage = {},
            onToggleArchived = {},
            onSave = {},
            onDiscardConfirm = {},
            onDiscardDismiss = {},
        )
    }

    @Test
    fun muscleGroups() = snap("muscle-groups") {
        MuscleGroupsScreen(
            state = MuscleGroupsUiState(
                isLoading = false,
                groups = listOf(
                    MuscleGroupItem("g1", "Pecho", 8),
                    MuscleGroupItem("g2", "Espalda", 9),
                    MuscleGroupItem("g3", "Pierna", 11),
                    MuscleGroupItem("g4", "Hombro", 6),
                    MuscleGroupItem("g5", "Abdominales", 1),
                    MuscleGroupItem("g6", "Cardio", 0),
                ),
            ),
            onBack = {},
            onRetry = {},
            onCreateClick = {},
            onRenameClick = { _, _ -> },
            onMoveUp = {},
            onMoveDown = {},
            onDelete = {},
            onEditorInputChange = {},
            onEditorSubmit = {},
            onEditorDismiss = {},
        )
    }
}
