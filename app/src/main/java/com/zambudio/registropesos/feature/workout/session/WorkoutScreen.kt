package com.zambudio.registropesos.feature.workout.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.core.ui.Pill
import com.zambudio.registropesos.core.ui.ScreenPadding
import com.zambudio.registropesos.domain.model.RestTimerState
import com.zambudio.registropesos.domain.usecase.GhostSet
import com.zambudio.registropesos.feature.workout.common.formatCountdown
import com.zambudio.registropesos.feature.workout.common.formatElapsed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TICK_MILLIS = 1_000L

@Composable
fun WorkoutRoute(
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit,
    onCreateExercise: (muscleGroupId: String) -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.workout_error_save)
    val finishFailed = stringResource(R.string.workout_error_finish)
    val discardFailed = stringResource(R.string.workout_error_discard)
    val setRemoved = stringResource(R.string.workout_set_removed)
    val exerciseRemoved = stringResource(R.string.workout_exercise_removed)
    val undo = stringResource(R.string.workout_undo)

    // La pantalla no se apaga mientras se entrena.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    RequestNotificationPermission()

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
            is WorkoutEvent.ExerciseRemoved -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(exerciseRemoved, undo, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) viewModel.onUndoRemoveExercise(event.exerciseId)
            }
        }
    }

    val actions = remember(viewModel) {
        WorkoutActions(
            onToggleExercise = viewModel::onToggleExercise,
            onWeightChange = viewModel::onWeightChange,
            onRepsChange = viewModel::onRepsChange,
            onStepWeight = viewModel::onStepWeight,
            onAddSet = viewModel::onAddSet,
            onRemoveSet = { exerciseId -> viewModel.onRemoveSet(exerciseId) },
            onCopyPrevious = viewModel::onCopyPrevious,
            onOpenPicker = viewModel::onOpenPicker,
            onDismissPicker = viewModel::onDismissPicker,
            onPickExercise = viewModel::onPickExercise,
            onCreateExercise = { groupId ->
                viewModel.onDismissPicker()
                onCreateExercise(groupId)
            },
            onRemoveExercise = viewModel::onRemoveExercise,
            onStartRest = viewModel::onStartRest,
            onAdjustRest = viewModel::onAdjustRest,
            onStopRest = viewModel::onStopRest,
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

/**
 * El aviso de fin de descanso es una notificación: en Android 13+ se pide el permiso al entrar en la
 * sesión (una vez por pantalla). Si se deniega, el descanso sigue funcionando dentro de la app.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Callbacks de la sesión (agrupados para no arrastrar diez parámetros por cada composable). */
class WorkoutActions(
    val onToggleExercise: (exerciseId: String) -> Unit = {},
    val onWeightChange: (setId: String, text: String) -> Unit = { _, _ -> },
    val onRepsChange: (setId: String, text: String) -> Unit = { _, _ -> },
    val onStepWeight: (setId: String, increase: Boolean) -> Unit = { _, _ -> },
    val onAddSet: (exerciseId: String) -> Unit = {},
    val onRemoveSet: (exerciseId: String) -> Unit = {},
    val onCopyPrevious: (exerciseId: String) -> Unit = {},
    val onOpenPicker: (muscleGroupId: String) -> Unit = {},
    val onDismissPicker: () -> Unit = {},
    val onPickExercise: (exerciseId: String) -> Unit = {},
    val onCreateExercise: (muscleGroupId: String) -> Unit = {},
    val onRemoveExercise: (exerciseId: String) -> Unit = {},
    val onStartRest: () -> Unit = {},
    val onAdjustRest: (deltaSeconds: Int) -> Unit = {},
    val onStopRest: () -> Unit = {},
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
    var confirmRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    // Quitar un ejercicio sin datos es inmediato (con "Deshacer"); con datos se confirma antes.
    val requestRemove: (WorkoutExerciseUi) -> Unit = { exercise ->
        if (exercise.hasData) confirmRemoveId = exercise.exerciseId else actions.onRemoveExercise(exercise.exerciseId)
    }

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
                BottomPanel(content, actions, onFinish = { confirmFinish = true })
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
            is WorkoutUiState.Content -> WorkoutContent(state, actions, padding, requestRemove)
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
    val removing = confirmRemoveId?.let { id ->
        content?.blocks?.flatMap { it.exercises }?.firstOrNull { it.exerciseId == id }
    }
    if (removing != null) {
        ConfirmDialog(
            title = stringResource(R.string.workout_remove_exercise_title, removing.name),
            message = stringResource(R.string.workout_remove_exercise_message),
            confirmLabel = stringResource(R.string.workout_remove_exercise_confirm),
            onConfirm = {
                confirmRemoveId = null
                actions.onRemoveExercise(removing.exerciseId)
            },
            onDismiss = { confirmRemoveId = null },
        )
    }
    content?.picker?.let { picker ->
        ExercisePickerDialog(
            picker = picker,
            onPick = actions.onPickExercise,
            onCreateExercise = { actions.onCreateExercise(picker.muscleGroupId) },
            onDismiss = actions.onDismissPicker,
        )
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
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = formatElapsed(now - startedAt),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Zona inferior fija: descanso entre series y "Terminar entrenamiento". */
@Composable
private fun BottomPanel(content: WorkoutUiState.Content, actions: WorkoutActions, onFinish: () -> Unit) {
    val enabled = !content.isClosing
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
            val rest = content.rest
            if (rest != null) {
                RestRunning(rest, enabled, actions)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (rest == null) {
                    RestStartButton(content.restDefaultSeconds, enabled, actions.onStartRest)
                }
                Button(
                    onClick = onFinish,
                    enabled = enabled,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.workout_finish_action),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Arranque manual del descanso con la duración de Ajustes. */
@Composable
private fun RestStartButton(defaultSeconds: Int, enabled: Boolean, onStart: () -> Unit) {
    val label = formatCountdown(defaultSeconds * MILLIS_PER_SECOND)
    val description = stringResource(R.string.workout_rest_start, label)
    FilledTonalButton(
        onClick = onStart,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = description },
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Cuenta atrás en marcha: progreso, tiempo restante y ajustes rápidos. */
@Composable
private fun RestRunning(rest: RestTimerState, enabled: Boolean, actions: WorkoutActions) {
    val now by produceState(System.currentTimeMillis(), rest) {
        while (true) {
            value = System.currentTimeMillis()
            delay(REST_TICK_MILLIS)
        }
    }
    val remaining = rest.remainingMillis(now)
    val progress = if (rest.durationMillis > 0) remaining.toFloat() / rest.durationMillis else 0f
    val finished = remaining == 0L
    val remainingText = formatCountdown(remaining)
    val description = stringResource(R.string.workout_rest_remaining, remainingText)
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Text(
                    text = stringResource(R.string.workout_rest_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (finished) stringResource(R.string.workout_rest_over) else remainingText,
                    style = with(MaterialTheme.typography) { if (finished) titleLarge else displaySmall },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!finished) {
                RestAdjustButton(
                    label = stringResource(R.string.workout_rest_minus_label, REST_STEP_SECONDS),
                    description = stringResource(R.string.workout_rest_minus, REST_STEP_SECONDS),
                    enabled = enabled,
                    onClick = { actions.onAdjustRest(-REST_STEP_SECONDS) },
                )
                RestAdjustButton(
                    label = stringResource(R.string.workout_rest_plus_label, REST_STEP_SECONDS),
                    description = stringResource(R.string.workout_rest_plus, REST_STEP_SECONDS),
                    enabled = enabled,
                    onClick = { actions.onAdjustRest(REST_STEP_SECONDS) },
                )
            }
            IconButton(onClick = actions.onStopRest) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workout_rest_skip))
            }
        }
    }
}

@Composable
private fun RestAdjustButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private const val REST_TICK_MILLIS = 250L
private const val REST_STEP_SECONDS = 15
private const val MILLIS_PER_SECOND = 1_000L

@Composable
private fun WorkoutContent(
    state: WorkoutUiState.Content,
    actions: WorkoutActions,
    padding: PaddingValues,
    onRequestRemove: (WorkoutExerciseUi) -> Unit,
) {
    Column(
        // El teclado tapa la barra inferior; la lista se encoge para que el campo activo siga visible.
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .consumeWindowInsets(padding)
            .imePadding(),
    ) {
        // Fijo encima de la lista: el recordatorio se ve siempre, también al reanudar.
        state.reminder?.let { ReminderBanner(it) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
                start = ScreenPadding,
                end = ScreenPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.blocks.forEach { block ->
                item(key = block.key) {
                    GroupHeader(block, enabled = !state.isClosing, onAdd = actions.onOpenPicker)
                }
                val groupId = block.muscleGroupId
                if (block.exercises.isEmpty() && groupId != null) {
                    item(key = "${block.key}-empty") {
                        EmptyGroupCard(
                            groupName = block.muscleGroupName,
                            enabled = !state.isClosing,
                            onClick = { actions.onOpenPicker(groupId) },
                        )
                    }
                }
                items(block.exercises, key = { it.exerciseId }) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        expanded = exercise.exerciseId == state.expandedExerciseId,
                        weightStepKg = state.weightStepKg,
                        enabled = !state.isClosing,
                        actions = actions,
                        onRequestRemove = { onRequestRemove(exercise) },
                    )
                }
            }
        }
    }
}

/** Comentario de la rutina, fijo arriba durante toda la sesión. */
@Composable
private fun ReminderBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.StickyNote2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                stringResource(R.string.workout_reminder_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = REMINDER_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val REMINDER_MAX_LINES = 3

@Composable
private fun GroupHeader(block: WorkoutBlockUi, enabled: Boolean, onAdd: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = block.muscleGroupName, style = MaterialTheme.typography.headlineSmall)
                if (block.exercises.isNotEmpty()) {
                    Pill(
                        text = block.exercises.size.toString(),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (!block.canAddExercise && block.exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_group_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val groupId = block.muscleGroupId
        if (groupId != null) {
            FilledIconButton(
                onClick = { onAdd(groupId) },
                enabled = enabled,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.workout_group_add, block.muscleGroupName),
                )
            }
        }
    }
}

/** Grupo todavía sin ejercicios: invita a elegir el primero (también abre el selector). */
@Composable
private fun EmptyGroupCard(groupName: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AddCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.workout_group_empty, groupName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutScreenPreview() {
    val sets = listOf(
        WorkoutSetUi("s1", 1, 80.0, 8, 77.5, 8),
        WorkoutSetUi("s2", 2, 82.5, null, 77.5, 8),
        WorkoutSetUi("s3", 3, null, null, 75.0, 6),
    )
    GymTheme {
        WorkoutScreen(
            state = WorkoutUiState.Content(
                sessionId = "s",
                routineName = "Día 1",
                startedAt = 0L,
                reminder = "Pecho: 4 ejercicios · Tríceps: 2 ejercicios",
                blocks = listOf(
                    WorkoutBlockUi(
                        "g1",
                        "Pecho",
                        "pecho",
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
                    WorkoutBlockUi("g2", "Tríceps", "triceps", emptyList()),
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
