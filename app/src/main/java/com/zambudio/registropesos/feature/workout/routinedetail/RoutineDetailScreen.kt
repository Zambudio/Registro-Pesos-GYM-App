package com.zambudio.registropesos.feature.workout.routinedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.zambudio.registropesos.core.ui.CardSpacing
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.HeroCard
import com.zambudio.registropesos.core.ui.IconBadge
import com.zambudio.registropesos.core.ui.ListContentPadding
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.NumberBadge
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.core.ui.OnHero
import com.zambudio.registropesos.core.ui.ScreenPadding
import com.zambudio.registropesos.core.ui.SectionHeader
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
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
                    if (content == null) Text(stringResource(R.string.workout_routine_detail_title))
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
            if (content != null && content.groups.isNotEmpty()) {
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
                if (state.groups.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.workout_routine_empty_title),
                        message = stringResource(R.string.workout_routine_empty_message),
                        actionLabel = stringResource(R.string.workout_edit_routine),
                        onAction = onEditRoutine,
                        modifier = contentModifier,
                    )
                } else {
                    RoutineOverview(
                        name = state.detail.routine.name,
                        comment = state.detail.routine.subtitle,
                        groups = state.groups,
                        modifier = contentModifier,
                    )
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ScreenPadding, vertical = 12.dp),
        ) {
            Button(
                onClick = onStart,
                enabled = enabled,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
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

/** Lo que se va a trabajar: cabecera con el nombre, comentario de la rutina (si hay) y grupos en orden. */
@Composable
private fun RoutineOverview(
    name: String,
    comment: String?,
    groups: List<MuscleGroup>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = ListContentPadding,
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        item(key = "hero") {
            HeroCard(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.workout_routine_detail_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnHero.copy(alpha = 0.85f),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnHero,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.workout_routine_groups_count, groups.size, groups.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnHero.copy(alpha = 0.85f),
                )
            }
        }
        if (!comment.isNullOrBlank()) {
            item(key = "comment") {
                GymCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            Icons.AutoMirrored.Filled.StickyNote2,
                            size = 36.dp,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.workout_routine_comment_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(text = comment, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item(key = "groups-header") {
            SectionHeader(
                title = stringResource(R.string.workout_routine_groups_header),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
            GymCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberBadge(index + 1)
                    Spacer(Modifier.width(14.dp))
                    Text(text = group.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item(key = "hint") {
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.workout_routine_exercises_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineDetailScreenPreview() {
    val chest = MuscleGroup("g1", "Pecho", 0, true, 0L, 0L)
    val triceps = MuscleGroup("g2", "Tríceps", 1, true, 0L, 0L)
    val detail = RoutineDetail(
        routine = Routine("r1", "Día 1", "Pecho: 4 ejercicios · Tríceps: 2 ejercicios", 0, 0L, 0L),
        blocks = listOf(
            RoutineBlockDetail(RoutineBlock("b1", "r1", "g1", 0, 0L, 0L), chest, emptyList()),
            RoutineBlockDetail(RoutineBlock("b2", "r1", "g2", 1, 0L, 0L), triceps, emptyList()),
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
