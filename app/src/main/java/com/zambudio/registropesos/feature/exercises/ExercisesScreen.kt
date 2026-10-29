package com.zambudio.registropesos.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.GymExtendedFab
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.Pill
import com.zambudio.registropesos.core.ui.ScreenPadding

private val ThumbnailSize = 64.dp
private val RowPadding = 12.dp

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
        // El Scaffold raíz ya aplica el inset inferior (barra de navegación).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
                actions = {
                    IconButton(onClick = onManageGroups) {
                        Icon(
                            Icons.Outlined.Category,
                            contentDescription = stringResource(R.string.exercises_manage_groups),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading && !state.isError) {
                GymExtendedFab(
                    text = stringResource(R.string.exercises_add),
                    icon = Icons.Filled.Add,
                    onClick = { showGroupPicker = true },
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

/** Buscador en forma de píldora y filtro de archivados. */
@Composable
private fun SearchRow(
    query: String,
    showArchived: Boolean,
    onQueryChange: (String) -> Unit,
    onShowArchivedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
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
            shape = RoundedCornerShape(percent = 50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                unfocusedBorderColor = colors.outlineVariant,
            ),
        )
        FilterChip(
            selected = showArchived,
            onClick = { onShowArchivedChange(!showArchived) },
            label = { Text(stringResource(R.string.exercises_show_archived)) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = colors.surfaceContainerLow,
                selectedContainerColor = colors.primaryContainer,
                selectedLabelColor = colors.onPrimaryContainer,
                selectedLeadingIconColor = colors.onPrimaryContainer,
            ),
            modifier = Modifier.padding(top = 4.dp),
        )
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
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 96.dp),
        ) {
            state.sections.forEach { section ->
                item(key = "group-${section.groupId}", contentType = "header") {
                    SectionTitle(name = section.groupName, count = section.exercises.size)
                }
                item(key = "card-${section.groupId}", contentType = "section") {
                    SectionCard(exercises = section.exercises, onExerciseClick = onExerciseClick)
                }
            }
        }
    }
}

/** Cabecera de grupo muscular: nombre en grande y recuento en una píldora. */
@Composable
private fun SectionTitle(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Pill(
            text = pluralStringResource(R.plurals.exercises_groups_count, count, count),
        )
    }
}

/** Ejercicios de un grupo en una sola tarjeta, separados por líneas finas. */
@Composable
private fun SectionCard(exercises: List<ExerciseListItem>, onExerciseClick: (String) -> Unit) {
    GymCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        exercises.forEachIndexed { index, exercise ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = RowPadding * 2 + ThumbnailSize),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            ExerciseRow(exercise = exercise, onClick = { onExerciseClick(exercise.id) })
        }
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseListItem, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(RowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowPadding),
        ) {
            ExerciseThumbnail(imageFile = exercise.imageFile, size = ThumbnailSize)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (exercise.isArchived) {
                    Pill(text = stringResource(R.string.exercises_archived_badge), icon = Icons.Filled.Archive)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
