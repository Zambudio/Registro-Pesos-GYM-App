package com.zambudio.registropesos.feature.routines.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.usecase.RoutineError
import com.zambudio.registropesos.domain.usecase.ValidateRoutine
import kotlinx.coroutines.launch

@Composable
fun RoutineEditRoute(
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    onCreateExercise: (muscleGroupId: String) -> Unit,
    viewModel: RoutineEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        when (event) {
            RoutineEditEvent.Close -> onClose()
            RoutineEditEvent.Deleted -> onDeleted()
            RoutineEditEvent.SaveFailed -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.routines_edit_save_failed))
            }
            RoutineEditEvent.DeleteFailed -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.routines_edit_delete_failed))
            }
        }
    }
    // Con cambios sin guardar, el back del sistema pasa por el ViewModel (diálogo de descarte).
    BackHandler(enabled = state.isDirty, onBack = viewModel::onBack)

    RoutineEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = RoutineEditActions(
            onBack = viewModel::onBack,
            onSave = viewModel::onSave,
            onDeleteClick = viewModel::onDeleteClick,
            onRetryLoad = viewModel::onRetryLoad,
            onNameChange = viewModel::onNameChange,
            onSubtitleChange = viewModel::onSubtitleChange,
            onAddBlockClick = viewModel::onAddBlockClick,
            onGroupSelected = viewModel::onGroupSelected,
            onGroupPickerDismiss = viewModel::onGroupPickerDismiss,
            onMoveBlockUp = viewModel::onMoveBlockUp,
            onMoveBlockDown = viewModel::onMoveBlockDown,
            onRemoveBlock = viewModel::onRemoveBlock,
            onAddExercisesClick = viewModel::onAddExercisesClick,
            onPickerToggle = viewModel::onPickerToggle,
            onPickerConfirm = viewModel::onPickerConfirm,
            onPickerDismiss = viewModel::onPickerDismiss,
            onCreateExercise = onCreateExercise,
            onMoveExerciseUp = viewModel::onMoveExerciseUp,
            onMoveExerciseDown = viewModel::onMoveExerciseDown,
            onRemoveExercise = viewModel::onRemoveExercise,
            onIncrementSets = viewModel::onIncrementSets,
            onDecrementSets = viewModel::onDecrementSets,
            onDiscardConfirm = viewModel::onDiscardConfirm,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDialogDismiss = viewModel::onDialogDismiss,
        ),
    )
}

/** Callbacks del editor agrupados (la pantalla no conoce el ViewModel). */
data class RoutineEditActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDeleteClick: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onSubtitleChange: (String) -> Unit = {},
    val onAddBlockClick: () -> Unit = {},
    val onGroupSelected: (MuscleGroup) -> Unit = {},
    val onGroupPickerDismiss: () -> Unit = {},
    val onMoveBlockUp: (blockId: String) -> Unit = {},
    val onMoveBlockDown: (blockId: String) -> Unit = {},
    val onRemoveBlock: (blockId: String) -> Unit = {},
    val onAddExercisesClick: (blockId: String) -> Unit = {},
    val onPickerToggle: (exerciseId: String) -> Unit = {},
    val onPickerConfirm: () -> Unit = {},
    val onPickerDismiss: () -> Unit = {},
    val onCreateExercise: (muscleGroupId: String) -> Unit = {},
    val onMoveExerciseUp: (blockId: String, itemId: String) -> Unit = { _, _ -> },
    val onMoveExerciseDown: (blockId: String, itemId: String) -> Unit = { _, _ -> },
    val onRemoveExercise: (blockId: String, itemId: String) -> Unit = { _, _ -> },
    val onIncrementSets: (blockId: String, itemId: String) -> Unit = { _, _ -> },
    val onDecrementSets: (blockId: String, itemId: String) -> Unit = { _, _ -> },
    val onDiscardConfirm: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDialogDismiss: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditScreen(
    state: RoutineEditUiState,
    actions: RoutineEditActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val editable = !state.isLoading && !state.notFound && !state.loadFailed
    Scaffold(
        modifier = modifier,
        // El Scaffold raíz ya aplica el inset inferior.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.routines_edit_title_new else R.string.routines_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.routines_edit_back),
                        )
                    }
                },
                actions = {
                    if (!state.isNew && editable) {
                        IconButton(onClick = actions.onDeleteClick) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.routines_edit_delete),
                            )
                        }
                    }
                    if (editable) {
                        TextButton(onClick = actions.onSave, enabled = !state.isSaving) {
                            Text(
                                stringResource(
                                    if (state.isSaving) R.string.routines_edit_saving else R.string.routines_edit_save,
                                ),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingState(contentModifier)
            state.notFound -> ErrorState(
                modifier = contentModifier,
                message = stringResource(R.string.routines_edit_not_found),
            )
            state.loadFailed -> ErrorState(
                modifier = contentModifier,
                message = stringResource(R.string.routines_edit_load_failed),
                onRetry = actions.onRetryLoad,
            )
            else -> EditorContent(state = state, actions = actions, modifier = contentModifier)
        }
    }

    RoutineEditDialogs(state = state, actions = actions)
}

