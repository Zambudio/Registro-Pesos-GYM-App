package com.zambudio.registropesos.feature.workout.train

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.zambudio.registropesos.core.util.formatTime
import com.zambudio.registropesos.domain.model.RoutineSummary
import kotlinx.coroutines.launch

@Composable
fun TrainRoute(
    onOpenRoutine: (String) -> Unit,
    onResumeSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onCreateRoutine: () -> Unit,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val discardFailed = stringResource(R.string.workout_error_discard)
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            TrainEvent.DiscardFailed -> scope.launch { snackbarHostState.showSnackbar(discardFailed) }
        }
    }
    TrainScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenRoutine = onOpenRoutine,
        onResumeSession = onResumeSession,
        onDiscardSession = viewModel::discardInProgress,
        onOpenHistory = onOpenHistory,
        onCreateRoutine = onCreateRoutine,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainScreen(
    state: TrainUiState,
    onOpenRoutine: (String) -> Unit,
    onResumeSession: (String) -> Unit,
    onDiscardSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onCreateRoutine: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workout_train_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.workout_open_history))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            TrainUiState.Loading -> LoadingState(contentModifier)
            TrainUiState.Error -> ErrorState(contentModifier, onRetry = onRetry)
            is TrainUiState.Content -> TrainContent(
                state = state,
                onOpenRoutine = onOpenRoutine,
                onResumeSession = onResumeSession,
                onDiscardSession = onDiscardSession,
                onCreateRoutine = onCreateRoutine,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun TrainContent(
    state: TrainUiState.Content,
    onOpenRoutine: (String) -> Unit,
    onResumeSession: (String) -> Unit,
    onDiscardSession: (String) -> Unit,
    onCreateRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        state.inProgress?.let { session ->
            InProgressCard(
                session = session,
                onResume = { onResumeSession(session.sessionId) },
                onDiscard = { confirmDiscard = true },
                modifier = Modifier.padding(16.dp),
            )
            if (confirmDiscard) {
                ConfirmDialog(
                    title = stringResource(R.string.workout_discard_title),
                    message = stringResource(R.string.workout_discard_message),
                    confirmLabel = stringResource(R.string.workout_discard_confirm),
                    onConfirm = {
                        confirmDiscard = false
                        onDiscardSession(session.sessionId)
                    },
                    onDismiss = { confirmDiscard = false },
                )
            }
        }
        if (state.routines.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.workout_train_empty_title),
                message = stringResource(R.string.workout_train_empty_message),
                icon = Icons.Filled.FitnessCenter,
                actionLabel = stringResource(R.string.workout_train_empty_action),
                onAction = onCreateRoutine,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.workout_train_routines_header),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.routines, key = { it.id }) { routine ->
                    RoutineRow(routine, onClick = { onOpenRoutine(routine.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InProgressCard(
    session: InProgressSessionUi,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.workout_in_progress_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = session.routineName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.workout_in_progress_started_at, formatTime(session.startedAt)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workout_in_progress_resume))
                }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workout_in_progress_discard))
                }
            }
        }
    }
}

@Composable
private fun RoutineRow(routine: RoutineSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(routine.name, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Column {
                Text(
                    text = if (routine.groupNames.isEmpty()) {
                        stringResource(R.string.workout_routine_no_groups)
                    } else {
                        routine.groupNames.joinToString(stringResource(R.string.workout_groups_separator))
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
                routine.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it) }
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}

@Preview(showBackground = true)
@Composable
private fun TrainScreenPreview() {
    GymTheme {
        TrainScreen(
            state = TrainUiState.Content(
                inProgress = InProgressSessionUi("s1", "Pecho - Tríceps", 1_700_000_000_000L),
                routines = listOf(
                    RoutineSummary("r1", "Día 1", "Pecho: 4 ejercicios · Tríceps: 2", listOf("Pecho", "Tríceps")),
                    RoutineSummary("r2", "Día 2", null, listOf("Espalda", "Bíceps")),
                ),
            ),
            onOpenRoutine = {},
            onResumeSession = {},
            onDiscardSession = {},
            onOpenHistory = {},
            onCreateRoutine = {},
            onRetry = {},
        )
    }
}
