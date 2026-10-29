package com.zambudio.registropesos.feature.workout.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.CardSpacing
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.HeroButtonContent
import com.zambudio.registropesos.core.ui.HeroCard
import com.zambudio.registropesos.core.ui.IconBadge
import com.zambudio.registropesos.core.ui.ListContentPadding
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.core.ui.OnHero
import com.zambudio.registropesos.core.ui.Pill
import com.zambudio.registropesos.core.ui.SectionHeader
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
    val session = state.inProgress
    if (session != null && confirmDiscard) {
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = ListContentPadding,
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        if (session != null) {
            item(key = "in-progress") {
                InProgressCard(
                    session = session,
                    onResume = { onResumeSession(session.sessionId) },
                    onDiscard = { confirmDiscard = true },
                )
            }
        }
        if (state.routines.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.workout_train_empty_title),
                    message = stringResource(R.string.workout_train_empty_message),
                    icon = Icons.Filled.FitnessCenter,
                    actionLabel = stringResource(R.string.workout_train_empty_action),
                    onAction = onCreateRoutine,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        } else {
            item(key = "header") {
                SectionHeader(
                    title = stringResource(R.string.workout_train_routines_header),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.routines, key = { it.id }) { routine ->
                RoutineCard(routine, onClick = { onOpenRoutine(routine.id) })
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
    HeroCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = OnHero, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.workout_in_progress_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = OnHero,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = session.routineName,
            style = MaterialTheme.typography.headlineMedium,
            color = OnHero,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.workout_in_progress_started_at, formatTime(session.startedAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = OnHero.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onResume,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = OnHero, contentColor = HeroButtonContent),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.workout_in_progress_resume))
            }
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, OnHero.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnHero),
            ) {
                Text(stringResource(R.string.workout_in_progress_discard))
            }
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineSummary, onClick: () -> Unit) {
    GymCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.FitnessCenter)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                routine.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (routine.groupNames.isEmpty()) {
            Text(
                stringResource(R.string.workout_routine_no_groups),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                routine.groupNames.forEach { Pill(it) }
            }
        }
    }
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
