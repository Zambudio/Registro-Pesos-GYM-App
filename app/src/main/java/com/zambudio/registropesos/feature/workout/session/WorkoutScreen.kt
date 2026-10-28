package com.zambudio.registropesos.feature.workout.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import com.zambudio.registropesos.domain.usecase.GhostSet
import com.zambudio.registropesos.feature.workout.common.formatElapsed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TICK_MILLIS = 1_000L

@Composable
fun WorkoutRoute(
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.workout_error_save)
    val finishFailed = stringResource(R.string.workout_error_finish)
    val discardFailed = stringResource(R.string.workout_error_discard)
    val setRemoved = stringResource(R.string.workout_set_removed)
    val undo = stringResource(R.string.workout_undo)

    // La pantalla no se apaga mientras se entrena.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is WorkoutEvent.OpenSummary -> onFinished(event.sessionId)
            WorkoutEvent.Discarded -> onDiscarded()
            WorkoutEvent.SaveFailed -> scope.launch { snackbarHostState.showSnackbar(saveFailed) }
            WorkoutEvent.FinishFailed -> scope.launch { snackbarHostState.showSnackbar(finishFailed) }
            WorkoutEvent.DiscardFailed -> scope.launch { snackbarHostState.showSnackbar(discardFailed) }
            is WorkoutEvent.SetRemoved -> scope.launch {
                // Solo se puede deshacer la última: un aviso anterior en pantalla ya no aplica.
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(setRemoved, undo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemoveSet(event.setId)
            }
        }
    }

    val actions = remember(viewModel) {
        WorkoutActions(
            onToggleExercise = viewModel::onToggleExercise,
            onWeightChange = viewModel::onWeightChange,
            onRepsChange = viewModel::onRepsChange,
            onToggleDone = viewModel::onToggleDone,
            onStepWeight = viewModel::onStepWeight,
            onAddSet = viewModel::onAddSet,
            onRemoveSet = { exerciseId -> viewModel.onRemoveSet(exerciseId) },
            onCopyPrevious = viewModel::onCopyPrevious,
        )
    }
    WorkoutScreen(
        state = state,
        actions = actions,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onFinish = viewModel::onFinish,
        onDiscard = viewModel::onDiscard,
        onRetry = viewModel::retry,
    )
}

/** Callbacks de edición de series (agrupados para no arrastrar diez parámetros por cada composable). */
class WorkoutActions(
    val onToggleExercise: (exerciseId: String) -> Unit = {},
    val onWeightChange: (setId: String, text: String) -> Unit = { _, _ -> },
    val onRepsChange: (setId: String, text: String) -> Unit = { _, _ -> },
    val onToggleDone: (setId: String) -> Unit = {},
    val onStepWeight: (setId: String, increase: Boolean) -> Unit = { _, _ -> },
    val onAddSet: (exerciseId: String) -> Unit = {},
    val onRemoveSet: (exerciseId: String) -> Unit = {},
    val onCopyPrevious: (exerciseId: String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutUiState,
    actions: WorkoutActions,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? WorkoutUiState.Content
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            WorkoutTopBar(
                content = content,
                onBack = onBack,
                onDiscard = { confirmDiscard = true },
            )
        },
        bottomBar = {
            if (content != null) {
                FinishBar(enabled = !content.isClosing, onFinish = { confirmFinish = true })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            WorkoutUiState.Loading -> LoadingState(contentModifier)
            WorkoutUiState.Error -> ErrorState(contentModifier, onRetry = onRetry)
            WorkoutUiState.NotInProgress -> EmptyState(
                title = stringResource(R.string.workout_not_in_progress),
                actionLabel = stringResource(R.string.common_back),
                onAction = onBack,
                modifier = contentModifier,
            )
            is WorkoutUiState.Content -> WorkoutContent(state, actions, padding)
        }
    }

    if (content != null && confirmFinish) {
        if (content.hasAnyData) {
            ConfirmDialog(
                title = stringResource(R.string.workout_finish_title),
                message = stringResource(R.string.workout_finish_message),
                confirmLabel = stringResource(R.string.workout_finish_confirm),
                onConfirm = {
                    confirmFinish = false
                    onFinish()
                },
                onDismiss = { confirmFinish = false },
            )
        } else {
            // Sin ninguna serie registrada, terminar dejaría una sesión vacía en el historial.
            ConfirmDialog(
                title = stringResource(R.string.workout_finish_empty_title),
                message = stringResource(R.string.workout_finish_empty_message),
                confirmLabel = stringResource(R.string.workout_discard_confirm),
                onConfirm = {
                    confirmFinish = false
                    onDiscard()
                },
                onDismiss = { confirmFinish = false },
            )
        }
    }
    if (content != null && confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.workout_discard_title),
            message = stringResource(R.string.workout_discard_message),
            confirmLabel = stringResource(R.string.workout_discard_confirm),
            onConfirm = {
                confirmDiscard = false
                onDiscard()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutTopBar(content: WorkoutUiState.Content?, onBack: () -> Unit, onDiscard: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(
                    text = content?.routineName ?: stringResource(R.string.workout_session_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (content != null) ElapsedText(content.startedAt)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
        },
        actions = {
            if (content != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workout_more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.workout_discard_action)) },
                            enabled = !content.isClosing,
                            onClick = {
                                menuOpen = false
                                onDiscard()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** Cronómetro desde el inicio de la sesión (reloj de la UI, se refresca cada segundo). */
@Composable
private fun ElapsedText(startedAt: Long) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
    }
    Text(
        text = formatElapsed(now - startedAt),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FinishBar(enabled: Boolean, onFinish: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                onClick = onFinish,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.workout_finish_action), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun WorkoutContent(state: WorkoutUiState.Content, actions: WorkoutActions, padding: PaddingValues) {
    LazyColumn(
        // El teclado tapa la barra inferior; la lista se encoge para que el campo activo siga visible.
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(padding)
            .imePadding(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        state.blocks.forEachIndexed { blockIndex, block ->
            item(key = "block-$blockIndex") {
                Text(
                    text = block.muscleGroupName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
                )
            }
            items(block.exercises, key = { it.exerciseId }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    expanded = exercise.exerciseId == state.expandedExerciseId,
                    weightStepKg = state.weightStepKg,
                    enabled = !state.isClosing,
                    actions = actions,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutScreenPreview() {
    val sets = listOf(
        WorkoutSetUi("s1", 1, 80.0, 8, true, 77.5, 8),
        WorkoutSetUi("s2", 2, 82.5, null, false, 77.5, 8),
        WorkoutSetUi("s3", 3, null, null, false, 75.0, 6),
    )
    GymTheme {
        WorkoutScreen(
            state = WorkoutUiState.Content(
                sessionId = "s",
                routineName = "Pecho - Tríceps",
                startedAt = 0L,
                blocks = listOf(
                    WorkoutBlockUi(
                        "Pecho",
                        listOf(
                            WorkoutExerciseUi(
                                "e1",
                                "Press banca",
                                sets,
                                listOf(GhostSet(0, 77.5, 8), GhostSet(1, 75.0, 6)),
                            ),
                            WorkoutExerciseUi("e2", "Aperturas", sets.take(2), emptyList()),
                        ),
                    ),
                ),
                expandedExerciseId = "e1",
                weightStepKg = 2.5,
                hasAnyData = true,
                isClosing = false,
            ),
            actions = WorkoutActions(),
            onBack = {},
            onFinish = {},
            onDiscard = {},
            onRetry = {},
        )
    }
}
