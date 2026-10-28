package com.zambudio.registropesos.feature.exercises.musclegroups

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.feature.exercises.text
import kotlinx.coroutines.launch

@Composable
fun MuscleGroupsListRoute(
    onBack: () -> Unit,
    viewModel: MuscleGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is MuscleGroupsEvent.ShowMessage -> {
                val text = context.text(event.message)
                scope.launch { snackbarHostState.showSnackbar(text) }
            }
        }
    }

    MuscleGroupsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onCreateClick = viewModel::onCreateClick,
        onRenameClick = viewModel::onRenameClick,
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onDelete = viewModel::onDelete,
        onEditorInputChange = viewModel::onEditorInputChange,
        onEditorSubmit = viewModel::onEditorSubmit,
        onEditorDismiss = viewModel::onEditorDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupsScreen(
    state: MuscleGroupsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCreateClick: () -> Unit,
    onRenameClick: (groupId: String, currentName: String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEditorInputChange: () -> Unit,
    onEditorSubmit: (String) -> Unit,
    onEditorDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var deleteCandidateId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_groups_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading && !state.isError) {
                ExtendedFloatingActionButton(
                    onClick = onCreateClick,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.exercises_groups_add)) },
                )
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingState(contentModifier)
            state.isError -> ErrorState(contentModifier, onRetry = onRetry)
            state.groups.isEmpty() -> EmptyState(
                title = stringResource(R.string.exercises_groups_empty_title),
                modifier = contentModifier,
                icon = Icons.Filled.Category,
            )
            else -> LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                itemsIndexed(state.groups, key = { _, group -> group.id }) { index, group ->
                    MuscleGroupRow(
                        group = group,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.groups.lastIndex,
                        onMoveUp = { onMoveUp(group.id) },
                        onMoveDown = { onMoveDown(group.id) },
                        onRename = { onRenameClick(group.id, group.name) },
                        onDelete = { deleteCandidateId = group.id },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        GroupNameDialog(
            editor = editor,
            onInputChange = onEditorInputChange,
            onSubmit = onEditorSubmit,
            onDismiss = onEditorDismiss,
        )
    }

    val candidate = state.groups.firstOrNull { it.id == deleteCandidateId }
    if (candidate != null) {
        ConfirmDialog(
            title = stringResource(R.string.exercises_groups_delete_title),
            message = stringResource(R.string.exercises_groups_delete_message, candidate.name),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                deleteCandidateId = null
                onDelete(candidate.id)
            },
            onDismiss = { deleteCandidateId = null },
        )
    }
}

@Composable
private fun MuscleGroupRow(
    group: MuscleGroupItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(group.name) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.exercises_groups_count, group.exerciseCount, group.exerciseCount))
        },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.exercises_groups_move_up, group.name),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.exercises_groups_move_down, group.name),
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.exercises_groups_rename, group.name),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.exercises_groups_delete, group.name),
                    )
                }
            }
        },
    )
}

@Composable
private fun GroupNameDialog(
    editor: GroupEditorState,
    onInputChange: () -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // El texto vive en el diálogo; el ViewModel valida al confirmar.
    var name by rememberSaveable(editor.groupId) { mutableStateOf(editor.initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.groupId == null) {
                        R.string.exercises_groups_create_title
                    } else {
                        R.string.exercises_groups_rename_title
                    },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    if (editor.error != null) onInputChange()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.exercises_groups_field_name)) },
                isError = editor.error != null,
                supportingText = editor.error?.let { error -> { Text(groupNameErrorText(error)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun groupNameErrorText(error: GroupNameError): String = when (error) {
    GroupNameError.EMPTY -> stringResource(R.string.exercises_groups_error_empty)
    GroupNameError.DUPLICATE -> stringResource(R.string.exercises_groups_error_duplicate)
}

@Preview(showBackground = true)
@Composable
private fun MuscleGroupsScreenPreview() {
    GymTheme {
        MuscleGroupsScreen(
            state = MuscleGroupsUiState(
                isLoading = false,
                groups = listOf(
                    MuscleGroupItem("g1", "Pecho", 3),
                    MuscleGroupItem("g2", "Espalda", 1),
                    MuscleGroupItem("g3", "Cardio", 0),
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
