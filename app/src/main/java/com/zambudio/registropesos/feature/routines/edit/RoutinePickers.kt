package com.zambudio.registropesos.feature.routines.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zambudio.registropesos.R
import com.zambudio.registropesos.domain.model.MuscleGroup

// Selectores del editor: grupo muscular de un bloque nuevo y ejercicios (selección múltiple).

private val PickerListMaxHeight = 400.dp
private val RowMinHeight = 48.dp

@Composable
internal fun MuscleGroupPickerDialog(
    groups: List<MuscleGroup>,
    onSelect: (MuscleGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_group_picker_title)) },
        text = {
            if (groups.isEmpty()) {
                PickerProgress()
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = PickerListMaxHeight)) {
                    items(groups, key = { it.id }) { group ->
                        Text(
                            group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = RowMinHeight)
                                .clickable(role = Role.Button) { onSelect(group) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_picker_cancel)) }
        },
    )
}

@Composable
internal fun ExercisePickerDialog(
    picker: ExercisePickerUiState,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onCreateExercise: () -> Unit,
) {
    val groupName = picker.muscleGroup.name
    val selectedCount = picker.selectedIds.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_picker_title, groupName)) },
        text = {
            when {
                picker.isLoading -> PickerProgress()
                picker.available.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val message = if (picker.groupHasExercises) {
                        R.string.routines_picker_all_added
                    } else {
                        R.string.routines_picker_empty
                    }
                    Text(stringResource(message, groupName), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCreateExercise) {
                        Text(stringResource(R.string.routines_picker_create_exercise))
                    }
                }
                else -> LazyColumn(modifier = Modifier.heightIn(max = PickerListMaxHeight)) {
                    items(picker.available, key = { it.id }) { exercise ->
                        val checked = exercise.id in picker.selectedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = RowMinHeight)
                                .toggleable(value = checked, role = Role.Checkbox) { onToggle(exercise.id) },
                        ) {
                            // El Row gestiona el toque; el Checkbox es solo visual.
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (picker.available.isNotEmpty()) {
                TextButton(onClick = onConfirm, enabled = selectedCount > 0) {
                    Text(
                        if (selectedCount > 0) {
                            stringResource(R.string.routines_picker_add_count, selectedCount)
                        } else {
                            stringResource(R.string.routines_picker_add)
                        },
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.routines_picker_cancel)) }
        },
    )
}

@Composable
private fun PickerProgress() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
