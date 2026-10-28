package com.zambudio.registropesos.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.util.formatKg
import com.zambudio.registropesos.domain.model.AppSettings

@Composable
fun MoreRoute(
    onOpenBackup: () -> Unit,
    onOpenMuscleGroups: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MoreScreen(
        state = state,
        onOpenBackup = onOpenBackup,
        onOpenMuscleGroups = onOpenMuscleGroups,
        onOpenHistory = onOpenHistory,
        onOpenAbout = onOpenAbout,
        onWeightStepSelected = viewModel::setWeightStep,
        onDefaultSetsChanged = viewModel::setDefaultSets,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    state: MoreUiState,
    onOpenBackup: () -> Unit,
    onOpenMuscleGroups: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
    onWeightStepSelected: (Double) -> Unit,
    onDefaultSetsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_more_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            NavigationEntry(
                icon = Icons.Filled.SaveAlt,
                title = stringResource(R.string.settings_backup),
                summary = stringResource(R.string.settings_backup_summary),
                onClick = onOpenBackup,
            )
            NavigationEntry(
                icon = Icons.Filled.Category,
                title = stringResource(R.string.settings_muscle_groups),
                summary = stringResource(R.string.settings_muscle_groups_summary),
                onClick = onOpenMuscleGroups,
            )
            NavigationEntry(
                icon = Icons.Filled.History,
                title = stringResource(R.string.settings_history),
                summary = stringResource(R.string.settings_history_summary),
                onClick = onOpenHistory,
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.settings_section_preferences),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            when {
                state.isLoading -> LoadingState(modifier = Modifier.padding(16.dp))
                state.isError -> ErrorState(message = stringResource(R.string.settings_error))
                else -> SettingsSection(state.settings, onWeightStepSelected, onDefaultSetsChanged)
            }
            HorizontalDivider()
            NavigationEntry(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about),
                summary = null,
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    settings: AppSettings,
    onWeightStepSelected: (Double) -> Unit,
    onDefaultSetsChanged: (Int) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_weight_step)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_weight_step_summary))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoreViewModel.WEIGHT_STEPS_KG.forEach { step ->
                        FilterChip(
                            selected = settings.weightStepKg == step,
                            onClick = { onWeightStepSelected(step) },
                            label = { Text(stringResource(R.string.settings_weight_step_option, formatKg(step))) },
                        )
                    }
                }
            }
        },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_default_sets)) },
        supportingContent = { Text(stringResource(R.string.settings_default_sets_summary)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onDefaultSetsChanged(settings.defaultSets - 1) },
                    enabled = settings.defaultSets > MoreViewModel.DEFAULT_SETS_RANGE.first,
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = stringResource(R.string.settings_default_sets_decrease),
                    )
                }
                Text(
                    settings.defaultSets.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                IconButton(
                    onClick = { onDefaultSetsChanged(settings.defaultSets + 1) },
                    enabled = settings.defaultSets < MoreViewModel.DEFAULT_SETS_RANGE.last,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_default_sets_increase))
                }
            }
        },
    )
}

@Composable
private fun NavigationEntry(icon: ImageVector, title: String, summary: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenPreview() {
    GymTheme {
        MoreScreen(
            state = MoreUiState(isLoading = false),
            onOpenBackup = {},
            onOpenMuscleGroups = {},
            onOpenHistory = {},
            onOpenAbout = {},
            onWeightStepSelected = {},
            onDefaultSetsChanged = {},
        )
    }
}
