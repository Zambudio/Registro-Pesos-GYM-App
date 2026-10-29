package com.zambudio.registropesos.feature.routines.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zambudio.registropesos.R
import com.zambudio.registropesos.domain.model.MuscleGroup

// Selector del editor: grupo muscular que se añade a la rutina (solo los que aún no están).

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
                Text(
                    stringResource(R.string.routines_group_picker_all_added),
                    style = MaterialTheme.typography.bodyMedium,
                )
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
