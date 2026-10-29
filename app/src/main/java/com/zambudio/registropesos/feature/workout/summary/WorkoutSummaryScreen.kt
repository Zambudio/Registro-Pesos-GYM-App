package com.zambudio.registropesos.feature.workout.summary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.CardSpacing
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.HeroCard
import com.zambudio.registropesos.core.ui.IconBadge
import com.zambudio.registropesos.core.ui.ListContentPadding
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.OnHero
import com.zambudio.registropesos.core.ui.SectionHeader
import com.zambudio.registropesos.core.ui.StatValue
import com.zambudio.registropesos.core.util.formatDuration
import com.zambudio.registropesos.core.util.formatKgWithUnit
import com.zambudio.registropesos.domain.usecase.NewRecord
import com.zambudio.registropesos.domain.usecase.RecordType

@Composable
fun WorkoutSummaryRoute(
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Atrás también vuelve a Entrenar: la sesión ya no está en la pila.
    BackHandler(onBack = onDone)
    WorkoutSummaryScreen(state = state, onDone = onDone, onRetry = viewModel::retry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    state: WorkoutSummaryUiState,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.workout_summary_title)) }) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = onDone,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_summary_done),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            WorkoutSummaryUiState.Loading -> LoadingState(contentModifier)
            WorkoutSummaryUiState.Error -> ErrorState(contentModifier, onRetry = onRetry)
            is WorkoutSummaryUiState.Content -> SummaryContent(state, contentModifier)
        }
    }
}

@Composable
private fun SummaryContent(state: WorkoutSummaryUiState.Content, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = ListContentPadding,
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        item(key = "routine") {
            HeroCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = OnHero,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.workout_summary_completed).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = OnHero,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    state.routineName,
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnHero,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item(key = "stats") {
            Column(verticalArrangement = Arrangement.spacedBy(CardSpacing)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                    StatCard(
                        icon = Icons.Outlined.Timer,
                        label = stringResource(R.string.workout_summary_duration),
                        value = formatDuration(state.durationMs),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Outlined.FitnessCenter,
                        label = stringResource(R.string.workout_summary_volume),
                        value = formatKgWithUnit(state.volumeKg),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                    StatCard(
                        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        label = stringResource(R.string.workout_summary_exercises),
                        value = state.exerciseCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Outlined.Repeat,
                        label = stringResource(R.string.workout_summary_sets),
                        value = state.setCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "records-header") {
            SectionHeader(stringResource(R.string.workout_summary_records), Modifier.padding(top = 8.dp))
        }
        if (state.newRecords.isEmpty()) {
            item(key = "records-empty") {
                Text(
                    text = stringResource(R.string.workout_summary_no_records),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.newRecords, key = { "${it.exerciseId}-${it.type}" }) { record -> RecordCard(record) }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    GymCard(modifier) {
        IconBadge(icon, size = 36.dp)
        Spacer(Modifier.height(12.dp))
        StatValue(value = value, label = label)
    }
}

@Composable
private fun RecordCard(record: NewRecord) {
    val type = stringResource(
        when (record.type) {
            RecordType.WEIGHT -> R.string.workout_record_weight
            RecordType.E1RM -> R.string.workout_record_e1rm
        },
    )
    GymCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Filled.EmojiEvents,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(record.exerciseName, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.workout_record_change,
                        type,
                        formatKgWithUnit(record.previousValueKg),
                        formatKgWithUnit(record.newValueKg),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkoutSummaryScreenPreview() {
    GymTheme {
        WorkoutSummaryScreen(
            state = WorkoutSummaryUiState.Content(
                routineName = "Pecho - Tríceps",
                durationMs = 3_900_000L,
                exerciseCount = 6,
                setCount = 22,
                volumeKg = 8_450.0,
                newRecords = listOf(NewRecord("e1", "Press banca", RecordType.WEIGHT, 80.0, 85.0)),
            ),
            onDone = {},
            onRetry = {},
        )
    }
}
