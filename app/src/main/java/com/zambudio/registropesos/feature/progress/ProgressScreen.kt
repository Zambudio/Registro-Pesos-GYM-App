package com.zambudio.registropesos.feature.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.EmptyState
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.util.formatDate
import com.zambudio.registropesos.core.util.formatKgWithUnit
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.PersonalRecord
import com.zambudio.registropesos.domain.usecase.ProgressRange

@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(
        state = state,
        callbacks = ProgressCallbacks(
            onTabSelected = viewModel::selectTab,
            onRangeSelected = viewModel::selectRange,
            onExerciseGroupSelected = viewModel::selectExerciseGroup,
            onExerciseSelected = viewModel::selectExercise,
            onRecordClick = viewModel::openExercise,
            onShowAllRecords = viewModel::clearExercise,
            onChartGroupSelected = viewModel::selectChartGroup,
            onRetry = viewModel::retry,
        ),
    )
}

/** Acciones de la pantalla agrupadas para no arrastrar ocho lambdas por cada composable. */
data class ProgressCallbacks(
    val onTabSelected: (ProgressTab) -> Unit = {},
    val onRangeSelected: (ProgressRange) -> Unit = {},
    val onExerciseGroupSelected: (String) -> Unit = {},
    val onExerciseSelected: (String) -> Unit = {},
    val onRecordClick: (exerciseId: String, muscleGroupId: String) -> Unit = { _, _ -> },
    val onShowAllRecords: () -> Unit = {},
    val onChartGroupSelected: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(state: ProgressUiState, callbacks: ProgressCallbacks, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                ProgressTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { callbacks.onTabSelected(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            RangeChips(selected = state.range, onSelected = callbacks.onRangeSelected)
            when {
                state.isLoading -> LoadingState()
                state.isError -> ErrorState(
                    message = stringResource(R.string.progress_error),
                    onRetry = callbacks.onRetry,
                )
                state.groups.isEmpty() -> EmptyState(title = stringResource(R.string.progress_empty_groups))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.tab) {
                        ProgressTab.EXERCISE -> exerciseTab(state, callbacks)
                        ProgressTab.GROUP -> groupTab(state, callbacks)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.exerciseTab(state: ProgressUiState, callbacks: ProgressCallbacks) {
    item(key = "selectors") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupSelector(state.groups, state.exerciseGroupId, callbacks.onExerciseGroupSelected)
            ExerciseSelector(state, callbacks.onExerciseSelected)
        }
    }
    val selected = state.selectedExercise
    if (selected == null) {
        recordsList(state.records, callbacks.onRecordClick)
        return
    }
    item(key = "show_all") {
        TextButton(onClick = callbacks.onShowAllRecords) { Text(stringResource(R.string.progress_show_all_records)) }
    }
    state.selectedRecord?.let { record ->
        item(key = "selected_record") {
            RecordCardView(selected.name, groupName = null, record = record, onClick = null)
        }
    }
    if (state.exercisePoints.isEmpty()) {
        item(key = "empty_points") {
            val message = if (state.selectedRecord == null) {
                R.string.progress_empty_exercise
            } else {
                R.string.progress_empty_exercise_range
            }
            EmptyState(title = stringResource(message), icon = Icons.AutoMirrored.Filled.ShowChart)
        }
        return
    }
    val labels = state.exercisePoints.map { chartDateLabel(it.startedAt) }
    val charts = listOf(
        R.string.progress_chart_max_weight to state.exercisePoints.map { it.maxWeightKg },
        R.string.progress_chart_volume to state.exercisePoints.map { it.volumeKg },
        R.string.progress_chart_e1rm to state.exercisePoints.map { it.bestE1rmKg },
    )
    charts.forEach { (titleRes, values) ->
        item(key = "chart_$titleRes") {
            ChartCard(
                title = stringResource(titleRes),
                labels = labels,
                values = values,
                latestText = formatKgWithUnit(values.last()),
                kind = ChartKind.LINE,
            )
        }
    }
}

private fun LazyListScope.recordsList(records: List<RecordCard>, onRecordClick: (String, String) -> Unit) {
    if (records.isEmpty()) {
        item(key = "empty_records") {
            EmptyState(
                title = stringResource(R.string.progress_empty_records),
                message = stringResource(R.string.progress_empty_records_message),
                icon = Icons.Filled.EmojiEvents,
            )
        }
        return
    }
    item(key = "records_title") {
        Text(stringResource(R.string.progress_records_title), style = MaterialTheme.typography.titleMedium)
    }
    items(records, key = { it.exerciseId }) { card ->
        RecordCardView(
            exerciseName = card.exerciseName,
            groupName = card.groupName,
            record = card.record,
            onClick = { onRecordClick(card.exerciseId, card.muscleGroupId) },
        )
    }
}

private fun LazyListScope.groupTab(state: ProgressUiState, callbacks: ProgressCallbacks) {
    item(key = "group_selector") {
        GroupSelector(state.groups, state.chartGroupId, callbacks.onChartGroupSelected)
    }
    if (state.weekly.isEmpty()) {
        item(key = "empty_group") {
            EmptyState(
                title = stringResource(R.string.progress_empty_group),
                icon = Icons.AutoMirrored.Filled.ShowChart,
            )
        }
        return
    }
    val labels = state.weekly.map { chartDateLabel(it.weekStartMillis) }
    item(key = "weekly_volume") {
        val values = state.weekly.map { it.volumeKg }
        ChartCard(
            title = stringResource(R.string.progress_chart_weekly_volume),
            labels = labels,
            values = values,
            latestText = formatKgWithUnit(values.last()),
            kind = ChartKind.COLUMN,
        )
    }
    item(key = "weekly_sessions") {
        val values = state.weekly.map { it.sessionCount.toDouble() }
        ChartCard(
            title = stringResource(R.string.progress_chart_weekly_sessions),
            labels = labels,
            values = values,
            latestText = state.weekly.last().sessionCount.toString(),
            kind = ChartKind.COLUMN,
        )
    }
}

@Composable
private fun RangeChips(selected: ProgressRange, onSelected: (ProgressRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelected(range) },
                label = { Text(stringResource(range.labelRes())) },
            )
        }
    }
}

