package com.zambudio.registropesos.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState

@Composable
fun ExercisesListRoute(
    onOpenExercise: (exerciseId: String) -> Unit,
    onAddExercise: (muscleGroupId: String) -> Unit,
    onManageGroups: () -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    ExercisesScreen(
        state = state,
        query = query,
        onQueryChange = viewModel::onQueryChange,
        onShowArchivedChange = viewModel::onShowArchivedChange,
        onExerciseClick = onOpenExercise,
        onAddExercise = onAddExercise,
        onManageGroups = onManageGroups,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    state: ExercisesUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onShowArchivedChange: (Boolean) -> Unit,
    onExerciseClick: (String) -> Unit,
    onAddExercise: (String) -> Unit,
    onManageGroups: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showGroupPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
                actions = {
                    IconButton(onClick = onManageGroups) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = stringResource(R.string.exercises_manage_groups),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading && !state.isError) {
                ExtendedFloatingActionButton(
                    onClick = { showGroupPicker = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.exercises_add)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> LoadingState()
                state.isError -> ErrorState(onRetry = onRetry)
                else -> {
                    SearchRow(
                        query = query,
                        showArchived = state.showArchived,
                        onQueryChange = onQueryChange,
                        onShowArchivedChange = onShowArchivedChange,
                    )
                    ExercisesContent(
                        state = state,
                        query = query,
                        onExerciseClick = onExerciseClick,
                        onAddClick = { showGroupPicker = true },
                    )
                }
            }
        }
    }

    if (showGroupPicker) {
        MuscleGroupPickerDialog(
            groups = state.groups,
            onSelect = { groupId ->
                showGroupPicker = false
                onAddExercise(groupId)
            },
            onDismiss = { showGroupPicker = false },
        )
    }
}

@Composable
private fun SearchRow(
    query: String,
    showArchived: Boolean,
    onQueryChange: (String) -> Unit,
    onShowArchivedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.exercises_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.exercises_search_clear))
                    }
                }
            },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showArchived,
                onClick = { onShowArchivedChange(!showArchived) },
                label = { Text(stringResource(R.string.exercises_show_archived)) },
            )
        }
    }
}

@Composable
private fun ExercisesContent(
    state: ExercisesUiState,
    query: String,
    onExerciseClick: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    when {
        state.isLibraryEmpty && !state.showArchived -> EmptyState(
            title = stringResource(R.string.exercises_empty_title),
            message = stringResource(R.string.exercises_empty_message),
            icon = Icons.Filled.FitnessCenter,
            actionLabel = stringResource(R.string.exercises_add),
            onAction = onAddClick,
        )
        state.sections.isEmpty() -> EmptyState(
            title = stringResource(R.string.exercises_no_results_title),
            message = query.trim().takeIf { it.isNotEmpty() }
                ?.let { stringResource(R.string.exercises_no_results_message, it) },
            icon = Icons.Filled.SearchOff,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Espacio para que el FAB no tape el último elemento.
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            state.sections.forEach { section ->
                item(key = "group-${section.groupId}", contentType = "header") {
                    Text(
                        text = section.groupName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(section.exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                    ExerciseRow(exercise = exercise, onClick = { onExerciseClick(exercise.id) })
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseListItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = if (exercise.isArchived) {
            { Text(stringResource(R.string.exercises_archived_badge)) }
        } else {
            null
        },
        leadingContent = { ExerciseThumbnail(imageFile = exercise.imageFile) },
        modifier = Modifier.clickableRow(onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun ExercisesScreenPreview() {
    GymTheme {
        ExercisesScreen(
            state = ExercisesUiState(
                isLoading = false,
                sections = listOf(
                    ExerciseSection(
                        groupId = "g1",
                        groupName = "Pecho",
                        exercises = listOf(
                            ExerciseListItem("e1", "Press banca", null, isArchived = false),
                            ExerciseListItem("e2", "Aperturas", null, isArchived = true),
                        ),
                    ),
                ),
            ),
            query = "",
            onQueryChange = {},
            onShowArchivedChange = {},
            onExerciseClick = {},
            onAddExercise = {},
            onManageGroups = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExercisesScreenEmptyPreview() {
    GymTheme {
        ExercisesScreen(
            state = ExercisesUiState(isLoading = false, isLibraryEmpty = true),
            query = "",
            onQueryChange = {},
            onShowArchivedChange = {},
            onExerciseClick = {},
            onAddExercise = {},
            onManageGroups = {},
            onRetry = {},
        )
    }
}