@Composable
private fun RoutineEditDialogs(state: RoutineEditUiState, actions: RoutineEditActions) {
    if (state.showGroupPicker) {
        MuscleGroupPickerDialog(
            groups = state.muscleGroups,
            onSelect = actions.onGroupSelected,
            onDismiss = actions.onGroupPickerDismiss,
        )
    }
    state.exercisePicker?.let { picker ->
        ExercisePickerDialog(
            picker = picker,
            onToggle = actions.onPickerToggle,
            onConfirm = actions.onPickerConfirm,
            onDismiss = actions.onPickerDismiss,
            onCreateExercise = { actions.onCreateExercise(picker.muscleGroup.id) },
        )
    }
    when (state.dialog) {
        RoutineEditDialog.DISCARD_CHANGES -> ConfirmDialog(
            title = stringResource(R.string.routines_discard_title),
            message = stringResource(R.string.routines_discard_message),
            confirmLabel = stringResource(R.string.routines_discard_confirm),
            dismissLabel = stringResource(R.string.routines_discard_keep_editing),
            onConfirm = actions.onDiscardConfirm,
            onDismiss = actions.onDialogDismiss,
        )
        RoutineEditDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.routines_delete_title),
            message = stringResource(R.string.routines_delete_message, state.draft.name.trim()),
            confirmLabel = stringResource(R.string.routines_delete_confirm),
            onConfirm = actions.onDeleteConfirm,
            onDismiss = actions.onDialogDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun EditorContent(state: RoutineEditUiState, actions: RoutineEditActions, modifier: Modifier = Modifier) {
    val draft = state.draft
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "name") {
            val nameError = RoutineError.NAME_EMPTY in state.errors
            OutlinedTextField(
                value = draft.name,
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(R.string.routines_edit_name)) },
                placeholder = { Text(stringResource(R.string.routines_edit_name_hint)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.routines_error_name_empty)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "subtitle") {
            OutlinedTextField(
                value = draft.subtitle,
                onValueChange = actions.onSubtitleChange,
                label = { Text(stringResource(R.string.routines_edit_subtitle)) },
                placeholder = { Text(stringResource(R.string.routines_edit_subtitle_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "header") {
            Text(stringResource(R.string.routines_edit_blocks), style = MaterialTheme.typography.titleMedium)
        }
        val blockErrors = state.errors - RoutineError.NAME_EMPTY
        if (blockErrors.isNotEmpty()) {
            item(key = "errors") { ValidationErrors(blockErrors) }
        }
        if (draft.blocks.isEmpty()) {
            item(key = "no-blocks") {
                Text(
                    stringResource(R.string.routines_edit_no_blocks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(draft.blocks, key = { it.id }) { block ->
            val index = draft.blocks.indexOf(block)
            BlockCard(
                block = block,
                canMoveUp = index > 0,
                canMoveDown = index < draft.blocks.lastIndex,
                actions = actions,
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "add-block") {
            OutlinedButton(onClick = actions.onAddBlockClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.routines_edit_add_block))
            }
        }
    }
}

@Composable
private fun ValidationErrors(errors: Set<RoutineError>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        errors.forEach { error ->
            val message = when (error) {
                RoutineError.NAME_EMPTY -> R.string.routines_error_name_empty
                RoutineError.NO_EXERCISES -> R.string.routines_error_no_exercises
                RoutineError.INVALID_DEFAULT_SETS -> R.string.routines_error_invalid_sets
            }
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun BlockCard(
    block: DraftBlock,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: RoutineEditActions,
    modifier: Modifier = Modifier,
) {
    val groupName = block.muscleGroup.name
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    groupName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onMoveBlockUp(block.id) }, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.routines_edit_move_block_up, groupName),
                    )
                }
                IconButton(onClick = { actions.onMoveBlockDown(block.id) }, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.routines_edit_move_block_down, groupName),
                    )
                }
                IconButton(onClick = { actions.onRemoveBlock(block.id) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.routines_edit_remove_block, groupName),
                    )
                }
            }
            if (block.exercises.isEmpty()) {
                Text(
                    stringResource(R.string.routines_edit_block_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            block.exercises.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(end = 12.dp))
                ExerciseRow(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < block.exercises.lastIndex,
                    onMoveUp = { actions.onMoveExerciseUp(block.id, item.id) },
                    onMoveDown = { actions.onMoveExerciseDown(block.id, item.id) },
                    onRemove = { actions.onRemoveExercise(block.id, item.id) },
                    onIncrement = { actions.onIncrementSets(block.id, item.id) },
                    onDecrement = { actions.onDecrementSets(block.id, item.id) },
                )
            }
            TextButton(onClick = { actions.onAddExercisesClick(block.id) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.routines_edit_add_exercises))
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    item: DraftExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val name = item.exercise.name
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (item.exercise.isArchived) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            stringResource(R.string.routines_edit_archived),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.routines_edit_remove_exercise, name),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.routines_edit_sets_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onDecrement, enabled = item.defaultSets > ValidateRoutine.MIN_DEFAULT_SETS) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.routines_edit_sets_decrease, name),
                )
            }
            Text(
                item.defaultSets.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 24.dp),
            )
            IconButton(onClick = onIncrement, enabled = item.defaultSets < ValidateRoutine.MAX_DEFAULT_SETS) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.routines_edit_sets_increase, name),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.routines_edit_move_exercise_up, name),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.routines_edit_move_exercise_down, name),
                )
            }
        }
    }
}

