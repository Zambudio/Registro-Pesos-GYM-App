package com.zambudio.registropesos.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.GymCard
import com.zambudio.registropesos.core.ui.IconBadge
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ScreenPadding
import com.zambudio.registropesos.core.ui.SectionHeader
import com.zambudio.registropesos.core.util.formatKg
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.ThemeMode
import com.zambudio.registropesos.feature.workout.common.formatCountdown

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
        onThemeModeSelected = viewModel::setThemeMode,
        onRestSecondsSelected = viewModel::setRestSeconds,
        onRestAutoStartChanged = viewModel::setRestAutoStart,
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
    onThemeModeSelected: (ThemeMode) -> Unit,
    onRestSecondsSelected: (Int) -> Unit,
    onRestAutoStartChanged: (Boolean) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_section_data), Modifier.padding(top = 4.dp))
            GymCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                NavigationEntry(
                    icon = Icons.Outlined.SaveAlt,
                    title = stringResource(R.string.settings_backup),
                    summary = stringResource(R.string.settings_backup_summary),
                    onClick = onOpenBackup,
                )
                EntryDivider()
                NavigationEntry(
                    icon = Icons.Outlined.Category,
                    title = stringResource(R.string.settings_muscle_groups),
                    summary = stringResource(R.string.settings_muscle_groups_summary),
                    onClick = onOpenMuscleGroups,
                )
                EntryDivider()
                NavigationEntry(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.settings_history),
                    summary = stringResource(R.string.settings_history_summary),
                    onClick = onOpenHistory,
                )
            }
            when {
                state.isLoading -> LoadingState(modifier = Modifier.padding(16.dp))
                state.isError -> ErrorState(message = stringResource(R.string.settings_error))
                else -> {
                    SectionHeader(stringResource(R.string.settings_section_appearance), Modifier.padding(top = 8.dp))
                    ThemeCard(state.settings.themeMode, onThemeModeSelected)
                    SectionHeader(stringResource(R.string.settings_section_preferences), Modifier.padding(top = 8.dp))
                    TrainingCard(state.settings, onWeightStepSelected, onDefaultSetsChanged)
                    SectionHeader(stringResource(R.string.settings_section_rest), Modifier.padding(top = 8.dp))
                    RestCard(state.settings, onRestSecondsSelected, onRestAutoStartChanged)
                }
            }
            GymCard(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                NavigationEntry(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.settings_about),
                    summary = null,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun ThemeCard(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.SYSTEM, R.string.settings_theme_system, Icons.Outlined.SettingsSuggest),
        Triple(ThemeMode.LIGHT, R.string.settings_theme_light, Icons.Outlined.LightMode),
        Triple(ThemeMode.DARK, R.string.settings_theme_dark, Icons.Outlined.DarkMode),
    )
    GymCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_theme_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label, icon) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                ) {
                    Text(stringResource(label), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TrainingCard(
    settings: AppSettings,
    onWeightStepSelected: (Double) -> Unit,
    onDefaultSetsChanged: (Int) -> Unit,
) {
    GymCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_weight_step), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_weight_step_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreViewModel.WEIGHT_STEPS_KG.forEach { step ->
                FilterChip(
                    selected = settings.weightStepKg == step,
                    onClick = { onWeightStepSelected(step) },
                    label = { Text(stringResource(R.string.settings_weight_step_option, formatKg(step))) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_default_sets), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_default_sets_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { onDefaultSetsChanged(settings.defaultSets - 1) },
                enabled = settings.defaultSets > MoreViewModel.DEFAULT_SETS_RANGE.first,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.settings_default_sets_decrease))
            }
            Text(
                settings.defaultSets.toString(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 36.dp),
            )
            FilledTonalIconButton(
                onClick = { onDefaultSetsChanged(settings.defaultSets + 1) },
                enabled = settings.defaultSets < MoreViewModel.DEFAULT_SETS_RANGE.last,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_default_sets_increase))
            }
        }
    }
}

@Composable
private fun RestCard(
    settings: AppSettings,
    onRestSecondsSelected: (Int) -> Unit,
    onRestAutoStartChanged: (Boolean) -> Unit,
) {
    GymCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_rest_duration), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_rest_duration_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreViewModel.REST_SECONDS_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = settings.restSeconds == seconds,
                    onClick = { onRestSecondsSelected(seconds) },
                    label = { Text(formatCountdown(seconds * MILLIS_PER_SECOND)) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = settings.restAutoStart,
                    role = Role.Switch,
                    onValueChange = onRestAutoStartChanged,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.settings_rest_auto), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_rest_auto_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // La fila entera es el interruptor (toggleable): el Switch solo lo pinta.
            Switch(checked = settings.restAutoStart, onCheckedChange = null)
        }
    }
}

private const val MILLIS_PER_SECOND = 1_000L

@Composable
private fun EntryDivider() {
    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun NavigationEntry(icon: ImageVector, title: String, summary: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, size = 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            onThemeModeSelected = {},
            onRestSecondsSelected = {},
            onRestAutoStartChanged = {},
        )
    }
}
