package com.zambudio.registropesos.feature.routines.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.zambudio.registropesos.core.ui.GymExtendedFab
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.core.ui.Pill
import com.zambudio.registropesos.core.ui.ScreenPadding
import com.zambudio.registropesos.domain.model.RoutineSummary
import kotlinx.coroutines.launch

@Composable
fun RoutinesRoute(
    onOpenRoutine: (String) -> Unit,
    onCreateRoutine: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.eventFlow) { event ->
        val message = when (event) {
            RoutinesEvent.ReorderFailed -> R.string.routines_reorder_failed
            RoutinesEvent.DeleteFailed -> R.string.routines_delete_failed
        }
        scope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
    }
    RoutinesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenRoutine = onOpenRoutine,
        onCreateRoutine = onCreateRoutine,
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onDeleteClick = viewModel::onDeleteClick,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onRetry = viewModel::onRetry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    state: RoutinesUiState,
    onOpenRoutine: (String) -> Unit,
    onCreateRoutine: () -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val showContent = !state.isLoading && !state.hasError && state.routines.isNotEmpty()
    Scaffold(
        modifier = modifier,
        // El Scaffold raíz ya aplica el inset inferior (barra de navegación).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.routines_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showContent) {
                GymExtendedFab(
                    text = stringResource(R.string.routines_new),
                    icon = Icons.Filled.Add,
                    onClick = onCreateRoutine,
                )
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingState(contentModifier)
            state.hasError -> ErrorState(modifier = contentModifier, onRetry = onRetry)
            state.routines.isEmpty() -> EmptyState(
                title = stringResource(R.string.routines_empty_title),
                message = stringResource(R.string.routines_empty_message),
                icon = Icons.Filled.FitnessCenter,
                actionLabel = stringResource(R.string.routines_empty_action),
                onAction = onCreateRoutine,
                modifier = contentModifier,
            )
            else -> RoutineList(
                routines = state.routines,
                onOpenRoutine = onOpenRoutine,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDeleteClick = onDeleteClick,
                modifier = contentModifier,
            )
        }
    }

    state.pendingDelete?.let { routine ->
        ConfirmDialog(
            title = stringResource(R.string.routines_delete_title),
            message = stringResource(R.string.routines_delete_message, routine.name),
            confirmLabel = stringResource(R.string.routines_delete_confirm),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun RoutineList(
    routines: List<RoutineSummary>,
    onOpenRoutine: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        // Hueco inferior para que el FAB no tape la última tarjeta.
        contentPadding = PaddingValues(start = ScreenPadding, top = 4.dp, end = ScreenPadding, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        itemsIndexed(routines, key = { _, routine -> routine.id }) { index, routine ->
            RoutineCard(
                routine = routine,
                canMoveUp = index > 0,
                canMoveDown = index < routines.lastIndex,
                onClick = { onOpenRoutine(routine.id) },
                onMoveUp = { onMoveUp(routine.id) },
                onMoveDown = { onMoveDown(routine.id) },
                onDelete = { onDeleteClick(routine.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Tarjeta de rutina: nombre en grande, comentario, grupos en una píldora y acciones al pie. */
@Composable
private fun RoutineCard(
    routine: RoutineSummary,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GymCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 4.dp),
    ) {
        Text(
            routine.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
        routine.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, end = 12.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val hasGroups = routine.groupNames.isNotEmpty()
            val colors = MaterialTheme.colorScheme
            // La píldora ocupa lo que necesita y las acciones quedan siempre a la derecha.
            Box(Modifier.weight(1f)) {
                Pill(
                    text = if (hasGroups) {
                        routine.groupNames.joinToString(stringResource(R.string.routines_groups_separator))
                    } else {
                        stringResource(R.string.routines_no_groups)
                    },
                    icon = Icons.Filled.Category,
                    containerColor = if (hasGroups) colors.primaryContainer else colors.secondaryContainer,
                    contentColor = if (hasGroups) colors.onPrimaryContainer else colors.onSecondaryContainer,
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.routines_move_up, routine.name),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.routines_move_down, routine.name),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.routines_delete_named, routine.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val previewRoutines = listOf(
    RoutineSummary("r1", "Día 1", "Pecho: 4 ejercicios · Tríceps: 2", listOf("Pecho", "Tríceps")),
    RoutineSummary("r2", "Día 2", null, listOf("Espalda")),
    RoutineSummary("r3", "Día 3", "Pierna: 4 · Abdomen: 2", listOf("Pierna", "Abdomen")),
)

@Preview(showBackground = true)
@Composable
private fun RoutinesScreenPreview() {
    GymTheme {
        RoutinesScreen(
            state = RoutinesUiState(isLoading = false, routines = previewRoutines),
            onOpenRoutine = {},
            onCreateRoutine = {},
            onMoveUp = {},
            onMoveDown = {},
            onDeleteClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutinesScreenEmptyPreview() {
    GymTheme {
        RoutinesScreen(
            state = RoutinesUiState(isLoading = false),
            onOpenRoutine = {},
            onCreateRoutine = {},
            onMoveUp = {},
            onMoveDown = {},
            onDeleteClick = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onRetry = {},
        )
    }
}
