package com.zambudio.registropesos.feature.history.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.ListContentPadding
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.MetaItem
import com.zambudio.registropesos.core.ui.SectionHeader
import com.zambudio.registropesos.core.util.formatDuration
import com.zambudio.registropesos.core.util.formatKgWithUnit
import com.zambudio.registropesos.core.util.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Fechas de la lista: cabecera por mes ("octubre 2026") y bloque de calendario por sesión ("29" / "OCT").
private val SpanishLocale: Locale = Locale.forLanguageTag("es-ES")
private val MonthHeaderFormat = DateTimeFormatter.ofPattern("MMMM yyyy", SpanishLocale)
private val DayFormat = DateTimeFormatter.ofPattern("d", SpanishLocale)
private val MonthShortFormat = DateTimeFormatter.ofPattern("MMM", SpanishLocale)
private val DateBadgeSize = 56.dp

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
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
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
                SessionList(state.sessions, onOpenSession, contentModifier)
            }
        }
    }
}

@Composable
private fun SessionList(sessions: List<HistoryItemUi>, onOpenSession: (String) -> Unit, modifier: Modifier) {
    // Agrupa por mes solo para presentar; el orden lo decide el ViewModel.
    val byMonth = sessions.groupBy { monthHeader(it.startedAt) }
    LazyColumn(
        modifier = modifier,
        contentPadding = ListContentPadding,
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        byMonth.forEach { (month, items) ->
            item(key = "month-$month", contentType = "header") {
                SectionHeader(title = month, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
            items(items, key = { it.sessionId }, contentType = { "session" }) { item ->
                HistoryCard(item, onClick = { onOpenSession(item.sessionId) })
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItemUi, onClick: () -> Unit) {
    GymCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateBadge(item.startedAt)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.routineName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MetaItem(Icons.Outlined.Schedule, formatTime(item.startedAt))
                    item.durationMs?.let { MetaItem(Icons.Outlined.Timer, formatDuration(it)) }
                    MetaItem(
                        Icons.Filled.FitnessCenter,
                        pluralStringResource(R.plurals.history_exercise_count, item.exerciseCount, item.exerciseCount),
                    )
                    MetaItem(Icons.Outlined.Scale, formatKgWithUnit(item.volumeKg))
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

/** Bloque tipo calendario con el día y el mes abreviado de la sesión. */
@Composable
private fun DateBadge(epochMillis: Long) {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    Column(
        modifier = Modifier
            .size(DateBadgeSize)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            DayFormat.format(date),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            MonthShortFormat.format(date).trimEnd('.').uppercase(SpanishLocale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun monthHeader(epochMillis: Long): String =
    MonthHeaderFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

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