@Composable
private fun GroupSelector(groups: List<MuscleGroup>, selectedId: String?, onSelected: (String) -> Unit) {
    SelectorField(
        label = stringResource(R.string.progress_group_label),
        value = groups.firstOrNull { it.id == selectedId }?.name,
        placeholder = stringResource(R.string.progress_select_placeholder),
        options = groups.map { it.id to it.name },
        onSelected = onSelected,
    )
}

@Composable
private fun ExerciseSelector(state: ProgressUiState, onSelected: (String) -> Unit) {
    val archivedFormat = stringResource(R.string.progress_exercise_archived)
    val options = state.exercises.map { option ->
        option.id to if (option.isArchived) archivedFormat.format(option.name) else option.name
    }
    SelectorField(
        label = stringResource(R.string.progress_exercise_label),
        value = state.selectedExercise?.name,
        placeholder = if (options.isEmpty()) {
            stringResource(R.string.progress_no_exercises_in_group)
        } else {
            stringResource(R.string.progress_exercise_placeholder)
        },
        options = options,
        onSelected = onSelected,
    )
}

/** Campo desplegable sencillo (evita la API cambiante de ExposedDropdownMenuBox). */
@Composable
private fun SelectorField(
    label: String,
    value: String?,
    placeholder: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(
            onClick = { expanded = true },
            enabled = options.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        value ?: placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelected(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecordCardView(exerciseName: String, groupName: String?, record: PersonalRecord, onClick: (() -> Unit)?) {
    val openDescription = stringResource(R.string.progress_record_open, exerciseName)
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                    if (!groupName.isNullOrBlank()) {
                        Text(
                            groupName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            RecordLine(R.string.progress_record_best_weight, record.bestWeightKg, record.bestWeightAt)
            RecordLine(R.string.progress_record_best_e1rm, record.bestE1rmKg, record.bestE1rmAt)
        }
    }
    if (onClick == null) {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = openDescription },
        ) { content() }
    }
}

@Composable
private fun RecordLine(@StringRes labelRes: Int, valueKg: Double, atMillis: Long) {
    Row {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.progress_record_value_date, formatKgWithUnit(valueKg), formatDate(atMillis)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@StringRes
private fun ProgressTab.labelRes(): Int = when (this) {
    ProgressTab.EXERCISE -> R.string.progress_tab_exercise
    ProgressTab.GROUP -> R.string.progress_tab_group
}

@StringRes
private fun ProgressRange.labelRes(): Int = when (this) {
    ProgressRange.M1 -> R.string.progress_range_1m
    ProgressRange.M3 -> R.string.progress_range_3m
    ProgressRange.M6 -> R.string.progress_range_6m
    ProgressRange.Y1 -> R.string.progress_range_1y
    ProgressRange.ALL -> R.string.progress_range_all
}

@Preview(showBackground = true)
@Composable
private fun ProgressScreenRecordsPreview() {
    val group = MuscleGroup("g1", "Pecho", 0, true, 0L, 0L)
    GymTheme {
        ProgressScreen(
            state = ProgressUiState(
                isLoading = false,
                groups = listOf(group),
                exerciseGroupId = "g1",
                records = listOf(
                    RecordCard(
                        exerciseId = "e1",
                        muscleGroupId = "g1",
                        exerciseName = "Press banca",
                        groupName = "Pecho",
                        record = PersonalRecord("e1", 80.0, 1_760_000_000_000L, 93.3, 1_760_000_000_000L),
                    ),
                ),
            ),
            callbacks = ProgressCallbacks(),
        )
    }
}
