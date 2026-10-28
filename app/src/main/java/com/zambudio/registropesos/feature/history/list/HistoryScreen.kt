package com.zambudio.registropesos.feature.history.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.util.formatDateTime
import com.zambudio.registropesos.core.util.formatDuration
import com.zambudio.registropesos.core.util.formatKgWithUnit

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onBack = onBack, onOpenSession = onOpenSession, onRetry = viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            HistoryUiState.Loading -> LoadingState(contentModifier)
            HistoryUiState.Error -> ErrorState(contentModifier, onRetry = onRetry)
            is HistoryUiState.Content -> if (state.sessions.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.history_empty_title),
                    message = stringResource(R.string.history_empty_message),
                    icon = Icons.Filled.History,
                    modifier = contentModifier,
                )
            } else {
                LazyColumn(contentModifier) {
                    items(state.sessions, key = { it.sessionId }) { item ->
                        HistoryRow(item, onClick = { onOpenSession(item.sessionId) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItemUi, onClick: () -> Unit) {
    val details = listOfNotNull(
        item.durationMs?.let(::formatDuration),
        pluralStringResource(R.plurals.history_exercise_count, item.exerciseCount, item.exerciseCount),
        formatKgWithUnit(item.volumeKg),
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        overlineContent = { Text(formatDateTime(item.startedAt)) },
        headlineContent = { Text(item.routineName, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(details) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}

@Preview(showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    GymTheme {
        HistoryScreen(
            state = HistoryUiState.Content(
                listOf(HistoryItemUi("s1", "Pecho - Tríceps", 1_700_000_000_000L, 3_900_000L, 6, 8_450.0)),
            ),
            onBack = {},
            onOpenSession = {},
            onRetry = {},
        )
    }
}
