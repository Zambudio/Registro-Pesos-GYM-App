package com.zambudio.registropesos.feature.workout.routinedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail
import kotlinx.coroutines.launch

@Composable
fun RoutineDetailRoute(
    onBack: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onOpenWorkout: (String) -> Unit,
    viewModel: RoutineDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val startFailed = stringResource(R.string.workout_error_start)
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RoutineDetailEvent.OpenWorkout -> onOpenWorkout(event.sessionId)
            RoutineDetailEvent.StartFailed -> scope.launch { snackbarHostState.showSnackbar(startFailed) }
        }
    }
    RoutineDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEditRoutine = { onEditRoutine(viewModel.routineId) },
        onStart = viewModel::startWorkout,
        onGoToInProgress = { sessionId ->
            viewModel.dismissConflict()
            onOpenWorkout(sessionId)
        },
        onDismissConflict = viewModel::dismissConflict,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    state: RoutineDetailUiState,
    onBack: () -> Unit,
    onEditRoutine: () -> Unit,
    onStart: () -> Unit,
    onGoToInProgress: (String) -> Unit,
    onDismissConflict: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? RoutineDetailUiState.Content
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = content?.detail?.routine?.name ?: stringResource(R.string.workout_routine_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (content != null) {
                        IconButton(onClick = onEditRoutine) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.workout_edit_routine))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (content != null && content.exerciseCount > 0) {
                StartBar(isStarting = content.isStarting, enabled = content.canStart, onStart = onStart)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            RoutineDetailUiState.Loading -> LoadingState(contentModifier)
            RoutineDetailUiState.Error -> ErrorState(contentModifier, onRetry = onRetry)
            RoutineDetailUiState.NotFound ->
                ErrorState(contentModifier, message = stringResource(R.string.workout_routine_not_found))
            is RoutineDetailUiState.Content -> {
                if (state.exerciseCount == 0) {
                    EmptyState(
                        title = stringResource(R.string.workout_routine_empty_title),
                        message = stringResource(R.string.workout_routine_empty_message),
                        actionLabel = stringResource(R.string.workout_edit_routine),
                        onAction = onEditRoutine,
                        modifier = contentModifier,
                    )
                } else {
                    RoutineBlocks(state.detail, contentModifier)
                }
            }
        }
    }

    val conflict = content?.conflictingSessionId
    if (conflict != null) {
        ConfirmDialog(
            title = stringResource(R.string.workout_conflict_title),
            message = stringResource(R.string.workout_conflict_message),
            confirmLabel = stringResource(R.string.workout_conflict_go),
            onConfirm = { onGoToInProgress(conflict) },
            onDismiss = onDismissConflict,
        )
    }
}

@Composable
private fun StartBar(isStarting: Boolean, enabled: Boolean, onStart: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Button(
                onClick = onStart,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                if (isStarting) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = stringResource(R.string.workout_start),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineBlocks(detail: RoutineDetail, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val subtitle = detail.routine.subtitle
        if (!subtitle.isNullOrBlank()) {
            item(key = "subtitle") {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(detail.blocks, key = { it.block.id }) { block ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = block.muscleGroup.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    block.exercises.forEach { item ->
                        ListItem(
                            headlineContent = { Text(item.exercise.name) },
                            trailingContent = {
                                Text(
                                    pluralStringResource(
                                        R.plurals.workout_set_count,
                                        item.routineExercise.defaultSets,
                                        item.routineExercise.defaultSets,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineDetailScreenPreview() {
    val group = MuscleGroup("g1", "Pecho", 0, true, 0L, 0L)
    val exercise = Exercise("e1", "g1", "Press banca", null, null, false, 0L, 0L)
    val detail = RoutineDetail(
        routine = Routine("r1", "Pecho - Tríceps", "4 ejer pecho, 2 tríceps", 0, 0L, 0L),
        blocks = listOf(
            RoutineBlockDetail(
                block = RoutineBlock("b1", "r1", "g1", 0, 0L, 0L),
                muscleGroup = group,
                exercises = listOf(RoutineExerciseDetail(RoutineExercise("re1", "b1", "e1", 0, 4, 0L, 0L), exercise)),
            ),
        ),
    )
    GymTheme {
        RoutineDetailScreen(
            state = RoutineDetailUiState.Content(detail),
            onBack = {},
            onEditRoutine = {},
            onStart = {},
            onGoToInProgress = {},
            onDismissConflict = {},
            onRetry = {},
        )
    }
}
