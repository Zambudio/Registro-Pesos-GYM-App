package com.zambudio.registropesos.feature.history.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.core.util.formatDateTime
import com.zambudio.registropesos.core.util.formatDuration
import com.zambudio.registropesos.core.util.formatKg
import com.zambudio.registropesos.core.util.formatKgWithUnit
import kotlinx.coroutines.launch

@Composable
fun SessionDetailRoute(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleteFailed = stringResource(R.string.history_error_delete)
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SessionDetailEvent.Deleted -> onBack()
            SessionDetailEvent.DeleteFailed -> scope.launch { snackbarHostState.showSnackbar(deleteFailed) }
        }
    }
    SessionDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        // Solo el botón se protege del doble toque: el evento Deleted llega en STARTED y no debe perderse.
        onBack = dropUnlessResumed(block = onBack),
        onDelete = viewModel::delete,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    state: SessionDetailUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? SessionDetailUiState.Content
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = content?.routineName ?: stringResource(R.string.history_detail_title),
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
                        IconButton(onClick = { confirmDelete = true }, enabled = !content.isDeleting) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            SessionDetailUiState.Loading -> LoadingState(contentModifier)
            SessionDetailUiState.Error -> ErrorState(contentModifier, onRetry = onRetry)
            SessionDetailUiState.NotFound ->
                ErrorState(contentModifier, message = stringResource(R.string.history_not_found))
            is SessionDetailUiState.Content -> DetailContent(state, contentModifier)
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            message = stringResource(R.string.history_delete_message),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailContent(state: SessionDetailUiState.Content, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            val details = listOfNotNull(
                formatDateTime(state.startedAt),
                state.durationMs?.let(::formatDuration),
                formatKgWithUnit(state.volumeKg),
            ).joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.exercises.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.history_detail_no_sets), style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(state.exercises, key = { it.exerciseId }) { exercise -> ExerciseSetsCard(exercise) }
    }
}

@Composable
private fun ExerciseSetsCard(exercise: HistoryExerciseUi) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(
                    R.string.history_exercise_group_volume,
                    exercise.muscleGroupName,
                    formatKgWithUnit(exercise.volumeKg),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            exercise.sets.forEach { set ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.history_set_number, set.number),
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.history_set_weight_reps,
                            set.weightKg?.let(::formatKg) ?: stringResource(R.string.history_no_value),
                            set.reps?.toString() ?: stringResource(R.string.history_no_value),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (set.e1rmKg > 0.0) {
                            stringResource(R.string.history_set_e1rm, formatKg(set.e1rmKg))
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionDetailScreenPreview() {
    GymTheme {
        SessionDetailScreen(
            state = SessionDetailUiState.Content(
                routineName = "Pecho - Tríceps",
                startedAt = 1_700_000_000_000L,
                durationMs = 3_900_000L,
                volumeKg = 1_920.0,
                exercises = listOf(
                    HistoryExerciseUi(
                        "e1",
                        "Press banca",
                        "Pecho",
                        listOf(HistorySetUi(1, 80.0, 8, 101.5), HistorySetUi(2, 80.0, 8, 101.5)),
                        1_280.0,
                    ),
                ),
            ),
            onBack = {},
            onDelete = {},
            onRetry = {},
        )
    }
}