// --- Previews ---

private fun previewGroup(id: String, name: String) = MuscleGroup(id, name, 0, true, 0L, 0L)

private fun previewExercise(id: String, groupId: String, name: String, archived: Boolean = false) =
    Exercise(id, groupId, name, null, null, archived, 0L, 0L)

private val previewState = RoutineEditUiState(
    isNew = false,
    draft = RoutineDraft(
        name = "Día 1",
        subtitle = "4 ejer pecho, 2 tríceps",
        blocks = listOf(
            DraftBlock(
                id = "b1",
                muscleGroup = previewGroup("g1", "Pecho"),
                exercises = listOf(
                    DraftExercise("i1", previewExercise("e1", "g1", "Press banca"), 4),
                    DraftExercise("i2", previewExercise("e2", "g1", "Aperturas"), 3),
                    DraftExercise("i3", previewExercise("e3", "g1", "Press inclinado máquina", archived = true), 4),
                ),
            ),
            DraftBlock(id = "b2", muscleGroup = previewGroup("g2", "Tríceps")),
        ),
    ),
)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun RoutineEditScreenPreview() {
    GymTheme {
        RoutineEditScreen(state = previewState, actions = RoutineEditActions())
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineEditNewPreview() {
    GymTheme {
        RoutineEditScreen(state = RoutineEditUiState(isNew = true), actions = RoutineEditActions())
    }
}
